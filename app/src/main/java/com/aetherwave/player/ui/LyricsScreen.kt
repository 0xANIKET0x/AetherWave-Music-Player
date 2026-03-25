package com.aetherwave.player.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aetherwave.player.PlaybackService
import com.aetherwave.player.data.LyricLine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsScreen(playbackService: PlaybackService, onBack: () -> Unit) {
    val currentTrack by playbackService.currentTrack.collectAsState(initial = null)
    val lyricsData by playbackService.lyrics.collectAsState(initial = null)
    val lyricsMode by playbackService.lyricsMode.collectAsState()
    val currentPosition by playbackService.currentPosition.collectAsState()
    val coverArtUrl by playbackService.coverArtUrl.collectAsState()
    val dominantColor by playbackService.dominantColor.collectAsState()
    
    val scope = rememberCoroutineScope()
    val scrollState = rememberLazyListState()
    
    val accentColor = dominantColor?.let { Color(it) } ?: Color(0xFF6C63FF)
    
    // Auto-scroll logic for Synced mode (Mode 1 & 2)
    if (lyricsData?.isSynced == true && lyricsMode > 0) {
        val currentMs = (currentPosition * 1000).toLong()
        val activeIndex = lyricsData!!.lines.indexOfLast { it.timeMs <= currentMs }.coerceAtLeast(0)
        
        LaunchedEffect(activeIndex) {
            if (activeIndex >= 0) {
                scrollState.animateScrollToItem(activeIndex, scrollOffset = -200)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // --- Dynamic Background ---
        AnimatedContent(
            targetState = coverArtUrl,
            transitionSpec = { fadeIn(tween(1000)) togetherWith fadeOut(tween(1000)) },
            label = "lyric_bg"
        ) { url ->
            Box(Modifier.fillMaxSize()) {
                if (url != null) {
                    AsyncImage(
                        model = url, contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().blur(if (lyricsMode == 2) 80.dp else 40.dp)
                    )
                }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(
                            Color.Black.copy(alpha = if (lyricsMode == 2) 0.4f else 0.7f),
                            accentColor.copy(alpha = 0.2f),
                            Color.Black.copy(alpha = 0.9f)
                        ))
                    )
                )
            }
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(currentTrack?.title ?: "Lyrics", color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Text(currentTrack?.artist ?: "", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.Close, "Close", tint = Color.White)
                        }
                    },
                    actions = {
                        if (lyricsData != null && !lyricsData!!.isSynced) {
                            TextButton(onClick = { scope.launch { playbackService.upgradeLyrics(true) } }) {
                                Text("Find Synced", color = Color(0xFFFFD700))
                            }
                        }
                        IconButton(onClick = {
                            playbackService.lyricsMode.value = (lyricsMode + 1) % 3
                        }) {
                            Icon(
                                when(lyricsMode) {
                                    1 -> Icons.Default.MusicNote
                                    2 -> Icons.Default.AutoAwesome
                                    else -> Icons.Default.Menu
                                },
                                "Switch Mode", tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                if (lyricsData == null || lyricsData!!.lines.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accentColor)
                    }
                } else {
                    val currentMs = (currentPosition * 1000).toLong()
                    
                    LazyColumn(
                        state = scrollState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 100.dp),
                        verticalArrangement = Arrangement.spacedBy(if (lyricsMode == 2) 32.dp else 24.dp)
                    ) {
                        itemsIndexed(lyricsData!!.lines) { index, line ->
                            val isActive = lyricsData!!.isSynced && lyricsData!!.lines.indexOfLast { it.timeMs <= currentMs } == index
                            
                            LyricLineItem(
                                line = line,
                                mode = lyricsMode,
                                isActive = isActive,
                                accentColor = accentColor,
                                onClick = { if (lyricsData!!.isSynced) playbackService.seekTo(line.timeMs / 1000f) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LyricLineItem(
    line: LyricLine,
    mode: Int,
    isActive: Boolean,
    accentColor: Color,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(if (isActive) 1.1f else 1.0f, label = "scale")
    val alpha by animateFloatAsState(if (isActive) 1.0f else 0.4f, label = "alpha")
    val blurRadius by animateDpAsState(if (isActive || mode != 2) 0.dp else 2.dp, label = "blur")
    
    val textStyle = when(mode) {
        2 -> MaterialTheme.typography.headlineMedium.copy(
            fontWeight = FontWeight.Black,
            fontSize = 32.sp,
            lineHeight = 40.sp,
            letterSpacing = (-1).sp
        )
        1 -> MaterialTheme.typography.headlineSmall.copy(
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp
        )
        else -> MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Medium,
            fontSize = 20.sp
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(if (mode > 0) scale else 1.0f)
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        contentAlignment = if (mode == 2) Alignment.Start else Alignment.Center
    ) {
        Text(
            text = line.text,
            style = textStyle,
            color = if (isActive && mode > 0) Color.White else Color.White.copy(alpha = alpha),
            textAlign = if (mode == 2) TextAlign.Start else TextAlign.Center,
            modifier = Modifier.blur(blurRadius)
        )
    }
}
