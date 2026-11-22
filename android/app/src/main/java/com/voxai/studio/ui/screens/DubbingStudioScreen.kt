package com.voxai.studio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voxai.studio.data.local.CachedGenerationEntity
import com.voxai.studio.data.local.CachedVoiceEntity
import com.voxai.studio.ui.components.AnimatedAudioWaveform
import com.voxai.studio.ui.components.GlassCard
import com.voxai.studio.ui.theme.*
import com.voxai.studio.viewmodel.StudioUiState

@Composable
fun DubbingStudioScreen(
    uiState: StudioUiState,
    voices: List<CachedVoiceEntity>,
    history: List<CachedGenerationEntity>,
    onSelectVoice: (String) -> Unit,
    onScriptChange: (String) -> Unit,
    onLanguageChange: (String) -> Unit,
    onProjectTitleChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onPlayAudio: (String, String) -> Unit,
    onDownloadAudio: (String?, String, String) -> Unit
) {
    val languageLabels = mapOf(
        "en" to "English",
        "hi" to "Hindi",
        "bn" to "Bengali",
        "es" to "Spanish",
        "fr" to "French",
        "de" to "German",
        "ja" to "Japanese",
        "ar" to "Arabic"
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        item {
            Text("AI Dubbing Studio", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Multilingual Neural Text-to-Speech Synthesis", color = Color(0xFF94A3B8), fontSize = 13.sp)
        }

        // Voice Selector
        item {
            GlassCard {
                Text("1. Select Cloned Voice", color = NeonBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                if (voices.isEmpty()) {
                    Text("No saved voices available. Clone a voice first.", color = RoseError, fontSize = 13.sp)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(voices) { v ->
                            FilterChip(
                                selected = uiState.selectedVoiceId == v.id,
                                onClick = { onSelectVoice(v.id) },
                                label = { Text(v.name) }
                            )
                        }
                    }
                }
            }
        }

        // Language Selector
        item {
            GlassCard {
                Text("2. Output Language", color = NeonBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.supportedLanguages.take(10)) { code ->
                        FilterChip(
                            selected = uiState.selectedLanguage == code,
                            onClick = { onLanguageChange(code) },
                            label = { Text(languageLabels[code] ?: code.uppercase()) }
                        )
                    }
                }
            }
        }

        // Script Editor
        item {
            GlassCard {
                OutlinedTextField(
                    value = uiState.projectTitle,
                    onValueChange = onProjectTitleChange,
                    label = { Text("Project Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = uiState.scriptText,
                    onValueChange = onScriptChange,
                    label = { Text("Enter Dubbing Script...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("${uiState.scriptText.length} / 10000 chars", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    Text("Auto-chunked synthesis enabled", color = NeonBlue, fontSize = 12.sp)
                }

                Spacer(Modifier.height(12.dp))

                if (uiState.isLoading && uiState.activeJob != null) {
                    AnimatedAudioWaveform(isActive = true, amplitude = 0.8f)
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (uiState.activeJob.progress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = NeonBlue
                    )
                    Text(
                        "Synthesizing (${uiState.activeJob.progress}%) - ${uiState.activeJob.status}",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                } else {
                    Button(
                        onClick = onGenerate,
                        enabled = !uiState.isLoading && voices.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Synthesize Dubbing Audio", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Generation History
        item {
            Text("Generation History", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        items(history) { item ->
            GlassCard {
                Text(item.projectTitle, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("${item.voiceName} • ${item.language.uppercase()} • ${item.status}", color = NeonBlue, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text(item.scriptText.take(120), color = Color(0xFF94A3B8), fontSize = 12.sp, maxLines = 2)
                Spacer(Modifier.height(10.dp))

                if (item.status == "COMPLETED") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { item.mp3Url?.let { onPlayAudio(it, item.projectTitle) } },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Text("Play")
                        }
                        OutlinedButton(onClick = { onDownloadAudio(item.mp3Url, item.projectTitle, "mp3") }) {
                            Text("MP3", color = Color.White)
                        }
                        OutlinedButton(onClick = { onDownloadAudio(item.wavUrl, item.projectTitle, "wav") }) {
                            Text("WAV", color = NeonBlue)
                        }
                    }
                }
            }
        }
    }
}
