package com.voxai.studio.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.voxai.studio.ui.components.AnimatedAudioWaveform
import com.voxai.studio.ui.components.GlassCard
import com.voxai.studio.ui.theme.*
import com.voxai.studio.viewmodel.StudioUiState

@Composable
fun VoiceCloneScreen(
    uiState: StudioUiState,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onImportAudio: (android.net.Uri) -> Unit,
    onCloneSubmit: (String, Boolean) -> Unit
) {
    val context = LocalContext.current
    var voiceName by remember { mutableStateOf("") }
    var consentChecked by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onStartRecord()
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { onImportAudio(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("AI Voice Cloning Lab", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(
            "Record 5–60 seconds of clear speech or upload a clean WAV/MP3/M4A sample.",
            color = Color(0xFF94A3B8),
            fontSize = 13.sp
        )

        GlassCard {
            AnimatedAudioWaveform(
                isActive = uiState.isRecording,
                amplitude = uiState.currentAmplitude
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (uiState.isRecording) "Recording: ${uiState.recordingSeconds}s"
                else if (uiState.stagedAudioName.isNotBlank()) "Ready: ${uiState.stagedAudioName}"
                else "No audio sample staged",
                color = if (uiState.isRecording) RoseError else NeonBlue,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        if (uiState.isRecording) {
                            onStopRecord()
                        } else {
                            val hasPerm = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasPerm) onStartRecord() else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isRecording) RoseError else ElectricPurple
                    )
                ) {
                    Icon(if (uiState.isRecording) Icons.Default.Stop else Icons.Default.Mic, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (uiState.isRecording) "Stop" else "Record Mic")
                }

                OutlinedButton(
                    onClick = { filePickerLauncher.launch("audio/*") },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isRecording
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, tint = NeonBlue)
                    Spacer(Modifier.width(6.dp))
                    Text("Upload File", color = Color.White)
                }
            }
        }

        GlassCard {
            Text("Voice Profile Details", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = voiceName,
                onValueChange = { voiceName = it },
                label = { Text("Custom Voice Name (e.g., My Studio Voice)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = consentChecked,
                    onCheckedChange = { consentChecked = it },
                    colors = CheckboxDefaults.colors(checkedColor = ElectricPurple)
                )
                Text(
                    text = "I confirm this is my own voice or I have explicit legal permission from the speaker to clone it.",
                    color = Color(0xFFCBD5E1),
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = { onCloneSubmit(voiceName, consentChecked) },
                enabled = !uiState.isLoading && !uiState.isRecording && uiState.stagedAudioFile != null,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Processing Voice Model...")
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Create Cloned Voice Profile", fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(80.dp))
    }
}
