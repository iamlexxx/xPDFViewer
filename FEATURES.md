# xPDF Viewer — Features & Architecture Documentation

A modern, high-performance, edge-to-edge Android PDF reader and utility suite built with Jetpack Compose, PdfBox-Android, and Google ML Kit.

---

## Table of Contents
1. [Core PDF Reader](#1-core-pdf-reader)
2. [Multi-Function Long-Press Menu](#2-multi-function-long-press-menu)
3. [Interactive Text Selection](#3-interactive-text-selection)
4. [Page Management & Insertion](#4-page-management--page-insertion)
5. [Save Mode System ("Overwrite" vs "Save As Copy")](#5-save-mode-system-overwrite-vs-save-as-copy)
6. [On-Device OCR Scanner](#6-on-device-ocr-scanner)
7. [PDF Merge Utility](#7-pdf-merge-utility)
8. [PDF Compression Utility](#8-pdf-compression-utility)
9. [Create PDF Speed Dial & Document Scanner](#9-create-pdf-speed-dial--document-scanner)
10. [System Architecture & Permissions](#10-system-architecture--permissions)
11. [Feature Matrix & File Map](#11-feature-matrix--file-map)

---

## 1. Core PDF Reader

### Overview
High-fidelity, smooth PDF viewing engine designed around Material 3 and edge-to-edge rendering.

### Capabilities
- **Edge-to-Edge UI**: Integrated with Android 15 / modern Android status bar and navigation bar insets (`statusBarsPadding()`, `navigationBarsPadding()`).
- **Dynamic Theming**: Full Light and Dark Mode support with tailored surface tones (`MaterialTheme.colorScheme`).
- **High-Performance Page Rendering**: Background page rendering using Android's native `android.graphics.pdf.PdfRenderer` and asynchronous Kotlin Coroutines.
- **Pinch-to-Zoom & Pan**: Full gesture support with zoom memory per page.
- **Recents & History Management**: Local tracking of recently opened documents, last-read page offsets, formatted file size, resolved storage paths, and last opened date & time (e.g., `Oct 18, 2026 • 7:15 PM`) displayed with calendar iconography across both "Jump Back In" on the Home tab and "Reading History" on the Recent tab. Supports "Clear All" bulk clearing alongside smooth Material 3 **Swipe to Remove** gestures with animated red trash reveal on individual cards.
- **Print & Share**: Built-in print spooling via Android `PrintManager` and document sharing.

### Key Classes
- `PdfViewerScreen.kt` (`com.xpdfviewer.ui.screens`)
- `PdfRendererCore.kt` (`com.xpdfviewer.pdf`)
- `PdfViewerViewModel.kt` (`com.xpdfviewer.viewmodel`)

---

## 2. Multi-Function Long-Press Menu

### Overview
Replaces standard single-action press-and-hold with an interactive action sheet for context-sensitive document manipulation.

### Actions Available
1. **Select Text**: Activates on-screen text selection mode for the current page.
2. **Add page**: Directly opens the system file picker to select a PDF whose pages will be inserted after the active page.
3. **Extract page**: Isolates the selected page and removes all other pages from the document. Guarded when document only has 1 page. Prompts with confirmation dialog and standard Overwrite vs Save As Copy choice.
4. **Delete page**: Deletes the currently viewed page from the document (guarded to prevent deleting the last remaining page).
5. **Add E-Signature**: Opens the full-screen E-Signature studio directly loaded with the current PDF document and navigated to the selected page, allowing instant signature placement, ink drawing, cursive styling, image stamp import, and password-protected export.

### User Flow
```
Long-press on PDF Page
   │
   ▼
[Action Sheet Bottom Modal]
   ├── "Select Text"     ──► Launches interactive On-Screen Selection
   ├── "Add page"        ──► Launches OpenDocument SAF picker
   ├── "Extract page"    ──► Confirms extraction -> Prompts Save Mode (removes other pages)
   ├── "Delete page"     ──► Confirms deletion -> Prompts Save Mode
   └── "Add E-Signature" ──► Launches animated E-Signature studio on target page
```

### Key Classes
- `PdfViewerScreen.kt`: `PageActionsBottomSheet`, `ExtractPageDialog`, `DeletePageDialog`, `SignPdfScreen` overlay
- `PageActionsBottomSheet.kt` (`com.xpdfviewer.ui.components`)
- `ExtractPageDialog.kt` (`com.xpdfviewer.ui.components`)
- `PdfViewerViewModel.kt` (`com.xpdfviewer.viewmodel`)
- `PdfDocumentModifier.kt` (`com.xpdfviewer.pdf`)
- `SignPdfScreen.kt` (`com.xpdfviewer.ui.screens`), `SignPdfViewModel.kt` (`com.xpdfviewer.viewmodel`)

---

## 3. Interactive Text Selection

### Overview
Extracts and renders text layers on demand directly from the PDF document without cloud processing.

### Capabilities
- **Local Text Extraction**: Uses PdfBox-Android (`PdfTextExtractor.kt`) to extract exact text content from the rendered page.
- **Inset-Aware Top Bar**: Fixed header with `.statusBarsPadding()` and `shadowElevation = 8.dp` so system cutouts, clock, and battery icons never overlap the action bar.
- **Live Search & Copy**: One-tap "Copy All Text" or selective drag selection with Android clipboard integration.

### Key Classes
- `PdfTextExtractor.kt` (`com.xpdfviewer.pdf`)
- `PdfViewerScreen.kt` (`com.xpdfviewer.ui.screens`)

---

## 4. Page Management (Insert, Extract & Delete Pages)

### Overview
In-place document modifications using the PdfBox-Android engine (`com.tom_roush.pdfbox`).

### Capabilities
- **Insert PDF Pages**: Select another PDF document via Storage Access Framework (`ActivityResultContracts.OpenDocument`). Every page from the chosen document is imported and spliced directly after the active page.
- **Extract Active Page**: Extracts the single active page and eliminates all other pages from the document using PdfBox `PDDocument.importPage()`, isolating the selected content with clean dictionary copying. Follows the dual Overwrite / Save As Copy workflow. Disabled on single-page documents.
- **Delete Active Page**: Removes the current page and updates all page indices. Disallowed if the document only has 1 page.
- **Add Blank Page**: Inserts an empty page matching the exact `mediaBox` aspect ratio of adjacent pages.

### Key Classes
- `PdfDocumentModifier.kt` (`com.xpdfviewer.pdf`)
- `PdfViewerViewModel.kt` (`com.xpdfviewer.viewmodel`)
- `ExtractPageDialog.kt` (`com.xpdfviewer.ui.components`)

---

## 5. Save Mode System ("Overwrite" vs "Save As Copy")

### Overview
Whenever a document is modified (adding pages, extracting a page, inserting external PDFs, or deleting pages), a safety dialog is displayed before writing to disk.

### Options
- **Overwrite**:
  - Overwrites the open file directly if write permission is granted.
  - Automatically reloads the refreshed document and updates the recents database.
- **Save As Copy**:
  - Launches `ActivityResultContracts.CreateDocument("application/pdf")`.
  - Prompts the user to pick a folder and name for the new file.
  - Keeps the original document untouched while saving the modified copy.

### Key Classes
- `SaveModeDialog.kt` (`com.xpdfviewer.ui.dialogs`)
- `PdfViewerViewModel.kt`: `executePendingModification()`

---

## 6. On-Device OCR Scanner

### Overview
A standalone optical character recognition tool accessible from the **PDF Utilities** tab. Transforms paper documents and photos into digital, editable text with zero internet required.

### Capabilities
- **100% Offline Machine Learning**: Powered by Google ML Kit Text Recognition (`com.google.mlkit:text-recognition:16.0.1`), packaging on-device neural pipelines (`libmlkit_google_ocr_pipeline.so`).
- **Instant Camera Capture**: Uses `ActivityResultContracts.TakePicturePreview` to snap photos using the default camera app without asking for `CAMERA` runtime permissions.
- **Photo Gallery Picker**: Uses `ActivityResultContracts.GetContent` for picking pre-existing photos from device storage.
- **Live Processing Feedback**: Animated scanning spinner during inference.
- **Preview & Metrics**: Displays a document thumbnail alongside real-time character and word counts.
- **Editable Text Field**: The extracted text is directly editable inside an `OutlinedTextField`.
- **Quick Action Bar**:
  - **Copy Text**: Copies entire extracted text to the Android clipboard.
  - **Share Text**: Launches standard Android text share sheet.
  - **New Scan**: Resets the canvas to scan another document.

### Navigation
- Found under **Tools Tab** -> **OCR Scanner** card (badged **Ready**).

### Key Classes
- `OcrScannerEngine.kt` (`com.xpdfviewer.ocr`)
- `OcrScannerViewModel.kt` (`com.xpdfviewer.viewmodel`)
- `OcrScannerScreen.kt` (`com.xpdfviewer.ui.screens`)

---

## 7. PDF Merge Utility

### Overview
A batch PDF merging tool accessible from the **PDF Utilities** tab. Combines multiple PDF files into a single, cohesive document.

### Capabilities
- **Multi-File Selection**: Uses `ActivityResultContracts.OpenMultipleDocuments` to allow selecting 2, 3, 5, or more PDFs in a single picker session.
- **Incremental File Addition**: "+ Add More" button allows appending extra files at any point.
- **Order Arrangement**: Move Up (▲) and Move Down (▼) buttons on each document card to arrange the sequence before merging.
- **Document Metadata**: Automatically computes each file's page count and human-readable size (e.g., `12 pages • 1.4 MB`).
- **Safety Gate**: Merge button is disabled until at least 2 documents are selected.
- **Custom Filename Prompt**: Tapping "Merge" displays an in-app dialog allowing the user to specify their custom filename before choosing the save destination.
- **Direct Destination Save**: The user picks the target storage location via Storage Access Framework `CreateDocument`, streaming directly to the chosen path without creating unwanted temporary files.
- **Auto-Clear Selected Files**: Once the merge is successfully written to storage, the selected document queue is automatically cleared for subsequent operations.
- **High-Fidelity Merge Engine**: Uses `PDDocument.importPage()` from PdfBox to merge without rasterization or quality loss.
- **Post-Merge Completion Sheet**:
  - Displays the saved document filename and total combined page count.
  - **Open in Viewer**: Immediately launches the merged document in `xPDFViewer`.
  - **Share PDF**: Dispatches the merged PDF via Android's native share sheet.
  - **Done**: Resets the screen ready for any new tasks.

### Navigation
- Found under **Tools Tab** -> **Merge PDFs** card (badged **Ready**).

### Key Classes
- `PdfDocumentModifier.kt` (`com.xpdfviewer.pdf`)
- `MergePdfViewModel.kt` (`com.xpdfviewer.viewmodel`)
- `MergePdfScreen.kt` (`com.xpdfviewer.ui.screens`)

---

## 8. PDF Compression Utility

### Overview
A dedicated offline on-device document compression studio accessible from the **Tools** tab. Reduces heavy PDF file sizes for email transmission, messaging app limits, and storage savings while keeping text, bookmarks, and vector shapes intact.

### Capabilities
- **Three-Tier Compression Levels**:
  - **Extreme Compression**: Aggressive reduction (~65–85% smaller), downscaled to ~1100px max dimension, JPEG quality 0.50.
  - **Recommended Compression**: Optimal balance (~45–65% smaller), downscaled to ~1600px max dimension, JPEG quality 0.72.
  - **Less Compression**: High image fidelity (~25–40% smaller), downscaled to ~2200px max dimension, JPEG quality 0.86.
- **Selective Resource Re-Sampling**: Recursively traverses `PDPage.resources` and nested `PDFormXObject`s, replacing heavy raw/uncompressed `PDImageXObject`s with optimized JPEGs using `JPEGFactory.createFromImage`.
- **Live Before/After Comparison**: Side-by-side file size metrics and prominent percentage savings badge (e.g. `Saved 72% (-3.8 MB)`).
- **Direct SAF Export**: User-defined output filename prompt followed by direct Storage Access Framework writing (`CreateDocument`).
- **Post-Compression Actions**: Immediate "Open in Viewer" and system "Share PDF" options.

### Navigation
- Found under **Tools Tab** -> **Compress** card (badged **Ready**).

### Key Classes
- `PdfCompressor.kt` (`com.xpdfviewer.pdf`)
- `CompressPdfViewModel.kt` (`com.xpdfviewer.viewmodel`)
- `CompressPdfScreen.kt` (`com.xpdfviewer.ui.screens`)

---

## 9. Create PDF Speed Dial & Document Scanner

### Overview
A floating `+` action button anchored in the bottom-right corner above the bottom navigation menu. When pressed, it expands with a spring animation and backdrop scrim to provide rapid document creation workflows:
1. **Scan a Document**: Real-time camera viewfinder with automated document bounding box detection, perspective warp, multi-page capture, and direct PDF generation.
2. **Merge PDF**: Direct bridge to the batch PDF merge flow.

### Capabilities
- **Floating Speed Dial (`CreatePdfSpeedDial.kt`)**:
  - Main `+` FAB rotates 45° into a close `✕` icon using `animateFloatAsState`.
  - Spring-staggered pill sub-buttons with icon chips and high-contrast labels.
  - Semi-transparent backdrop scrim (`Color.Black.copy(alpha = 0.45f)`) with tap-to-dismiss and hardware back-button dismiss (`BackHandler`).
- **In-App Compose Document Scanner & Editor (`CustomDocumentScannerScreen.kt`)**:
  - Full-featured custom in-app scanner granting 100% UI and branding control.
  - **CameraX 3:4 Viewfinder**: Viewfinder aspect ratio calibrated to 3:4 (`AspectRatio.RATIO_4_3`) matching standard document/camera sensor geometry without vertical stretching or distortion.
  - **Auto-Detect Bounding Box & Auto-Crop on Shot**: Real-time on-device ML Kit text and document detection dynamically morphs and tracks document bounds with glowing animated corner reticles, outer scrim dimming, and "Document Detected" status pill. When the user taps the shutter button, the captured high-resolution photo is **automatically cropped** to the detected document bounds with safety margins, isolating the document card from the background.
  - **Re-crop from Full Photo**: Original uncropped photo is retained in `DocumentScanPage.uncroppedBitmap`, allowing the user to toggle between auto-cropped and full-frame source in the Crop tool.
  - **Flip Camera Button Removed**: Dedicated rear document-scanning camera view without unnecessary flip camera button.
  - **Dedicated Responsive Tool Buttons**: High-contrast, horizontal scrollable row (`Modifier.horizontalScroll`) ensuring zero clipping and instantaneous reactive updates via immutable copy state mutation:
    - **Enhance**: Instant contrast boost and paper illumination sharpness.
    - **Filters**: Interactive filter selector (Original, Enhance, Grayscale, B&W, Color).
    - **Crop & Perspective Warp**: Full-screen interactive touch crop studio (`CropDocumentStudioView`) with 4 independent draggable corner handles (Top-Left, Top-Right, Bottom-Right, Bottom-Left). Each corner can be freely dragged anywhere on the screen without affecting the other three corners, allowing precise polygon fitting around skewed, rotated, or angled documents. Upon tapping "Apply Crop", the engine performs high-fidelity **perspective warp rectification** (`setPolyToPoly` homography mapping), stretching and deskewing trapezoidal angles into a flat rectangular document with true 90-degree corners. Features edge midpoint grips, whole-body translation, perspective-interpolated rule-of-thirds grid, and live dimmed difference scrim. Rendered natively in the Activity Window with full `navigationBarsPadding()` so bottom buttons are never cut off.
    - **Rotate**: Instant 90° clockwise page rotation per tap with live preview update.
    - **Clean**: Paper background whitening and shadow/noise suppression.
  - **Labeled "+ Add Page" Button**: Explicitly labeled button `[ + Add Page ]` next to the horizontal page thumbnails tray to capture or import subsequent pages.
  - **"Create" Button**: Prominent bottom action button renamed to **"Create"** for final document compilation.
- **Filename Prompt Before Saving (`ScanFilenamePromptDialog`)**:
  - Immediately following document creation, prompts the user to enter their custom filename (`.pdf`).
  - Completely prevents unwanted automatic file generation.
  - User chooses target storage folder via Storage Access Framework (`CreateDocument`) before bytes are written.
- **Post-Scan Completion Sheet (`ScannedDocumentDialog.kt`)**:
  - Displays document preview with saved filename, total page count, and formatted size.
  - **Open in Viewer**: Immediately launches the scanned document in `PdfViewerScreen` and logs to Recents.
  - **Share PDF**: Dispatches PDF via Android's native share sheet.

### Key Classes
- `CustomDocumentScannerScreen.kt` (`com.xpdfviewer.ui.screens`)
- `DocumentImageProcessor.kt` (`com.xpdfviewer.scanner`)
- `ScannedPdfCreator.kt` (`com.xpdfviewer.scanner`)
- `CreatePdfSpeedDial.kt` (`com.xpdfviewer.ui.components`)
- `ScannedDocumentDialog.kt` (`com.xpdfviewer.ui.dialogs`)
- `MainAppContainer.kt` (`com.xpdfviewer.ui.screens`)

---

## 10. E-Signature & Secure Contract Signing

### Overview
A comprehensive on-device PDF signing and contract execution suite. Allows users to sign contracts, letters, invoices, and legal agreements directly on Android without uploading sensitive documents to cloud services. Includes optional 128-bit AES password encryption for signed exports.

### Capabilities
- **Triple Creation Modes**:
  - **Draw**: Touch and stylus canvas with quadratic bezier stroke smoothing, ink color selector (Black, Navy, Blue), stroke width slider, and undo/clear actions.
  - **Type**: Calligraphic cursive signature generator rendering typed names into flowing italic signatures with flourish baselines.
  - **Image / Open File**: Import physical handwritten signatures or digital stamps from device storage (supports PNG, JPG, JPEG, WEBP). Features built-in luminance-based paper background removal that filters out physical white paper and anti-aliases ink edges into crisp transparent stamps, plus tight bounding-box auto-cropping.
- **Saved Signatures Vault**:
  - Automatically caches signatures locally in private app storage.
  - Quick-reuse carousel allows stamping recurring signatures in 1 tap across any contract.
- **Interactive Drag-and-Resize Overlay**:
  - View full high-resolution PDF pages with page-by-page navigation.
  - Drag signature stamps anywhere on the page.
  - Live corner resize handle adjusts stamp width and height proportionally.
  - Top-right deletion badge removes individual stamps.
- **Streamlined Signature Deck**:
  - Clean, focused "+ Signature" primary action deck.
- **Safe Discard & Clear on Exit**:
  - Pressing Back (via top navigation arrow or system back gesture) prompts a clear confirmation dialog asking if the user wants to discard changes ("Yes" or "No").
  - Confirming "Yes" discards changes, closes `PdfRendererCore`, and clears the open document state completely so re-entering the tool starts fresh.
- **128-Bit AES Password Encryption**:
  - Protects signed PDF contracts with user-specified owner and user passwords via PdfBox's `StandardProtectionPolicy` and `AccessPermission`.
- **Direct Export & Sharing**:
  - Exports cleanly via Storage Access Framework with custom filename prompt.
  - Completion sheet offers instant "Open in Viewer" and native Android "Share PDF" intent.

### Key Classes
- `SignPdfScreen.kt` (`com.xpdfviewer.ui.screens`)
- `SignPdfViewModel.kt` (`com.xpdfviewer.viewmodel`)
- `PdfSignerEngine.kt` (`com.xpdfviewer.signature`)
- `SignatureBitmapHelper.kt` (`com.xpdfviewer.signature`)
- `SignatureStorageManager.kt` (`com.xpdfviewer.signature`)
- `SignaturePadDialog.kt` (`com.xpdfviewer.ui.dialogs`)
- `SignExportDialog.kt` (`com.xpdfviewer.ui.dialogs`)
- `SignSuccessDialog.kt` (`com.xpdfviewer.ui.dialogs`)
- `DiscardChangesDialog.kt` (`com.xpdfviewer.ui.dialogs`)

---

## 10. Unified Tools Discard Architecture (`DiscardChangesDialog.kt`)

### Overview
A redesigned, tactile confirmation dialog applied consistently across all Tools under **PDF Utilities** (Compress, Merge PDFs, OCR Scanner, E-Signature, and In-App Camera Scanner). 

### Key Capabilities
- **Tactile Visual Hierarchy**: 26dp rounded surface card, glowing circular warning badge (`Color(0xFFDC2626)` on soft red background), bold 20sp header, and clean consequence statement.
- **Balanced Dual Actions**: Full-width side-by-side action buttons (min 48dp touch targets):
  - **"No"**: High-contrast outlined action that dismisses the prompt and preserves editing state.
  - **"Yes"**: Solid crimson button with trash/discard iconography that completely clears open files, frees memory descriptors, and navigates back.
- **Coverage**:
  - **E-Signature**: Closes `PdfRendererCore` and resets stamp/signature state.
  - **Compress**: Clears loaded source PDF and target compression configuration.
  - **Merge PDFs**: Clears all documents queued in the merge deck.
  - **OCR Scanner**: Clears loaded images and extracted text buffers.
  - **In-App Camera Scanner**: Clears captured pages and camera bitmaps.

---

## 11. PDF Utilities Motion & Transition Architecture

### Overview
A fluid, native Material 3 motion system providing high-fidelity animated transitions when entering and exiting any tool screen under **PDF Utilities** (OCR Scanner, Merge PDFs, Compress, E-Signature, and in-app Camera Scanner).

### Key Motion Specs & Implementation
- **Animated Full-Screen Overlay Container**:
  - Replaced abrupt early returns with a non-destructive Compose overlay architecture (`AnimatedVisibility`) in `MainAppContainer.kt`.
  - Underlying navigation drawer, scaffold, and tools tab state remain completely composed and intact during and after transitions, preserving scroll positions.
- **Enter Transition (Tool Opening)**:
  - **Vertical Motion**: Slides upward from 28% container height offset (`slideInVertically(initialOffsetY = { (it * 0.28f).toInt() }, tween(350ms, CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)))`).
  - **Opacity**: Fades in from 0f to 1f (`fadeIn(tween(280ms, LinearOutSlowInEasing))`).
  - **Scale**: Expands smoothly from 92% to 100% scale (`scaleIn(initialScale = 0.92f, tween(350ms, CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)))`).
- **Exit Transition (Tool Closing / Discarding)**:
  - **Vertical Motion**: Slides downward by 22% container height offset (`slideOutVertically(targetOffsetY = { (it * 0.22f).toInt() }, tween(240ms, FastOutLinearInEasing))`).
  - **Opacity**: Fades out from 1f to 0f (`fadeOut(tween(200ms, FastOutLinearInEasing))`).
  - **Scale**: Contracts subtly to 94% scale (`scaleOut(targetScale = 0.94f, tween(240ms, FastOutLinearInEasing))`).
- **Exit Animation State Retention**:
  - Employs a cached `displayedToolScreen` state buffer alongside `activeToolScreen` (`ActiveToolScreen.NONE`) to ensure the exiting screen remains rendered and visible as it descends and fades out smoothly.
- **Card Tap Micro-Interactions (`ToolsScreen.kt`)**:
  - Spring-driven press squish (`scale = 0.94f` via `animateFloatAsState` and `graphicsLayer`).
  - Interactive border glow highlighting primary accent when pressed (`BorderStroke(1.dp, primary.copy(alpha = 0.5f))`).

---

## 12. System Architecture & Permissions

### Storage Access Framework (SAF)
All file interactions (opening, creating copies, inserting documents) use Android's Storage Access Framework:
- No legacy `READ_EXTERNAL_STORAGE` or `WRITE_EXTERNAL_STORAGE` permissions required.
- Full compatibility with Android 10, 11, 12, 13, 14, and 15 scoped storage.
- Persistent URI permission grants using `takePersistableUriPermission`.

### Secure FileProvider
- Provider authority: `${applicationId}.provider` (`com.xpdfviewer.provider`).
- Configured in `AndroidManifest.xml` with `res/xml/file_paths.xml`.
- Enables zero-risk file sharing via content URIs, preventing `FileUriExposedException`.

---

## 13. Feature Matrix & File Map

| Feature | Primary UI Location | Core ViewModel / Engine | Dependencies |
|---|---|---|---|
| **PDF Reader** | `PdfViewerScreen.kt` | `PdfViewerViewModel.kt`, `PdfRendererCore.kt` | Native `PdfRenderer` |
| **Long-Press Menu** | `PdfViewerScreen.kt` | `PdfViewerViewModel.kt` | Compose Material 3 |
| **Text Selection** | `PdfViewerScreen.kt` | `PdfTextExtractor.kt` | PdfBox-Android |
| **Insert Pages** | `PdfViewerScreen.kt` | `PdfDocumentModifier.kt` | PdfBox-Android |
| **Extract Page** | `PdfViewerScreen.kt`, `ExtractPageDialog.kt` | `PdfDocumentModifier.kt`, `PdfViewerViewModel.kt` | PdfBox-Android |
| **Delete Page** | `PdfViewerScreen.kt`, `DeletePageDialog.kt` | `PdfDocumentModifier.kt` | PdfBox-Android |
| **E-Signature** | `SignPdfScreen.kt`, `PdfViewerScreen.kt` | `SignPdfViewModel.kt`, `PdfSignerEngine.kt` | PdfBox-Android |
| **Save Mode Dialog** | `SaveModeDialog.kt` | `PdfViewerViewModel.kt` | Compose Dialog |
| **OCR Scanner** | `OcrScannerScreen.kt` | `OcrScannerEngine.kt`, `OcrScannerViewModel.kt` | Google ML Kit (`16.0.1`) |
| **Merge PDFs** | `MergePdfScreen.kt` | `PdfDocumentModifier.kt`, `MergePdfViewModel.kt` | PdfBox-Android |
| **PDF Utilities Transitions** | `MainAppContainer.kt`, `ToolsScreen.kt` | Compose `AnimatedVisibility` | Material 3 Motion |
| **Create PDF Speed Dial** | `CreatePdfSpeedDial.kt`, `MainAppContainer.kt` | `MainAppContainer.kt` | Compose Material 3 Animation |
| **ML Kit Document Scanner** | `ScannedDocumentDialog.kt`, `MainAppContainer.kt` | `DocumentScannerManager.kt` | Google ML Kit Document Scanner (`16.0.0`) |
| **File Provider** | `AndroidManifest.xml` | `res/xml/file_paths.xml` | AndroidX Core |

---
*Documentation generated for xPDFViewer. All features offline, secure, and fully verified.*

