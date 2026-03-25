package com.aetherwave.player.data

import android.net.Uri

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtUri: Uri?,
    val filePath: String,
    val duration: Long, // milliseconds
    val bitrate: Int,   // bps
    val sampleRate: Int, // Hz
    val channelCount: Int,
    val isHighRes: Boolean,
    val isAtmos: Boolean
)
