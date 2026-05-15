package com.aetherwave.player

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.media.MediaMetadataRetriever
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.palette.graphics.Palette
import android.os.Binder
import android.os.IBinder
import com.aetherwave.player.data.CoverArtRepository
import com.aetherwave.player.data.LyricLine
import com.aetherwave.player.data.LyricsRepository
import com.aetherwave.player.data.MediaRepository
import com.aetherwave.player.data.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

class PlaybackService : Service() {

    var activeEngine = NativeAudioEngine()
    var fadingEngine: NativeAudioEngine? = null
    var isCrossfading = false

    val engineStatus = MutableStateFlow("Initialized")
    val duration = MutableStateFlow(0.0)
    val currentPosition = MutableStateFlow(0.0)
    val isPlaying = MutableStateFlow(false)
    val currentTrackPath = MutableStateFlow<String?>(null)
    val eqGains = MutableStateFlow(FloatArray(5) { 0f })
    
    val inputBitrate = MutableStateFlow(0)
    val inputBitDepth = MutableStateFlow(0)
    val outputSampleRate = MutableStateFlow(0)
    val isDolbyAtmosFile = MutableStateFlow(false)
    val outputBitrate = MutableStateFlow("")
    val currentEffectMode = MutableStateFlow(0)
    val eqEnabled = MutableStateFlow(true)
    val outputDeviceInfo = MutableStateFlow("")
    val amplitude = MutableStateFlow(0.0f)
    val audioEnergy = MutableStateFlow(0f)
    val audiophileMode = MutableStateFlow(false)
    private var smoothedAmplitude = 0f

    val queueManager = QueueManager()
    val queue: MutableStateFlow<List<Track>> = queueManager.currentQueue
    val currentIndex: MutableStateFlow<Int> = queueManager.currentIndex
    val currentTrack: MutableStateFlow<Track?> = queueManager.currentTrack
    val shuffleEnabled: MutableStateFlow<Boolean> = queueManager.shuffleEnabled
    val repeatMode: MutableStateFlow<Int> = queueManager.repeatMode
    
    val coverArtUrl = MutableStateFlow<String?>(null)
    val lyrics = MutableStateFlow<LyricsRepository.FetchedLyrics?>(null)
    
    val dominantColor = MutableStateFlow<Int?>(null)
    val vibrantColor = MutableStateFlow<Int?>(null)
    val lyricsMode = MutableStateFlow(0) // 0: Apple Mesh, 1: 3D Kinetic Snow, 2: The Black Hole Book

    private val coverArtRepo = CoverArtRepository(this)
    private val lyricsRepo = LyricsRepository(this)

    private val binder = LocalBinder()
    private lateinit var audioManager: AudioManager
    lateinit var mediaSession: MediaSession
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    
    private var audioFocusRequest: AudioFocusRequest? = null
    private var isAdvancing = false
    private var isRerouting = false
    private var lastSeekTime = 0L 

