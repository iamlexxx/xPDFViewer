package com.xpdfviewer.data

import android.net.Uri

data class RecentDocument(
    val uriString: String,
    val fileName: String,
    val pageCount: Int,
    val lastReadPage: Int = 1,
    val lastOpenedTimestamp: Long = System.currentTimeMillis(),
    val fileSizeFormatted: String = "",
    val filePath: String = ""
) {
    val uri: Uri
        get() = Uri.parse(uriString)

    val progressPercent: Int
        get() = if (pageCount > 0) ((lastReadPage.toFloat() / pageCount.toFloat()) * 100).toInt().coerceIn(1, 100) else 100

    /**
     * Resolves human-readable file path, with fallback to parsing uriString for older history items.
     */
    fun getResolvedPath(): String {
        if (filePath.isNotBlank()) return filePath
        return resolveDisplayPath(uri, fileName)
    }

    /**
     * Formats the last date and time the file was opened.
     */
    fun getFormattedLastOpened(): String {
        if (lastOpenedTimestamp <= 0L) return ""
        val sdf = java.text.SimpleDateFormat("MMM d, yyyy • h:mm a", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(lastOpenedTimestamp))
    }

    companion object {
        fun resolveDisplayPath(uri: Uri, fallbackName: String = ""): String {
            try {
                if (uri.scheme == "file") {
                    return uri.path ?: ""
                }
                val authority = uri.authority ?: ""
                val path = uri.path ?: ""
                val decoded = java.net.URLDecoder.decode(path, "UTF-8")

                if (authority == "com.android.externalstorage.documents") {
                    if (decoded.contains("primary:")) {
                        return "/storage/emulated/0/" + decoded.substringAfter("primary:")
                    }
                    val parts = decoded.substringAfter("document/").split(":")
                    if (parts.size >= 2) {
                        return if (parts[0].equals("primary", ignoreCase = true)) {
                            "/storage/emulated/0/${parts[1]}"
                        } else {
                            "/storage/${parts[0]}/${parts[1]}"
                        }
                    }
                }

                if (authority == "com.android.providers.downloads.documents") {
                    if (decoded.contains("raw:")) {
                        return decoded.substringAfter("raw:")
                    }
                    if (fallbackName.isNotBlank()) {
                        return "/storage/emulated/0/Download/$fallbackName"
                    }
                }

                if (authority == "com.xpdfviewer.provider") {
                    return "/app_cache/" + (uri.lastPathSegment ?: fallbackName)
                }

                if (decoded.contains("/storage/")) {
                    return "/storage/" + decoded.substringAfter("/storage/")
                }

                if (decoded.startsWith("/") && decoded.contains(".pdf")) {
                    return decoded
                }

                if (fallbackName.isNotBlank()) {
                    return fallbackName
                }
            } catch (ignored: Exception) {}
            return uri.path ?: uri.toString()
        }
    }
}

enum class AppTab {
    HOME,
    RECENT,
    TOOLS
}
