package com.aetherwave.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aetherwave.player.PlaybackService

@Composable
fun MiniPlayer(
    playbackService: PlaybackService,
    onExpand: () -> Unit
) {
    val currentTrack by playbackService.currentTrack.collectAsState()
    val isPlaying by playbackService.isPlaying.collectAsState()
    val duration by playbackService.duration.collectAsState()
    val currentPosition by playbackService.currentPosition.collectAsState()
    val coverArtUrl by playbackService.coverArtUrl.collectAsState()

    val track = currentTrack ?: return
    val progress = if (duration > 0.0) (currentPosition / duration).toFloat().coerceIn(0f, 1f) else 0f

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        tonalElevation = 8.dp,
        modifier = Modifier.fillMaxWidth().clickable { onExpand() }
    ) {
        Column {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cover art or placeholder
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.size(44.dp)
                ) {
                    if (coverArtUrl != null) {
                        AsyncImage(model = coverArtUrl, contentDescription = "Art", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.MusicNote, "Music", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                IconButton(onClick = { playbackService.previousTrack() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.SkipPrevious, "Previous", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                }

                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.size(40.dp).clickable {
                        if (isPlaying) playbackService.pauseTrack() else playbackService.playTrack()
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            "Play/Pause",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                IconButton(onClick = { playbackService.nextTrack() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.SkipNext, "Next", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
