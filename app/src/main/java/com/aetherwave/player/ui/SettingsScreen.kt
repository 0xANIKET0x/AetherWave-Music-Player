package com.aetherwave.player.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aetherwave.player.data.PlaylistStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    playlistStore: PlaylistStore,
    allFolders: List<String>,
    isAudiophileMode: Boolean,
    onAddFolder: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onForceMaxBitrateChange: (Boolean) -> Unit,
    onAudiophileModeChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    
    val autoPlayNext = remember { mutableStateOf(playlistStore.getBoolean("auto_play_next", true)) }
    val crossfade = remember { mutableStateOf(playlistStore.getBoolean("crossfade", false)) }
    val forceMaxBitrate = remember { mutableStateOf(playlistStore.getBoolean("force_max_bitrate", false)) }
    val excludedFolders = remember { mutableStateOf(playlistStore.getExcludedFolders()) }
    val manualFolders = remember { mutableStateOf(playlistStore.getManualFolders()) }
    val currentAccent = remember { mutableStateOf(playlistStore.getAccentColor()) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Hero Developer Card
            item {
                HeroSupportCard(context)
            }

            // Theme Customization
            item {
                SettingsSection(title = "Appearance") {
                    ThemeSelectionRow(currentAccent, playlistStore)
                }
            }

            // Audio Tweaks
            item {
                SettingsSection(title = "Audio Engine") {
                    SettingsToggleRow(
                        icon = Icons.Default.SkipNext,
                        title = "Auto-Play Next Track",
                        subtitle = "Seamless transition to the next song",
                        checked = autoPlayNext.value,
                        onCheckedChange = {
                            autoPlayNext.value = it
                            playlistStore.setBoolean("auto_play_next", it)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsToggleRow(
                        icon = Icons.Default.GraphicEq,
                        title = "Crossfade",
                        subtitle = "Blend tracks smoothly together",
                        checked = crossfade.value,
                        onCheckedChange = {
                            crossfade.value = it
                            playlistStore.setBoolean("crossfade", it)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsToggleRow(
                        icon = Icons.Default.HighQuality,
                        title = "Force Max Bitrate",
                        subtitle = "Always decode at maximum quality",
                        checked = forceMaxBitrate.value,
                        onCheckedChange = {
                            forceMaxBitrate.value = it
                            playlistStore.setBoolean("force_max_bitrate", it)
                            onForceMaxBitrateChange(it)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    
                    var audiophile by remember { mutableStateOf(isAudiophileMode) }
                    SettingsToggleRow(
                        icon = Icons.Default.Speaker,
                        title = "Audiophile Mode",
                        subtitle = "Bit-perfect DAC bypass (High battery usage)",
                        checked = audiophile,
                        onCheckedChange = {
                            audiophile = it
                            onAudiophileModeChange(it)
                        }
                    )
                }
            }

            // Folder Management
            item {
                SettingsSection(title = "Library Folders") {
                    SettingsActionRow(
                        icon = Icons.Default.Add,
                        title = "Add Music Folder",
                        subtitle = "Manually include specific directories",
                        onClick = onAddFolder
                    )
                    
                    val folders = manualFolders.value.toList()
                    if (folders.isNotEmpty()) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        folders.forEachIndexed { index, uriStr ->
                            val displayName = try {
                                android.net.Uri.parse(uriStr).lastPathSegment ?: uriStr
                            } catch (_: Exception) { uriStr }
                            
                            SettingsItemRow(
                                icon = Icons.Default.Folder,
                                title = displayName,
                                subtitle = "Manual Folder",
                                actionIcon = Icons.Default.Delete,
                                onActionClick = {
                                    playlistStore.removeManualFolder(uriStr)
                                    manualFolders.value = playlistStore.getManualFolders()
                                }
                            )
                            if (index < folders.size - 1) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }

            if (allFolders.isNotEmpty()) {
                item {
                    SettingsSection(title = "Excluded Folders", description = "Disable folders you don't want scanned (e.g., WhatsApp Audio)") {
                        allFolders.forEachIndexed { index, folder ->
                            val isExcluded = excludedFolders.value.contains(folder)
                            val displayName = folder.substringAfterLast("/")
                            
                            SettingsToggleRow(
                                icon = Icons.Default.FolderOff,
                                title = displayName,
                                subtitle = folder,
                                checked = !isExcluded,
                                onCheckedChange = {
                                    playlistStore.toggleExcludedFolder(folder)
                                    excludedFolders.value = playlistStore.getExcludedFolders()
                                }
                            )
                            if (index < allFolders.size - 1) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }

            // Backup & Restore
            item {
                SettingsSection(title = "Backup & Restore", description = "Export your playlists, settings, cached lyrics, and covers.") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onImport,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Import")
                        }
                        Button(
                            onClick = onExport,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Export")
                        }
                    }
                }
            }

            // Bottom Spacer
            item { Spacer(modifier = Modifier.height(48.dp)) }
        }
    }
}

@Composable
fun HeroSupportCard(context: Context) {
    val gradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.tertiary
        )
    )
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 8.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = Icons.Default.LibraryMusic,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "AetherWave Player",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    "Premium Audiophile Engine",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Developed by Aniket Kumar with ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                    )
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Love",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                
                // Donation Buttons
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(16.dp),
                        onClick = { initiateUPIDonation(context) }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("UPI", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1f),
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(16.dp),
                        onClick = { openPatreon(context) }
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Public, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Patreon", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)
            )
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(content = content)
        }
    }
}

