package com.aetherwave.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.aetherwave.player.data.PlaylistStore
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    playlistStore: PlaylistStore,
    allFolders: List<String>,
    onAddFolder: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onForceMaxBitrateChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val autoPlayNext = remember { mutableStateOf(playlistStore.getBoolean("auto_play_next", true)) }
    val crossfade = remember { mutableStateOf(playlistStore.getBoolean("crossfade", false)) }
    val forceMaxBitrate = remember { mutableStateOf(playlistStore.getBoolean("force_max_bitrate", false)) }
    val excludedFolders = remember { mutableStateOf(playlistStore.getExcludedFolders()) }
    val manualFolders = remember { mutableStateOf(playlistStore.getManualFolders()) }
    val currentAccent = remember { mutableStateOf(playlistStore.getAccentColor()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Developer Credit
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("AetherWave", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("Premium Audiophile Engine", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Developed by Aniket Kumar", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            // Theme Customization
            item {
                SectionLabel("Theme Customization")
            }
            item {
                val themeOptions = listOf(
                    "Dynamic (Album Art)" to null,
                    "Neon Cyan" to 0xFF00FFFF,
                    "Crimson" to 0xFFDC143C,
                    "Amethyst" to 0xFF9966CC
                )
                
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        themeOptions.forEach { (name, colorVal) ->
                            val isSelected = currentAccent.value == colorVal
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        currentAccent.value = colorVal
                                        playlistStore.setAccentColor(colorVal)
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = if (colorVal == null) MaterialTheme.colorScheme.surfaceVariant else Color(colorVal),
                                    shape = RoundedCornerShape(50),
                                    modifier = Modifier.size(24.dp)
                                ) {}
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(name, style = MaterialTheme.typography.bodyLarge, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }

            // Audio Tweaks
            item {
                SectionLabel("Audio Tweaks")
            }
            item {
                SettingsToggle("Auto-Play Next Track", "Automatically play the next track in queue", autoPlayNext.value) {
                    autoPlayNext.value = it
                    playlistStore.setBoolean("auto_play_next", it)
                }
            }
            item {
                SettingsToggle("Crossfade", "Smooth transition between tracks", crossfade.value) {
                    crossfade.value = it
                    playlistStore.setBoolean("crossfade", it)
                }
            }
            item {
                SettingsToggle("Force Max Bitrate", "Always decode at maximum quality", forceMaxBitrate.value) {
                    forceMaxBitrate.value = it
                    playlistStore.setBoolean("force_max_bitrate", it)
                    onForceMaxBitrateChange(it)
                }
            }

            // Manual Folder Management
            item {
                SectionLabel("Custom Music Folders")
                Text(
                    "Add folders manually if they are not showing up in the library.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(manualFolders.value.toList()) { uriStr ->
                val displayName = try {
                    android.net.Uri.parse(uriStr).lastPathSegment ?: uriStr
                } catch (_: Exception) { uriStr }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(displayName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = {
                            playlistStore.removeManualFolder(uriStr)
                            manualFolders.value = playlistStore.getManualFolders()
                        }) {
                            Icon(Icons.Default.Delete, "Remove", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = {
                        onAddFolder()
                        // The re-scan is handled by MainActivity's picker callback
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Music Folder")
                }
            }

            // Folder Management (Exclusions)
            if (allFolders.isNotEmpty()) {
                item {
                    SectionLabel("Exclude Folders")
                    Text(
                        "Disable folders you don't want scanned (e.g., WhatsApp voice notes)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                items(allFolders) { folder ->
                    val isExcluded = excludedFolders.value.contains(folder)
                    val displayName = folder.substringAfterLast("/")

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(displayName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                Text(folder, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            Switch(
                                checked = !isExcluded,
                                onCheckedChange = {
                                    playlistStore.toggleExcludedFolder(folder)
                                    excludedFolders.value = playlistStore.getExcludedFolders()
                                }
                            )
                        }
                    }
                }
            }

            // Backup & Restore
            item {
                SectionLabel("Backup & Restore")
            }
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Data Portability", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("Export your playlists, settings, cached lyrics, and covers to a single file. You can restore them if you reinstall the app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = onImport,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Import Backup")
                            }
                            Button(
                                onClick = onExport,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Export Backup")
                            }
                        }
                    }
                }
            }

            // Spacer
            item { Spacer(modifier = Modifier.height(48.dp)) }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsToggle(title: String, subtitle: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onToggle)
        }
    }
}
