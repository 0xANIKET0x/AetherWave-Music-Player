package com.aetherwave.player.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class LyricsRepository(private val context: Context) {
    private val TAG = "LyricsRepository"
    private val LYRICA_BASE_URL = "https://lyrica-f924.onrender.com"
    private val cache = HashMap<String, FetchedLyrics?>()

    // Internal class for two-pass architecture
    data class FetchedLyrics(val lines: List<LyricLine>, val isSynced: Boolean)

    suspend fun fetchLyrics(title: String, artist: String): FetchedLyrics? = withContext(Dispatchers.IO) {
        val key = "${title.lowercase().replace(" ", "_")}_${artist.lowercase().replace(" ", "_")}"
        Log.d(TAG, "Fetching lyrics for: $title by $artist (key: $key)")
        
        // 1. Check Memory Cache (Only return if it's NOT null, to allow retry of failed fetches)
        val cached = cache[key]
        if (cached != null) {
            Log.d(TAG, "Memory cache hit for $key")
            return@withContext cached
        }

        // 2. Check Disk Cache
        val cacheDir = File(context.cacheDir, "lyrics")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val localFile = File(cacheDir, "$key.json")

        if (localFile.exists()) {
            try {
                val jsonStr = localFile.readText()
                val lines = parseJsonLyrics(jsonStr)
                if (lines.isNotEmpty()) {
                    Log.d(TAG, "Disk cache hit for $key (${lines.size} lines)")
                    val isSynced = lines.any { it.timeMs >= 0 }
                    val fetched = FetchedLyrics(lines, isSynced)
                    cache[key] = fetched
                    return@withContext fetched
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading disk cache: ${e.message}")
            }
        }

        // 3. Metadata Cleaning
        val cleanTitle = cleanMetadata(title)
        val cleanArtist = cleanMetadata(artist)
        val isGenericArt = isGenericArtist(cleanArtist)
        Log.d(TAG, "Cleaned metadata: '$cleanTitle' by '$cleanArtist' (Generic: $isGenericArt)")

        // --- 4. Two-Pass Synced-First Architecture ---
        var bestPlainLyrics: List<LyricLine>? = null
        
        // Priority sequence: Sweep all sources for SYNCED. Settle for PLAIN only at the end.
        val sources = listOf(
            { fetchFromLrcLibGet(title, artist) },                  // 1. LRCLib Exact
            { if (cleanTitle != title || cleanArtist != artist) fetchFromLrcLibGet(cleanTitle, cleanArtist) else null }, // 2. Cleaned Exact
            { if (!isGenericArt) fetchFromLrcLibSearch(cleanTitle, cleanArtist) else null }, // 3. Cleaned Search
            { fetchFromLrcLibSearchGlobal(cleanTitle) },            // 4. Global Title Search (Fuzzy)
            { fetchFromLyricsOvh(cleanTitle, if (isGenericArt) "" else cleanArtist) }, // 5. OVH Fallback
            { fetchFromLyrica(cleanTitle, cleanArtist) },           // 6. Lyrica Exact (Last Resort)
            { fetchFromLyrica(cleanTitle, null) }                   // 7. Lyrica Title-Only (Absolute Last Resort)
        )

        for (source in sources) {
            val result = source()
            if (result != null) {
                if (result.isSynced) {
                    Log.d(TAG, "SUCCESS: Found SYNCED lyrics. Ending search sweep.")
                    saveLyricsToDisk(localFile, result.lines)
                    val fetched = FetchedLyrics(result.lines, true)
                    cache[key] = fetched
                    return@withContext fetched
                } else if (bestPlainLyrics == null) {
                    Log.d(TAG, "SAVED: Found plain lyrics fallback. Continuing search for synced version...")
                    bestPlainLyrics = result.lines 
                }
            }
        }

        // Final Fallback if no synced lyrics were found anywhere
        if (bestPlainLyrics != null) {
            Log.d(TAG, "No synced lyrics found in any database. Returning best plain-text version.")
            saveLyricsToDisk(localFile, bestPlainLyrics)
            val fetched = FetchedLyrics(bestPlainLyrics, false)
            cache[key] = fetched
            return@withContext fetched
        }

        Log.w(TAG, "TOTAL FAILURE: No lyrics found for: $title ($artist)")
        null
    }

    suspend fun upgradeToSyncedLyrics(title: String, artist: String, currentPlainLyrics: String?): FetchedLyrics? = withContext(Dispatchers.IO) {
        Log.d(TAG, "UPGRADE REQUEST: Searching synced-only for $title")
        val cleanTitle = cleanMetadata(title)
        val cleanArtist = cleanMetadata(artist)
        
        // Only try synced-capable sources
        val sources = listOf(
            { fetchFromLrcLibGet(title, artist) },
            { fetchFromLrcLibSearch(cleanTitle, cleanArtist) },
            { fetchFromLyrica(cleanTitle, cleanArtist) }
        )

        for (source in sources) {
            val result = source()
            if (result?.isSynced == true) {
                Log.d(TAG, "UPGRADE SUCCESS: Found synced version")
                return@withContext result
            }
        }
        null
    }


    private fun isGenericArtist(artist: String): Boolean {
        val lower = artist.lowercase()
        return lower.isBlank() || lower == "unknown" || lower == "unknown artist" || 
               lower == "various artists" || lower == "various" || lower.startsWith("(")
    }

    private fun fetchFromLyrica(title: String, artist: String? = null): FetchedLyrics? {
        if (title.isBlank()) return null
        return try {
            val encodedTitle = URLEncoder.encode(title, "UTF-8")
            val urlString = if (artist.isNullOrBlank()) {
                "$LYRICA_BASE_URL/lyrics/?song=$encodedTitle&timestamps=true"
            } else {
                val encodedArtist = URLEncoder.encode(artist, "UTF-8")
                "$LYRICA_BASE_URL/lyrics/?artist=$encodedArtist&song=$encodedTitle&timestamps=true"
            }
            
            Log.d(TAG, "Lyrica Fetch: $urlString")
            val response = makeRequest(URL(urlString))
            if (response != null) {
                val json = JSONObject(response)
                val lyricText = json.optString("lyrics", "")
                val hasTimestamps = json.optBoolean("hasTimestamps", false)
                
                if (lyricText.isNotBlank()) {
                    val parsed = parseLrc(lyricText)
                    if (parsed.isNotEmpty()) {
                        // Extra safety check: if parseLrc finds timestamps, it's synced regardless of flag
                        val isActuallySynced = hasTimestamps || parsed.any { it.timeMs > 0 }
                        return FetchedLyrics(parsed, isActuallySynced)
                    } else if (!hasTimestamps) {
                        // Plain text parser fallback
                        val lines = lyricText.lines()
                            .filter { it.isNotBlank() }
                            .map { LyricLine(-1, it.trim()) }
                        if (lines.isNotEmpty()) return FetchedLyrics(lines, false)
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Lyrica fetch failed: ${e.message}")
            null
        }
    }

    private fun cleanMetadata(text: String): String {
        return text
            .replace(Regex("""^\d+\s*[-.]*\s*"""), "") // Remove leading track numbers (04 - Song)
            .replace("\"", "")
            .replace("'", "")
            .replace(Regex("""\(.*?\)|\[.*?\]"""), "")
            .replace(Regex("""(?i)\b(official|music|lyric|video|hd|hq|audio|vocal|remastered|remaster|live)\b.*"""), "")
            .replace(Regex("""[^a-zA-Z0-9\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun fetchFromLrcLibGet(title: String, artist: String): FetchedLyrics? {
        if (title.isBlank()) return null
        return try {
            val trackName = URLEncoder.encode(title, "UTF-8")
            val artistName = URLEncoder.encode(artist, "UTF-8")
            val urlString = "https://lrclib.net/api/get?track_name=$trackName&artist_name=$artistName"
            Log.d(TAG, "LrcLib GET: $urlString")
            val response = makeRequest(URL(urlString))
            if (response != null) {
                parseLrcLibResponse(JSONObject(response))
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "LrcLib GET failed: ${e.message}")
            null
        }
    }

    private fun fetchFromLrcLibSearchGlobal(query: String): FetchedLyrics? {
        if (query.isBlank()) return null
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val urlString = "https://lrclib.net/api/search?q=$encodedQuery"
            Log.d(TAG, "LrcLib GLOBAL SEARCH: $urlString")
            val response = makeRequest(URL(urlString))
            if (response != null) {
                processSearchResponse(JSONArray(response))
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "LrcLib Global Search failed: ${e.message}")
            null
        }
    }

    private fun fetchFromLrcLibSearch(title: String, artist: String): FetchedLyrics? {
        if (title.isBlank()) return null
        return try {
            val trackName = URLEncoder.encode(title, "UTF-8")
            val artistName = URLEncoder.encode(artist, "UTF-8")
            val urlString = "https://lrclib.net/api/search?track_name=$trackName&artist_name=$artistName"
            Log.d(TAG, "LrcLib SEARCH: $urlString")
            val response = makeRequest(URL(urlString))
            if (response != null) {
                processSearchResponse(JSONArray(response))
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "LrcLib Search failed: ${e.message}")
            null
        }
    }

    private fun processSearchResponse(results: JSONArray): FetchedLyrics? {
        if (results.length() == 0) return null
        Log.d(TAG, "Processing ${results.length()} search results")
        
        // Loop 1: Return FetchedLyrics(parsed, true) the moment valid synced lyrics are found.
        for (i in 0 until results.length()) {
            val obj = results.getJSONObject(i)
            val syncedLyrics = obj.optString("syncedLyrics", "")
            if (syncedLyrics.isNotBlank()) {
                val parsed = parseLrc(syncedLyrics)
                if (parsed.isNotEmpty()) {
                    Log.d(TAG, "SYNC PRIORITY: Found SYNCED lyrics in result #$i")
                    return FetchedLyrics(parsed, true)
                }
            }
        }

        // Loop 2: If no synced lyrics exist in any result, return FetchedLyrics(plainLines, false) for the first valid plain lyrics.
        for (i in 0 until results.length()) {
            val obj = results.getJSONObject(i)
            val plainLyrics = obj.optString("plainLyrics", "")
            if (plainLyrics.isNotBlank()) {
                val lines = plainLyrics.lines().filter { it.isNotBlank() }.map { LyricLine(timeMs = -1, text = it.trim()) }
                if (lines.isNotEmpty()) {
                    Log.d(TAG, "Found plain lyrics fallback in search result #$i")
                    return FetchedLyrics(lines, false)
                }
            }
        }
        return null
    }

    private fun fetchFromLyricsOvh(title: String, artist: String): FetchedLyrics? {
        if (title.isBlank()) return null
        return try {
            val artistName = URLEncoder.encode(artist, "UTF-8")
            val trackName = URLEncoder.encode(title, "UTF-8")
            val urlString = "https://api.lyrics.ovh/v1/$artistName/$trackName"
            Log.d(TAG, "Lyrics.ovh GET: $urlString")
            val response = makeRequest(URL(urlString))
            if (response != null) {
                val json = JSONObject(response)
                val plainLyrics = json.optString("lyrics", "")
                if (plainLyrics.isNotBlank()) {
                    val lines = plainLyrics.lines()
                        .filter { it.isNotBlank() && !it.startsWith("Paroles de la chanson") }
                        .map { LyricLine(timeMs = -1, text = it.trim()) }
                    if (lines.isNotEmpty()) return FetchedLyrics(lines, false)
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Lyrics.ovh failed: ${e.message}")
            null
        }
    }

    private fun makeRequest(url: URL): String? {
        return try {
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", "AetherWave/1.1")

            val code = conn.responseCode
            if (code == 200) {
                conn.inputStream.bufferedReader().readText()
            } else {
                Log.w(TAG, "Request to $url returned code $code")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network request failed for $url: ${e.message}")
            null
        }
    }

    private fun parseLrcLibResponse(json: JSONObject): FetchedLyrics? {
        val synced = json.optString("syncedLyrics", "")
        if (synced.isNotBlank()) {
            val parsed = parseLrc(synced)
            if (parsed.isNotEmpty()) return FetchedLyrics(parsed, true)
        }
        val plain = json.optString("plainLyrics", "")
        if (plain.isNotBlank()) {
            val lines = plain.lines().filter { it.isNotBlank() }.map { LyricLine(-1, it.trim()) }
            if (lines.isNotEmpty()) return FetchedLyrics(lines, false)
        }
        return null
    }

    private fun saveLyricsToDisk(file: File, lines: List<LyricLine>) {
        try {
            val array = JSONArray()
            lines.forEach { line ->
                val obj = JSONObject()
                obj.put("timeMs", line.timeMs)
                obj.put("text", line.text)
                array.put(obj)
            }
            file.writeText(array.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save to disk: ${e.message}")
        }
    }

    private fun parseJsonLyrics(jsonStr: String): List<LyricLine> {
        return try {
            val array = JSONArray(jsonStr)
            val lines = mutableListOf<LyricLine>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                lines.add(LyricLine(timeMs = obj.getLong("timeMs"), text = obj.getString("text")))
            }
            lines
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun parseLrc(lrc: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val regex = Regex("""\[(\d+):(\d{2})(?:\.(\d+))?\](.*)""")

        for (line in lrc.lines()) {
            val match = regex.find(line) ?: continue
            val min = match.groupValues[1]
            val sec = match.groupValues[2]
            val msStr = match.groupValues[3]
            val text = match.groupValues[4].trim()

            if (text.isBlank()) continue

            val minutes = min.toLongOrNull() ?: 0L
            val seconds = sec.toLongOrNull() ?: 0L
            val millis = if (msStr.isNotEmpty()) {
                when (msStr.length) {
                    1 -> msStr.toLong() * 100
                    2 -> msStr.toLong() * 10
                    3 -> msStr.toLong()
                    else -> msStr.substring(0, 3).toLong()
                }
            } else 0L

            lines.add(LyricLine(timeMs = minutes * 60000 + seconds * 1000 + millis, text = text))
        }
        return lines.sortedBy { it.timeMs }
    }
}