@Composable
fun ThemeSelectionRow(currentAccent: MutableState<Long?>, playlistStore: PlaylistStore) {
    val themeOptions = listOf(
        "Dynamic" to null,
        "Cyan" to 0xFF00FFFF,
        "Crimson" to 0xFFDC143C,
        "Amethyst" to 0xFF9966CC,
        "Emerald" to 0xFF2ECC71,
        "Amber" to 0xFFFFBF00
    )

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Accent Color", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            themeOptions.forEach { (name, colorVal) ->
                val isSelected = currentAccent.value == colorVal
                val targetSize by animateDpAsState(if (isSelected) 48.dp else 40.dp, animationSpec = spring())
                
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null
                ) {
                    currentAccent.value = colorVal
                    playlistStore.setAccentColor(colorVal)
                }) {
                    Box(
                        modifier = Modifier
                            .size(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 36.dp else 40.dp)
                                .clip(CircleShape)
                                .background(if (colorVal == null) MaterialTheme.colorScheme.surfaceVariant else Color(colorVal))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), CircleShape)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(name, style = MaterialTheme.typography.labelSmall, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SoftIcon(icon)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingsActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SoftIcon(icon)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SettingsItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionIcon: ImageVector,
    onActionClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SoftIcon(icon)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        IconButton(onClick = onActionClick) {
            Icon(actionIcon, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun SoftIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}

private fun initiateUPIDonation(context: Context, amount: String = "100.00") {
    val upiId = "aniketkumar00123@okicici" 
    val name = "Aniket Kumar"
    val note = "Donation for AetherWave Player"

    val uri = Uri.Builder()
        .scheme("upi")
        .authority("pay")
        .appendQueryParameter("pa", upiId)      
        .appendQueryParameter("pn", name)       
        .appendQueryParameter("tn", note)       
        .appendQueryParameter("am", amount)     
        .appendQueryParameter("cu", "INR")      
        .build()

    val intent = Intent(Intent.ACTION_VIEW).apply { data = uri }
    val chooser = Intent.createChooser(intent, "Pay with...")
    
    try {
        context.startActivity(chooser)
    } catch (e: android.content.ActivityNotFoundException) {
        Toast.makeText(context, "No UPI app found on this device.", Toast.LENGTH_SHORT).show()
    }
}

private fun openPatreon(context: Context) {
    val url = "https://www.patreon.com/posts/aetherwave-158326210?utm_medium=clipboard_copy&utm_source=copyLink&utm_campaign=postshare_creator&utm_content=join_link"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open web browser.", Toast.LENGTH_SHORT).show()
    }
}
