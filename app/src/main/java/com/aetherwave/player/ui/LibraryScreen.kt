package com.aetherwave.player.ui

import androidx.compose.foundation.BorderStroke
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.aetherwave.player.data.PlaylistStore
import com.aetherwave.player.data.Track
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration

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
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Scanning your music library...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    val columns = GridCells.Adaptive(minSize = 300.dp)

    LazyVerticalGrid(
        columns = columns,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // ─── HEADER ───
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("AetherWave", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(4.dp))
                Text("${allTracks.size} tracks · ${albumGroups.size} albums", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Category Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }
        }

        // ─── FAVORITES ───
        if ((selectedCategory == "All" || selectedCategory == "Exclusive") && favTracks.isNotEmpty()) {
            item(key = "fav_section", span = { GridItemSpan(maxLineSpan) }) {
                SectionListItem(
                    title = "Favorites",
                    subtitle = "${favTracks.size} loved tracks",
                    icon = Icons.Default.Favorite,
                    artworkUris = favTracks.mapNotNull { it.albumArtUri }.distinct().take(4),
                    onItemClick = { onDetailsClick("Favorites", favTracks) },
                    onPlayClick = { onTrackClick(favTracks, 0) }
                )
            }
        }

        // ─── PLAYLISTS ───
        if (selectedCategory == "All" || selectedCategory == "Playlists") {
            item(key = "playlist_header", span = { GridItemSpan(maxLineSpan) }) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Playlists", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                        Text("${playlistNames.value.size} custom collections", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = {
                        playlistDialogTrackPath = null
                        showPlaylistDialog = true
                    }) {
                        Icon(Icons.Default.Add, "New Playlist", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            
            if (playlistNames.value.isEmpty()) {
                item(key = "playlist_empty", span = { GridItemSpan(maxLineSpan) }) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).clickable {
                            playlistDialogTrackPath = null
                            showPlaylistDialog = true
                        }
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.QueueMusic, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Create your first playlist", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(playlistNames.value, key = { "pl_$it" }, span = { GridItemSpan(maxLineSpan) }) { name ->
                    val plTracks = remember(name, allTracks, playlistVersion) {
                        val paths = playlistStore.getPlaylistTracks(name)
                        allTracks.filter { paths.contains(it.filePath) }
                    }
                    SectionListItem(
                        title = name,
                        subtitle = "${plTracks.size} tracks",
                        icon = Icons.Default.QueueMusic,
                        artworkUris = plTracks.mapNotNull { it.albumArtUri }.distinct().take(4),
                        onItemClick = { onDetailsClick(name, plTracks) },
                        onPlayClick = { onTrackClick(plTracks, 0) }
                    )
                }
            }
        }

        // ─── EXCLUSIVE (HI-RES / ATMOS) ───
        if (selectedCategory == "All" || selectedCategory == "Exclusive") {
            if (hiResTracks.isNotEmpty()) {
                item(key = "hires_section", span = { GridItemSpan(maxLineSpan) }) {
                    SectionListItem(
                        title = "High-Res Audio",
                        subtitle = "${hiResTracks.size} audiophile tracks",
                        icon = Icons.Default.HighQuality,
                        artworkUris = hiResTracks.mapNotNull { it.albumArtUri }.distinct().take(4),
                        onItemClick = { onDetailsClick("High-Res Audio", hiResTracks) },
                        onPlayClick = { onTrackClick(hiResTracks, 0) }
                    )
                }
            }

            if (atmosTracks.isNotEmpty()) {
                item(key = "atmos_section", span = { GridItemSpan(maxLineSpan) }) {
                    SectionListItem(
                        title = "Dolby Atmos",
                        subtitle = "${atmosTracks.size} spatial audio tracks",
                        icon = Icons.Default.SurroundSound,
                        artworkUris = atmosTracks.mapNotNull { it.albumArtUri }.distinct().take(4),
                        onItemClick = { onDetailsClick("Dolby Atmos", atmosTracks) },
                        onPlayClick = { onTrackClick(atmosTracks, 0) }
                    )
                }
            }
        }

        // ─── ALBUMS ───
        if (selectedCategory == "All" || selectedCategory == "Albums") {
            if (regularAlbums.isNotEmpty()) {
                item(key = "albums_header", span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Albums", "${regularAlbums.size} albums") }
                items(regularAlbums.entries.toList(), key = { "album_${it.key}" }) { (albumName, tracks) ->
                    AlbumCard(
                        title = albumName,
                        subtitle = tracks.firstOrNull()?.artist ?: "Various Artists",
                        artworkUris = tracks.mapNotNull { it.albumArtUri }.distinct().take(4),
                        onCardClick = { onDetailsClick(albumName, tracks) },
                        onPlayClick = { onTrackClick(tracks, 0) }
                    )
                }
            }
        }

        // ─── FOLDERS ───
        if (selectedCategory == "All" || selectedCategory == "Folders") {
            val allFolders = allTracks.map { it.filePath.substringBeforeLast("/") }.distinct().sorted()
            if (allFolders.isNotEmpty()) {
                item(key = "folders_header", span = { GridItemSpan(maxLineSpan) }) { SectionHeader("Folders", "${allFolders.size} locations") }
                items(allFolders, key = { "fol_$it" }) { folderPath ->
                    val folderTracks = allTracks.filter { it.filePath.startsWith(folderPath) }
                    FolderCard(
                        title = folderPath.substringAfterLast("/"),
                        subtitle = "${folderTracks.size} tracks",
                        artworkUris = folderTracks.mapNotNull { it.albumArtUri }.distinct().take(4),
                        onCardClick = { onDetailsClick(folderPath.substringAfterLast("/"), folderTracks) },
                        onPlayClick = { onTrackClick(folderTracks, 0) }
                    )
                }
            }
        }
    }
}

