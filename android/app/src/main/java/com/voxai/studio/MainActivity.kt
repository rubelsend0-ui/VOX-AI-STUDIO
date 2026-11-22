package com.voxai.studio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voxai.studio.ui.components.MiniAudioPlayerBar
import com.voxai.studio.ui.screens.*
import com.voxai.studio.ui.theme.*
import com.voxai.studio.viewmodel.StudioViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            VoxStudioTheme {
                VoxStudioApp()
            }
        }
    }
}

@Composable
fun VoxStudioApp(vm: StudioViewModel = viewModel()) {
    val uiState by vm.uiState.collectAsState()
    val voices by vm.voices.collectAsState()
    val history by vm.history.collectAsState()
    var currentTab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = AmoledBlack,
        topBar = {
            Column {
                uiState.errorBanner?.let { err ->
                    Surface(color = RoseError, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(err, color = Color.White, modifier = Modifier.weight(1f))
                            TextButton(onClick = { vm.clearBanners() }) {
                                Text("Dismiss", color = Color.White)
                            }
                        }
                    }
                }
                uiState.statusBanner?.let { msg ->
                    Surface(color = DeepViolet, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(msg, color = Color.White, modifier = Modifier.weight(1f))
                            TextButton(onClick = { vm.clearBanners() }) {
                                Text("OK", color = NeonBlue)
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column {
                MiniAudioPlayerBar(
                    title = uiState.currentPlayingTitle,
                    isPlaying = uiState.isPlaying,
                    positionMs = uiState.playbackPositionMs,
                    durationMs = uiState.playbackDurationMs,
                    onTogglePlay = { vm.togglePlayPause() },
                    onSeek = { vm.seekTo(it) }
                )
                NavigationBar(containerColor = Color(0xFF0A0B10)) {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = "Home") },
                        label = { Text("Home") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        icon = { Icon(Icons.Default.Mic, contentDescription = "Clone") },
                        label = { Text("Clone") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 },
                        icon = { Icon(Icons.Default.RecordVoiceOver, contentDescription = "Library") },
                        label = { Text("Voices") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 3,
                        onClick = { currentTab = 3 },
                        icon = { Icon(Icons.Default.GraphicEq, contentDescription = "Dubbing") },
                        label = { Text("Dubbing") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 4,
                        onClick = { currentTab = 4 },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (currentTab) {
                0 -> DashboardScreen(
                    uiState = uiState,
                    voices = voices,
                    history = history,
                    onNavigateToClone = { currentTab = 1 },
                    onNavigateToDubbing = { currentTab = 3 },
                    onNavigateToSettings = { currentTab = 4 },
                    onRefresh = { vm.refreshAll() },
                    onPlayGeneration = { url, title -> vm.playAudioUrl(url, title) }
                )
                1 -> VoiceCloneScreen(
                    uiState = uiState,
                    onStartRecord = { vm.startMicRecording() },
                    onStopRecord = { vm.stopMicRecording() },
                    onImportAudio = { uri -> vm.stageImportedUri(uri) },
                    onCloneSubmit = { name, consent -> vm.cloneStagedVoice(name, consent) }
                )
                2 -> VoiceLibraryScreen(
                    voices = voices,
                    onPreview = { id, name -> vm.previewVoice(id, name) },
                    onUseForDubbing = { id ->
                        vm.selectVoiceForDubbing(id)
                        currentTab = 3
                    },
                    onRename = { id, newName -> vm.renameVoice(id, newName) },
                    onDelete = { id -> vm.deleteVoice(id) }
                )
                3 -> DubbingStudioScreen(
                    uiState = uiState,
                    voices = voices,
                    history = history,
                    onSelectVoice = { vm.selectVoiceForDubbing(it) },
                    onScriptChange = { vm.updateScriptText(it) },
                    onLanguageChange = { vm.updateLanguage(it) },
                    onProjectTitleChange = { vm.updateProjectTitle(it) },
                    onGenerate = { vm.generateDubbing() },
                    onPlayAudio = { url, title -> vm.playAudioUrl(url, title) },
                    onDownloadAudio = { url, title, ext -> vm.downloadAudio(url, title, ext) }
                )
                4 -> SettingsScreen(
                    uiState = uiState,
                    onSaveBackendConnection = { url, key -> vm.saveConnectionConfig(url, key) },
                    onSaveAiProvider = { provider, url -> vm.updateProviderSettings(provider, url) }
                )
            }
        }
    }
}
