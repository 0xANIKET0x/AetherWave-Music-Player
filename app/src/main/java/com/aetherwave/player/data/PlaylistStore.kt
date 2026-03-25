package com.aetherwave.player.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class PlaylistStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("aetherwave_playlists", Context.MODE_PRIVATE)

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes = _changes.asSharedFlow()

    private fun notifyChange() {
        _changes.tryEmit(Unit)
    }

    // ─── Favorites ───
    fun isFavorite(path: String): Boolean =
        getFavorites().contains(path)

    fun toggleFavorite(path: String): Boolean {
        val favs = getFavorites().toMutableSet()
        val added = if (favs.contains(path)) { favs.remove(path); false } else { favs.add(path); true }
        prefs.edit().putStringSet("favorites", favs).apply()
        notifyChange()
        return added
    }

    fun getFavorites(): Set<String> = try {
        prefs.getStringSet("favorites", emptySet()) ?: emptySet()
    } catch (e: Throwable) {
        android.util.Log.e("AetherWave", "Error reading favorites: ${e.message}", e)
        emptySet()
    }

    fun getPlaylistNames(): List<String> = try {
        val json = prefs.getString("playlists_index", "[]") ?: "[]"
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (_: Throwable) { emptyList() }

    fun createPlaylist(name: String) {
        val names = getPlaylistNames().toMutableList()
        if (!names.contains(name)) {
            names.add(name)
            prefs.edit()
                .putString("playlists_index", JSONArray(names).toString())
                .putString("playlist_$name", "[]")
                .apply()
            notifyChange()
        }
    }

    fun addToPlaylist(playlistName: String, trackPath: String) {
        val tracks = getPlaylistTracks(playlistName).toMutableList()
        if (!tracks.contains(trackPath)) {
            tracks.add(trackPath)
            prefs.edit().putString("playlist_$playlistName", JSONArray(tracks).toString()).apply()
            notifyChange()
        }
    }

    fun getPlaylistTracks(playlistName: String): List<String> = try {
        val json = prefs.getString("playlist_$playlistName", "[]") ?: "[]"
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (_: Throwable) { emptyList() }

    fun removeFromPlaylist(playlistName: String, trackPath: String) {
        val tracks = getPlaylistTracks(playlistName).toMutableList()
        tracks.remove(trackPath)
        prefs.edit().putString("playlist_$playlistName", JSONArray(tracks).toString()).apply()
        notifyChange()
    }

    fun deletePlaylist(name: String) {
        val names = getPlaylistNames().toMutableList()
        names.remove(name)
        prefs.edit()
            .putString("playlists_index", JSONArray(names).toString())
            .remove("playlist_$name")
            .apply()
        notifyChange()
    }

    // ─── Settings ───
    fun getBoolean(key: String, default: Boolean = true): Boolean = try {
        prefs.getBoolean(key, default)
    } catch (_: Throwable) {
        // Fallback for string "true"/"false" if any
        try { prefs.getString(key, default.toString())?.toBoolean() ?: default }
        catch (_: Throwable) { default }
    }
    
    fun setBoolean(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()

    fun getExcludedFolders(): Set<String> = try {
        prefs.getStringSet("excluded_folders", emptySet()) ?: emptySet()
    } catch (e: Throwable) {
        android.util.Log.e("AetherWave", "Error reading folders: ${e.message}", e)
        emptySet()
    }

    fun toggleExcludedFolder(folder: String) {
        val folders = getExcludedFolders().toMutableSet()
        if (folders.contains(folder)) folders.remove(folder) else folders.add(folder)
        prefs.edit().putStringSet("excluded_folders", folders).apply()
    }

    fun getManualFolders(): Set<String> = try {
        prefs.getStringSet("manual_folders", emptySet()) ?: emptySet()
    } catch (e: Throwable) {
        emptySet()
    }

    fun addManualFolder(folderUri: String) {
        val folders = getManualFolders().toMutableSet()
        folders.add(folderUri)
        prefs.edit().putStringSet("manual_folders", folders).apply()
        notifyChange()
    }

    fun removeManualFolder(folderUri: String) {
        val folders = getManualFolders().toMutableSet()
        folders.remove(folderUri)
        prefs.edit().putStringSet("manual_folders", folders).apply()
        notifyChange()
    }

    // ─── Theming ───
    fun getAccentColor(): Long? = try {
        val color = prefs.getLong("accent_color", -1L)
        if (color == -1L) null else color
    } catch (_: Throwable) {
        try {
            val color = prefs.getInt("accent_color", -1)
            if (color == -1) null else color.toLong()
        } catch (_: Throwable) { null }
    }
    
    fun setAccentColor(color: Long?) {
        if (color == null) prefs.edit().remove("accent_color").apply()
        else prefs.edit().putLong("accent_color", color).apply()
    }

    // ─── Theme ───
    fun getTheme(): String = try {
        prefs.getString("ui_theme", "DARK") ?: "DARK"
    } catch (_: Throwable) { "DARK" }
    
    fun setTheme(theme: String) {
        prefs.edit().putString("ui_theme", theme).apply()
        notifyChange()
    }
}
