package com.gmailchecker.app.data

import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.channels.Channels
import java.nio.channels.ServerSocketChannel
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

data class OAuthLoginResult(
    val email: String,
    val refreshToken: String
)

/** The stored sign-in is no longer usable (revoked/expired/missing); the user must sign in again. */
class NeedsLoginException(message: String) : Exception(message)

/** Temporary server-side failure (HTTP 429/5xx); safe to retry later. */
class TransientApiException(message: String) : IOException(message)

class GoogleApiClient {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun performInteractiveOAuthLogin(
        clientId: String,
        clientSecret: String,
        loginHint: String?,
        openAuthUrlInBrowser: (String) -> Unit
    ): OAuthLoginResult = withContext(Dispatchers.IO) {
        require(clientId.isNotBlank()) {
            "Please configure your Google OAuth Client ID in Settings first."
        }

        val codeVerifier = randomUrlSafe(32)
        val state = randomUrlSafe(16)

        ServerSocketChannel.open().use { channel ->
            channel.bind(InetSocketAddress(InetAddress.getByName(LOOPBACK_HOST), 0))
            val redirectUri = "http://$LOOPBACK_HOST:${channel.socket().localPort}/callback"

            val authUri = Uri.parse(AUTH_URL).buildUpon()
                .appendQueryParameter("client_id", clientId.trim())
                .appendQueryParameter("redirect_uri", redirectUri)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("scope", SCOPES)
                .appendQueryParameter("access_type", "offline")
                .appendQueryParameter("prompt", "consent select_account")
                .appendQueryParameter("code_challenge", codeChallenge(codeVerifier))
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter("state", state)
                .apply { if (!loginHint.isNullOrBlank()) appendQueryParameter("login_hint", loginHint) }
                .build()
                .toString()

            withContext(Dispatchers.Main) {
                openAuthUrlInBrowser(authUri)
            }

            // runInterruptible + an interruptible channel lets both the timeout and user
            // cancellation unblock accept()/read immediately and release the port.
            val authCode = withTimeoutOrNull(SIGN_IN_TIMEOUT_MS) {
                runInterruptible { waitForLoopbackCallback(channel, state) }
            } ?: throw IllegalStateException("Sign-in timed out after 3 minutes.")

            val tokenJson = postForm(
                TOKEN_URL,
                "Token exchange",
                mapOf(
                    "client_id" to clientId.trim(),
                    "client_secret" to clientSecret.trim(),
                    "code" to authCode,
                    "redirect_uri" to redirectUri,
                    "grant_type" to "authorization_code",
                    "code_verifier" to codeVerifier
                )
            )

            val accessToken = tokenJson.optString("access_token").takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Google did not return an access_token.")
            val refreshToken = tokenJson.optString("refresh_token").takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Google did not return a refresh_token. Please try signing in again and grant consent.")

            OAuthLoginResult(email = fetchAccountEmail(accessToken), refreshToken = refreshToken)
        }
    }

    suspend fun performKeepAlive(
        email: String,
        refreshToken: String?,
        settings: AppSettings
    ): String = withContext(Dispatchers.IO) {
        check(settings.oauthClientId.isNotBlank()) { "Missing OAuth Client ID in Settings" }
        if (refreshToken.isNullOrBlank()) {
            throw NeedsLoginException("No saved sign-in for this account — tap Re-login")
        }

        val accessToken = postForm(
            TOKEN_URL,
            "Token refresh",
            mapOf(
                "client_id" to settings.oauthClientId.trim(),
                "client_secret" to settings.oauthClientSecret.trim(),
                "refresh_token" to refreshToken.trim(),
                "grant_type" to "refresh_token"
            )
        ).optString("access_token").takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("No access_token in refresh response")

        buildList {
            if (settings.actionReadGmail) {
                add("Gmail: ${readGmail(accessToken)} msgs in mailbox")
            }
            if (settings.actionSendSelfEmail) {
                add(if (sendAndTrashSelfEmail(accessToken, email)) "Self-email sent & trashed" else "Self-email sent (trash failed)")
            }
            if (settings.actionListDrive) {
                add("Drive: ${listDriveFiles(accessToken)} files listed")
            }
            if (isEmpty()) add("OAuth token refreshed")
        }.joinToString(" • ")
    }

