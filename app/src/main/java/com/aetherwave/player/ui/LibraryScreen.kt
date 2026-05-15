package com.aetherwave.player.ui

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aetherwave.player.data.PlaylistStore
import com.aetherwave.player.data.Track
import java.util.Calendar

// ─── Premium Bounce Click Modifier ───
fun Modifier.bounceClick(onClick: () -> Unit = {}) = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isPressed) 0.95f else 1f, label = "bounceScale")

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    val up = waitForUpOrCancellation()
                    isPressed = false
                    if (up != null) {
                        onClick()
                    }
                }
            }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    allTracks: List<Track>,
    audiophileTracks: List<Track>,
    albumGroups: Map<String, List<Track>>,
    playlistStore: PlaylistStore,
    onTrackClick: (List<Track>, Int) -> Unit,
    onDetailsClick: (String, List<Track>) -> Unit,
    isScanning: Boolean
) {
    val hiResTracks = remember(allTracks) { allTracks.filter { it.isHighRes && !it.isAtmos } }
    val atmosTracks = remember(allTracks) { allTracks.filter { it.isAtmos } }
    val regularTracks = remember(allTracks) { allTracks.filter { !it.isHighRes && !it.isAtmos } }
    val regularAlbums = remember(regularTracks) { regularTracks.groupBy { it.album } }

    // Favorites
    val favPaths = remember { mutableStateOf(playlistStore.getFavorites()) }
    val favTracks = remember(allTracks, favPaths.value) { allTracks.filter { favPaths.value.contains(it.filePath) } }

    // Playlists
    val playlistNames = remember { mutableStateOf(playlistStore.getPlaylistNames()) }

    // Observe changes
    var playlistVersion by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        playlistStore.changes.collect {
            playlistNames.value = playlistStore.getPlaylistNames()
            favPaths.value = playlistStore.getFavorites()
            playlistVersion++
        }
    }

    // Playlist dialog state
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var playlistDialogTrackPath by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf("All") }
    val categories = listOf("All", "Playlists", "Albums", "Exclusive", "Folders")

    if (showPlaylistDialog) {
        PlaylistDialog(playlistStore, playlistDialogTrackPath, context) {
            showPlaylistDialog = false
            playlistNames.value = playlistStore.getPlaylistNames()
        }
    }

    if (isScanning) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF0F0F23), Color(0xFF000000)))),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color(0xFF22C55E))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Curating your library...", color = Color.White.copy(alpha = 0.7f))
            }
        }
        return
    }

    // Dynamic Greeting
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) {
        in 0..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        else -> "Good Evening"
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Deep Premium Background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF1E1B4B).copy(alpha = 0.5f), Color(0xFF0F0F23)),
                        radius = 1500f
                    )
                )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 48.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // ─── HEADER ───
            item {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        text = greeting,
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 32.sp),
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${allTracks.size} tracks · ${albumGroups.size} albums",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }

            // ─── CATEGORY PILLS ───
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp)
                ) {
                    items(categories) { cat ->
                        val isSelected = selectedCategory == cat
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) Color(0xFF4338CA) else Color.White.copy(alpha = 0.1f),
                            border = if (isSelected) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { selectedCategory = cat }
                        ) {
                            Text(
                                text = cat,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }

            // ─── HERO SECTION (FAVORITES) ───
            if ((selectedCategory == "All" || selectedCategory == "Exclusive") && favTracks.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                        SectionTitle("Listen Now")
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Premium Glassmorphism Hero Card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .bounceClick { onDetailsClick("Favorites", favTracks) }
                                .clip(RoundedCornerShape(24.dp))
                        ) {
                            // Background Art
                            val heroArt = favTracks.lastOrNull { it.albumArtUri != null }?.albumArtUri
                            AsyncImage(
                                model = heroArt,
                                contentDescription = "Favorites",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            // Glass Overlay
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Black.copy(alpha = 0.2f),
                                                Color.Black.copy(alpha = 0.8f)
                                            )
                                        )
                                    )
                            )
                            
                            // Content
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(20.dp)
                            ) {
                                Text(
                                    text = "Your Favorites",
                                    color = Color.White,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${favTracks.size} loved tracks",
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            
                            // Floating Play Button
                            IconButton(
                                onClick = { onTrackClick(favTracks, 0) },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(16.dp)
                                    .size(56.dp)
                                    .background(Color(0xFF22C55E), CircleShape) // Vibrant CTA Green
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = "Play Favorites",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ─── PLAYLISTS CAROUSEL ───
            if (selectedCategory == "All" || selectedCategory == "Playlists") {
                item {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionTitle("Your Playlists")
                            IconButton(
                                onClick = {
                                    playlistDialogTrackPath = null
                                    showPlaylistDialog = true
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(Color.White.copy(alpha = 0.1f), CircleShape)
                            ) {
                                Icon(Icons.Default.Add, "New Playlist", tint = Color.White)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        if (playlistNames.value.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                                    .bounceClick {
                                        playlistDialogTrackPath = null
                                        showPlaylistDialog = true
                                    }
                                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Tap to create a playlist", color = Color.White.copy(alpha = 0.5f))
                            }
                        } else {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(horizontal = 24.dp)
                            ) {
                                items(playlistNames.value, key = { "pl_$it" }) { name ->
                                    val plTracks = remember(name, allTracks, playlistVersion) {
                                        val paths = playlistStore.getPlaylistTracks(name)
                                        allTracks.filter { paths.contains(it.filePath) }
                                    }
                                    CarouselCard(
                                        title = name,
                                        subtitle = "${plTracks.size} tracks",
                                        icon = Icons.AutoMirrored.Filled.QueueMusic,
                                        artworks = plTracks.mapNotNull { it.albumArtUri }.distinct().take(4),
                                        onClick = { onDetailsClick(name, plTracks) },
                                        onPlayClick = { if (plTracks.isNotEmpty()) onTrackClick(plTracks, 0) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ─── EXCLUSIVE CAROUSEL (HI-RES / ATMOS) ───
            if (selectedCategory == "All" || selectedCategory == "Exclusive") {
                if (hiResTracks.isNotEmpty() || atmosTracks.isNotEmpty()) {
                    item {
                        Column {
                            SectionTitle("Audiophile Picks", modifier = Modifier.padding(horizontal = 24.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(horizontal = 24.dp)
                            ) {
                                if (hiResTracks.isNotEmpty()) {
                                    item {
                                        CarouselCard(
                                            title = "High-Res Audio",
                                            subtitle = "${hiResTracks.size} tracks",
                                            icon = Icons.Default.HighQuality,
                                            artworks = hiResTracks.mapNotNull { it.albumArtUri }.distinct().take(4),
                                            onClick = { onDetailsClick("High-Res Audio", hiResTracks) },
                                            onPlayClick = { onTrackClick(hiResTracks, 0) },
                                            accentColor = Color(0xFFEAB308) // Yellow
                                        )
                                    }
                                }
                                if (atmosTracks.isNotEmpty()) {
                                    item {
                                        CarouselCard(
                                            title = "Dolby Atmos",
                                            subtitle = "${atmosTracks.size} tracks",
                                            icon = Icons.Default.SurroundSound,
                                            artworks = atmosTracks.mapNotNull { it.albumArtUri }.distinct().take(4),
                                            onClick = { onDetailsClick("Dolby Atmos", atmosTracks) },
                                            onPlayClick = { onTrackClick(atmosTracks, 0) },
                                            accentColor = Color(0xFF06B6D4) // Cyan
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ─── ALBUMS CAROUSEL ───
            if (selectedCategory == "All" || selectedCategory == "Albums") {
                if (regularAlbums.isNotEmpty()) {
                    item {
                        Column {
                            SectionTitle("Your Albums", modifier = Modifier.padding(horizontal = 24.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(horizontal = 24.dp)
                            ) {
                                items(regularAlbums.entries.toList().take(20), key = { "album_${it.key}" }) { (albumName, tracks) ->
                                    CarouselCard(
                                        title = albumName,
                                        subtitle = tracks.firstOrNull()?.artist ?: "Unknown Artist",
                                        artworks = tracks.mapNotNull { it.albumArtUri }.distinct().take(1),
                                        onClick = { onDetailsClick(albumName, tracks) },
                                        onPlayClick = { onTrackClick(tracks, 0) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ─── FOLDERS CAROUSEL ───
            if (selectedCategory == "All" || selectedCategory == "Folders") {
                val allFolders = allTracks.map { it.filePath.substringBeforeLast("/") }.distinct().sorted()
                if (allFolders.isNotEmpty()) {
                    item {
                        Column {
                            SectionTitle("Local Folders", modifier = Modifier.padding(horizontal = 24.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(horizontal = 24.dp)
                            ) {
                                items(allFolders, key = { "fol_$it" }) { folderPath ->
                                    val folderTracks = allTracks.filter { it.filePath.startsWith(folderPath) }
                                    CarouselCard(
                                        title = folderPath.substringAfterLast("/"),
                                        subtitle = "${folderTracks.size} tracks",
                                        icon = Icons.Default.Folder,
                                        artworks = folderTracks.mapNotNull { it.albumArtUri }.distinct().take(4),
                                        onClick = { onDetailsClick(folderPath.substringAfterLast("/"), folderTracks) },
                                        onPlayClick = { onTrackClick(folderTracks, 0) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = modifier
    )
}

@Composable
private fun CarouselCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    artworks: List<android.net.Uri>,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    accentColor: Color = Color.Transparent
) {
    Box(
        modifier = Modifier
            .width(160.dp)
            .height(200.dp)
            .bounceClick { onClick() }
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.05f))
    ) {
        // Art
        if (artworks.isNotEmpty()) {
            CompositeCoverArt(
                artworks = artworks,
                modifier = Modifier.fillMaxSize()
            )
        } else if (icon != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(64.dp))
            }
        }

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.9f)
                        ),
                        startY = 100f
                    )
                )
        )

        // Accent border if specified
        if (accentColor != Color.Transparent) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.2f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // Text
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
                .padding(end = 36.dp)
        ) {
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Mini Play Button
        IconButton(
            onClick = { onPlayClick() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .size(36.dp)
                .background(Color.White.copy(alpha = 0.2f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "Quick Play",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun TrackRow(track: Track, index: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$index",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.width(32.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
