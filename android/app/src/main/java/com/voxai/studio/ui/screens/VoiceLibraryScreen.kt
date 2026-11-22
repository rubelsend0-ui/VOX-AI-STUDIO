package com.voxai.studio.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.voxai.studio.data.local.CachedVoiceEntity
import com.voxai.studio.ui.components.GlassCard
import com.voxai.studio.ui.theme.*

@Composable
fun VoiceLibraryScreen(
    voices: List<CachedVoiceEntity>,
    onPreview: (String, String) -> Unit,
    onUseForDubbing: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit
) {
    var renameTarget by remember { mutableStateOf<CachedVoiceEntity?>(null) }
    var newNameText by remember { mutableStateOf("") }

    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Voice Profile") },
            text = {
                OutlinedTextField(
                    value = newNameText,
                    onValueChange = { newNameText = it },
                    singleLine = true,
                    label = { Text("New Voice Name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    renameTarget?.let { onRename(it.id, newNameText) }
                    renameTarget = null
                }) { Text("Save", color = NeonBlue) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        item {
            Text("Saved Voice Library", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Manage your personal cloned voice models", color = Color(0xFF94A3B8), fontSize = 13.sp)
        }

        if (voices.isEmpty()) {
            item {
                GlassCard {
                    Text("Your Voice Library is empty.", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Use the Clone tab to record or upload an authorized voice sample.", color = Color(0xFF94A3B8), fontSize = 13.sp)
                }
            }
        } else {
            items(voices) { voice ->
                GlassCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(voice.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("ID: ${voice.id.take(8)}... • ${voice.provider.uppercase()}", color = NeonBlue, fontSize = 12.sp)
                            Text("Created: ${voice.createdAt.take(10)} • Status: ${voice.status}", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        }
                        Row {
                            IconButton(onClick = {
                                renameTarget = voice
                                newNameText = voice.name
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Rename", tint = Color(0xFFCBD5E1))
                            }
                            IconButton(onClick = { onDelete(voice.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RoseError)
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { onPreview(voice.id, voice.name) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, tint = NeonBlue)
                            Spacer(Modifier.width(6.dp))
                            Text("Preview", color = Color.White)
                        }
                        Button(
                            onClick = { onUseForDubbing(voice.id) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricPurple)
                        ) {
                            Icon(Icons.Default.GraphicEq, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Use Voice")
                        }
                    }
                }
            }
        }
    }
}
