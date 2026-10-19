package com.gmailchecker.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.gmailchecker.app.ui.DashboardScreen
import com.gmailchecker.app.ui.MainViewModel
import com.gmailchecker.app.ui.SettingsDialog

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private var isBatteryUnrestricted by mutableStateOf(false)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        updateBatteryStatus()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val uiState by viewModel.uiState.collectAsState()

                DashboardScreen(
                    uiState = uiState,
                    isBatteryUnrestricted = isBatteryUnrestricted,
                    onRequestBatteryUnrestricted = { requestIgnoreBatteryOptimizations() },
                    onToggleAutoCheck = { viewModel.toggleAutoCheck(it) },
                    onOpenSettings = { viewModel.openSettings() },
                    onCheckAccount = { viewModel.checkAccount(it) },
                    onCheckAll = { viewModel.checkAllAccounts() },
                    onAddAccount = { viewModel.addAccount(loginHint = null, ::openBrowserForOAuth) },
                    onReLogin = { email -> viewModel.addAccount(loginHint = email, ::openBrowserForOAuth) },
                    onCancelAddAccount = { viewModel.cancelAddAccount() },
                    onRemoveAccount = { viewModel.removeAccount(it) },
                    onDismissBanner = { viewModel.clearBanner() }
                )

                if (uiState.showSettingsDialog) {
                    SettingsDialog(
                        initialSettings = uiState.settings,
                        onDismiss = { viewModel.closeSettings() },
                        onSave = { viewModel.saveSettings(it) }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateBatteryStatus()
        viewModel.refreshFromStore()
    }

    private fun openBrowserForOAuth(url: String) {
        val uri = Uri.parse(url)
        try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .build()
            customTabsIntent.launchUrl(this, uri)
        } catch (_: Exception) {
            val browserIntent = Intent(Intent.ACTION_VIEW, uri)
            startActivity(browserIntent)
        }
    }

    private fun updateBatteryStatus() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        isBatteryUnrestricted = pm.isIgnoringBatteryOptimizations(packageName)
    }

    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimizations() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(fallback)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
