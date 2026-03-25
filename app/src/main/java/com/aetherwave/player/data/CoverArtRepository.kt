package com.aetherwave.player.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class CoverArtRepository(private val context: Context) {
    private val cache = HashMap<String, String?>()

    suspend fun fetchCoverArtUrl(title: String, artist: String): String? = withContext(Dispatchers.IO) {
        val key = "${title.lowercase().replace(" ", "_")}_${artist.lowercase().replace(" ", "_")}"
        
        // 1. Check Memory Cache
        cache[key]?.let { return@withContext it }

        // 2. Check Disk Cache
        val cacheDir = File(context.cacheDir, "covers")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val localFile = File(cacheDir, "$key.jpg")
        
        if (localFile.exists()) {
            val localPath = localFile.absolutePath
            cache[key] = localPath
            return@withContext localPath
        }

        // 3. Fetch from API
        try {
            val query = URLEncoder.encode("$title $artist", "UTF-8")
            val url = URL("https://itunes.apple.com/search?term=$query&media=music&entity=song&limit=1")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            
            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject(response)
                val results = json.getJSONArray("results")
                if (results.length() > 0) {
                    val remoteUrl = results.getJSONObject(0)
                        .getString("artworkUrl100")
                        .replace("100x100", "600x600")
                    
                    // Download and save locally
                    val downloadedFile = downloadImage(remoteUrl, localFile)
                    if (downloadedFile != null) {
                        val finalPath = downloadedFile.absolutePath
                        cache[key] = finalPath
                        return@withContext finalPath
                    }
                    return@withContext remoteUrl // Fallback to remote if download fails
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    private fun downloadImage(url: String, targetFile: File): File? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 5000
            conn.inputStream.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            targetFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