    private fun waitForLoopbackCallback(channel: ServerSocketChannel, expectedState: String): String {
        while (true) {
            channel.accept().use { client ->
                val reader = BufferedReader(InputStreamReader(Channels.newInputStream(client), Charsets.UTF_8))
                val out = Channels.newOutputStream(client)
                val requestLine = reader.readLine() ?: return@use
                val path = requestLine.split(" ").getOrNull(1).orEmpty()

                if (!path.startsWith("/callback")) {
                    writeHttpResponse(out, 404, "Not found")
                    return@use
                }

                val uri = Uri.parse("http://$LOOPBACK_HOST$path")
                // Ignore anything that didn't come from our own authorization request
                // (e.g. another local app poking the port).
                if (uri.getQueryParameter("state") != expectedState) {
                    writeHttpResponse(out, 400, "Invalid state")
                    return@use
                }

                uri.getQueryParameter("error")?.takeIf { it.isNotBlank() }?.let { error ->
                    writeResultPage(out, "Sign-In Cancelled", "Google returned: $error")
                    throw IllegalStateException("Google sign-in error: $error")
                }

                uri.getQueryParameter("code")?.takeIf { it.isNotBlank() }?.let { code ->
                    writeResultPage(out, "Account Connected!", "Sign-in complete.")
                    return code
                }

                writeHttpResponse(out, 400, "Missing authorization code")
            }
        }
    }

