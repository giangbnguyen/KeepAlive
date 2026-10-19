package com.gmailchecker.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gmailchecker.app.data.AccountEntity
import com.gmailchecker.app.data.AccountStatus
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private val BgDark = Color(0xFF0F172A)
private val SurfaceDark = Color(0xFF1E293B)
private val BorderSlate = Color(0xFF334155)
private val BlueAccent = Color(0xFF3B82F6)
private val BlueCardBg = Color(0xFF172554)
private val GreenAccent = Color(0xFF10B981)
private val GreenText = Color(0xFF34D399)
private val RedAccent = Color(0xFFEF4444)
private val RedText = Color(0xFFF87171)
private val TextPrimary = Color(0xFFF8FAFC)
private val TextMuted = Color(0xFF94A3B8)

@Composable
fun DashboardScreen(
    uiState: UiState,
    isBatteryUnrestricted: Boolean,
    onRequestBatteryUnrestricted: () -> Unit,
    onToggleAutoCheck: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onCheckAccount: (String) -> Unit,
    onCheckAll: () -> Unit,
    onAddAccount: () -> Unit,
    onReLogin: (String) -> Unit,
    onCancelAddAccount: () -> Unit,
    onRemoveAccount: (String) -> Unit,
    onDismissBanner: () -> Unit
) {
    val nowMs by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(60_000)
            value = System.currentTimeMillis()
        }
    }

    Scaffold(
        containerColor = BgDark,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🛡️ Gmail Keep-Alive",
                    color = TextPrimary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BorderSlate,
                        contentColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(text = "⚙ Settings", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BgDark)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Button(
                    onClick = if (uiState.isAddingAccount) onCancelAddAccount else onAddAccount,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GreenAccent,
                        contentColor = Color(0xFF052E16)
                    )
                ) {
                    if (uiState.isAddingAccount) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color(0xFF052E16),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Waiting for sign-in… (tap to cancel)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    } else {
                        Text(
                            text = "+ Add Google Account (OAuth2)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            if (!uiState.bannerMessage.isNullOrBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                    border = BorderStroke(1.dp, BlueAccent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clickable { onDismissBanner() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.bannerMessage,
                            color = TextPrimary,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "✕",
                            color = TextMuted,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }

            ScheduleCard(
                autoCheckEnabled = uiState.settings.autoCheckEnabled,
                intervalDays = uiState.settings.intervalDays,
                isBatteryUnrestricted = isBatteryUnrestricted,
                onToggleAutoCheck = onToggleAutoCheck,
                onRequestBatteryUnrestricted = onRequestBatteryUnrestricted
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "GOOGLE ACCOUNTS (${uiState.accounts.size})",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.6.sp
                )
                Button(
                    onClick = onCheckAll,
                    enabled = uiState.accounts.isNotEmpty() && uiState.checkingEmails.isEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2563EB),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(text = "▶ Check All", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (uiState.accounts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .border(1.dp, BorderSlate, RoundedCornerShape(16.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No Google accounts added yet",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap '+ Add Google Account (OAuth2)' below to sign in once. The app will keep your account active automatically.",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.accounts, key = { it.email }) { account ->
                        val isChecking = uiState.checkingEmails.contains(account.email)
                        AccountCard(
                            account = account,
                            isChecking = isChecking,
                            nowMs = nowMs,
                            onCheckClick = { onCheckAccount(account.email) },
                            onReLoginClick = { onReLogin(account.email) },
                            onRemoveClick = { onRemoveAccount(account.email) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleCard(
    autoCheckEnabled: Boolean,
    intervalDays: Int,
    isBatteryUnrestricted: Boolean,
    onToggleAutoCheck: (Boolean) -> Unit,
    onRequestBatteryUnrestricted: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF1E3A8A), SurfaceDark)
                )
            )
            .border(1.dp, BlueAccent, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-Check in Background",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (autoCheckEnabled) {
                            "Every $intervalDays days via Android WorkManager"
                        } else {
                            "Background schedule paused (manual check only)"
                        },
                        color = Color(0xFF93C5FD),
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = autoCheckEnabled,
                    onCheckedChange = onToggleAutoCheck,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = BlueAccent
                    )
                )
            }

            val pillBg = if (isBatteryUnrestricted) Color(0x2E10B981) else Color(0x33F59E0B)
            val pillBorder = if (isBatteryUnrestricted) Color(0x6610B981) else Color(0x88F59E0B)
            val pillText = if (isBatteryUnrestricted) {
                "⚡ Unrestricted Battery: Enabled"
            } else {
                "⚡ Tap to allow Unrestricted Battery (prevents sleep)"
            }
            val pillTextColor = if (isBatteryUnrestricted) Color(0xFF6EE7B7) else Color(0xFFFCD34D)

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(pillBg)
                    .border(1.dp, pillBorder, RoundedCornerShape(8.dp))
                    .clickable(enabled = !isBatteryUnrestricted) { onRequestBatteryUnrestricted() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = pillText,
                    color = pillTextColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun AccountCard(
    account: AccountEntity,
    isChecking: Boolean,
    nowMs: Long,
    onCheckClick: () -> Unit,
    onReLoginClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    val isFailed = account.status == AccountStatus.ERROR || account.status == AccountStatus.NEEDS_LOGIN
    val borderColor = when {
        isChecking -> BlueAccent
        isFailed -> RedAccent
        else -> BorderSlate
    }
    val cardBg = when {
        isChecking -> BlueCardBg
        isFailed -> Color(0x407F1D1D)
        else -> SurfaceDark
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = account.email,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                StatusBadge(status = account.status, isChecking = isChecking)
            }

            val detailText = when {
                isChecking -> "Refreshing OAuth token & checking Gmail/Drive..."
                isFailed -> "Error: ${account.errorMessage ?: "Check failed"}"
                else -> account.lastSummary
            }
            val detailColor = if (isFailed && !isChecking) {
                Color(0xFFFCA5A5)
            } else {
                TextMuted
            }

            Text(
                text = detailText,
                color = detailColor,
                fontSize = 12.sp
            )

            HorizontalDivider(color = Color(0x1F94A3B8))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Last check: ${formatLastChecked(account.lastCheckedEpochMs, nowMs)}",
                    color = Color(0xFFCBD5E1),
                    fontSize = 11.sp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isChecking) {
                        // Remove is hidden while checking so an in-flight result can't race the removal.
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color(0xFF60A5FA),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        when (account.status) {
                            AccountStatus.NEEDS_LOGIN -> {
                                SmallButton("Retry", BorderSlate, TextPrimary, onCheckClick)
                                SmallButton("Re-login", Color(0xFFDC2626), Color.White, onReLoginClick)
                            }
                            AccountStatus.ERROR -> SmallButton("Retry", BorderSlate, TextPrimary, onCheckClick)
                            else -> SmallButton("Check", BorderSlate, TextPrimary, onCheckClick)
                        }

                        OutlinedButton(
                            onClick = onRemoveClick,
                            border = BorderStroke(1.dp, Color(0xFF475569)),
                            shape = RoundedCornerShape(7.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(text = "✕", color = TextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallButton(text: String, containerColor: Color, contentColor: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
        shape = RoundedCornerShape(7.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        modifier = Modifier.height(28.dp)
    ) {
        Text(text = text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatusBadge(status: AccountStatus, isChecking: Boolean) {
    val (bg, fg, label) = when {
        isChecking -> Triple(Color(0x403B82F6), Color(0xFF60A5FA), "CHECKING...")
        status == AccountStatus.OK -> Triple(Color(0x3310B981), GreenText, "✓ ACTIVE")
        status == AccountStatus.NEEDS_LOGIN -> Triple(Color(0x40EF4444), RedText, "! NEEDS LOGIN")
        status == AccountStatus.ERROR -> Triple(Color(0x40EF4444), RedText, "! ERROR")
        else -> Triple(Color(0x3394A3B8), TextMuted, "NEVER CHECKED")
    }

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun formatLastChecked(epochMs: Long?, nowMs: Long): String {
    if (epochMs == null || epochMs <= 0L) return "Never"
    val diffMs = (nowMs - epochMs).coerceAtLeast(0L)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diffMs)
    val hours = TimeUnit.MILLISECONDS.toHours(diffMs)
    val days = TimeUnit.MILLISECONDS.toDays(diffMs)

    val relative = when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24 -> "$hours hr ago"
        else -> "$days days ago"
    }
    val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(epochMs))
    return "$relative ($dateStr)"
}
