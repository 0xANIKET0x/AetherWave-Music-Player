package com.aetherwave.player

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.media.session.MediaController
import android.app.Activity
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.aetherwave.player.data.MediaRepository
import com.aetherwave.player.data.PlaylistStore
import com.aetherwave.player.data.Track
import com.aetherwave.player.ui.LibraryScreen
import com.aetherwave.player.ui.MiniPlayer
import com.aetherwave.player.ui.PlayerScreen
import com.aetherwave.player.ui.SettingsScreen
import com.aetherwave.player.ui.LyricsScreen
import com.aetherwave.player.ui.SearchScreen
import com.aetherwave.player.data.BackupRepository
import com.aetherwave.player.ui.SplashScreen
import com.aetherwave.player.ui.theme.AetherWaveTheme
import com.aetherwave.player.PlaybackService
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import android.media.session.PlaybackState

class MainActivity : ComponentActivity() {

    private var playbackService: PlaybackService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as PlaybackService.LocalBinder
            playbackService = binder.getService()
            isBound = true
            // Support MediaController retrieval via LocalContext/Activity
            playbackService?.mediaSession?.let { session ->
                this@MainActivity.setMediaController(session.controller)
                Log.d("MainActivity", "MediaController registered with Activity.")
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            playbackService = null
            Log.d("MainActivity", "PlaybackService disconnected.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val intent = Intent(this, PlaybackService::class.java)
        try {
            startForegroundService(intent)
        } catch (e: Exception) {
            startService(intent)
        }
        bindService(intent, connection, Context.BIND_AUTO_CREATE)

        val playlistStore = PlaylistStore(this)

        setContent {
            var currentTheme by remember { mutableStateOf(playlistStore.getTheme()) }
            
            // Observe theme changes from store
            LaunchedEffect(Unit) {
                playlistStore.changes.collect {
                    currentTheme = playlistStore.getTheme()
                }
            }

            // Re-fetch service state from the activity instance to ensure UI stays reactive
            val currentService = remember { mutableStateOf<PlaybackService?>(null) }
            
            // Poll for binding completion without blocking the UI
            LaunchedEffect(Unit) {
                while(playbackService == null) {
                    kotlinx.coroutines.delay(50)
                }
                currentService.value = playbackService
            }

            val coroutineScope = rememberCoroutineScope()
            val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
                uri?.let {
                    contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    playlistStore.addManualFolder(it.toString())
                }
            }



            AetherWaveTheme(theme = currentTheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val service = currentService.value
                    if (service != null) {
                        AetherWaveApp(
                            service, 
                            this@MainActivity, 
                            playlistStore, 
                            currentTheme,
                            onAddFolder = { folderPicker.launch(null) }
                        ) {
                            val themes = listOf("DARK", "MIDNIGHT", "EMERALD", "LAVENDER", "LIGHT")
                            val next = themes[(themes.indexOf(currentTheme) + 1) % themes.size]
                            playlistStore.setTheme(next)
                        }
                    } else {
                        SplashScreen()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}

enum class Screen { LIBRARY, PLAYER, SETTINGS, LYRICS, ALBUM_DETAILS, SEARCH }

@Composable
fun AetherWaveApp(
    playbackService: PlaybackService,
    context: Context,
    playlistStore: PlaylistStore,
    currentTheme: String,
    onAddFolder: () -> Unit,
    onThemeToggle: () -> Unit
) {
    // Backup UI logic
    val view = androidx.compose.ui.platform.LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val backupRepo = remember { BackupRepository(context) }
    
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        uri?.let {
            val success = backupRepo.exportBackup(it)
            Toast.makeText(context, if (success) "Backup exported successfully" else "Backup failed", Toast.LENGTH_SHORT).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission() // This is for permission, but I need OpenDocument
    ) { } // Placeholder, will fix below
    
    val actualImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val success = backupRepo.importBackup(it)
            if (success) {
                Toast.makeText(context, "Settings restored. Restarting app...", Toast.LENGTH_LONG).show()
                // Force a cold restart to ensure all services and native engine reload settings
                coroutineScope.launch {
                    delay(1500)
                    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    context.startActivity(intent)
                    android.os.Process.killProcess(android.os.Process.myPid())
                    System.exit(0)
                }
            } else {
                Toast.makeText(context, "Import failed", Toast.LENGTH_SHORT).show()
            }
        }
    }
    var currentScreen by rememberSaveable { mutableStateOf(Screen.LIBRARY) }
    var previousScreen by rememberSaveable { mutableStateOf(Screen.LIBRARY) }
    var selectedDetailTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var selectedDetailTitle by remember { mutableStateOf("") }

    var allRawTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var allTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var audiophileTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var albumGroups by remember { mutableStateOf<Map<String, List<Track>>>(emptyMap()) }
    var allFolders by remember { mutableStateOf<List<String>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(false) }



    LaunchedEffect(currentScreen) {
        val window = (context as? android.app.Activity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        if (currentScreen == Screen.LYRICS) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Handle intent-based navigation (e.g. from notification or file manager)
    val currentIntent = (context as? android.app.Activity)?.intent
    LaunchedEffect(currentIntent) {
        if (currentIntent?.getBooleanExtra("OPEN_PLAYER", false) == true) {
            currentScreen = Screen.PLAYER
            // Clear the extra so we don't keep jumping back
            currentIntent.removeExtra("OPEN_PLAYER")
        }
        
        if (currentIntent?.action == Intent.ACTION_VIEW) {
            val uri = currentIntent.data
            if (uri != null) {
                var realPath: String? = null
                if (uri.scheme == "file") {
                    realPath = uri.path
                } else if (uri.scheme == "content") {
                    try {
                        val cursor = context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.MediaColumns.DATA), null, null, null)
                        if (cursor != null && cursor.moveToFirst()) {
                            val dataIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)
                            if (dataIndex != -1) {
                                realPath = cursor.getString(dataIndex)
                            }
                            cursor.close()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                if (realPath != null) {
                    val track = Track(
                        id = realPath.hashCode().toLong(),
                        title = realPath.substringAfterLast("/"),
                        artist = "Unknown Artist",
                        album = "Unknown Album",
                        albumArtUri = null,
                        filePath = realPath,
                        duration = 0,
                        bitrate = 0,
                        sampleRate = 0,
                        channelCount = 2,
                        isHighRes = false,
                        isAtmos = false
                    )
                    playbackService.setQueue(listOf(track), 0)
                    previousScreen = currentScreen
                    currentScreen = Screen.PLAYER
                    
                    // Consume the intent so it doesn't re-trigger
                    currentIntent.action = Intent.ACTION_MAIN
                }
            }
        }
    }

    val currentTrack by playbackService.currentTrack.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) {
            coroutineScope.launch {
                val repo = MediaRepository(context)
                // 1. Load from cache first for instant UI
                val cached = repo.loadCachedTracks()
                if (cached.isNotEmpty()) {
                    allRawTracks = cached
                    allFolders = cached.map { it.filePath.substringBeforeLast("/") }.distinct().sorted()
                }
                
                // 2. Scan for updates ONLY if cache is empty (1st launch)
                if (cached.isEmpty()) {
                    isScanning = true
                    val tracks = repo.scanAllTracks(cached)
                    allRawTracks = tracks
                    allFolders = tracks.map { it.filePath.substringBeforeLast("/") }.distinct().sorted()
                    repo.saveTracksToCache(tracks)
                    isScanning = false
                }
            }
        }
    }

    LaunchedEffect(allRawTracks, currentScreen) {
        if (allRawTracks.isNotEmpty() && currentScreen == Screen.LIBRARY) {
            val excluded = playlistStore.getExcludedFolders()
            val filtered = allRawTracks.filter { !excluded.contains(it.filePath.substringBeforeLast("/")) }
            allTracks = filtered
            val repo = MediaRepository(context)
            audiophileTracks = repo.getAudiophileTracks(filtered)
            albumGroups = repo.getAlbumGroups(filtered)
        }
    }

    LaunchedEffect(Unit) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) {
            hasPermission = true
            val repo = MediaRepository(context)
            
            // 1. Load from cache first for instant UI
            val cached = repo.loadCachedTracks()
            if (cached.isNotEmpty()) {
                allRawTracks = cached
                allFolders = cached.map { it.filePath.substringBeforeLast("/") }.distinct().sorted()
            }

            // 2. Scan for updates ONLY if cache is empty (1st launch)
            if (cached.isEmpty()) {
                isScanning = true
                val tracks = repo.scanAllTracks(cached, playlistStore.getManualFolders())
                allRawTracks = tracks
                allFolders = tracks.map { it.filePath.substringBeforeLast("/") }.distinct().sorted()
                repo.saveTracksToCache(tracks)
                isScanning = false
            }
        } else {
            permissionLauncher.launch(permission)
        }
    }

