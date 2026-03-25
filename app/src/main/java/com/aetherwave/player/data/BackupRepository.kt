package com.aetherwave.player.data

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupRepository(private val context: Context) {

    fun exportBackup(uri: Uri): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                ZipOutputStream(BufferedOutputStream(os)).use { zos ->
                    // 1. Export SharedPreferences
                    exportPrefs(zos, "aetherwave_playlists", "playlists.json")
                    exportPrefs(zos, "aetherwave_audio", "audio_settings.json")

                    // 2. Export Media Cache
                    exportDir(zos, File(context.cacheDir, "covers"), "covers/")
                    exportDir(zos, File(context.cacheDir, "lyrics"), "lyrics/")
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun importBackup(uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { `is` ->
                ZipInputStream(BufferedInputStream(`is`)).use { zis ->
                    var entry: ZipEntry? = zis.getNextEntry()
                    while (entry != null) {
                        when {
                            entry.name == "playlists.json" -> importPrefs(zis, "aetherwave_playlists")
                            entry.name == "audio_settings.json" -> importPrefs(zis, "aetherwave_audio")
                            entry.name.startsWith("covers/") -> importFile(zis, entry.name, "covers")
                            entry.name.startsWith("lyrics/") -> importFile(zis, entry.name, "lyrics")
                        }
                        zis.closeEntry()
                        entry = zis.getNextEntry()
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun exportPrefs(zos: ZipOutputStream, prefName: String, fileName: String) {
        val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
        val allEntries = prefs.all
        android.util.Log.d("AetherWaveBackup", "Exporting $prefName: found ${allEntries.size} keys")
        val json = JSONObject()
        for ((key, value) in allEntries) {
            if (value is Set<*>) {
                val arr = org.json.JSONArray()
                value.forEach { arr.put(it) }
                json.put(key, arr)
            } else {
                json.put(key, value)
            }
        }
        
        zos.putNextEntry(ZipEntry(fileName))
        zos.write(json.toString().toByteArray())
        zos.closeEntry()
    }

    private fun importPrefs(zis: ZipInputStream, prefName: String) {
        val jsonStr = zis.readBytes().toString(Charsets.UTF_8)
        if (jsonStr.isBlank()) return
        val json = JSONObject(jsonStr)
        android.util.Log.d("AetherWaveBackup", "Importing $prefName: found ${json.length()} keys")
        val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit()
        prefs.clear()
        
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.opt(key) ?: continue
            android.util.Log.d("AetherWaveBackup", "Processing key: $key (type: ${value.javaClass.simpleName})")
            when (value) {
                is Boolean -> prefs.putBoolean(key, value)
                is Int -> prefs.putInt(key, value)
                is Long -> prefs.putLong(key, value)
                is Double -> {
                    // Try to store as the most likely type
                    if (value == value.toInt().toDouble()) prefs.putInt(key, value.toInt())
                    else if (value == value.toLong().toDouble()) prefs.putLong(key, value.toLong())
                    else prefs.putFloat(key, value.toFloat())
                }
                is String -> prefs.putString(key, value)
                is org.json.JSONArray -> {
                    val set = mutableSetOf<String>()
                    for (i in 0 until value.length()) {
                        set.add(value.getString(i))
                    }
                    prefs.putStringSet(key, set)
                }
            }
        }
        prefs.apply()
    }

    private fun exportDir(zos: ZipOutputStream, dir: File, zipPrefix: String) {
        if (!dir.exists() || !dir.isDirectory) return
        dir.listFiles()?.forEach { file ->
            if (file.isFile) {
                zos.putNextEntry(ZipEntry(zipPrefix + file.name))
                file.inputStream().copyTo(zos)
                zos.closeEntry()
            }
        }
    }

    private fun importFile(zis: ZipInputStream, entryName: String, dirName: String) {
        val fileName = entryName.substringAfter("/")
        if (fileName.isBlank()) return
        
        val targetDir = File(context.cacheDir, dirName)
        if (!targetDir.exists()) targetDir.mkdirs()
        
        val targetFile = File(targetDir, fileName)
        targetFile.outputStream().use { fos ->
            zis.copyTo(fos)
        }
    }
}
