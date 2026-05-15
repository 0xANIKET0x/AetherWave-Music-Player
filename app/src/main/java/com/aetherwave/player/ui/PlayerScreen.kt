package com.aetherwave.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider as Divider
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import android.content.res.Configuration
import com.aetherwave.player.PlaybackService
import com.aetherwave.player.data.PlaylistStore
import com.aetherwave.player.data.Track
import androidx.compose.ui.platform.LocalConfiguration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.app.Activity
import android.util.Log

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    playbackService: PlaybackService,
    playlistStore: PlaylistStore,
    onBack: () -> Unit,
    onLyricsClick: () -> Unit
) {
    val context = LocalContext.current
    BackHandler { onBack() }

    val currentTrack by playbackService.currentTrack.collectAsState(initial = null)
    val duration by playbackService.duration.collectAsState(initial = 0.0)
    val isPlaying by playbackService.isPlaying.collectAsState(initial = false)
    val inputBitrate by playbackService.inputBitrate.collectAsState(initial = 0)
    val isDolbyAtmosFile by playbackService.isDolbyAtmosFile.collectAsState(initial = false)
    val shuffleEnabled by playbackService.shuffleEnabled.collectAsState(initial = false)
    val repeatMode by playbackService.repeatMode.collectAsState(initial = 0)
    val coverArtUrl by playbackService.coverArtUrl.collectAsState(initial = null)
    val lyricsMode by playbackService.lyricsMode.collectAsState(initial = 0)
    val outputDeviceInfo by playbackService.outputDeviceInfo.collectAsState()
    
    // Favorite state
    var isFavorite by remember(currentTrack) {
        mutableStateOf(currentTrack?.filePath?.let { playlistStore.isFavorite(it) } ?: false)
    }

    val dominantColor by playbackService.dominantColor.collectAsState()
    
    // Bottom sheet states
    var showDsp by remember { mutableStateOf(false) }
    var showEq by remember { mutableStateOf(false) }
    var showPlaylist by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }

    // Bottom sheets
    if (showDsp) DspSheet(playbackService) { showDsp = false }
    if (showEq) EqSheet(playbackService) { showEq = false }
    if (showPlaylist) {
        PlaylistDialog(playlistStore, currentTrack?.filePath, context) { showPlaylist = false }
    }
    if (showInfo) {
        TrackInfoDialog(currentTrack, outputDeviceInfo, inputBitrate, isDolbyAtmosFile) { showInfo = false }
    }

    val accentColorSetting = playlistStore.getAccentColor()
    val extractedColor = dominantColor?.let { Color(it) } ?: Color(0xFF1E1E1E)
    val finalAccentColor = if (accentColorSetting != null) Color(accentColorSetting) else extractedColor

    Box(modifier = Modifier.fillMaxSize()) {
        // Blurred background cover art
        AnimatedContent(
            targetState = coverArtUrl,
            transitionSpec = { fadeIn(tween(800)) togetherWith fadeOut(tween(800)) },
            label = "art_blur"
        ) { url ->
            if (url != null) {
                AsyncImage(
                    model = url, contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(50.dp)
                )
            } else {
                Box(Modifier.fillMaxSize().background(Color.DarkGray))
            }
        }
        
        // Dark gradient overlay using Accent Color
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(
                    finalAccentColor.copy(alpha = 0.5f),
                    Color.Black.copy(alpha = 0.85f),
                    Color.Black.copy(alpha = 0.95f)
                ))
            )
        )

        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

        if (isLandscape) {
            // Landscape: Side-by-side
            Row(
                modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            ) {
                // Left: Album Art
                Box(
                    modifier = Modifier.weight(0.45f).fillMaxHeight().padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    PlayerAlbumArt(coverArtUrl)
                }

                // Right: Controls
                Column(
                    modifier = Modifier.weight(0.55f).fillMaxHeight().padding(end = 32.dp, top = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    // Back button at top of right pane
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = { showInfo = true }) {
                            Icon(Icons.Default.Info, "Media Info", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(24.dp))
                        }
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.KeyboardArrowDown, "Back", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(28.dp))
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    PlayerTrackInfo(currentTrack, inputBitrate, isDolbyAtmosFile)
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    PlayerSeekbar(
                        playbackService = playbackService,
                        totalDurationSeconds = duration.toFloat(),
                        onSeekRequested = { seconds -> playbackService.seekTo(seconds) }
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    PlayerControls(shuffleEnabled, isPlaying, repeatMode, playbackService)
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    PlayerActions(
                        isFavorite = isFavorite,
                        onFavoriteClick = {
                            currentTrack?.filePath?.let { isFavorite = playlistStore.toggleFavorite(it) }
                        },
                        onLyricsClick = onLyricsClick,
                        onDspClick = { showDsp = true },
                        onEqClick = { showEq = true },
                        onPlaylistClick = { showPlaylist = true }
                    )

                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        } else {
            // Portrait: Stacked
            Column(
                modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            ) {
                // ─── Top Bar ───
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.KeyboardArrowDown, "Back", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(28.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (inputBitrate >= 1400000) QualityBadge("HI-RES", Color(0xFFFFD700))
                        if (isDolbyAtmosFile) QualityBadge("ATMOS", Color(0xFF00E5FF))
                        IconButton(onClick = { showInfo = true }) {
                            Icon(Icons.Default.Info, "Media Info", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(24.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(0.3f))

                // ─── Album Art ───
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    PlayerAlbumArt(coverArtUrl)
                }

                Spacer(modifier = Modifier.weight(0.3f))

                // ─── Track Info ───
                PlayerTrackInfo(currentTrack, inputBitrate, isDolbyAtmosFile, showBadges = false)

                Spacer(modifier = Modifier.height(20.dp))

                // ─── Seekbar ───
                PlayerSeekbar(
                    playbackService = playbackService,
                    totalDurationSeconds = duration.toFloat(),
                    onSeekRequested = { seconds -> playbackService.seekTo(seconds) }
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ─── Playback Controls ───
                PlayerControls(shuffleEnabled, isPlaying, repeatMode, playbackService)

                Spacer(modifier = Modifier.height(20.dp))

                // ─── Action Row ───
                PlayerActions(
                    isFavorite = isFavorite,
                    onFavoriteClick = {
                        currentTrack?.filePath?.let { isFavorite = playlistStore.toggleFavorite(it) }
                    },
                    onLyricsClick = onLyricsClick,
                    onDspClick = { showDsp = true },
                    onEqClick = { showEq = true },
                    onPlaylistClick = { showPlaylist = true }
                )

                Spacer(modifier = Modifier.weight(0.3f))
            }
        }
    }
}

@Composable
private fun PlayerAlbumArt(coverArtUrl: String?) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 32.dp,
        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
    ) {
        if (coverArtUrl != null) {
            AsyncImage(model = coverArtUrl, contentDescription = "Album Art", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(Color(0xFF6C63FF), Color(0xFF3F51B5), Color(0xFF1A237E)))
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MusicNote, "Music", tint = Color.White.copy(alpha = 0.3f), modifier = Modifier.size(72.dp))
            }
        }
    }
}

