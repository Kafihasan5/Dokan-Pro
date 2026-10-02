package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.data.firebase.CloudAuthManager
import com.example.ui.CloudPrompt

/**
 * Asks the user for what the server needs before cloud sync can continue:
 * a strong new master PIN, or the current PIN when the one saved on the phone was rejected.
 * The app keeps working offline if the user postpones it.
 */
@Composable
fun CloudSecurityDialog(
    prompt: CloudPrompt,
    message: String,
    isBusy: Boolean,
    defaultIdentifier: String,
    onSubmitLogin: (identifier: String, pin: String) -> Unit,
    onSubmitNewPin: (newPin: String) -> Unit,
    onDismiss: () -> Unit
) {
    val needsNewPin = prompt == CloudPrompt.SET_STRONG_PIN || prompt == CloudPrompt.CHANGE_WEAK_PIN
    var identifier by remember(prompt) { mutableStateOf(defaultIdentifier) }
    var pin by remember(prompt) { mutableStateOf("") }
    var confirm by remember(prompt) { mutableStateOf("") }

    val newPinError = if (!needsNewPin || pin.isEmpty()) null else {
        val p = CloudAuthManager.normalizePin(pin)
        when {
            !CloudAuthManager.isValidPin(p) -> "পিন ৪-১২টি সংখ্যা হতে হবে"
            CloudAuthManager.isWeakPin(p) -> "১২৩৪, ০০০০ বা একই সংখ্যা বারবার — এমন সহজ পিন দেওয়া যাবে না"
            confirm.isNotEmpty() && CloudAuthManager.normalizePin(confirm) != p -> "দুটি পিন মিলছে না"
            else -> null
        }
    }
    val canSubmit = !isBusy && if (needsNewPin) {
        pin.isNotEmpty() && confirm.isNotEmpty() && newPinError == null
    } else {
        pin.isNotEmpty() && identifier.isNotBlank()
    }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        properties = DialogProperties(dismissOnClickOutside = false),
        icon = { Icon(if (needsNewPin) Icons.Default.Security else Icons.Default.Lock, contentDescription = null) },
        title = {
            Text(
                when (prompt) {
                    CloudPrompt.SET_STRONG_PIN -> "নিরাপদ মাস্টার পিন সেট করুন"
                    CloudPrompt.CHANGE_WEAK_PIN -> "মাস্টার পিন পরিবর্তন করুন"
                    CloudPrompt.ENTER_PIN -> "ক্লাউডে লগইন করুন"
                    CloudPrompt.STAFF_REJOIN -> "আবার লগইন করুন"
                }
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (message.isNotBlank()) {
                    Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!needsNewPin) {
                    DokanTextField(
                        value = identifier,
                        onValueChange = { identifier = it },
                        label = "দোকান কোড অথবা ইমেইল",
                        keyboardType = KeyboardType.Email,
                        leadingIcon = Icons.Default.Email,
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                DokanTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 12) pin = it },
                    label = when {
                        needsNewPin -> "নতুন মাস্টার পিন (৬ সংখ্যা সুপারিশকৃত)"
                        prompt == CloudPrompt.STAFF_REJOIN -> "কর্মচারী পিন"
                        else -> "মাস্টার পিন"
                    },
                    keyboardType = KeyboardType.NumberPassword,
                    visualTransformation = PasswordVisualTransformation(),
                    leadingIcon = Icons.Default.Key,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                )
                if (needsNewPin) {
                    DokanTextField(
                        value = confirm,
                        onValueChange = { if (it.length <= 12) confirm = it },
                        label = "নতুন পিন আবার দিন",
                        keyboardType = KeyboardType.NumberPassword,
                        visualTransformation = PasswordVisualTransformation(),
                        leadingIcon = Icons.Default.Key,
                        enabled = !isBusy,
                        isError = newPinError != null,
                        errorText = newPinError,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSubmit,
                onClick = { if (needsNewPin) onSubmitNewPin(pin) else onSubmitLogin(identifier, pin) }
            ) {
                Text(if (isBusy) "অপেক্ষা করুন…" else if (needsNewPin) "পিন সেট করুন" else "লগইন")
            }
        },
        dismissButton = {
            TextButton(enabled = !isBusy, onClick = onDismiss) {
                Icon(Icons.Default.CloudOff, contentDescription = null)
                Text(" পরে করব")
            }
        }
    )
}
