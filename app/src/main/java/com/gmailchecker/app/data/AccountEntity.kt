package com.gmailchecker.app.data

enum class AccountStatus {
    NEVER_CHECKED,
    OK,
    ERROR,
    NEEDS_LOGIN
}

data class AccountEntity(
    val email: String,
    val status: AccountStatus = AccountStatus.NEVER_CHECKED,
    val lastCheckedEpochMs: Long? = null,
    val lastSummary: String = "Not checked yet",
    val errorMessage: String? = null
)

data class AppSettings(
    val oauthClientId: String = "",
    val oauthClientSecret: String = "",
    val autoCheckEnabled: Boolean = true,
    val intervalDays: Int = 14,
    val actionReadGmail: Boolean = true,
    val actionSendSelfEmail: Boolean = true,
    val actionListDrive: Boolean = true
)
