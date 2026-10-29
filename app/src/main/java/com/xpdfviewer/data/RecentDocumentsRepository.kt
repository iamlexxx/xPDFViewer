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

    companion object {
        private const val PREFS_NAME = "xpdf_recents_pref"
        private const val KEY_RECENTS = "recent_documents_list"
    }
}