    private val noisyReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                pauseTrack()
            }
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): PlaybackService = this@PlaybackService
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        createNotificationChannel()

        mediaSession = MediaSession(this, "AetherWaveSession")
        mediaSession.setCallback(object : MediaSession.Callback() {
            override fun onPlay() { playTrack() }
            override fun onPause() { pauseTrack() }
            override fun onStop() { stopTrack() }
            override fun onSeekTo(pos: Long) {
                val seconds = pos / 1000f
                lastSeekTime = System.currentTimeMillis()
                activeEngine.seekTo(seconds)
                currentPosition.value = seconds.toDouble()
                updatePlaybackState(if (isPlaying.value) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED)
                updateNotification()
            }
            override fun onSkipToNext() { nextTrack() }
            override fun onSkipToPrevious() { previousTrack() }
        })
        
        mediaSession.isActive = true
        // Initial state to let system know we support seeking
        updatePlaybackState(PlaybackState.STATE_STOPPED)

        // Prevent ForegroundServiceDidNotStartInTimeException by starting immediately
        startForeground(1, createNotification())

        registerReceiver(noisyReceiver, android.content.IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))

        // Register device change callback for seamless audio routing
        audioManager.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                handleDeviceRouteChange()
            }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                handleDeviceRouteChange()
            }
        }, null)
        
        loadSettings()

        serviceScope.launch {
            var lastInfoPoll = 0L
            while (isActive) {
                val now = System.currentTimeMillis()
                
                // 1. High-frequency (50ms) Position & Critical Status
                if (now - lastSeekTime > 500) {
                    currentPosition.value = activeEngine.getCurrentPosition()
                }
                val status = activeEngine.getEngineStatus()
                engineStatus.value = status
                val rawAmp = activeEngine.getAmplitude()
                amplitude.value = rawAmp
                
                // Smoothed energy for UI reactivity (Low-pass filter)
                val alpha = 0.2f 
                smoothedAmplitude = smoothedAmplitude + alpha * (rawAmp - smoothedAmplitude)
                audioEnergy.value = smoothedAmplitude

                // 2. Low-frequency (1000ms) Info Polling
                if (now - lastInfoPoll >= 1000) {
                    val engineDuration = activeEngine.getDuration()
                    duration.value = if (engineDuration > 0.0) engineDuration else (queueManager.currentTrack.value?.duration?.toDouble()?.div(1000.0) ?: 0.0)
                    
                    inputBitrate.value = activeEngine.getInputBitrate()
                    inputBitDepth.value = activeEngine.getInputBitDepth()
                    outputSampleRate.value = activeEngine.getOutputSampleRate()
                    outputBitrate.value = activeEngine.getOutputBitrateString()
                    isDolbyAtmosFile.value = activeEngine.isDolbyAtmos()
                    outputDeviceInfo.value = getActiveOutputDeviceDescription()
                    
                    if (isPlaying.value) {
                        updatePlaybackState(PlaybackState.STATE_PLAYING)
                    }
                    savePlayerState()
                    lastInfoPoll = now
                }

                // 3. Playback Logic (Crossfade/Advance)
                val timeRemaining = duration.value - currentPosition.value
                val crossfadeDuration = 3.0
                
                if (isPlaying.value && !isAdvancing && duration.value > 0 && timeRemaining <= crossfadeDuration && !isCrossfading && queueManager.getNextTrackIndex(true) != null) {
                    val isCrossfadeEnabled = getSharedPreferences("aetherwave_playlists", Context.MODE_PRIVATE)
                        .getBoolean("crossfade", false)
                    
                    if (isCrossfadeEnabled) {
                        isCrossfading = true
                        serviceScope.launch {
                            try {
                            val fadeOutEngine = activeEngine
                            fadingEngine = fadeOutEngine
                            activeEngine = NativeAudioEngine()
                            
                            activeEngine.setEffectMode(currentEffectMode.value)
                            activeEngine.setEqEnabled(eqEnabled.value)
                            activeEngine.setAudiophileMode(audiophileMode.value)
                            eqGains.value.forEachIndexed { i, gain -> if (i < 5) activeEngine.setEqBandGain(i, gain) }

                            queueManager.moveToNext(true)
                            val nextTrack = queueManager.currentTrack.value ?: run {
                                // Queue emptied mid-crossfade — abort gracefully
                                activeEngine.release()
                                activeEngine = fadeOutEngine
                                fadingEngine = null
                                isCrossfading = false
                                isAdvancing = false
                                return@launch
                            }
                        currentTrackPath.value = nextTrack.filePath
                        
                        coverArtUrl.value = null
                        lyrics.value = null
                        launch { coverArtUrl.value = coverArtRepo.fetchCoverArtUrl(nextTrack.title, nextTrack.artist) }
                        launch { lyrics.value = lyricsRepo.fetchLyrics(nextTrack.title, nextTrack.artist) }
                        
                        activeEngine.loadTrack(nextTrack.filePath)
                        activeEngine.setVolume(0f)
                        activeEngine.start()
                        updateMediaMetadata() // Update metadata on track change
                        updatePlaybackState(PlaybackState.STATE_PLAYING)
                        startForeground(1, createNotification())
                        
                            val steps = 20
                            val stepDelay = (crossfadeDuration * 1000 / steps).toLong()
                            for (i in 1..steps) {
                                val vol = i.toFloat() / steps
                                activeEngine.setVolume(vol)
                                fadeOutEngine.setVolume(1f - vol)
                                delay(stepDelay)
                            }
                            
                            fadeOutEngine.stop()
                            fadeOutEngine.release()
                            fadingEngine = null
                            } finally {
                                isAdvancing = false
                                isCrossfading = false
                            }
                        }
                    }
                }

                if (isPlaying.value && !isAdvancing && !isCrossfading && status.contains("EOF")) {
                    isAdvancing = true
                    try {
                        onTrackCompleted()
                    } finally {
                        isAdvancing = false
                    }
                }
                
                delay(50)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                "ACTION_PLAY" -> playTrack()
                "ACTION_PAUSE" -> pauseTrack()
                "ACTION_STOP" -> stopTrack()
                "ACTION_NEXT" -> nextTrack()
                "ACTION_PREV" -> previousTrack()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        mediaSession.release()
        activeEngine.stop()
        activeEngine.release()
        fadingEngine?.stop()
        fadingEngine?.release()
        abandonAudioFocus()
        try {
            unregisterReceiver(noisyReceiver)
        } catch (e: Exception) {}
    }

    fun loadTrack(path: String) {
        currentTrackPath.value = path
        activeEngine.loadTrack(path)
    }

    private fun handleDeviceRouteChange() {
        if (!isPlaying.value || isRerouting) return
        isRerouting = true
        val path = currentTrackPath.value ?: run { isRerouting = false; return }
        val savedPosition = currentPosition.value.toFloat()
        serviceScope.launch {
            kotlinx.coroutines.delay(200) // Allow AUDIO_BECOMING_NOISY to process and pause if necessary
            if (!isPlaying.value) {
                isRerouting = false
                return@launch
            }
            try {
                activeEngine.stop()
                kotlinx.coroutines.delay(150) // Brief pause for route to settle
                activeEngine.loadTrack(path)
                activeEngine.start()
                if (savedPosition > 0.5f) {
                    activeEngine.seekTo(savedPosition)
                }
                isPlaying.value = true
            } catch (_: Exception) { }
            isRerouting = false
        }
    }

    fun setQueue(tracks: List<Track>, startIndex: Int = 0) {
        queueManager.setQueue(tracks, startIndex)
        playTrack()
    }

    fun playTrackAt(index: Int) {
        queueManager.jumpToIndex(index)
        playTrack()
    }

    fun nextTrack() {
        if (queueManager.moveToNext()) playTrack()
    }

    fun previousTrack() {
        val pos = currentPosition.value
        if (pos > 3.0) {
            seekTo(0f)
            return
        }
        if (queueManager.moveToPrevious()) playTrack()
    }

    fun toggleShuffle() {
        queueManager.toggleShuffle()
    }

    fun cycleRepeatMode() {
        queueManager.toggleRepeat()
    }

    private fun onTrackCompleted() {
        val trackBefore = queueManager.currentTrack.value
        if (queueManager.moveToNext(isAutoAdvance = true)) {
            val trackAfter = queueManager.currentTrack.value
            if (trackBefore != null && trackBefore.filePath == trackAfter?.filePath) {
                activeEngine.stop()
                activeEngine.loadTrack(trackAfter.filePath)
                currentPosition.value = 0.0
            }
            playTrack()
        } else {
            isPlaying.value = false
            activeEngine.stop()
            updateNotification()
        }
    }

    fun playTrack() {
        val track = queueManager.currentTrack.value ?: return
        
        requestAudioFocus()
        
        // Only stop and reload if the track path has actually changed
        // This prevents the song from restarting when unpausing
        if (currentTrackPath.value != track.filePath) {
            activeEngine.stop()
            currentTrackPath.value = track.filePath
            currentPosition.value = 0.0 
            isAdvancing = false
            coverArtUrl.value = null
            lyrics.value = null
            activeEngine.loadTrack(track.filePath)
            serviceScope.launch { coverArtUrl.value = coverArtRepo.fetchCoverArtUrl(track.title, track.artist) }
            serviceScope.launch { lyrics.value = lyricsRepo.fetchLyrics(track.title, track.artist) }
        }
        
        activeEngine.start()
        
        // Immediately set duration fallback BEFORE updating MediaSession to avoid 00:00 notification
        val engineDur = activeEngine.getDuration()
        duration.value = if (engineDur > 0.0) engineDur else (track.duration.toDouble() / 1000.0)
        
        isPlaying.value = true
        updateMediaMetadata()
        updatePlaybackState(PlaybackState.STATE_PLAYING)
        startForeground(1, createNotification())
    }

    fun pauseTrack() {
        activeEngine.pause()
        isPlaying.value = false
        updatePlaybackState(PlaybackState.STATE_PAUSED)
        updateNotification()
        stopForeground(STOP_FOREGROUND_DETACH) 
    }

    fun stopTrack() {
        activeEngine.stop()
        isPlaying.value = false
        updatePlaybackState(PlaybackState.STATE_STOPPED)
        abandonAudioFocus()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun seekTo(seconds: Float) {
        lastSeekTime = System.currentTimeMillis()
        activeEngine.seekTo(seconds)
        currentPosition.value = seconds.toDouble()
        updatePlaybackState(if (isPlaying.value) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED)
    }

    suspend fun upgradeLyrics(force: Boolean = false) {
        val track = currentTrack.value ?: return
        val currentLyrics = lyrics.value
        
        // Don't upgrade if already synced unless forced
        if (currentLyrics?.isSynced == true && !force) return
        
        val plainText = currentLyrics?.lines?.joinToString("\n") { it.text }
        val upgraded = lyricsRepo.upgradeToSyncedLyrics(track.title, track.artist, plainText)
        
        if (upgraded != null) {
            lyrics.value = upgraded
        }
    }
    
    fun setEffectMode(mode: Int) {
        currentEffectMode.value = mode
        activeEngine.setEffectMode(mode)
        getSharedPreferences("aetherwave_audio", Context.MODE_PRIVATE).edit()
            .putInt("effect_mode", mode).apply()
    }

    fun setForceMaxBitrate(force: Boolean) {
        activeEngine.setForceMaxBitrate(force)
    }

    fun setAudiophileMode(enabled: Boolean) {
        audiophileMode.value = enabled
        activeEngine.setAudiophileMode(enabled)
        getSharedPreferences("aetherwave_audio", Context.MODE_PRIVATE).edit()
            .putBoolean("audiophile_mode", enabled).apply()
        
        // Hot-restart the stream so the new Oboe configuration takes effect
        if (isPlaying.value) {
            val path = currentTrackPath.value ?: return
            val savedPosition = currentPosition.value.toFloat()
            serviceScope.launch {
                activeEngine.stop()
                delay(100)
                activeEngine.loadTrack(path)
                activeEngine.start()
                if (savedPosition > 0.5f) {
                    activeEngine.seekTo(savedPosition)
                }
                isPlaying.value = true
            }
        }
    }

    fun setEqBandGain(index: Int, gainDb: Float) {
        val newGains = eqGains.value.clone()
        newGains[index] = gainDb
        eqGains.value = newGains
        activeEngine.setEqBandGain(index, gainDb)
        
        val gainStr = newGains.joinToString(",")
        getSharedPreferences("aetherwave_audio", Context.MODE_PRIVATE).edit()
            .putString("eq_gains", gainStr).apply()
    }

    fun setEqEnabled(enabled: Boolean) {
        eqEnabled.value = enabled
        activeEngine.setEqEnabled(enabled)
        getSharedPreferences("aetherwave_audio", Context.MODE_PRIVATE).edit()
            .putBoolean("eq_enabled", enabled).apply()
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> pauseTrack()
            AudioManager.AUDIOFOCUS_GAIN -> playTrack()
        }
    }

    private fun requestAudioFocus(): Boolean {
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setOnAudioFocusChangeListener(audioFocusChangeListener)
            .build()
        return audioManager.requestAudioFocus(audioFocusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
    }

    private fun updatePlaybackState(state: Int) {
        val positionMs = (currentPosition.value * 1000).toLong()
        val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or 
                     PlaybackState.ACTION_STOP or PlaybackState.ACTION_SEEK_TO or 
                     PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        
        val playbackState = PlaybackState.Builder()
            .setActions(actions)
            .setState(state, positionMs, 1.0f)
            .build()
        mediaSession.setPlaybackState(playbackState)
    }

    private fun updateMediaMetadata() {
        val track = currentTrack.value
        val title = track?.title ?: currentTrackPath.value?.substringAfterLast("/") ?: "AetherWave"
        val artist = track?.artist ?: "Premium Audio Engine"
        
        val metadataBuilder = android.media.MediaMetadata.Builder()
            .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, artist)
            .putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, (duration.value * 1000).toLong())

        val coverBitmap = getEmbeddedAlbumArt()
        if (coverBitmap != null) {
            val p = Palette.from(coverBitmap).generate()
            dominantColor.value = p.dominantSwatch?.rgb ?: p.mutedSwatch?.rgb
            vibrantColor.value = p.vibrantSwatch?.rgb ?: p.lightVibrantSwatch?.rgb
            
            metadataBuilder.putBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART, coverBitmap)
        } else {
            dominantColor.value = null
            vibrantColor.value = null
        }

        mediaSession.setMetadata(metadataBuilder.build())
    }

    private fun getEmbeddedAlbumArt(): Bitmap? {
        val path = currentTrackPath.value ?: return null
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(path)
            val artBytes = retriever.embeddedPicture
            retriever.release()
            if (artBytes != null) {
                BitmapFactory.decodeByteArray(artBytes, 0, artBytes.size)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel("AETHER_WAVE_CHANNEL", "AetherWave Playback", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(1, createNotification())
    }

    private fun createNotification(): Notification {
        val playPauseAction = if (isPlaying.value) {
            Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_media_pause), "Pause", pendingIntent("ACTION_PAUSE")).build()
        } else {
            Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_media_play), "Play", pendingIntent("ACTION_PLAY")).build()
        }

        val nextAction = Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_media_next), "Next", pendingIntent("ACTION_NEXT")).build()

        val mediaStyle = Notification.MediaStyle().setMediaSession(mediaSession.sessionToken).setShowActionsInCompactView(0, 1)

        val track = currentTrack.value
        val trackTitle = track?.title ?: currentTrackPath.value?.substringAfterLast("/") ?: "Ready"
        val trackArtist = track?.artist ?: ""

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_PLAYER", true)
        }
        val contentIntent = PendingIntent.getActivity(
            this, 0, openAppIntent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = Notification.Builder(this, "AETHER_WAVE_CHANNEL")
            .setContentTitle(trackTitle)
            .setContentText(trackArtist)
            .setSmallIcon(android.R.drawable.ic_media_play) 
            .setStyle(mediaStyle)
            .setContentIntent(contentIntent)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .setOngoing(isPlaying.value)

        val coverBitmap = getEmbeddedAlbumArt()
        if (coverBitmap != null) {
            builder.setLargeIcon(coverBitmap)
        }

        return builder.build()
    }

    private fun pendingIntent(action: String): PendingIntent {
        val intent = Intent(this, PlaybackService::class.java).apply { this.action = action }
        return PendingIntent.getService(this, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun getActiveOutputDeviceDescription(): String {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        // Priority-tiered selection: music profiles first
        val priorityOrder = listOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,   // Hi-fi BT music
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,     // Low-quality BT call — last resort
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
        )

        val device = priorityOrder.firstNotNullOfOrNull { targetType ->
            devices.firstOrNull { it.type == targetType }
        } ?: return "Unknown output"

        // Suppress SCO if A2DP is also connected (same physical device, two profiles)
        if (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO &&
            devices.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }) {
            val a2dp = devices.first { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
            return formatDeviceInfo(a2dp)
        }

        return formatDeviceInfo(device)
    }

    private fun formatDeviceInfo(device: AudioDeviceInfo): String {
        val typeName = when (device.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "🎧 Bluetooth"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "🎧 Bluetooth (Call)"
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "🎧 Wired Headphones"
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> "🎧 Wired Headset"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "🔌 USB Headset"
            AudioDeviceInfo.TYPE_USB_DEVICE -> "🔌 USB Audio"
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "🔊 Built-in Speaker"
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "📱 Earpiece"
            else -> "🔊 Output"
        }

        val productName = device.productName?.toString()
            ?.takeIf { it.isNotBlank() && it != "null" && !it.matches(Regex("[A-Z0-9]{8,}")) }
            ?: ""

        // Detect codec from encodings
        val encodings = device.encodings
        val codecName = detectCodec(encodings, device.type)

        val engineRate = outputSampleRate.value
        val rateStr = if (engineRate > 0) "${engineRate / 1000}kHz" else ""

        val channels = device.channelCounts
        val maxCh = if (channels.isNotEmpty()) channels.max() else 0

        val parts = mutableListOf(typeName)
        if (productName.isNotBlank()) parts.add(productName)
        if (codecName.isNotBlank()) parts.add(codecName)
        if (rateStr.isNotBlank()) parts.add(rateStr)
        if (maxCh > 0) parts.add("${maxCh}ch")

        return parts.joinToString(" · ")
    }

    @Suppress("DEPRECATION")
    private fun detectCodec(encodings: IntArray, deviceType: Int): String {
        if (encodings.isEmpty()) {
            // Bluetooth with no encoding info → likely SBC or AAC
            return if (deviceType == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) "SBC" else ""
        }

        // Map encoding constants to codec names, ordered by quality (best first)
        // AudioFormat constants
        val codecMap = linkedMapOf(
            21 to "LDAC",          // ENCODING_LDAC (API 34+)
            20 to "LHDC",          // ENCODING_LHDC (if available)
            16 to "aptX HD",       // ENCODING_APTX_HD (API 34+)
            15 to "aptX",          // ENCODING_APTX (API 34+)
            14 to "Opus",          // ENCODING_OPUS
            9 to "FLAC",           // ENCODING_PCM_FLOAT → FLAC pass-through
            7 to "AAC",            // ENCODING_AAC_LC
            17 to "AAC-HE",       // ENCODING_AAC_HE_V1
            18 to "AAC-HEv2",     // ENCODING_AAC_HE_V2
            5 to "AC3",            // ENCODING_AC3
            6 to "E-AC3",          // ENCODING_E_AC3
            2 to "PCM 16-bit",     // ENCODING_PCM_16BIT
            3 to "PCM 8-bit",      // ENCODING_PCM_8BIT
            4 to "PCM Float"       // ENCODING_PCM_FLOAT
        )

        // Find the best codec the device supports
        for ((encoding, name) in codecMap) {
            if (encodings.contains(encoding)) return name
        }

        return if (deviceType == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) "SBC" else "PCM"
    }

    private fun loadSettings() {
        val audioPrefs = getSharedPreferences("aetherwave_audio", Context.MODE_PRIVATE)
        
        // Load Effect Mode
        try {
            val mode = try {
                audioPrefs.getInt("effect_mode", 0)
            } catch (_: Throwable) {
                try { audioPrefs.getLong("effect_mode", 0L).toInt() }
                catch (_: Throwable) { 0 }
            }
            currentEffectMode.value = mode
            activeEngine.setEffectMode(mode)
        } catch (_: Throwable) {}
        
        // Load EQ Gains
        try {
            val gainStr = audioPrefs.getString("eq_gains", "0,0,0,0,0") ?: "0,0,0,0,0"
            val gains = gainStr.split(",").map { it.toFloat() }.toFloatArray()
            if (gains.size >= 5) {
                eqGains.value = gains
                for (i in 0 until minOf(gains.size, 5)) { activeEngine.setEqBandGain(i, gains[i]) }
            }
        } catch (_: Throwable) {}

        // Load Force Max Bitrate
        try {
            val force = getSharedPreferences("aetherwave_playlists", Context.MODE_PRIVATE)
                .getBoolean("force_max_bitrate", false)
            activeEngine.setForceMaxBitrate(force)
        } catch (_: Throwable) {}
        
        // Load EQ Enabled
        try {
            val enabled = audioPrefs.getBoolean("eq_enabled", true)
            eqEnabled.value = enabled
            activeEngine.setEqEnabled(enabled)
        } catch (_: Throwable) {}

        // Load Audiophile Mode
        try {
            val isAudiophile = audioPrefs.getBoolean("audiophile_mode", false)
            audiophileMode.value = isAudiophile
            activeEngine.setAudiophileMode(isAudiophile)
        } catch (_: Throwable) {}
        
        restorePlayerState()
    }

    private fun savePlayerState() {
        if (queueManager.currentQueue.value.isEmpty()) return
        serviceScope.launch(Dispatchers.IO) {
            val prefs = getSharedPreferences("aetherwave_state", Context.MODE_PRIVATE)
            val origQ = queueManager.originalQueue.map { it.filePath }.joinToString("|")
            val currQ = queueManager.currentQueue.value.map { it.filePath }.joinToString("|")
            
            prefs.edit()
                .putString("orig_queue", origQ)
                .putString("curr_queue", currQ)
                .putInt("curr_index", queueManager.currentIndex.value)
                .putFloat("curr_pos", currentPosition.value.toFloat())
                .putBoolean("shuffle", queueManager.shuffleEnabled.value)
                .putInt("repeat", queueManager.repeatMode.value)
                .apply()
        }
    }

    private fun restorePlayerState() {
        val prefs = getSharedPreferences("aetherwave_state", Context.MODE_PRIVATE)
        val origQStr = prefs.getString("orig_queue", "") ?: ""
        val currQStr = prefs.getString("curr_queue", "") ?: ""
        if (origQStr.isEmpty() || currQStr.isEmpty()) return
        
        serviceScope.launch(Dispatchers.IO) {
            val repo = MediaRepository(this@PlaybackService)
            val cachedTracks = repo.loadCachedTracks().associateBy { it.filePath }
            
            val origTracks = origQStr.split("|").mapNotNull { cachedTracks[it] }
            val currTracks = currQStr.split("|").mapNotNull { cachedTracks[it] }
            
            if (origTracks.isNotEmpty() && currTracks.isNotEmpty()) {
                val idx = prefs.getInt("curr_index", 0)
                val pos = prefs.getFloat("curr_pos", 0f)
                val shuffle = prefs.getBoolean("shuffle", false)
                val repeat = prefs.getInt("repeat", 0)
                
                withContext(Dispatchers.Main) {
                    queueManager.restoreState(origTracks, currTracks, idx, shuffle, repeat)
                    val track = queueManager.currentTrack.value
                    if (track != null) {
                        currentTrackPath.value = track.filePath
                        coverArtUrl.value = null
                        lyrics.value = null
                        activeEngine.loadTrack(track.filePath)
                        activeEngine.seekTo(pos)
                        currentPosition.value = pos.toDouble()
                        duration.value = track.duration.toDouble() / 1000.0
                        updateMediaMetadata()
                        updatePlaybackState(PlaybackState.STATE_PAUSED)
                        updateNotification()
                        
                        launch { coverArtUrl.value = coverArtRepo.fetchCoverArtUrl(track.title, track.artist) }
                        launch { lyrics.value = lyricsRepo.fetchLyrics(track.title, track.artist) }
                    }
                }
            }
        }
    }
}

