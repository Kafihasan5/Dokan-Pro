package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.AppUpdateInfo
import com.example.util.AppUpdater
import com.example.util.UpdateState
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun UpdateDialog(
    updateInfo: AppUpdateInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentState by remember { mutableStateOf<UpdateState>(UpdateState.UpdateAvailable(updateInfo)) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }

    AlertDialog(
        onDismissRequest = {
            if (currentState !is UpdateState.Downloading) {
                onDismiss()
            }
        },
        icon = {
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = "Update",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "নতুন আপডেট উপলব্ধ!",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "নতুন ভার্সন: ${updateInfo.versionName}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (updateInfo.releaseNotes.isNotBlank()) {
                    Text(
                        text = updateInfo.releaseNotes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                when (val state = currentState) {
                    is UpdateState.Downloading -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { state.progress / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                            Text(
                                text = "ডাউনলোড হচ্ছে: ${state.progress}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    is UpdateState.ReadyToInstall -> {
                        Text(
                            text = "✅ ডাউনলোড সম্পন্ন হয়েছে! ইনস্টল বাটনে ট্যাপ করুন।",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF2E7D32)
                        )
                    }
                    is UpdateState.Error -> {
                        Text(
                            text = "❌ ডাউনলোডে সমস্যা হয়েছে: ${state.message}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    else -> {}
                }
            }
        },
        confirmButton = {
            when (val state = currentState) {
                is UpdateState.Downloading -> {
                    // Downloading in progress, disabled
                    Button(onClick = {}, enabled = false) {
                        Text("ডাউনলোড হচ্ছে...")
                    }
                }
                is UpdateState.ReadyToInstall -> {
                    Button(
                        onClick = {
                            downloadedFile?.let { file ->
                                AppUpdater.installApk(context, file)
                            }
                        }
                    ) {
                        Text("ইনস্টল করুন")
                    }
                }
                else -> {
                    Button(
                        onClick = {
                            currentState = UpdateState.Downloading(0)
                            scope.launch {
                                val file = AppUpdater.downloadApk(
                                    context = context,
                                    downloadUrl = updateInfo.downloadUrl,
                                    onProgress = { progress ->
                                        currentState = UpdateState.Downloading(progress)
                                    }
                                )
                                if (file != null && file.exists()) {
                                    downloadedFile = file
                                    currentState = UpdateState.ReadyToInstall(file)
                                    // Automatically trigger system installer
                                    AppUpdater.installApk(context, file)
                                } else {
                                    currentState = UpdateState.Error("ফাইল ডাউনলোড ব্যর্থ হয়েছে।")
                                }
                            }
                        }
                    ) {
                        Text("এখনই আপডেট করুন")
                    }
                }
            }
        },
        dismissButton = {
            if (currentState !is UpdateState.Downloading) {
                TextButton(onClick = onDismiss) {
                    Text("পরে")
                }
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