@Composable
fun SectionListItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    artworkUris: List<android.net.Uri> = emptyList(),
    onItemClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemClick() } // Opens the detailed song list
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Icon / Artwork
        CompositeCoverArt(
            artworks = artworkUris,
            modifier = Modifier.size(56.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        // 2. Text Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(
            onClick = { onPlayClick() }, // Consumes the click, plays instantly
            modifier = Modifier
                .size(44.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "Quick Play",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ─── Section Header ───
@Composable
private fun SectionHeader(title: String, subtitle: String, badgeText: String? = null, badgeColor: Color = Color.Transparent) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (badgeText != null) {
            Surface(color = badgeColor.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))) {
                Text(badgeText, color = badgeColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
    }
}

// ─── Showcase Card ───
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ShowcaseCard(track: Track, accentColor: Color, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)),
        modifier = Modifier.width(160.dp).clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Surface(color = accentColor.copy(alpha = 0.1f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.MusicNote, null, tint = accentColor.copy(alpha = 0.6f), modifier = Modifier.size(36.dp))
                    AsyncImage(
                        model = track.albumArtUri,
                        contentDescription = "Album Art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(track.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ─── Playlist Card ───
@Composable
private fun PlaylistCard(name: String, tracks: List<Track>, onTrackClick: (List<Track>, Int) -> Unit) {
    GridGroupCard(
        title = name,
        subtitle = "${tracks.size} tracks",
        tracks = tracks,
        isPlaylist = true,
        accentColor = MaterialTheme.colorScheme.primary,
        onClick = { onTrackClick(tracks, 0) }
    )
}

@Composable
fun AlbumCard(
    title: String,
    subtitle: String,
    artworkUris: List<android.net.Uri>,
    onCardClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable { onCardClick() }, // Navigates to song list
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
    ) {
        Box(modifier = Modifier.aspectRatio(1f)) {
            // 1. Album Art
            CompositeCoverArt(
                artworks = artworkUris,
                modifier = Modifier.fillMaxSize()
            )

            // 2. Dark Gradient for text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                            startY = 100f
                        )
                    )
            )

            // 3. Text Info (Bottom Left)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .padding(end = 48.dp) // Leave space for play button
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

            // 4. Quick Play Button (Bottom Right)
            IconButton(
                onClick = { onPlayClick() }, // Plays instantly, consumes the click!
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Quick Play",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// Reuse AlbumCard logic for FolderCard to keep visual consistency
@Composable
fun FolderCard(
    title: String,
    subtitle: String,
    artworkUris: List<android.net.Uri>,
    onCardClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clickable { onCardClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
    ) {
        Box(modifier = Modifier.aspectRatio(1f)) {
            // 1. Folder Icon / Art
            CompositeCoverArt(
                artworks = artworkUris,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                            startY = 100f
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
                    .padding(end = 48.dp)
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

            IconButton(
                onClick = { onPlayClick() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Quick Play",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// ─── Shared Grid Group Card ───
@Composable
private fun GridGroupCard(title: String, subtitle: String, tracks: List<Track>, isPlaylist: Boolean, accentColor: Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
        ) {
            CompositeCoverArt(
                artworks = tracks.mapNotNull { it.albumArtUri }.distinct().take(4),
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            title, 
            style = MaterialTheme.typography.titleSmall, 
            fontWeight = FontWeight.Bold, 
            color = MaterialTheme.colorScheme.onSurface, 
            maxLines = 1, 
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Text(
            subtitle, 
            style = MaterialTheme.typography.labelSmall, 
            color = MaterialTheme.colorScheme.onSurfaceVariant, 
            maxLines = 1, 
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

// Removed PlaylistCollage as it's replaced by CompositeCoverArt


// ─── Track Row (basic) ───
@Composable
fun TrackRow(track: Track, index: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onClick() }.padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$index", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(24.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(track.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// Removed old FolderCard implementation

// ─── Micro Badge ───
@Composable
private fun MicroBadge(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp), border = BorderStroke(0.5.dp, color.copy(alpha = 0.5f))) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
    }
}