@Composable
private fun PlayerTrackInfo(track: Track?, bitrate: Int, isAtmos: Boolean, showBadges: Boolean = true) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
        horizontalAlignment = if (showBadges) Alignment.Start else Alignment.CenterHorizontally
    ) {
        if (showBadges) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                if (bitrate >= 1400000) QualityBadge("HI-RES", Color(0xFFFFD700))
                if (isAtmos) QualityBadge("ATMOS", Color(0xFF00E5FF))
            }
        }
        Text(
            track?.title ?: "No Track",
            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
            color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, 
            textAlign = if (showBadges) TextAlign.Start else TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            track?.artist ?: "",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PlayerSeekbar(
    playbackService: PlaybackService,
    totalDurationSeconds: Float,
    onSeekRequested: (Float) -> Unit
) {
    val currentPositionSeconds by playbackService.currentPosition.collectAsState()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isInteracting = isPressed || isDragged

    // Local state for the thumb position
    var sliderValue by remember { mutableFloatStateOf(0f) }

    // Only accept Service updates if the user is NOT touching the slider
    LaunchedEffect(currentPositionSeconds, isInteracting) {
        if (!isInteracting) {
            sliderValue = currentPositionSeconds.toFloat()
        }
    }

    val safeDuration = totalDurationSeconds.coerceAtLeast(0.1f)

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Slider(
            value = sliderValue.coerceIn(0f, safeDuration),
            valueRange = 0f..safeDuration,
            interactionSource = interactionSource,
            onValueChange = { 
                sliderValue = it 
            },
            onValueChangeFinished = {
                android.util.Log.d("AetherWaveUI", "Seekbar released: Seeking to $sliderValue s")
                onSeekRequested(sliderValue)
            },
            enabled = totalDurationSeconds > 0f,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Color.White, 
                activeTrackColor = Color.White, 
                inactiveTrackColor = Color.White.copy(alpha = 0.15f)
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime((sliderValue * 1000).toLong()), 
                style = MaterialTheme.typography.bodySmall, 
                color = Color.White.copy(alpha = 0.5f)
            )
            Text(
                text = formatTime((totalDurationSeconds * 1000).toLong()), 
                style = MaterialTheme.typography.bodySmall, 
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun PlayerControls(shuffleEnabled: Boolean, isPlaying: Boolean, repeatMode: Int, playbackService: PlaybackService) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PremiumIconButton(Icons.Default.Shuffle, 40.dp, isActive = shuffleEnabled) { playbackService.toggleShuffle() }
        PremiumIconButton(Icons.Default.SkipPrevious, 48.dp) { playbackService.previousTrack() }
        PremiumPlayPause(isPlaying) {
            if (isPlaying) playbackService.pauseTrack() else playbackService.playTrack()
        }
        PremiumIconButton(Icons.Default.SkipNext, 48.dp) { playbackService.nextTrack() }
        PremiumIconButton(
            icon = if (repeatMode == 2) Icons.Default.RepeatOne else Icons.Default.Repeat,
            size = 40.dp,
            isActive = repeatMode > 0
        ) { playbackService.cycleRepeatMode() }
    }
}

@Composable
private fun PlayerActions(isFavorite: Boolean, onFavoriteClick: () -> Unit, onLyricsClick: () -> Unit, onDspClick: () -> Unit, onEqClick: () -> Unit, onPlaylistClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onFavoriteClick) {
            Icon(
                if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                "Favorite",
                tint = if (isFavorite) Color(0xFFFF4081) else Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp)
            )
        }
        IconButton(onClick = onLyricsClick) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, "Lyrics", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(24.dp))
        }
        IconButton(onClick = onPlaylistClick) {
            Icon(Icons.Default.PlaylistAdd, "Add to Playlist", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(26.dp))
        }
        IconButton(onClick = onDspClick) {
            Icon(Icons.Default.Tune, "DSP", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(24.dp))
        }
        IconButton(onClick = onEqClick) {
            Icon(Icons.Default.Equalizer, "EQ", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(24.dp))
        }
    }
}

