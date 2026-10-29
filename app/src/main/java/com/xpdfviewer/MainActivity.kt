package com.xpdfviewer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.xpdfviewer.ui.screens.MainAppContainer
import com.xpdfviewer.ui.screens.PdfViewerScreen
import com.xpdfviewer.ui.theme.XPDFViewerTheme
import com.xpdfviewer.viewmodel.PdfViewerViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: PdfViewerViewModel by viewModels()

    // Storage Access Framework Document Picker for PDF
    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            takePersistentPermission(it)
            viewModel.loadPdf(this, it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize recents repository and restore reading history
        viewModel.initialize(this)

        // Keep screen on while reading document
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Handle incoming intent if app was opened with a PDF from external app
        handleIntent(intent)

        setContent {
            val uiState by viewModel.uiState.collectAsState()

            XPDFViewerTheme(darkTheme = uiState.isDarkMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (uiState.uri == null) {
                        MainAppContainer(
                            uiState = uiState,
                            viewModel = viewModel,
                            onOpenFile = {
                                openDocumentLauncher.launch(arrayOf("application/pdf"))
                            }
                        )
                    } else {
                        PdfViewerScreen(
                            viewModel = viewModel,
                            onOpenFile = {
                                openDocumentLauncher.launch(arrayOf("application/pdf"))
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val data = intent.data

        if ((Intent.ACTION_VIEW == action || Intent.ACTION_EDIT == action) && data != null) {
            takePersistentPermission(data)
            viewModel.loadPdf(this, data)
        }
    }

    private fun takePersistentPermission(uri: Uri) {
        try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (ignored: Exception) {
            // Not all content URIs support persistable permissions
        }
    }
}
