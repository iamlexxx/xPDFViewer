package com.xpdfviewer.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class RecentDocumentsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getRecentDocuments(): List<RecentDocument> {
        val jsonString = prefs.getString(KEY_RECENTS, null) ?: return emptyList()
        val list = mutableListOf<RecentDocument>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    RecentDocument(
                        uriString = obj.getString("uri"),
                        fileName = obj.getString("name"),
                        pageCount = obj.optInt("pages", 1),
                        lastReadPage = obj.optInt("lastPage", 1),
                        lastOpenedTimestamp = obj.optLong("time", System.currentTimeMillis()),
                        fileSizeFormatted = obj.optString("size", ""),
                        filePath = obj.optString("path", "")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveRecentDocument(doc: RecentDocument) {
        val currentList = getRecentDocuments().toMutableList()
        // Remove existing item with same URI
        currentList.removeAll { it.uriString == doc.uriString }
        // Insert at beginning
        currentList.add(0, doc)

        // Keep maximum of 30 recent documents
        val trimmed = if (currentList.size > 30) currentList.subList(0, 30) else currentList
        persist(trimmed)
    }

    fun updateLastReadPage(uriString: String, page: Int) {
        val currentList = getRecentDocuments().toMutableList()
        val index = currentList.indexOfFirst { it.uriString == uriString }
        if (index != -1) {
            val existing = currentList[index]
            currentList[index] = existing.copy(
                lastReadPage = page,
                lastOpenedTimestamp = System.currentTimeMillis()
            )
            persist(currentList)
        }
    }

    fun removeRecentDocument(uriString: String) {
        val currentList = getRecentDocuments().toMutableList()
        val removed = currentList.removeAll { it.uriString == uriString }
        if (removed) {
            persist(currentList)
        }
    }

    fun clearRecents() {
        prefs.edit().remove(KEY_RECENTS).apply()
    }

    // region User Bookmarks

    fun getAllBookmarks(): List<UserBookmark> {
        val jsonString = prefs.getString(KEY_BOOKMARKS, null) ?: return emptyList()
        val list = mutableListOf<UserBookmark>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    UserBookmark(
                        id = obj.getString("id"),
                        uriString = obj.getString("uri"),
                        pageIndex = obj.getInt("pageIndex"),
                        title = obj.getString("title"),
                        note = obj.optString("note", ""),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun getUserBookmarks(uriString: String): List<UserBookmark> {
        return getAllBookmarks()
            .filter { it.uriString == uriString }
            .sortedBy { it.pageIndex }
    }

    fun saveBookmark(bookmark: UserBookmark) {
        val all = getAllBookmarks().toMutableList()
        // If bookmark already exists with this ID, replace it
        val index = all.indexOfFirst { it.id == bookmark.id }
        if (index != -1) {
            all[index] = bookmark
        } else {
            // Also replace if there is already a bookmark on the exact same page for this URI
            val pageIndex = all.indexOfFirst { it.uriString == bookmark.uriString && it.pageIndex == bookmark.pageIndex }
            if (pageIndex != -1) {
                all[pageIndex] = bookmark
            } else {
                all.add(bookmark)
            }
        }
        persistBookmarks(all)
    }

    fun deleteBookmark(bookmarkId: String) {
        val all = getAllBookmarks().toMutableList()
        val removed = all.removeAll { it.id == bookmarkId }
        if (removed) {
            persistBookmarks(all)
        }
    }

    fun deleteBookmarkForPage(uriString: String, pageIndex: Int) {
        val all = getAllBookmarks().toMutableList()
        val removed = all.removeAll { it.uriString == uriString && it.pageIndex == pageIndex }
        if (removed) {
            persistBookmarks(all)
        }
    }

    fun isPageBookmarked(uriString: String, pageIndex: Int): Boolean {
        return getAllBookmarks().any { it.uriString == uriString && it.pageIndex == pageIndex }
    }

    fun getBookmarkForPage(uriString: String, pageIndex: Int): UserBookmark? {
        return getAllBookmarks().firstOrNull { it.uriString == uriString && it.pageIndex == pageIndex }
    }

    private fun persistBookmarks(list: List<UserBookmark>) {
        try {
            val jsonArray = JSONArray()
            for (item in list) {
                val obj = JSONObject().apply {
                    put("id", item.id)
                    put("uri", item.uriString)
                    put("pageIndex", item.pageIndex)
                    put("title", item.title)
                    put("note", item.note)
                    put("createdAt", item.createdAt)
                }
                jsonArray.put(obj)
            }
            prefs.edit().putString(KEY_BOOKMARKS, jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // endregion

    private fun persist(list: List<RecentDocument>) {
        try {
            val jsonArray = JSONArray()
            for (item in list) {
                val obj = JSONObject().apply {
                    put("uri", item.uriString)
                    put("name", item.fileName)
                    put("pages", item.pageCount)
                    put("lastPage", item.lastReadPage)
                    put("time", item.lastOpenedTimestamp)
                    put("size", item.fileSizeFormatted)
                    put("path", item.filePath)
                }
                jsonArray.put(obj)
            }
            prefs.edit().putString(KEY_RECENTS, jsonArray.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // region Reading Mode

    fun getSavedReadingMode(): ReadingMode {
        val id = prefs.getString(KEY_SAVED_READING_MODE, null) ?: return ReadingMode.DEFAULT
        return ReadingMode.fromId(id)
    }

    fun saveReadingMode(mode: ReadingMode) {
        prefs.edit().putString(KEY_SAVED_READING_MODE, mode.id).apply()
    }

    // endregion

    // region Voice and Pitch Settings

    fun getSavedVoiceId(): String? {
        return prefs.getString(KEY_SAVED_VOICE_ID, null)
    }

    fun saveVoiceId(voiceId: String?) {
        if (voiceId != null) {
            prefs.edit().putString(KEY_SAVED_VOICE_ID, voiceId).apply()
        } else {
            prefs.edit().remove(KEY_SAVED_VOICE_ID).apply()
        }
    }

    fun getSavedVoicePitch(): Float {
        return prefs.getFloat(KEY_SAVED_VOICE_PITCH, 1.0f)
    }

    fun saveVoicePitch(pitch: Float) {
        prefs.edit().putFloat(KEY_SAVED_VOICE_PITCH, pitch).apply()
    }

    // endregion

    companion object {
        private const val PREFS_NAME = "xpdf_recents_pref"
        private const val KEY_RECENTS = "recent_documents_list"
        private const val KEY_BOOKMARKS = "user_bookmarks_list"
        private const val KEY_SAVED_READING_MODE = "saved_reading_mode"
        private const val KEY_SAVED_VOICE_ID = "saved_audio_voice_id"
        private const val KEY_SAVED_VOICE_PITCH = "saved_audio_voice_pitch"
    }
}