// ─── Premium Icon Button ───
@Composable
private fun PremiumIconButton(
    icon: ImageVector,
    size: Dp,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "scale"
    )
    IconButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier.size(size).scale(scale)
    ) {
        Icon(
            icon, null,
            tint = if (isActive) Color(0xFF6C63FF) else Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(size * 0.6f)
        )
    }
}

// ─── Premium Play/Pause ───
@Composable
private fun PremiumPlayPause(isPlaying: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "pp"
    )
    Surface(
        color = Color.White,
        shape = CircleShape,
        shadowElevation = 12.dp,
        modifier = Modifier.size(72.dp).scale(scale)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                "Play/Pause",
                tint = Color.Black,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

// ─── Quality Badge ───
@Composable
private fun QualityBadge(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(text, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

@Composable
fun TrackInfoDialog(track: Track?, deviceInfo: String, bitrate: Int, isAtmos: Boolean, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Track Information", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MetadataInfoRow("Title", track?.title ?: "Unknown")
                MetadataInfoRow("Artist", track?.artist ?: "Unknown")
                MetadataInfoRow("Album", track?.album ?: "Unknown")
                Divider(color = Color.White.copy(alpha = 0.1f))
                MetadataInfoRow("Format", if (bitrate >= 1411200) "Lossless (Hi-Res)" else "Standard")
                MetadataInfoRow("Bitrate", "${bitrate / 1000} kbps")
                MetadataInfoRow("Sample Rate", "${track?.sampleRate ?: 0} Hz")
                MetadataInfoRow("Channels", if (isAtmos) "Dolby Atmos (Spatial)" else "${track?.channelCount ?: 2} Channels")
                MetadataInfoRow("Output Device", deviceInfo.ifBlank { "System Speakers" })
                Divider(color = Color.White.copy(alpha = 0.1f))
                Text("File Path:", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.5f))
                Text(track?.filePath ?: "N/A", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Color(0xFF6C63FF)) } },
        containerColor = Color(0xFF1E1E1E)
    )
}

@Composable
fun MetadataInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodyMedium)
        Text(value, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

private fun formatTime(ms: Long): String {
    if (ms < 0) return "0:00"
    val totalSec = (ms / 1000).toInt()
    return "${totalSec / 60}:${(totalSec % 60).toString().padStart(2, '0')}"
}
