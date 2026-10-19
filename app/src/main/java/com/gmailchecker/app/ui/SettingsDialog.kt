package com.gmailchecker.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.gmailchecker.app.data.AppSettings

@Composable
fun SettingsDialog(
    initialSettings: AppSettings,
    onDismiss: () -> Unit,
    onSave: (AppSettings) -> Unit
) {
    var clientId by remember { mutableStateOf(initialSettings.oauthClientId) }
    var clientSecret by remember { mutableStateOf(initialSettings.oauthClientSecret) }
    var intervalDays by remember { mutableIntStateOf(initialSettings.intervalDays) }
    var readGmail by remember { mutableStateOf(initialSettings.actionReadGmail) }
    var sendSelf by remember { mutableStateOf(initialSettings.actionSendSelfEmail) }
    var listDrive by remember { mutableStateOf(initialSettings.actionListDrive) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            border = BorderStroke(1.dp, Color(0xFF475569)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "⚙ App Settings",
                    color = Color(0xFFF8FAFC),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Column {
                    Text(
                        text = "Google OAuth Client ID (Desktop App)",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = clientId,
                        onValueChange = { clientId = it },
                        placeholder = {
                            Text(
                                "123456-abc.apps.googleusercontent.com",
                                color = Color(0xFF64748B),
                                fontSize = 12.sp
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFFF8FAFC),
                            unfocusedTextColor = Color(0xFFF8FAFC),
                            focusedContainerColor = Color(0xFF0F172A),
                            unfocusedContainerColor = Color(0xFF0F172A),
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color(0xFF334155)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Column {
                    Text(
                        text = "Google OAuth Client Secret (from Desktop Client JSON)",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = clientSecret,
                        onValueChange = { clientSecret = it },
                        placeholder = {
                            Text(
                                "GOCSPX-...",
                                color = Color(0xFF64748B),
                                fontSize = 12.sp
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFFF8FAFC),
                            unfocusedTextColor = Color(0xFFF8FAFC),
                            focusedContainerColor = Color(0xFF0F172A),
                            unfocusedContainerColor = Color(0xFF0F172A),
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color(0xFF334155)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Column {
                    Text(
                        text = "Background Check Frequency",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    listOf(
                        7 to "Every 7 days",
                        14 to "Every 14 days (Recommended)",
                        30 to "Every 30 days"
                    ).forEach { (days, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { intervalDays = days }
                        ) {
                            RadioButton(
                                selected = intervalDays == days,
                                onClick = { intervalDays = days },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color(0xFF60A5FA),
                                    unselectedColor = Color(0xFF94A3B8)
                                )
                            )
                            Text(text = label, color = Color(0xFFE2E8F0), fontSize = 12.sp)
                        }
                    }
                }

                Column {
                    Text(
                        text = "Activity Actions per Check",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    ActionCheckRow(
                        checked = readGmail,
                        label = "Read Gmail Inbox & latest email",
                        onCheckedChange = { readGmail = it }
                    )
                    ActionCheckRow(
                        checked = sendSelf,
                        label = "Send & trash self keep-alive email",
                        onCheckedChange = { sendSelf = it }
                    )
                    ActionCheckRow(
                        checked = listDrive,
                        label = "List Google Drive files",
                        onCheckedChange = { listDrive = it }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF334155),
                            contentColor = Color(0xFFF8FAFC)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Cancel", fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            onSave(
                                initialSettings.copy(
                                    oauthClientId = clientId.trim(),
                                    oauthClientSecret = clientSecret.trim(),
                                    intervalDays = intervalDays,
                                    actionReadGmail = readGmail,
                                    actionSendSelfEmail = sendSelf,
                                    actionListDrive = listDrive
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2563EB),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save Settings", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionCheckRow(
    checked: Boolean,
    label: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = Color(0xFF3B82F6),
                uncheckedColor = Color(0xFF94A3B8)
            )
        )
        Text(text = label, color = Color(0xFFE2E8F0), fontSize = 12.sp)
    }
}
