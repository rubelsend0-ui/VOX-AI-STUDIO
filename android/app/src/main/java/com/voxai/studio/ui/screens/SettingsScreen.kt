package com.voxai.studio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voxai.studio.ui.components.GlassCard
import com.voxai.studio.ui.theme.*
import com.voxai.studio.viewmodel.StudioUiState

@Composable
fun SettingsScreen(
    uiState: StudioUiState,
    onSaveBackendConnection: (String, String) -> Unit,
    onSaveAiProvider: (String, String) -> Unit
) {
    var backendUrl by remember(uiState.backendUrl) { mutableStateOf(uiState.backendUrl) }
    var apiKey by remember(uiState.apiKey) { mutableStateOf(uiState.apiKey) }
    var selectedProvider by remember(uiState.activeProvider) { mutableStateOf(uiState.activeProvider) }
    var xttsUrl by remember(uiState.xttsUrl) { mutableStateOf(uiState.xttsUrl) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Studio & AI Engine Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)

        GlassCard {
            Text("Personal Render Backend Connection", color = NeonBlue, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = backendUrl,
                onValueChange = { backendUrl = it },
                label = { Text("Render Backend HTTPS URL") },
                placeholder = { Text("https://vox-ai-studio-api.onrender.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("Personal APP_API_KEY") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onSaveBackendConnection(backendUrl, apiKey) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple)
            ) {
                Text("Save & Connect Backend", fontWeight = FontWeight.Bold)
            }
        }

        GlassCard {
            Text("AI Voice Cloning Provider", color = NeonBlue, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = selectedProvider == "xtts",
                    onClick = { selectedProvider = "xtts" },
                    label = { Text("XTTS-v2 (Open-Source)") }
                )
                FilterChip(
                    selected = selectedProvider == "elevenlabs",
                    onClick = { selectedProvider = "elevenlabs" },
                    label = { Text("External Cloud API") }
                )
            }

            if (selectedProvider == "xtts") {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = xttsUrl,
                    onValueChange = { xttsUrl = it },
                    label = { Text("XTTS-v2 Inference Server URL") },
                    placeholder = { Text("https://your-gpu-xtts-node.example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Spacer(Modifier.height(8.dp))
                Text(
                    "External API Key is securely read from your Render server environment variable (AI_PROVIDER_API_KEY).",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onSaveAiProvider(selectedProvider, xttsUrl) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonBlue, contentColor = AmoledBlack)
            ) {
                Text("Apply AI Provider Configuration", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}