    private fun writeResultPage(out: OutputStream, title: String, message: String) {
        val safeTitle = title.htmlEscape()
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <title>$safeTitle</title>
              <style>
                body { background:#0f172a; color:#f8fafc; font-family:sans-serif; text-align:center; padding:48px 20px; }
                .card { background:#1e293b; border:1px solid #334155; border-radius:16px; padding:24px; max-width:360px; margin:0 auto; }
                a.btn { display:inline-block; margin-top:18px; padding:12px 20px; background:#3b82f6; color:#fff; text-decoration:none; border-radius:10px; font-weight:bold; }
              </style>
              <script>
                setTimeout(function() { window.location.href = "$APP_RETURN_URI"; }, 400);
              </script>
            </head>
            <body>
              <div class="card">
                <h2>$safeTitle</h2>
                <p>${message.htmlEscape()}</p>
                <p>Tap the button below to return to Gmail Keep-Alive.</p>
                <a class="btn" href="$APP_RETURN_URI">Return to App</a>
              </div>
            </body>
            </html>
        """.trimIndent()
        writeHttpResponse(out, 200, html, "text/html; charset=utf-8")
    }

    private fun writeHttpResponse(
        out: OutputStream,
        statusCode: Int,
        body: String,
        contentType: String = "text/plain; charset=utf-8"
    ) {
        val reason = when (statusCode) {
            200 -> "OK"
            400 -> "Bad Request"
            404 -> "Not Found"
            else -> "Error"
        }
        val bytes = body.toByteArray(Charsets.UTF_8)
        val headers = "HTTP/1.1 $statusCode $reason\r\n" +
            "Content-Type: $contentType\r\n" +
            "Content-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n\r\n"
        out.write(headers.toByteArray(Charsets.US_ASCII))
        out.write(bytes)
        out.flush()
    }

    private fun fetchAccountEmail(accessToken: String): String =
        runCatching { getJson(GMAIL_PROFILE_URL, accessToken, "Gmail profile").optString("emailAddress") }
            .getOrNull()?.takeIf { it.isNotBlank() }
            ?: getJson(USERINFO_URL, accessToken, "User info").optString("email").takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Could not determine the Google account email")

    private fun readGmail(accessToken: String): Int {
        val total = getJson(GMAIL_PROFILE_URL, accessToken, "Gmail profile").optInt("messagesTotal", 0)
        val latestId = getJson("$GMAIL_MESSAGES_URL?maxResults=1", accessToken, "Gmail list")
            .optJSONArray("messages")?.optJSONObject(0)?.optString("id")
        if (!latestId.isNullOrBlank()) {
            getJson("$GMAIL_MESSAGES_URL/$latestId?format=metadata", accessToken, "Gmail read")
        }
        return total
    }

    private fun sendAndTrashSelfEmail(accessToken: String, email: String): Boolean {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val rawMime = "From: $email\r\n" +
            "To: $email\r\n" +
            "Subject: [Keep-Alive] Automated Google Account Activity ($timestamp)\r\n" +
            "Content-Type: text/plain; charset=utf-8\r\n\r\n" +
            "Automated keep-alive ping executed at $timestamp to prevent account inactivity deletion."
        val encodedMime = Base64.encodeToString(
            rawMime.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )

        val sentId = postJson(
            "$GMAIL_MESSAGES_URL/send",
            accessToken,
            "Gmail send",
            JSONObject().put("raw", encodedMime)
        ).optString("id")
        if (sentId.isBlank()) return false
        return runCatching { postJson("$GMAIL_MESSAGES_URL/$sentId/trash", accessToken, "Gmail trash") }.isSuccess
    }

    private fun listDriveFiles(accessToken: String): Int =
        getJson("$DRIVE_FILES_URL?pageSize=5&fields=files(id,name)", accessToken, "Drive list")
            .optJSONArray("files")?.length() ?: 0

    private fun getJson(url: String, accessToken: String, label: String): JSONObject =
        execute(
            Request.Builder().url(url).header("Authorization", "Bearer $accessToken").get().build(),
            label
        )

    private fun postJson(url: String, accessToken: String, label: String, body: JSONObject = JSONObject()): JSONObject =
        execute(
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build(),
            label
        )

    private fun postForm(url: String, label: String, fields: Map<String, String>): JSONObject {
        val form = FormBody.Builder().apply {
            fields.forEach { (key, value) -> if (value.isNotBlank()) add(key, value) }
        }.build()
        return execute(Request.Builder().url(url).post(form).build(), label)
    }

    private fun execute(request: Request, label: String): JSONObject =
        httpClient.newCall(request).execute().use { response ->
            val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrDefault(JSONObject())
            if (!response.isSuccessful) throw apiError(label, response.code, json)
            json
        }

    private fun apiError(label: String, code: Int, json: JSONObject): Exception {
        // OAuth endpoints return "error" as a string; Google APIs return it as an object.
        val oauthError = json.opt("error") as? String
        val detail = json.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
            ?: json.optString("error_description").takeIf { it.isNotBlank() }
            ?: oauthError
            ?: "no details"
        val message = "$label failed (HTTP $code): $detail"
        return when {
            oauthError == "invalid_grant" || (code == 401 && oauthError == null) ->
                NeedsLoginException("$message — tap Re-login")
            code == 429 || code >= 500 -> TransientApiException(message)
            else -> IllegalStateException(message)
        }
    }

    private fun randomUrlSafe(byteCount: Int): String =
        ByteArray(byteCount).also { SecureRandom().nextBytes(it) }.toUrlSafeBase64()

    private fun codeChallenge(verifier: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
            .toUrlSafeBase64()

    private fun ByteArray.toUrlSafeBase64(): String =
        Base64.encodeToString(this, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun String.htmlEscape(): String = replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

    private companion object {
        const val LOOPBACK_HOST = "127.0.0.1"
        const val SIGN_IN_TIMEOUT_MS = 180_000L
        const val APP_RETURN_URI = "gmailchecker://oauth2done"
        const val AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth"
        const val TOKEN_URL = "https://oauth2.googleapis.com/token"
        const val USERINFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo"
        const val GMAIL_PROFILE_URL = "https://gmail.googleapis.com/gmail/v1/users/me/profile"
        const val GMAIL_MESSAGES_URL = "https://gmail.googleapis.com/gmail/v1/users/me/messages"
        const val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
        // gmail.modify covers read + send + trash, but (unlike https://mail.google.com/) not permanent delete.
        const val SCOPES = "openid email https://www.googleapis.com/auth/gmail.modify " +
            "https://www.googleapis.com/auth/drive.metadata.readonly"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
