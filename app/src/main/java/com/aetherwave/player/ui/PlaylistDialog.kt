package com.aetherwave.player.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.aetherwave.player.data.PlaylistStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDialog(
    playlistStore: PlaylistStore,
    trackPath: String?,
    context: Context,
    onDismiss: () -> Unit
) {
    var showCreateNew by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    val playlistNames = remember { mutableStateOf(playlistStore.getPlaylistNames()) }

    if (showCreateNew) {
        AlertDialog(
            onDismissRequest = { showCreateNew = false },
            title = { Text("Create Playlist") },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist Name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPlaylistName.isNotBlank()) {
                        playlistStore.createPlaylist(newPlaylistName.trim())
                        if (trackPath != null) {
                            playlistStore.addToPlaylist(newPlaylistName.trim(), trackPath)
                            Toast.makeText(context, "Added to ${newPlaylistName.trim()}", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Playlist created", Toast.LENGTH_SHORT).show()
                        }
                        playlistNames.value = playlistStore.getPlaylistNames()
                        newPlaylistName = ""
                        showCreateNew = false
                        onDismiss()
                    }
                }) { Text(if (trackPath != null) "Create & Add" else "Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateNew = false }) { Text("Cancel") }
            }
        )
        return
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)
        ) {
            Text(if (trackPath != null) "Add to Playlist" else "New Playlist", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            // Create new option
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().clickable { showCreateNew = true }
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, "Create New", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Create New Playlist", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (playlistNames.value.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    Text("No playlists yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                playlistNames.value.forEach { name ->
                    val trackCount = playlistStore.getPlaylistTracks(name).size
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable {
                            if (trackPath != null) {
                                playlistStore.addToPlaylist(name, trackPath)
                                Toast.makeText(context, "Added to $name", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        }
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("$trackCount tracks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
