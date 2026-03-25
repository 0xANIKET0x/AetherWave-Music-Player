package com.aetherwave.player.data

import android.content.Context
import android.provider.MediaStore
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MediaRepository(private val context: Context) {
    suspend fun loadCachedTracks(): List<Track> {
        val prefs = context.getSharedPreferences("media_repo", Context.MODE_PRIVATE)
        val jsonStr = prefs.getString("cached_tracks", "[]") ?: "[]"
        val list = mutableListOf<Track>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val uriStr = obj.optString("albumArtUri", "")
                val uri = if (uriStr.isNotEmpty()) Uri.parse(uriStr) else null
                list.add(Track(
                    id = obj.getLong("id"),
                    title = obj.getString("title"),
                    artist = obj.getString("artist"),
                    album = obj.getString("album"),
                    albumArtUri = uri,
                    filePath = obj.getString("filePath"),
                    duration = obj.getLong("duration"),
                    bitrate = obj.optInt("bitrate", 0),
                    sampleRate = obj.optInt("sampleRate", 0),
                    channelCount = obj.optInt("channelCount", 2),
                    isHighRes = obj.optBoolean("isHighRes", false),
                    isAtmos = obj.optBoolean("isAtmos", false)
                ))
            }
        } catch (_: Exception) {}
        return list
    }

    suspend fun saveTracksToCache(tracks: List<Track>) {
        val prefs = context.getSharedPreferences("media_repo", Context.MODE_PRIVATE)
        val arr = JSONArray()
        tracks.forEach { t ->
            val obj = JSONObject()
            obj.put("id", t.id)
            obj.put("title", t.title)
            obj.put("artist", t.artist)
            obj.put("album", t.album)
            obj.put("albumArtUri", t.albumArtUri?.toString() ?: "")
            obj.put("filePath", t.filePath)
            obj.put("duration", t.duration)
            obj.put("bitrate", t.bitrate)
            obj.put("sampleRate", t.sampleRate)
            obj.put("channelCount", t.channelCount)
            obj.put("isHighRes", t.isHighRes)
            obj.put("isAtmos", t.isAtmos)
            arr.put(obj)
        }
        prefs.edit().putString("cached_tracks", arr.toString()).apply()
    }

    suspend fun scanAllTracks(existing: List<Track> = emptyList(), manualFolders: Set<String> = emptySet()): List<Track> {
        val newTracks = mutableListOf<Track>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID
        )
        
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol) ?: "Unknown Title"
                val artist = cursor.getString(artistCol) ?: "Unknown Artist"
                val album = cursor.getString(albumCol) ?: "Unknown Album"
                val data = cursor.getString(dataCol) ?: ""
                val duration = cursor.getLong(durCol)
                val albumId = cursor.getLong(albumIdCol)

                val contentUri = Uri.parse("content://media/external/audio/albumart")
                val albumArtUri = android.content.ContentUris.withAppendedId(contentUri, albumId)

                newTracks.add(Track(
                    id = id,
                    title = title,
                    artist = artist,
                    album = album,
                    albumArtUri = albumArtUri,
                    filePath = data,
                    duration = duration,
                    bitrate = 0,
                    sampleRate = 44100,
                    channelCount = 2,
                    isHighRes = false,
                    isAtmos = false
                ))
            }
        }
        
        for (folderUri in manualFolders) {
            try {
                val dir = File(folderUri)
                if (dir.exists() && dir.isDirectory) {
                    dir.walkTopDown().filter { it.isFile && (it.name.endsWith(".mp3") || it.name.endsWith(".flac")) }.forEach { file ->
                        if (newTracks.none { it.filePath == file.absolutePath }) {
                            newTracks.add(Track(
                                id = file.absolutePath.hashCode().toLong(),
                                title = file.nameWithoutExtension,
                                artist = "Unknown",
                                album = "Unknown",
                                albumArtUri = null,
                                filePath = file.absolutePath,
                                duration = 0L,
                                bitrate = 0,
                                sampleRate = 44100,
                                channelCount = 2,
                                isHighRes = false,
                                isAtmos = false
                            ))
                        }
                    }
                }
            } catch (e: Exception) {}
        }
        
        return newTracks
    }

    fun getAudiophileTracks(tracks: List<Track>): List<Track> {
        return tracks.filter { it.isHighRes || it.isAtmos || (it.sampleRate ?: 0) >= 48000 || (it.bitrate ?: 0) >= 1400000 }
    }

    fun getAlbumGroups(tracks: List<Track>): Map<String, List<Track>> {
        return tracks.groupBy { it.album }
    }
}
