# Gmail Keep-Alive (Android)

An Android app (Kotlin + Jetpack Compose) that keeps your old Google accounts from being deleted under Google's 2-year Inactive Account Policy.

It signs into each account **once** via OAuth 2.0 (tokens are encrypted with an Android Keystore key inside the app and are **not** added to your phone's global Google accounts), and then runs unattended either manually from the dashboard or automatically in the background via Android `WorkManager`.

---

## 1. One-Time: Create a Signing Key (Required for Updates)

Android only lets you update an installed app if the new APK is signed with the **same key**. GitHub runners are fresh VMs, so without your own key every build would get a new random key, and updating would require uninstalling (which wipes all saved accounts).

On any machine with a JDK (`keytool` ships with Java):

```bash
keytool -genkeypair -v -keystore gmailchecker.jks -alias gmailchecker \
  -keyalg RSA -keysize 4096 -validity 36500
```

Then base64-encode it:

```bash
# Linux/macOS/Git Bash
base64 -w0 gmailchecker.jks > gmailchecker.jks.b64
# PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("gmailchecker.jks")) | Out-File gmailchecker.jks.b64 -NoNewline
```

In your GitHub repo → **Settings → Secrets and variables → Actions → New repository secret**, add:

| Secret | Value |
| :--- | :--- |
| `ANDROID_KEYSTORE_B64` | contents of `gmailchecker.jks.b64` |
| `ANDROID_KEYSTORE_PASSWORD` | keystore password |
| `ANDROID_KEY_ALIAS` | `gmailchecker` |
| `ANDROID_KEY_PASSWORD` | key password |

**Back up `gmailchecker.jks` and its passwords somewhere safe** (password manager). If you lose it you can never update the installed app. Never commit it (`.gitignore` already excludes `*.jks`).

---

## 2. Building the APK on GitHub (No Local SDK Needed)

- **Every branch push** → **Actions** tab → workflow run → **Artifacts** → `GmailKeepAlive-debug-apk`.
- **Release** → push a version tag:
  ```bash
  git tag v1.0.0
  git push origin v1.0.0
  ```
  GitHub builds a signed release APK and attaches `GmailKeepAlive-v1.0.0.apk` to a new entry under **Releases**. Download it on your phone and install (allow "Install unknown apps" for your browser once).

Version code is the workflow run number, so each new build installs as an update over the previous one.

---

## 3. One-Time Google Cloud OAuth Setup

You only need **one** Google Cloud project for all of your old accounts:

1. Go to [Google Cloud Console](https://console.cloud.google.com/) and create a new project.
2. Go to **APIs & Services → Library** and enable:
   - **Gmail API**
   - **Google Drive API**
3. Go to **APIs & Services → OAuth consent screen**:
   - Choose **External** and fill in an app name (e.g., `Gmail Keep-Alive`) and your email.
   - Add scopes `gmail.modify` and `drive.metadata.readonly`.
   - Click **Publish App** so the status changes from *Testing* to **In production** *(in Testing, refresh tokens expire after 7 days; you do NOT need to submit for Google verification)*.
4. Go to **APIs & Services → Credentials → Create Credentials → OAuth client ID**:
   - Application type: **Desktop app** (uses the loopback `127.0.0.1` redirect, so no Android SHA-1 fingerprint is needed).
   - Copy the **Client ID** and **Client Secret**.

---

## 4. Using the App on Your Phone

1. Open **Gmail Keep-Alive** and tap **⚙ Settings**.
2. Paste your **Client ID** and **Client Secret**, choose your check frequency (**Every 14 days** recommended), and tap **Save Settings**.
3. Tap **⚡ Unrestricted Battery** on the dashboard card so Android doesn't put the background scheduler into deep sleep.
4. Tap **+ Add Google Account (OAuth2)**:
   - Sign in to your old Google account in the browser tab.
   - If Google shows *"Google hasn't verified this app"*, tap **Advanced → Go to Gmail Keep-Alive (unsafe)** → **Continue**.
   - Tap **Return to App**. The first keep-alive check runs automatically.
   - Changed your mind? Tap the green button again (*"tap to cancel"*).
5. Tap **Check** on any account or **▶ Check All** at any time.

### Status meanings

| Badge | Meaning | Action |
| :--- | :--- | :--- |
| ✓ ACTIVE | Last check succeeded | none |
| ! ERROR | Temporary/config problem (network, Google outage, API not enabled) | **Retry** |
| ! NEEDS LOGIN | Sign-in revoked or expired (e.g. password changed) | **Re-login** |

Background runs retry automatically (with backoff, up to 3 times) on network/server errors.

---

## Caveat: What Google Counts as "Activity"

Google's policy lists examples such as reading or sending email, using Drive, and signing in to third-party apps with Google. It does **not** explicitly confirm that API access counts. The self-email (*Send & trash self keep-alive email*) is the strongest signal, so keep it enabled. As a safety net, also sign in to each account through a browser occasionally (the Add/Re-login flow does exactly this).
