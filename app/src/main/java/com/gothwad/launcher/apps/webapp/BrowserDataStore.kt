package com.gothwad.launcher.apps.webapp

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class BrowserEntry(
    val id: String = System.currentTimeMillis().toString(),
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String // "BOOKMARK", "HISTORY", "DOWNLOAD"
)

class BrowserDataStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val dataFile = File(context.filesDir, "browser_records.json")

    @Synchronized
    fun getAll(): List<BrowserEntry> {
        if (!dataFile.exists()) return emptyList()
        return runCatching {
            json.decodeFromString<List<BrowserEntry>>(dataFile.readText())
        }.getOrDefault(emptyList())
    }

    @Synchronized
    private fun saveAll(list: List<BrowserEntry>) {
        runCatching {
            dataFile.writeText(json.encodeToString(list))
        }
    }

    fun addBookmark(title: String, url: String) {
        val current = getAll().toMutableList()
        if (current.none { it.type == "BOOKMARK" && it.url == url }) {
            current.add(0, BrowserEntry(title = title, url = url, type = "BOOKMARK"))
            saveAll(current)
        }
    }

    fun removeBookmark(url: String) {
        val filtered = getAll().filterNot { it.type == "BOOKMARK" && it.url == url }
        saveAll(filtered)
    }

    fun isBookmarked(url: String): Boolean {
        return getAll().any { it.type == "BOOKMARK" && it.url == url }
    }

    fun addHistory(title: String, url: String) {
        if (url == "chrome://newtab" || url.startsWith("data:")) return
        val current = getAll().toMutableList()
        current.removeAll { it.type == "HISTORY" && it.url == url }
        current.add(0, BrowserEntry(title = title, url = url, type = "HISTORY"))
        // Keep last 100 history entries
        val historyEntries = current.filter { it.type == "HISTORY" }.take(100)
        val otherEntries = current.filterNot { it.type == "HISTORY" }
        saveAll(historyEntries + otherEntries)
    }

    fun addDownload(fileName: String, url: String) {
        val current = getAll().toMutableList()
        current.add(0, BrowserEntry(title = fileName, url = url, type = "DOWNLOAD"))
        saveAll(current)
    }

    fun deleteEntry(id: String) {
        val filtered = getAll().filterNot { it.id == id }
        saveAll(filtered)
    }

    fun clearType(type: String) {
        val filtered = getAll().filterNot { it.type == type }
        saveAll(filtered)
    }

    fun clearAll() {
        saveAll(emptyList())
    }
}
