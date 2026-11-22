package com.voxai.studio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voxai.studio.data.local.CachedGenerationEntity
import com.voxai.studio.data.local.CachedVoiceEntity
import com.voxai.studio.ui.components.GlassCard
import com.voxai.studio.ui.theme.*
import com.voxai.studio.viewmodel.StudioUiState

@Composable
fun DashboardScreen(
    uiState: StudioUiState,
    voices: List<CachedVoiceEntity>,
    history: List<CachedGenerationEntity>,
    onNavigateToClone: () -> Unit,
    onNavigateToDubbing: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onRefresh: () -> Unit,
    onPlayGeneration: (String, String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 90.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "VOX AI STUDIO",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = "Personal Neural Voice & Dubbing Suite",
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, contentDescription = "Sync", tint = NeonBlue)
                }
            }
        }

        if (!uiState.isConfigured) {
            item {
                GlassCard {
                    Text("Backend Setup Required", color = NeonBlue, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Connect your Render Node.js backend URL and personal API Key in Settings to activate voice cloning and dubbing.",
                        color = Color(0xFFCBD5E1),
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onNavigateToSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple)
                    ) {
                        Text("Configure Backend Connection")
                    }
                }
            }
        }

        // System & AI Engine Status Card
        item {
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(if (uiState.apiConnected) EmeraldStatus else RoseError, CircleShape)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (uiState.apiConnected) "API Online" else "API Disconnected",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                    Surface(
                        color = ElectricPurple.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = uiState.activeProvider.uppercase(),
                            color = NeonBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "AI Engine: ${uiState.aiEngineStatus}",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            }
        }

        // Metrics Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassCard(modifier = Modifier.weight(1f)) {
                    Text("Saved Voices", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("${voices.size}", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
                GlassCard(modifier = Modifier.weight(1f)) {
                    Text("Generated Audio", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("${history.size}", color = NeonBlue, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Quick Actions
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onNavigateToClone,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Clone Voice", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onNavigateToDubbing,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonBlue, contentColor = AmoledBlack)
                ) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("AI Dubbing", fontWeight = FontWeight.Bold)
                }
            }
        }

        // Recently Used Voices
        item {
            Text("Voice Profiles", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            if (voices.isEmpty()) {
                GlassCard {
                    Text("No cloned voices yet. Tap 'Clone Voice' to create your first voice profile.", color = Color(0xFF94A3B8), fontSize = 13.sp)
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(voices.take(6)) { voice ->
                        GlassCard(modifier = Modifier.width(160.dp)) {
                            Icon(Icons.Default.RecordVoiceOver, contentDescription = null, tint = ElectricPurple)
                            Spacer(Modifier.height(6.dp))
                            Text(voice.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text("${voice.durationSeconds}s • ${voice.provider}", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Recent Projects
        item {
            Text("Recent Dubbing Projects", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        if (history.isEmpty()) {
            item {
                GlassCard {
                    Text("No generated dubs yet. Open AI Dubbing Studio to synthesize speech.", color = Color(0xFF94A3B8), fontSize = 13.sp)
                }
            }
        } else {
            items(history.take(5)) { item ->
                GlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.projectTitle, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("${item.voiceName} • ${item.language.uppercase()}", color = NeonBlue, fontSize = 12.sp)
                        }
                        if (!item.mp3Url.isNullOrBlank()) {
                            IconButton(onClick = { onPlayGeneration(item.mp3Url, item.projectTitle) }) {
                                Icon(Icons.Default.PlayCircleFilled, contentDescription = "Play", tint = ElectricPurple, modifier = Modifier.size(34.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
