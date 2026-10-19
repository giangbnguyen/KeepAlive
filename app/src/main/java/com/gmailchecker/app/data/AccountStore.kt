package com.gmailchecker.app.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Process-wide store shared by the UI and the background worker, so every read-modify-write
 * of the account list is serialized on the same lock.
 */
class AccountStore private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
    private val cipher = TokenCipher()

    @Synchronized
    fun getAccounts(): List<AccountEntity> {
        val arr = runCatching { JSONArray(prefs.getString(KEY_ACCOUNTS_JSON, null) ?: "[]") }
            .getOrDefault(JSONArray())
        return (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            val email = obj.optString("email").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            AccountEntity(
                email = email,
                status = runCatching { AccountStatus.valueOf(obj.optString("status")) }
                    .getOrDefault(AccountStatus.NEVER_CHECKED),
                lastCheckedEpochMs = if (obj.isNull("lastCheckedEpochMs")) null else obj.optLong("lastCheckedEpochMs"),
                lastSummary = obj.optString("lastSummary", "Not checked yet"),
                errorMessage = if (obj.isNull("errorMessage")) null else obj.optString("errorMessage").takeIf { it.isNotBlank() }
            )
        }
    }

    @Synchronized
    fun addOrReplaceAccount(email: String, refreshToken: String) {
        val accounts = getAccounts().toMutableList()
        val index = accounts.indexOfFirst { it.email.equals(email, ignoreCase = true) }
        val signedIn = (accounts.getOrNull(index) ?: AccountEntity(email = email)).copy(
            status = AccountStatus.NEVER_CHECKED,
            lastSummary = "Signed in — running initial check...",
            errorMessage = null
        )
        if (index >= 0) accounts[index] = signedIn else accounts.add(signedIn)
        prefs.edit()
            .putString(KEY_ACCOUNTS_JSON, serialize(accounts))
            .putString(tokenKey(email), cipher.encrypt(refreshToken))
            .apply()
    }

    @Synchronized
    fun recordSuccess(email: String, summary: String) = updateIfPresent(email) {
        it.copy(
            status = AccountStatus.OK,
            lastCheckedEpochMs = System.currentTimeMillis(),
            lastSummary = summary,
            errorMessage = null
        )
    }

    @Synchronized
    fun recordFailure(email: String, error: Throwable) = updateIfPresent(email) {
        it.copy(
            status = if (error is NeedsLoginException) AccountStatus.NEEDS_LOGIN else AccountStatus.ERROR,
            lastCheckedEpochMs = System.currentTimeMillis(),
            errorMessage = error.message ?: "Check failed"
        )
    }

    @Synchronized
    fun removeAccount(email: String) {
        val remaining = getAccounts().filterNot { it.email.equals(email, ignoreCase = true) }
        prefs.edit()
            .putString(KEY_ACCOUNTS_JSON, serialize(remaining))
            .remove(tokenKey(email))
            .apply()
    }

    @Synchronized
    fun getRefreshToken(email: String): String? =
        prefs.getString(tokenKey(email), null)?.let(cipher::decrypt)

    @Synchronized
    fun getSettings(): AppSettings = AppSettings(
        oauthClientId = prefs.getString(KEY_CLIENT_ID, "").orEmpty(),
        oauthClientSecret = prefs.getString(KEY_CLIENT_SECRET, null)?.let(cipher::decrypt).orEmpty(),
        autoCheckEnabled = prefs.getBoolean(KEY_AUTO_CHECK, true),
        intervalDays = prefs.getInt(KEY_INTERVAL_DAYS, 14),
        actionReadGmail = prefs.getBoolean(KEY_ACT_READ_GMAIL, true),
        actionSendSelfEmail = prefs.getBoolean(KEY_ACT_SEND_SELF, true),
        actionListDrive = prefs.getBoolean(KEY_ACT_LIST_DRIVE, true)
    )

    @Synchronized
    fun saveSettings(settings: AppSettings) {
        val secret = settings.oauthClientSecret.trim()
        prefs.edit()
            .putString(KEY_CLIENT_ID, settings.oauthClientId.trim())
            .putString(KEY_CLIENT_SECRET, if (secret.isEmpty()) null else cipher.encrypt(secret))
            .putBoolean(KEY_AUTO_CHECK, settings.autoCheckEnabled)
            .putInt(KEY_INTERVAL_DAYS, settings.intervalDays)
            .putBoolean(KEY_ACT_READ_GMAIL, settings.actionReadGmail)
            .putBoolean(KEY_ACT_SEND_SELF, settings.actionSendSelfEmail)
            .putBoolean(KEY_ACT_LIST_DRIVE, settings.actionListDrive)
            .apply()
    }

    // Never re-adds an account that was removed while a check was in flight.
    private fun updateIfPresent(email: String, transform: (AccountEntity) -> AccountEntity) {
        val accounts = getAccounts().toMutableList()
        val index = accounts.indexOfFirst { it.email.equals(email, ignoreCase = true) }
        if (index < 0) return
        accounts[index] = transform(accounts[index])
        prefs.edit().putString(KEY_ACCOUNTS_JSON, serialize(accounts)).apply()
    }

    private fun serialize(accounts: List<AccountEntity>): String = JSONArray().apply {
        accounts.forEach { acc ->
            put(JSONObject().apply {
                put("email", acc.email)
                put("status", acc.status.name)
                acc.lastCheckedEpochMs?.let { put("lastCheckedEpochMs", it) }
                put("lastSummary", acc.lastSummary)
                acc.errorMessage?.let { put("errorMessage", it) }
            })
        }
    }.toString()

    private fun tokenKey(email: String): String = "refresh_token_${email.lowercase()}"

    companion object {
        private const val PREFS_FILE_NAME = "gmail_checker_prefs"
        private const val KEY_ACCOUNTS_JSON = "accounts_json"
        private const val KEY_CLIENT_ID = "oauth_client_id"
        private const val KEY_CLIENT_SECRET = "oauth_client_secret_enc"
        private const val KEY_AUTO_CHECK = "auto_check_enabled"
        private const val KEY_INTERVAL_DAYS = "interval_days"
        private const val KEY_ACT_READ_GMAIL = "act_read_gmail"
        private const val KEY_ACT_SEND_SELF = "act_send_self"
        private const val KEY_ACT_LIST_DRIVE = "act_list_drive"

        @Volatile
        private var instance: AccountStore? = null

        fun get(context: Context): AccountStore =
            instance ?: synchronized(this) {
                instance ?: AccountStore(context.applicationContext).also { instance = it }
            }
    }
}
