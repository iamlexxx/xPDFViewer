package com.xpdfviewer.viewmodel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.OpenableColumns
import android.widget.Toast
import com.xpdfviewer.pdf.PdfDocumentModifier
import com.xpdfviewer.pdf.PdfFormManager
import com.xpdfviewer.pdf.PdfPrintAdapter
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xpdfviewer.data.AddBookmarkDialogState
import com.xpdfviewer.data.AppTab
import com.xpdfviewer.data.DocumentFormState
import com.xpdfviewer.data.AudioPlaybackStatus
import com.xpdfviewer.data.AudioReaderUiState
import com.xpdfviewer.data.AudioSpeed
import com.xpdfviewer.data.FormFieldItem
import com.xpdfviewer.data.OutlineNode
import com.xpdfviewer.data.OutlineTab
import com.xpdfviewer.data.PageDimensions
import com.xpdfviewer.data.PageModificationType
import com.xpdfviewer.data.PageSpatialData
import com.xpdfviewer.data.PdfSearchState
import com.xpdfviewer.data.PdfViewerUiState
import com.xpdfviewer.data.PendingPageAction
import com.xpdfviewer.data.RecentDocument
import com.xpdfviewer.data.RecentDocumentsRepository
import com.xpdfviewer.data.ReadingMode
import com.xpdfviewer.data.SearchMatch
import com.xpdfviewer.data.SleepTimerPreset
import com.xpdfviewer.data.UserBookmark
import com.xpdfviewer.pdf.PdfOutlineExtractor
import com.xpdfviewer.pdf.PdfRendererCore
import com.xpdfviewer.pdf.PdfSearchEngine
import com.xpdfviewer.pdf.PdfTextExtractor
import com.xpdfviewer.pdf.SpatialTextExtractor
import com.xpdfviewer.speech.ContinuousAudioReader
import com.xpdfviewer.speech.TextToSpeechManager
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PdfViewerViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(PdfViewerUiState())
    val uiState: StateFlow<PdfViewerUiState> = _uiState.asStateFlow()

    private var pdfRenderer: PdfRendererCore? = null
    private var recentRepository: RecentDocumentsRepository? = null
    private var ttsManager: TextToSpeechManager? = null
    private var continuousAudioReader: ContinuousAudioReader? = null
    private var searchJob: Job? = null

    fun initialize(context: Context) {
        if (recentRepository == null) {
            recentRepository = RecentDocumentsRepository(context.applicationContext)
            loadRecentDocuments()
            val savedReadingMode = recentRepository?.getSavedReadingMode() ?: ReadingMode.DEFAULT
            _uiState.update { it.copy(readingMode = savedReadingMode) }
        }
    }

    private fun loadRecentDocuments() {
        val list = recentRepository?.getRecentDocuments() ?: emptyList()
        _uiState.update { it.copy(recentDocuments = list) }
    }

    fun selectTab(tab: AppTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun closeDocument() {
        searchJob?.cancel()
        searchJob = null
        pdfRenderer?.close()
        pdfRenderer = null
        _uiState.update {
            it.copy(
                uri = null,
                fileName = "",
                pageCount = 0,
                currentPage = 1,
                showControls = true,
                showTextSheet = false,
                showPageActionsSheet = false,
                showDeleteConfirmDialog = false,
                isModifyingDocument = false,
                showSaveChoiceDialog = false,
                pendingPageAction = null,
                onScreenSelectPageIndex = null,
                onScreenSelectText = "",
                onScreenSpatialData = null,
                selectedWordIndices = emptySet(),
                selectedWordText = "",
                isExtractingOnScreenText = false,
                isTtsSpeaking = false,
                outlineNodes = emptyList(),
                userBookmarks = emptyList(),
                showOutlineSheet = false,
                isOutlineLoading = false,
                addBookmarkDialogState = AddBookmarkDialogState(),
                searchState = PdfSearchState(),
                formState = DocumentFormState(),
                audioReaderState = AudioReaderUiState()
            )
        }
        stopSpeaking()
        stopAudioReader()
        loadRecentDocuments()
    }

    fun loadPdf(context: Context, uri: Uri, startPage: Int = 1) {
        initialize(context)

        viewModelScope.launch {
            searchJob?.cancel()
            searchJob = null
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    uri = uri,
                    zoom = 1.0f,
                    currentPage = startPage,
                    showTextSheet = false,
                    extractedText = "",
                    outlineNodes = emptyList(),
                    userBookmarks = emptyList(),
                    showOutlineSheet = false,
                    isOutlineLoading = true,
                    searchState = PdfSearchState(),
                    formState = DocumentFormState()
                )
            }

            // Close existing document
            pdfRenderer?.close()

            val renderer = PdfRendererCore(context.applicationContext, uri)
            val success = renderer.initialize()

            if (success && renderer.pageCount > 0) {
                pdfRenderer = renderer
                val actualPage = startPage.coerceIn(1, renderer.pageCount)
                val fileSize = resolveFileSize(context, uri)
                val filePath = resolveFilePath(context, uri, renderer.fileName)

                // Save to Recents
                val recentDoc = RecentDocument(
                    uriString = uri.toString(),
                    fileName = renderer.fileName,
                    pageCount = renderer.pageCount,
                    lastReadPage = actualPage,
                    lastOpenedTimestamp = System.currentTimeMillis(),
                    fileSizeFormatted = fileSize,
                    filePath = filePath
                )
                recentRepository?.saveRecentDocument(recentDoc)
                loadRecentDocuments()

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        fileName = renderer.fileName,
                        pageCount = renderer.pageCount,
                        currentPage = actualPage,
                        errorMessage = null
                    )
                }

                // Asynchronously extract Table of Contents and load user bookmarks
                loadOutlineAndBookmarks(context, uri)

                // Asynchronously detect and parse interactive AcroForm fields
                loadFormFields(context, uri)
            } else {
                renderer.close()
                pdfRenderer = null
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Unable to parse or open PDF document."
                    )
                }
            }
        }
    }

    suspend fun getPageDimensions(pageIndex: Int): PageDimensions? {
        return pdfRenderer?.getPageDimensions(pageIndex)
    }

    suspend fun renderPage(pageIndex: Int, targetWidth: Int, targetHeight: Int): Bitmap? {
        return pdfRenderer?.renderPage(pageIndex, targetWidth, targetHeight)
    }

    fun updateCurrentPage(page: Int) {
        val total = _uiState.value.pageCount
        if (total > 0 && page in 1..total) {
            _uiState.update { it.copy(currentPage = page) }
            val uriStr = _uiState.value.uri?.toString()
            if (uriStr != null) {
                recentRepository?.updateLastReadPage(uriStr, page)
            }
        }
    }

    fun toggleTheme() {
        _uiState.update { it.copy(isDarkMode = !it.isDarkMode) }
    }

    fun setDarkMode(enabled: Boolean) {
        _uiState.update { it.copy(isDarkMode = enabled) }
    }

    fun setZoom(zoom: Float) {
        _uiState.update { it.copy(zoom = zoom.coerceIn(1.0f, 4.0f)) }
    }

    fun resetZoom() {
        _uiState.update { it.copy(zoom = 1.0f) }
    }

    fun showJumpDialog(show: Boolean) {
        _uiState.update { it.copy(showJumpDialog = show) }
    }

    fun toggleControls() {
        _uiState.update { it.copy(showControls = !it.showControls) }
    }

    fun openTextSelection(context: Context, pageIndex: Int? = null) {
        val targetPage = pageIndex ?: (_uiState.value.currentPage - 1).coerceAtLeast(0)
        val uri = _uiState.value.uri ?: return

        _uiState.update {
            it.copy(
                showTextSheet = true,
                textSheetPage = targetPage + 1,
                isExtractingText = true,
                extractedText = "",
                isEntireDocumentMode = false
            )
        }

        viewModelScope.launch {
            val text = PdfTextExtractor.extractPageText(context.applicationContext, uri, targetPage)
            _uiState.update {
                it.copy(
                    isExtractingText = false,
                    extractedText = text
                )
            }
        }
    }

    fun toggleTextScope(context: Context) {
        val uri = _uiState.value.uri ?: return
        val currentEntireMode = _uiState.value.isEntireDocumentMode
        val newEntireMode = !currentEntireMode

        _uiState.update {
            it.copy(
                isEntireDocumentMode = newEntireMode,
                isExtractingText = true,
                extractedText = ""
            )
        }

        viewModelScope.launch {
            val text = if (newEntireMode) {
                PdfTextExtractor.extractAllText(context.applicationContext, uri)
            } else {
                val targetPage = (_uiState.value.textSheetPage - 1).coerceAtLeast(0)
                PdfTextExtractor.extractPageText(context.applicationContext, uri, targetPage)
            }
            _uiState.update {
                it.copy(
                    isExtractingText = false,
                    extractedText = text
                )
            }
        }
    }

    fun closeTextSelection() {
        _uiState.update { it.copy(showTextSheet = false) }
    }

    fun openPageActions(pageIndex: Int) {
        _uiState.update {
            it.copy(
                showPageActionsSheet = true,
                pageActionsTargetIndex = pageIndex
            )
        }
    }

    fun closePageActions() {
        _uiState.update { it.copy(showPageActionsSheet = false) }
    }

    fun openTextSelectionForTargetPage(context: Context) {
        val targetIndex = _uiState.value.pageActionsTargetIndex
        startOnScreenTextSelection(context, targetIndex)
    }

    fun startOnScreenTextSelection(context: Context, pageIndex: Int) {
        closePageActions()
        val uri = _uiState.value.uri ?: return

        _uiState.update {
            it.copy(
                onScreenSelectPageIndex = pageIndex,
                onScreenSelectText = "",
                onScreenSpatialData = null,
                selectedWordIndices = emptySet(),
                selectedWordText = "",
                isExtractingOnScreenText = true,
                showTextSheet = false
            )
        }

        viewModelScope.launch {
            val spatialData = SpatialTextExtractor.extractSpatialText(context.applicationContext, uri, pageIndex)
            val fallbackText = if (spatialData.fullText.isNotBlank()) spatialData.fullText else PdfTextExtractor.extractPageText(context.applicationContext, uri, pageIndex)
            _uiState.update {
                it.copy(
                    isExtractingOnScreenText = false,
                    onScreenSelectText = fallbackText,
                    onScreenSpatialData = spatialData,
                    selectedWordIndices = emptySet(),
                    selectedWordText = ""
                )
            }
        }
    }

    fun selectWord(index: Int) {
        val spatial = _uiState.value.onScreenSpatialData ?: return
        if (index !in spatial.words.indices) return
        val newSet = setOf(index)
        val word = spatial.words[index]
        _uiState.update {
            it.copy(
                selectedWordIndices = newSet,
                selectedWordText = word.text
            )
        }
    }

    fun selectWordRange(startIndex: Int, endIndex: Int) {
        val spatial = _uiState.value.onScreenSpatialData ?: return
        if (spatial.words.isEmpty()) return
        val low = minOf(startIndex, endIndex).coerceIn(0, spatial.words.lastIndex)
        val high = maxOf(startIndex, endIndex).coerceIn(0, spatial.words.lastIndex)
        val range = (low..high).toSet()
        val text = spatial.words.filter { it.index in range }.joinToString(" ") { it.text }
        _uiState.update {
            it.copy(
                selectedWordIndices = range,
                selectedWordText = text
            )
        }
    }

    fun selectAllWords() {
        val spatial = _uiState.value.onScreenSpatialData ?: return
        val allIndices = spatial.words.indices.toSet()
        _uiState.update {
            it.copy(
                selectedWordIndices = allIndices,
                selectedWordText = spatial.fullText
            )
        }
    }

    fun clearWordSelection() {
        stopSpeaking()
        _uiState.update {
            it.copy(
                selectedWordIndices = emptySet(),
                selectedWordText = ""
            )
        }
    }

    fun speakText(context: Context, text: String) {
        if (text.isBlank()) return
        val manager = ttsManager ?: TextToSpeechManager(context.applicationContext).also { newManager ->
            ttsManager = newManager
            viewModelScope.launch {
                newManager.isSpeaking.collect { speaking ->
                    _uiState.update { it.copy(isTtsSpeaking = speaking) }
                }
            }
        }
        manager.speak(text)
    }

    fun stopSpeaking() {
        ttsManager?.stop()
        _uiState.update { it.copy(isTtsSpeaking = false) }
    }

    fun exitOnScreenTextSelection() {
        stopSpeaking()
        _uiState.update {
            it.copy(
                onScreenSelectPageIndex = null,
                onScreenSelectText = "",
                onScreenSpatialData = null,
                selectedWordIndices = emptySet(),
                selectedWordText = "",
                isExtractingOnScreenText = false,
                isTtsSpeaking = false
            )
        }
    }

    fun prepareAddBlankPage(pageIndex: Int) {
        closePageActions()
        _uiState.update {
            it.copy(
                showSaveChoiceDialog = true,
                pendingPageAction = PendingPageAction(
                    type = PageModificationType.ADD_BLANK_PAGE,
                    targetPageIndex = pageIndex
                )
            )
        }
    }

    fun prepareInsertPdf(context: Context, insertUri: Uri, targetPageIndex: Int) {
        closePageActions()
        val insertFileName = resolveFileName(context, insertUri)
        _uiState.update {
            it.copy(
                showSaveChoiceDialog = true,
                pendingPageAction = PendingPageAction(
                    type = PageModificationType.INSERT_PDF_DOCUMENT,
                    targetPageIndex = targetPageIndex,
                    insertUri = insertUri,
                    insertFileName = insertFileName
                )
            )
        }
    }

    fun requestDeletePage() {
        closePageActions()
        _uiState.update { it.copy(showDeleteConfirmDialog = true) }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(showDeleteConfirmDialog = false) }
    }

    fun confirmDeleteRequest() {
        val pageIndex = _uiState.value.pageActionsTargetIndex
        val totalPages = _uiState.value.pageCount
        if (totalPages <= 1) {
            dismissDeleteDialog()
            return
        }
        dismissDeleteDialog()
        _uiState.update {
            it.copy(
                showSaveChoiceDialog = true,
                pendingPageAction = PendingPageAction(
                    type = PageModificationType.DELETE_PAGE,
                    targetPageIndex = pageIndex
                )
            )
        }
    }

    fun requestExtractPage() {
        closePageActions()
        _uiState.update { it.copy(showExtractConfirmDialog = true) }
    }

    fun dismissExtractDialog() {
        _uiState.update { it.copy(showExtractConfirmDialog = false) }
    }

    fun confirmExtractRequest() {
        val pageIndex = _uiState.value.pageActionsTargetIndex
        val totalPages = _uiState.value.pageCount
        if (totalPages <= 1) {
            dismissExtractDialog()
            return
        }
        dismissExtractDialog()
        _uiState.update {
            it.copy(
                showSaveChoiceDialog = true,
                pendingPageAction = PendingPageAction(
                    type = PageModificationType.EXTRACT_PAGE,
                    targetPageIndex = pageIndex
                )
            )
        }
    }

    fun cancelPendingAction() {
        _uiState.update {
            it.copy(
                showSaveChoiceDialog = false,
                pendingPageAction = null
            )
        }
    }

    fun executePendingActionOverwrite(context: Context) {
        val uri = _uiState.value.uri ?: return
        val pending = _uiState.value.pendingPageAction ?: return
        cancelPendingAction()
        executeModification(context, sourceUri = uri, destinationUri = uri, pending = pending)
    }

    fun executePendingActionSaveCopy(context: Context, destinationUri: Uri) {
        val sourceUri = _uiState.value.uri ?: return
        val pending = _uiState.value.pendingPageAction ?: return
        cancelPendingAction()
        executeModification(context, sourceUri = sourceUri, destinationUri = destinationUri, pending = pending)
    }

    private fun executeModification(
        context: Context,
        sourceUri: Uri,
        destinationUri: Uri,
        pending: PendingPageAction
    ) {
        val isCopy = sourceUri != destinationUri
        _uiState.update { it.copy(isModifyingDocument = true) }

        viewModelScope.launch {
            // Must close active renderer to release file lock
            pdfRenderer?.close()
            pdfRenderer = null

            val success = when (pending.type) {
                PageModificationType.ADD_BLANK_PAGE -> {
                    PdfDocumentModifier.addBlankPage(
                        context = context.applicationContext,
                        sourceUri = sourceUri,
                        destinationUri = destinationUri,
                        afterPageIndex = pending.targetPageIndex
                    )
                }
                PageModificationType.DELETE_PAGE -> {
                    PdfDocumentModifier.deletePage(
                        context = context.applicationContext,
                        sourceUri = sourceUri,
                        destinationUri = destinationUri,
                        pageIndex = pending.targetPageIndex
                    )
                }
                PageModificationType.INSERT_PDF_DOCUMENT -> {
                    val insertUri = pending.insertUri ?: return@launch
                    PdfDocumentModifier.insertPdfDocument(
                        context = context.applicationContext,
                        sourceUri = sourceUri,
                        insertUri = insertUri,
                        destinationUri = destinationUri,
                        afterPageIndex = pending.targetPageIndex
                    )
                }
                PageModificationType.EXTRACT_PAGE -> {
                    PdfDocumentModifier.extractPage(
                        context = context.applicationContext,
                        sourceUri = sourceUri,
                        destinationUri = destinationUri,
                        pageIndex = pending.targetPageIndex
                    )
                }
            }

            _uiState.update { it.copy(isModifyingDocument = false) }

            if (success) {
                val msg = when (pending.type) {
                    PageModificationType.ADD_BLANK_PAGE ->
                        if (isCopy) "Page added & saved as new copy" else "Page added after Page ${pending.targetPageIndex + 1}"
                    PageModificationType.DELETE_PAGE ->
                        if (isCopy) "Page deleted & saved as new copy" else "Page ${pending.targetPageIndex + 1} deleted"
                    PageModificationType.INSERT_PDF_DOCUMENT ->
                        if (isCopy) "PDF inserted & saved as new copy" else "Pages inserted after Page ${pending.targetPageIndex + 1}"
                    PageModificationType.EXTRACT_PAGE ->
                        if (isCopy) "Page ${pending.targetPageIndex + 1} extracted & saved as new copy" else "Page ${pending.targetPageIndex + 1} extracted (other pages removed)"
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

                val targetPage = when (pending.type) {
                    PageModificationType.ADD_BLANK_PAGE,
                    PageModificationType.INSERT_PDF_DOCUMENT -> pending.targetPageIndex + 2
                    PageModificationType.DELETE_PAGE -> {
                        val prevTotal = _uiState.value.pageCount
                        (pending.targetPageIndex + 1).coerceAtMost(prevTotal - 1).coerceAtLeast(1)
                    }
                    PageModificationType.EXTRACT_PAGE -> 1
                }
                loadPdf(context, destinationUri, startPage = targetPage)
            } else {
                val errorMsg = if (!isCopy) {
                    "Unable to overwrite original file (may be read-only). Please try 'Save As Copy'."
                } else {
                    "Failed to save modified PDF document."
                }
                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                loadPdf(context, sourceUri, startPage = pending.targetPageIndex + 1)
            }
        }
    }

    fun getSuggestedFileName(): String {
        val fileName = _uiState.value.fileName
        val baseName = if (fileName.contains('.')) {
            fileName.substringBeforeLast('.')
        } else {
            fileName.ifBlank { "document" }
        }
        val pending = _uiState.value.pendingPageAction
        return if (pending?.type == PageModificationType.EXTRACT_PAGE) {
            "${baseName}_page_${pending.targetPageIndex + 1}.pdf"
        } else {
            "${baseName}_edited.pdf"
        }
    }

    fun getPendingActionTitle(): String {
        val pending = _uiState.value.pendingPageAction ?: return "modify document"
        return when (pending.type) {
            PageModificationType.ADD_BLANK_PAGE -> "Add blank page after Page ${pending.targetPageIndex + 1}"
            PageModificationType.DELETE_PAGE -> "Delete Page ${pending.targetPageIndex + 1}"
            PageModificationType.INSERT_PDF_DOCUMENT -> {
                val name = pending.insertFileName ?: "PDF document"
                "Insert \"$name\" after Page ${pending.targetPageIndex + 1}"
            }
            PageModificationType.EXTRACT_PAGE -> "Extract Page ${pending.targetPageIndex + 1} (keep only this page)"
        }
    }

    private fun resolveFileName(context: Context, uri: Uri): String {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        } catch (ignored: Exception) {}
        return "Selected PDF"
    }

    fun clearRecentHistory() {
        recentRepository?.clearRecents()
        loadRecentDocuments()
    }

    fun removeRecentDocument(doc: RecentDocument) {
        recentRepository?.removeRecentDocument(doc.uriString)
        loadRecentDocuments()
    }

    fun copyToClipboard(context: Context, text: String, label: String = "PDF Text") {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun printDocument(context: Context) {
        val uri = _uiState.value.uri ?: return
        val docName = _uiState.value.fileName.ifBlank { "Document" }
        val pageCount = _uiState.value.pageCount
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val adapter = PdfPrintAdapter(context.applicationContext, uri, docName, pageCount)
        printManager.print("xPDF_$docName", adapter, PrintAttributes.Builder().build())
    }

    private fun resolveFileSize(context: Context, uri: Uri): String {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        val sizeBytes = cursor.getLong(sizeIndex)
                        if (sizeBytes > 0) {
                            val mb = sizeBytes / (1024f * 1024f)
                            return String.format("%.1f MB", mb)
                        }
                    }
                }
            }
        } catch (ignored: Exception) {}
        return ""
    }

    private fun resolveFilePath(context: Context, uri: Uri, fallbackName: String): String {
        try {
            if (uri.scheme == "file") {
                return uri.path ?: ""
            }

            val authority = uri.authority ?: ""

            // ExternalStorageProvider: primary:Download/...
            if (authority == "com.android.externalstorage.documents") {
                val docId = android.provider.DocumentsContract.getDocumentId(uri)
                val split = docId.split(":")
                if (split.size >= 2) {
                    val type = split[0]
                    val relativePath = split[1]
                    return if ("primary".equals(type, ignoreCase = true)) {
                        "/storage/emulated/0/$relativePath"
                    } else {
                        "/storage/$type/$relativePath"
                    }
                }
            }

            // DownloadsProvider
            if (authority == "com.android.providers.downloads.documents") {
                try {
                    val docId = android.provider.DocumentsContract.getDocumentId(uri)
                    if (docId.startsWith("raw:")) {
                        return docId.substringAfter("raw:")
                    }
                } catch (ignored: Exception) {}
                if (fallbackName.isNotBlank()) {
                    return "/storage/emulated/0/Download/$fallbackName"
                }
            }

            // MediaStore query for DATA and RELATIVE_PATH
            val projection = arrayOf(
                android.provider.MediaStore.MediaColumns.DATA,
                android.provider.MediaStore.MediaColumns.RELATIVE_PATH
            )
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val dataIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)
                    if (dataIndex != -1) {
                        val data = cursor.getString(dataIndex)
                        if (!data.isNullOrBlank() && (data.startsWith("/") || data.contains("/"))) {
                            return data
                        }
                    }

                    val relIndex = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.RELATIVE_PATH)
                    if (relIndex != -1) {
                        val rel = cursor.getString(relIndex)
                        if (!rel.isNullOrBlank()) {
                            return "/storage/emulated/0/$rel$fallbackName"
                        }
                    }
                }
            }
        } catch (ignored: Exception) {}

        return RecentDocument.resolveDisplayPath(uri, fallbackName)
    }

    // region Outline & Bookmarks

    fun loadOutlineAndBookmarks(context: Context, uri: Uri) {
        val uriString = uri.toString()
        val cachedBookmarks = recentRepository?.getUserBookmarks(uriString) ?: emptyList()
        _uiState.update {
            it.copy(
                userBookmarks = cachedBookmarks,
                isOutlineLoading = true
            )
        }

        viewModelScope.launch {
            val nodes = PdfOutlineExtractor.extractOutline(context, uri)
            _uiState.update {
                it.copy(
                    outlineNodes = nodes,
                    isOutlineLoading = false
                )
            }
        }
    }

    fun openOutlineSheet(tab: OutlineTab = OutlineTab.CONTENTS) {
        _uiState.update {
            it.copy(
                showOutlineSheet = true,
                selectedOutlineTab = tab
            )
        }
    }

    fun closeOutlineSheet() {
        _uiState.update {
            it.copy(showOutlineSheet = false)
        }
    }

    fun selectOutlineTab(tab: OutlineTab) {
        _uiState.update {
            it.copy(selectedOutlineTab = tab)
        }
    }

    fun toggleCurrentPageBookmark() {
        val state = _uiState.value
        val activeIndex = state.currentPage - 1
        val existingBookmark = state.userBookmarks.firstOrNull { it.pageIndex == activeIndex }

        if (existingBookmark != null) {
            // Open dialog in edit/delete mode for existing bookmark
            _uiState.update {
                it.copy(
                    addBookmarkDialogState = AddBookmarkDialogState(
                        isOpen = true,
                        targetPageIndex = activeIndex,
                        initialTitle = existingBookmark.title,
                        initialNote = existingBookmark.note,
                        existingBookmarkId = existingBookmark.id
                    )
                )
            }
        } else {
            // Open dialog to add new bookmark
            _uiState.update {
                it.copy(
                    addBookmarkDialogState = AddBookmarkDialogState(
                        isOpen = true,
                        targetPageIndex = activeIndex,
                        initialTitle = "Page ${state.currentPage}",
                        initialNote = "",
                        existingBookmarkId = null
                    )
                )
            }
        }
    }

    fun openAddBookmarkDialog(pageIndex: Int) {
        val state = _uiState.value
        val existingBookmark = state.userBookmarks.firstOrNull { it.pageIndex == pageIndex }

        _uiState.update {
            it.copy(
                addBookmarkDialogState = AddBookmarkDialogState(
                    isOpen = true,
                    targetPageIndex = pageIndex,
                    initialTitle = existingBookmark?.title ?: "Page ${pageIndex + 1}",
                    initialNote = existingBookmark?.note ?: "",
                    existingBookmarkId = existingBookmark?.id
                )
            )
        }
    }

    fun closeAddBookmarkDialog() {
        _uiState.update {
            it.copy(addBookmarkDialogState = AddBookmarkDialogState(isOpen = false))
        }
    }

    fun saveBookmark(pageIndex: Int, title: String, note: String, existingId: String? = null) {
        val uri = _uiState.value.uri ?: return
        val uriString = uri.toString()
        val bookmark = UserBookmark(
            id = existingId ?: UUID.randomUUID().toString(),
            uriString = uriString,
            pageIndex = pageIndex,
            title = title.ifBlank { "Page ${pageIndex + 1}" },
            note = note.trim()
        )
        recentRepository?.saveBookmark(bookmark)
        val updated = recentRepository?.getUserBookmarks(uriString) ?: emptyList()
        _uiState.update {
            it.copy(
                userBookmarks = updated,
                addBookmarkDialogState = AddBookmarkDialogState(isOpen = false)
            )
        }
    }

    fun deleteBookmark(bookmarkId: String) {
        val uri = _uiState.value.uri ?: return
        val uriString = uri.toString()
        recentRepository?.deleteBookmark(bookmarkId)
        val updated = recentRepository?.getUserBookmarks(uriString) ?: emptyList()
        _uiState.update {
            it.copy(
                userBookmarks = updated,
                addBookmarkDialogState = AddBookmarkDialogState(isOpen = false)
            )
        }
    }

    fun jumpToOutlinePage(pageIndex: Int) {
        val pageCount = _uiState.value.pageCount
        if (pageIndex in 0 until pageCount) {
            updateCurrentPage(pageIndex + 1)
        }
        closeOutlineSheet()
    }

    // endregion

    // region In-Document Full-Text Search

    fun openSearch() {
        _uiState.update {
            it.copy(
                searchState = it.searchState.copy(
                    isSearchActive = true
                )
            )
        }
    }

    fun closeSearch() {
        searchJob?.cancel()
        searchJob = null
        _uiState.update {
            it.copy(
                searchState = PdfSearchState(isSearchActive = false)
            )
        }
    }

    fun updateSearchQuery(context: Context, query: String) {
        val uri = _uiState.value.uri ?: return
        val pageCount = _uiState.value.pageCount
        if (pageCount <= 0) return

        searchJob?.cancel()

        _uiState.update {
            it.copy(
                searchState = it.searchState.copy(
                    query = query,
                    isSearching = query.isNotBlank(),
                    matches = if (query.isBlank()) emptyList() else it.searchState.matches,
                    currentMatchIndex = if (query.isBlank()) -1 else it.searchState.currentMatchIndex
                )
            )
        }

        if (query.isBlank()) {
            return
        }

        searchJob = viewModelScope.launch {
            delay(300) // Debounce rapid typing
            PdfSearchEngine.searchDocument(
                context = context,
                uri = uri,
                pageCount = pageCount,
                query = query
            ) { updatedMatches, isComplete ->
                _uiState.update { current ->
                    val newCurrentIndex = if (updatedMatches.isEmpty()) {
                        -1
                    } else if (current.searchState.currentMatchIndex in updatedMatches.indices) {
                        current.searchState.currentMatchIndex
                    } else {
                        0
                    }

                    current.copy(
                        searchState = current.searchState.copy(
                            isSearching = !isComplete,
                            matches = updatedMatches,
                            currentMatchIndex = newCurrentIndex
                        )
                    )
                }

                // If a match is active, sync page
                val targetMatch = _uiState.value.searchState.currentMatch
                if (targetMatch != null) {
                    val targetPage = targetMatch.displayPage
                    if (_uiState.value.currentPage != targetPage) {
                        updateCurrentPage(targetPage)
                    }
                }
            }
        }
    }

    fun nextSearchMatch() {
        val state = _uiState.value.searchState
        if (!state.hasMatches) return

        val nextIndex = (state.currentMatchIndex + 1) % state.totalMatches
        _uiState.update {
            it.copy(
                searchState = it.searchState.copy(currentMatchIndex = nextIndex)
            )
        }
        val targetMatch = _uiState.value.searchState.currentMatch
        if (targetMatch != null) {
            updateCurrentPage(targetMatch.displayPage)
        }
    }

    fun previousSearchMatch() {
        val state = _uiState.value.searchState
        if (!state.hasMatches) return

        val prevIndex = if (state.currentMatchIndex - 1 < 0) {
            state.totalMatches - 1
        } else {
            state.currentMatchIndex - 1
        }
        _uiState.update {
            it.copy(
                searchState = it.searchState.copy(currentMatchIndex = prevIndex)
            )
        }
        val targetMatch = _uiState.value.searchState.currentMatch
        if (targetMatch != null) {
            updateCurrentPage(targetMatch.displayPage)
        }
    }

    // endregion

    // region Reading Mode & Eye Comfort

    fun openReadingModeSheet() {
        _uiState.update { it.copy(showReadingModeSheet = true) }
    }

    fun closeReadingModeSheet() {
        _uiState.update { it.copy(showReadingModeSheet = false) }
    }

    fun selectReadingMode(mode: ReadingMode) {
        recentRepository?.saveReadingMode(mode)
        _uiState.update {
            it.copy(
                readingMode = mode,
                showReadingModeSheet = false
            )
        }
    }

    // endregion

    // region Interactive Form Fields (AcroForm)

    fun loadFormFields(context: Context, uri: Uri) {
        viewModelScope.launch {
            val fields = PdfFormManager.extractFormFields(context, uri)
            _uiState.update {
                it.copy(
                    formState = DocumentFormState(
                        hasAcroForm = fields.isNotEmpty(),
                        isFormEditable = true,
                        fields = fields
                    )
                )
            }
        }
    }

    fun updateFormFieldValue(fieldId: String, newValue: String) {
        _uiState.update { state ->
            val updatedFields = state.formState.fields.map { field ->
                if (field.id == fieldId) field.copy(currentValue = newValue) else field
            }
            state.copy(
                formState = state.formState.copy(
                    fields = updatedFields,
                    hasUnsavedChanges = true
                )
            )
        }
    }

    fun updateCheckboxField(fieldId: String, isChecked: Boolean) {
        _uiState.update { state ->
            val updatedFields = state.formState.fields.map { field ->
                if (field.id == fieldId) field.copy(isChecked = isChecked, currentValue = if (isChecked) "Yes" else "No") else field
            }
            state.copy(
                formState = state.formState.copy(
                    fields = updatedFields,
                    hasUnsavedChanges = true
                )
            )
        }
    }

    fun clearAllFormFields() {
        _uiState.update { state ->
            val resetFields = state.formState.fields.map { field ->
                field.copy(currentValue = "", isChecked = false)
            }
            state.copy(
                formState = state.formState.copy(
                    fields = resetFields,
                    hasUnsavedChanges = true
                )
            )
        }
    }

    fun setFormSheetVisible(visible: Boolean) {
        _uiState.update { it.copy(formState = it.formState.copy(showFormSheet = visible)) }
    }

    fun setFormSaveChoiceDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(formState = it.formState.copy(showSaveChoiceDialog = visible)) }
    }

    fun onFormFieldClicked(field: FormFieldItem) {
        _uiState.update { it.copy(formState = it.formState.copy(editingField = field, activeFieldId = field.id)) }
    }

    fun dismissEditingField() {
        _uiState.update { it.copy(formState = it.formState.copy(editingField = null)) }
    }

    fun saveFormToDestination(
        context: Context,
        destinationUri: Uri,
        flatten: Boolean,
        onComplete: () -> Unit = {}
    ) {
        val srcUri = _uiState.value.uri ?: return
        val fields = _uiState.value.formState.fields
        viewModelScope.launch {
            _uiState.update { it.copy(formState = it.formState.copy(isSaving = true)) }
            val success = PdfFormManager.saveFormDocument(context, srcUri, destinationUri, fields, flatten)
            _uiState.update {
                it.copy(
                    formState = it.formState.copy(
                        isSaving = false,
                        hasUnsavedChanges = if (success) false else it.formState.hasUnsavedChanges
                    )
                )
            }
            if (success) {
                onComplete()
            }
        }
    }

    // endregion

    // region Continuous Audio Reader (Audiobook Mode)

    private fun getOrCreateAudioReader(context: Context): ContinuousAudioReader {
        return continuousAudioReader ?: ContinuousAudioReader(context.applicationContext, viewModelScope).also { reader ->
            continuousAudioReader = reader
            reader.onSentenceChanged = { pageIndex, sentence, totalSentences ->
                _uiState.update { state ->
                    state.copy(
                        audioReaderState = state.audioReaderState.copy(
                            currentPageIndex = pageIndex,
                            currentSentenceIndex = sentence.index,
                            currentSentenceText = sentence.text,
                            activeSentenceWordBounds = sentence.wordBounds,
                            totalSentencesOnPage = totalSentences
                        )
                    )
                }
            }
            reader.onPlaybackStatusChanged = { status ->
                _uiState.update { state ->
                    state.copy(
                        audioReaderState = state.audioReaderState.copy(status = status)
                    )
                }
            }
            reader.onPageCompleted = { completedPageIndex ->
                val nextPageIndex = completedPageIndex + 1
                if (nextPageIndex < _uiState.value.pageCount) {
                    loadAndReadPage(context, nextPageIndex)
                } else {
                    stopAudioReader()
                }
            }
            reader.onSleepTimerTick = { seconds ->
                _uiState.update { state ->
                    state.copy(
                        audioReaderState = state.audioReaderState.copy(sleepTimerRemainingSeconds = seconds)
                    )
                }
            }
            reader.onSleepTimerExpired = {
                stopAudioReader()
            }
        }
    }

    fun toggleAudioReader(context: Context) {
        val currentState = _uiState.value.audioReaderState
        if (currentState.isActive) {
            stopAudioReader()
        } else {
            val startPage = (_uiState.value.currentPage - 1).coerceAtLeast(0)
            _uiState.update {
                it.copy(
                    audioReaderState = AudioReaderUiState(
                        isActive = true,
                        currentPageIndex = startPage,
                        status = AudioPlaybackStatus.BUFFERING
                    )
                )
            }
            loadAndReadPage(context, startPage)
        }
    }

    private fun loadAndReadPage(context: Context, pageIndex: Int, startSentenceIndex: Int = 0) {
        val docUri = _uiState.value.uri ?: return
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(
                    currentPage = pageIndex + 1,
                    audioReaderState = state.audioReaderState.copy(
                        currentPageIndex = pageIndex,
                        status = AudioPlaybackStatus.BUFFERING,
                        activeSentenceWordBounds = emptyList()
                    )
                )
            }

            val spatialData = SpatialTextExtractor.extractSpatialText(context, docUri, pageIndex)
            val reader = getOrCreateAudioReader(context)
            val skipHeaders = _uiState.value.audioReaderState.skipHeaderFooter
            val pageContent = reader.segmentIntoSentences(pageIndex, spatialData.words, skipHeaders)

            reader.loadAndPlayPage(pageContent, startSentenceIndex)
        }
    }

    fun toggleSkipHeaderFooter(context: Context) {
        val newSkip = !_uiState.value.audioReaderState.skipHeaderFooter
        _uiState.update { state ->
            state.copy(
                audioReaderState = state.audioReaderState.copy(skipHeaderFooter = newSkip)
            )
        }
        continuousAudioReader?.skipHeaderFooter = newSkip
        if (_uiState.value.audioReaderState.isActive) {
            loadAndReadPage(context, _uiState.value.audioReaderState.currentPageIndex)
        }
    }

    fun toggleAudioPlayPause(context: Context) {
        val reader = getOrCreateAudioReader(context)
        val status = _uiState.value.audioReaderState.status
        if (status == AudioPlaybackStatus.PLAYING) {
            reader.pause()
        } else if (status == AudioPlaybackStatus.PAUSED) {
            reader.resume()
        } else {
            loadAndReadPage(context, _uiState.value.audioReaderState.currentPageIndex)
        }
    }

    fun skipAudioNext(context: Context) {
        val reader = getOrCreateAudioReader(context)
        reader.skipNext()
    }

    fun skipAudioPrevious(context: Context) {
        val reader = getOrCreateAudioReader(context)
        reader.skipPrevious()
    }

    fun setAudioSpeed(speed: AudioSpeed) {
        continuousAudioReader?.setSpeed(speed)
        _uiState.update { state ->
            state.copy(audioReaderState = state.audioReaderState.copy(speed = speed))
        }
    }

    fun setAudioSleepTimer(preset: SleepTimerPreset) {
        continuousAudioReader?.setSleepTimer(preset)
        _uiState.update { state ->
            state.copy(audioReaderState = state.audioReaderState.copy(sleepTimer = preset))
        }
    }

    fun stopAudioReader() {
        continuousAudioReader?.stop()
        _uiState.update { state ->
            state.copy(
                audioReaderState = AudioReaderUiState(isActive = false)
            )
        }
    }

    // endregion

    override fun onCleared() {
        super.onCleared()
        searchJob?.cancel()
        searchJob = null
        pdfRenderer?.close()
        pdfRenderer = null
        ttsManager?.shutdown()
        ttsManager = null
        continuousAudioReader?.release()
        continuousAudioReader = null
    }
}
