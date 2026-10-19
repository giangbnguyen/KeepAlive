package com.gmailchecker.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gmailchecker.app.data.AccountEntity
import com.gmailchecker.app.data.AccountStore
import com.gmailchecker.app.data.AppSettings
import com.gmailchecker.app.data.GoogleApiClient
import com.gmailchecker.app.worker.WorkScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val accounts: List<AccountEntity> = emptyList(),
    val checkingEmails: Set<String> = emptySet(),
    val isAddingAccount: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val showSettingsDialog: Boolean = false,
    val bannerMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val store = AccountStore.get(application)
    private val apiClient = GoogleApiClient()
    private var signInJob: Job? = null

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        val initialSettings = store.getSettings()
        _uiState.update {
            it.copy(
                accounts = store.getAccounts(),
                settings = initialSettings
            )
        }
        WorkScheduler.syncSchedule(getApplication(), initialSettings)
    }

    fun refreshFromStore() {
        _uiState.update {
            it.copy(
                accounts = store.getAccounts(),
                settings = store.getSettings()
            )
        }
    }

    fun openSettings() {
        _uiState.update { it.copy(showSettingsDialog = true) }
    }

    fun closeSettings() {
        _uiState.update { it.copy(showSettingsDialog = false) }
    }

    fun clearBanner() {
        _uiState.update { it.copy(bannerMessage = null) }
    }

    fun saveSettings(newSettings: AppSettings) {
        store.saveSettings(newSettings)
        WorkScheduler.syncSchedule(getApplication(), newSettings)
        _uiState.update {
            it.copy(
                settings = newSettings,
                showSettingsDialog = false,
                bannerMessage = "Settings saved"
            )
        }
    }

    fun toggleAutoCheck(enabled: Boolean) {
        val updated = _uiState.value.settings.copy(autoCheckEnabled = enabled)
        store.saveSettings(updated)
        WorkScheduler.syncSchedule(getApplication(), updated)
        _uiState.update { it.copy(settings = updated) }
    }

    fun removeAccount(email: String) {
        store.removeAccount(email)
        _uiState.update { it.copy(accounts = store.getAccounts()) }
    }

    fun addAccount(loginHint: String?, openBrowserUrl: (String) -> Unit) {
        val currentSettings = store.getSettings()
        if (currentSettings.oauthClientId.isBlank()) {
            _uiState.update {
                it.copy(
                    showSettingsDialog = true,
                    bannerMessage = "Enter your Google OAuth Client ID first."
                )
            }
            return
        }

        if (signInJob?.isActive == true) return

        signInJob = viewModelScope.launch {
            _uiState.update { it.copy(isAddingAccount = true, bannerMessage = null) }
            try {
                val loginResult = apiClient.performInteractiveOAuthLogin(
                    clientId = currentSettings.oauthClientId,
                    clientSecret = currentSettings.oauthClientSecret,
                    loginHint = loginHint,
                    openAuthUrlInBrowser = openBrowserUrl
                )
                store.addOrReplaceAccount(loginResult.email, loginResult.refreshToken)
                _uiState.update {
                    it.copy(
                        accounts = store.getAccounts(),
                        bannerMessage = "Connected ${loginResult.email}"
                    )
                }
                checkAccount(loginResult.email)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(bannerMessage = e.message ?: "Failed to add account") }
            } finally {
                _uiState.update { it.copy(isAddingAccount = false) }
            }
        }
    }

    fun cancelAddAccount() {
        if (signInJob?.isActive != true) return
        signInJob?.cancel()
        _uiState.update { it.copy(bannerMessage = "Sign-in cancelled") }
    }

    fun checkAccount(email: String) {
        if (_uiState.value.checkingEmails.contains(email)) return

        viewModelScope.launch {
            _uiState.update { it.copy(checkingEmails = it.checkingEmails + email) }
            try {
                val summary = apiClient.performKeepAlive(
                    email = email,
                    refreshToken = store.getRefreshToken(email),
                    settings = store.getSettings()
                )
                store.recordSuccess(email, summary)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                store.recordFailure(email, e)
            } finally {
                _uiState.update {
                    it.copy(
                        accounts = store.getAccounts(),
                        checkingEmails = it.checkingEmails - email
                    )
                }
            }
        }
    }

    fun checkAllAccounts() {
        _uiState.value.accounts.forEach { checkAccount(it.email) }
    }
}