    val saveableStateHolder = rememberSaveableStateHolder()

    Box(modifier = Modifier.fillMaxSize()) {
        saveableStateHolder.SaveableStateProvider(currentScreen) {
            when (currentScreen) {
                Screen.PLAYER -> {
                PlayerScreen(
                    playbackService = playbackService,
                    playlistStore = playlistStore,
                    onBack = { currentScreen = previousScreen },
                    onLyricsClick = { currentScreen = Screen.LYRICS }
                )
            }
            Screen.LYRICS -> {
                LyricsScreen(
                    playbackService = playbackService,
                    onBack = { currentScreen = Screen.PLAYER }
                )
            }
            Screen.SETTINGS -> {
                BackHandler { currentScreen = Screen.LIBRARY }
                SettingsScreen(
                    playlistStore = playlistStore,
                    allFolders = allFolders,
                    isAudiophileMode = playbackService.audiophileMode.collectAsState().value,
                    onAddFolder = onAddFolder,
                    onExport = { exportLauncher.launch("AetherWave_Backup_${System.currentTimeMillis()}.zip") },
                    onImport = { actualImportLauncher.launch(arrayOf("application/zip")) },
                    onForceMaxBitrateChange = { playbackService.setForceMaxBitrate(it) },
                    onAudiophileModeChange = { playbackService.setAudiophileMode(it) },
                    onBack = { currentScreen = Screen.LIBRARY }
                )
            }

            Screen.ALBUM_DETAILS -> {
                val albumListState = rememberLazyListState()
                BackHandler { currentScreen = Screen.LIBRARY }
                Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { currentScreen = Screen.LIBRARY }) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(selectedDetailTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("${selectedDetailTracks.size} tracks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    
                    // Track List
                    LazyColumn(modifier = Modifier.fillMaxSize(), state = albumListState, contentPadding = PaddingValues(bottom = 100.dp)) {
                        itemsIndexed(selectedDetailTracks) { index, track ->
                            com.aetherwave.player.ui.TrackRow(track, index + 1) {
                                playbackService.setQueue(selectedDetailTracks, index)
                                previousScreen = currentScreen
                                currentScreen = Screen.PLAYER
                            }
                        }
                    }
                }
            }
            Screen.SEARCH -> {
                BackHandler { currentScreen = Screen.LIBRARY }
                SearchScreen(
                    allTracks = allTracks,
                    onTrackClick = { tracks, index ->
                        playbackService.setQueue(tracks, index)
                        previousScreen = currentScreen
                        currentScreen = Screen.PLAYER
                    },
                    onBack = { currentScreen = Screen.LIBRARY }
                )
            }
            Screen.LIBRARY -> {

                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().statusBarsPadding()
                    ) {
                        // Top bar with theme toggle + settings
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { onThemeToggle() }) {
                                Text(
                                    currentTheme.lowercase().replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = {
                                coroutineScope.launch {
                                    isScanning = true
                                    val repo = MediaRepository(context)
                                    val cached = repo.loadCachedTracks()
                                    val tracks = repo.scanAllTracks(cached, playlistStore.getManualFolders())
                                    allRawTracks = tracks
                                    allFolders = tracks.map { it.filePath.substringBeforeLast("/") }.distinct().sorted()
                                    repo.saveTracksToCache(tracks)
                                    isScanning = false
                                }
                            }) {
                                Icon(Icons.Default.Refresh, "Refresh Library", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { currentScreen = Screen.SEARCH }) {
                                Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { currentScreen = Screen.SETTINGS }) {
                                Icon(Icons.Default.Settings, "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = {
                                (context as? android.app.Activity)?.finish()
                                context.stopService(android.content.Intent(context, com.aetherwave.player.PlaybackService::class.java))
                                android.os.Process.killProcess(android.os.Process.myPid())
                                java.lang.System.exit(0)
                            }) {
                                Icon(Icons.Default.ExitToApp, "Exit App", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                            }
                        }

                        LibraryScreen(
                            allTracks = allTracks,
                            audiophileTracks = audiophileTracks,
                            albumGroups = albumGroups,
                            playlistStore = playlistStore,
                            onTrackClick = { tracks, index ->
                                playbackService.setQueue(tracks, index)
                                previousScreen = currentScreen
                                currentScreen = Screen.PLAYER
                            },
                            onDetailsClick = { title, tracks ->
                                selectedDetailTitle = title
                                selectedDetailTracks = tracks
                                currentScreen = Screen.ALBUM_DETAILS
                            },
                            isScanning = isScanning
                        )
                    }

                    // MiniPlayer
                    if (currentTrack != null) {
                        Box(modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) {
                            MiniPlayer(playbackService = playbackService, onExpand = { currentScreen = Screen.PLAYER })
                        }
                    }
                }
            }
        }
    }
    }
}