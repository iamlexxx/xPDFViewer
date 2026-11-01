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
High-fidelity, coordinate-aligned spatial text selection layer operating on demand directly over the rendered PDF page without replacing or obscuring the original visual document layout.

### Capabilities
- **Local Spatial Extraction (`SpatialTextExtractor.kt`)**: Subclasses PdfBox `PDFTextStripper` to extract exact glyph positions and word coordinates (`TextPosition`), calculating normalized bounding rectangles (`[0f..1f]`) for every discrete word on the page.
- **On-Canvas Text Overlay (`PdfTextOverlayCanvas.kt`)**: Transparent touch layer drawn directly above the high-resolution page bitmap, rendering rounded highlight boxes (`Color(0xFF2563EB).copy(alpha = 0.35f)`) and draggable teardrop boundary handles.
- **Natural Touch Gestures**: Tap individual words or drag across multiple lines to select full sentences and paragraphs.
- **Floating Action Toolbar (`FloatingTextToolbar.kt`)**: Ergonomic floating pill menu offering:
  - **Copy**: One-tap copy with haptic feedback.
  - **Share**: System text share sheet dispatcher.
  - **Select All**: Expands selection to cover all words on the active page.
  - **Web Search**: Dispatches search intent directly to the default browser.
  - **Read Aloud (TTS)**: Offline text-to-speech reading powered by `TextToSpeechManager.kt`.
- **Inset-Aware Selection Header**: Top bar displaying live selection metrics (e.g., `42 words selected`), a "Copy All" shortcut, and a "Done" button.
- **Scanned Document Awareness**: Detects pages with zero vector text and alerts users that the page is a scanned document.

### Key Classes
- `SpatialTextExtractor.kt` (`com.xpdfviewer.pdf`)
- `SpatialTextModels.kt` (`com.xpdfviewer.data`)
- `PdfTextOverlayCanvas.kt` (`com.xpdfviewer.ui.components`)
- `FloatingTextToolbar.kt` (`com.xpdfviewer.ui.components`)
- `TextToSpeechManager.kt` (`com.xpdfviewer.speech`)
- `PdfViewerScreen.kt` (`com.xpdfviewer.ui.screens`)
- `PdfPageItem.kt` (`com.xpdfviewer.ui.components`)

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

## 12. Visual Page Organizer & Grid Reordering Studio

### Overview
A full-screen, interactive document management studio accessible from the PDF Viewer's top bar or long-press contextual menu. Provides an adaptive grid of page thumbnail cards to reorder, rotate, delete, extract, or add pages in batch.

### Capabilities
- **Adaptive Thumbnail Grid**: Renders lightweight, aspect-ratio calibrated thumbnail cards via `PdfRendererCore` and background LRU caching across 2 to 4 responsive columns (`GridCells.Adaptive(minSize = 145.dp)`).
- **Sequential Reordering**: Move pages earlier (`◀`) or later (`▶`) with smooth Compose placement animations (`Modifier.animateItem()`).
- **Per-Page 90° Rotation**: Instant hardware-accelerated preview rotation via `graphicsLayer { rotationZ = ... }` and dictionary persistence.
- **Rotate All Pages**: Global one-tap rotation action across all document pages.
- **Multi-Selection Batch Actions**:
  - **Extract (N)**: Exports selected pages into a new standalone PDF via SAF `CreateDocument`.
  - **Delete (N)**: Removes selected pages from the document (safeguarded against deleting all pages).
- **Add Blank Page**: Inserts clean A4 pages directly into the sequence.
- **Batch Reorganization Engine**: `PdfDocumentModifier.applyPageOrganization(...)` reconstructs the document according to the specified page permutation and rotation array, writing directly to destination SAF content URIs.
- **Dual Save & Safe Discard Integration**: Full support for `SaveChoiceDialog` (Overwrite vs Save as Copy) and `DiscardChangesDialog` on back-navigation.

### Key Classes
- `PageOrganizerScreen.kt` (`com.xpdfviewer.ui.screens`)
- `OrganizerPageCard.kt` (`com.xpdfviewer.ui.components`)
- `PageOrganizerViewModel.kt` (`com.xpdfviewer.viewmodel`)
- `PageOrganizerUiState.kt` (`com.xpdfviewer.data`)
- `PdfDocumentModifier.kt` (`com.xpdfviewer.pdf`)

---

## 13. Standalone PDF Lock & Unlock Tool

### Overview
A dedicated offline security utility in the **PDF Utilities** tab enabling users to protect any PDF document with passwords and granular access permissions, or strip password restrictions from documents for which they possess credentials.

### Capabilities
- **Dual Operating Modes**:
  - **Protect Document**: Applies AES encryption with user (viewing) and owner (permission control) passwords.
  - **Unlock Document**: Validates credentials against protected files, strips security dictionaries (`setAllSecurityToBeRemoved(true)`), and saves unencrypted copies.
- **Enterprise-Grade AES Encryption**:
  - Supports 128-bit AES (`StandardProtectionPolicy` length 128) and 256-bit AES (length 256) encryption standard.
- **Granular Access Permissions (`AccessPermission`)**:
  - **Allow Printing**: Controls document rasterization and printing privileges (`setCanPrint`).
  - **Allow Content Extraction / Copy**: Enforces clipboard and text scraping restrictions (`setCanExtractContent`).
  - **Allow Annotations & Form Fill**: Restricts digital markings, signatures, and form completion (`setCanModifyAnnotations`).
  - **Allow Document Modifications**: Controls page restructuring, splitting, and merging (`setCanModify`).
- **Smart Password Verification**:
  - Secure credential validation using PdfBox-Android `PDDocument.load(stream, password)`.
  - Automatic trapping of `com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException` with user-friendly error feedback.
- **SAF Document Picker & Export**:
  - Open file selection using Android's `OpenDocument` contract.
  - Export destination routing via `CreateDocument("application/pdf")`.
- **Completion Workflow (`SecuritySuccessDialog.kt`)**:
  - Post-processing dialog providing one-tap **Open in Viewer** navigation and **Share PDF** via secure `FileProvider`.

### Key Classes
- `PdfSecurityScreen.kt` (`com.xpdfviewer.ui.screens`)
- `PdfSecurityViewModel.kt` (`com.xpdfviewer.viewmodel`)
- `PdfSecurityManager.kt` (`com.xpdfviewer.pdf`)
- `PdfSecurityModels.kt` (`com.xpdfviewer.data`)
- `SecuritySuccessDialog.kt` (`com.xpdfviewer.ui.dialogs`)
- `ToolsScreen.kt` (`com.xpdfviewer.ui.screens`)

---

## 14. Document Outline (Table of Contents) & User Bookmarks

### Overview
A comprehensive document navigation system allowing users to explore the embedded hierarchical Table of Contents (PDF outline) and manage personal page bookmarks with custom titles and annotations.

### Capabilities
- **Native Hierarchical Outline Parsing (`PdfOutlineExtractor.kt`)**:
  - Recursively traverses `PDDocumentCatalog.getDocumentOutline()` using PdfBox-Android.
  - Resolves multi-level chapter titles, nested subsections, and destination pages via `item.findDestinationPage(doc)` and `PDActionGoTo` to 0-based page indices.
  - Gracefully handles documents lacking embedded outlines with clean empty-state messaging and guidance to the Bookmarks tab.
- **Dual-Tab Navigation Sheet (`DocumentOutlineSheet.kt`)**:
  - Built with Material 3 `ModalBottomSheet` and segmented tabs: **Contents** and **Bookmarks**.
  - **Contents View**: Expandable/collapsible chapter tree (`OutlineNode`), indentation proportional to hierarchy level, chevron toggles, active reading page highlighting, and 1-tap jump chips (`p. 42`).
  - **Bookmarks View**: Comprehensive card-based list of user-created bookmarks showing page badge, custom title, optional note preview, and relative creation date.
  - Quick action to "Bookmark Current Page (p. X)" directly from the sheet header.
- **Persistent User Bookmarks (`RecentDocumentsRepository.kt`)**:
  - Offline JSON storage in `SharedPreferences` keyed by document URI string.
  - Persists custom bookmark labels, multiline notes, page indices, and timestamps across document reloads and application restarts.
- **Top Bar Integration (`PdfTopBar.kt`)**:
  - Dedicated **Document Outline** button (`Icons.AutoMirrored.Filled.MenuBook`) opening the navigation sheet.
  - Dynamic **Bookmark Toggle** icon: shows filled `Icons.Default.Bookmark` in primary color if the current page is pinned; shows `Icons.Default.BookmarkBorder` if unpinned.
- **Interactive Add/Edit Modal (`AddEditBookmarkDialog.kt`)**:
  - Clean Material 3 dialog allowing users to edit titles, attach personal study notes, or delete existing bookmarks.
- **Orientation-Aware Smooth Navigation**:
  - Instant animated jumping to target pages across both portrait (`listState.animateScrollToItem`) and landscape (`pagerState.animateScrollToPage`).

### Key Classes
- `PdfOutlineExtractor.kt` (`com.xpdfviewer.pdf`)
- `PdfOutlineModels.kt` (`com.xpdfviewer.data`)
- `DocumentOutlineSheet.kt` (`com.xpdfviewer.ui.components`)
- `AddEditBookmarkDialog.kt` (`com.xpdfviewer.ui.dialogs`)
- `PdfTopBar.kt` (`com.xpdfviewer.ui.components`)
- `RecentDocumentsRepository.kt` (`com.xpdfviewer.data`)
- `PdfViewerViewModel.kt` (`com.xpdfviewer.viewmodel`)
- `PdfViewerScreen.kt` (`com.xpdfviewer.ui.screens`)

---

## 15. In-Document Full-Text Search Engine

### Overview
A real-time, interactive search deck that scans the entire PDF document, highlights matching terms and phrases with exact spatial bounding boxes directly on the page canvas, and offers bidirectional previous/next match cycling.

### Capabilities
- **Background Search Engine (`PdfSearchEngine.kt`)**:
  - Scans document text layers page-by-page off the main thread (`Dispatchers.Default`).
  - Resolves substring and phrase matches to word bounding boxes (`NormalizedRect`) with character-to-word span mapping.
  - Progressively yields matches page-by-page as they are discovered so users see instant results on multi-hundred-page documents without waiting for complete indexing.
  - Automatically cancels in-flight search jobs upon new keystrokes using Kotlin Coroutines (`ensureActive()`) with a 300ms debounce.
- **Top Bar Search Deck (`PdfSearchBar.kt`)**:
  - Seamlessly replaces standard top bar controls when active.
  - Contains query `BasicTextField`, 1-tap query clear button, and `ImeAction.Search` integration.
  - Live occurrence counter: displays `3 of 18`, `0 matches`, or `Searching...` progress indicator.
  - Bidirectional navigation controls (`▲` Previous / `▼` Next) with automatic modulo wrap-around.
- **Dual-Tone Canvas Highlighting (`PdfSearchHighlightCanvas.kt`)**:
  - Placed directly inside `PdfPageItem.kt` above the rendered page bitmap.
  - Highlights all matches on the page in translucent lemon yellow (`Color(0xFFFFEB3B).copy(alpha = 0.38f)`).
  - Highlights the currently focused match in vibrant amber (`Color(0xFFFF9800).copy(alpha = 0.55f)`) with a bold focus stroke (`Color(0xFFE65100)`).
- **Automated Viewport Synchronization**:
  - Jumping between matches smoothly scrolls the reader directly to the match's page in both portrait (`LazyColumn`) and landscape (`HorizontalPager`) orientations.
  - Hardware back navigation seamlessly closes search mode before exiting the document.

### Key Classes
- `PdfSearchEngine.kt` (`com.xpdfviewer.pdf`)
- `PdfSearchModels.kt` (`com.xpdfviewer.data`)
- `PdfSearchBar.kt` (`com.xpdfviewer.ui.components`)
- `PdfSearchHighlightCanvas.kt` (`com.xpdfviewer.ui.components`)
- `PdfTopBar.kt` (`com.xpdfviewer.ui.components`)
- `PdfPageItem.kt` (`com.xpdfviewer.ui.components`)
- `PdfViewerViewModel.kt` (`com.xpdfviewer.viewmodel`)
- `PdfViewerScreen.kt` (`com.xpdfviewer.ui.screens`)

---

## 16. Night Reading & Eye Comfort Modes

### Overview
A real-time, GPU-accelerated visual display filter pipeline that transforms the PDF rendering layer using Jetpack Compose `ColorFilter.colorMatrix`. Provides 5 eye-comfort presets to eliminate glare, reduce eye fatigue, and minimize blue-light exposure during low-light reading.

### Capabilities
- **5 Calibrated Reading Presets (`ReadingMode.kt`)**:
  - **Original**: Native, unmodified PDF document colors and white paper background.
  - **Night (Dark)**: Calibrated luminescence matrix mapping white paper to pleasant `#191919` dark charcoal and text to `#E1E1E1` crisp off-white without crushing colored graphics into pure black.
  - **Warm Sepia**: Amber parchment paper filter removing 26% of harsh blue-violet light for soothing bedtime reading.
  - **High Contrast**: Dynamic contrast amplification clamping low tones to deep black and boosting faint scanned text.
  - **Grayscale**: Standard ITU-R BT.601 monochrome filter for clean, distraction-free study.
- **Zero Overhead & 60fps Performance**:
  - Applied directly onto the hardware canvas layer inside `PdfPageItem.kt` with zero disk I/O, zero bitmap re-renders, and zero garbage collection spikes.
- **Coordinated Viewport & Card Canvas**:
  - Viewport container and page card background colors automatically match the active filter background to prevent white margin flashes.
- **Interactive Sheet & Top Bar Trigger (`ReadingModeBottomSheet.kt`)**:
  - Material 3 `ModalBottomSheet` with live sample swatches and descriptive subtitles.
  - Dedicated palette action button (`Icons.Default.Palette`) in `PdfTopBar.kt` highlighted in primary tint when an eye comfort mode is active.
- **Cross-Session Memory (`RecentDocumentsRepository.kt`)**:
  - User preference automatically saved in `SharedPreferences` and restored upon subsequent app launches and document loads.

### Key Classes
- `ReadingMode.kt` (`com.xpdfviewer.data`)
- `ReadingModeBottomSheet.kt` (`com.xpdfviewer.ui.components`)
- `PdfTopBar.kt` (`com.xpdfviewer.ui.components`)
- `PdfPageItem.kt` (`com.xpdfviewer.ui.components`)
- `RecentDocumentsRepository.kt` (`com.xpdfviewer.data`)
- `PdfViewerViewModel.kt` (`com.xpdfviewer.viewmodel`)
- `PdfViewerScreen.kt` (`com.xpdfviewer.ui.screens`)

---

## 17. System Architecture & Permissions

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

## 18. Feature Matrix & File Map

| Feature | Primary UI Location | Core ViewModel / Engine | Dependencies |
|---|---|---|---|
| **PDF Reader** | `PdfViewerScreen.kt` | `PdfViewerViewModel.kt`, `PdfRendererCore.kt` | Native `PdfRenderer` |
| **Long-Press Menu** | `PdfViewerScreen.kt` | `PdfViewerViewModel.kt` | Compose Material 3 |
| **Text Selection** | `PdfViewerScreen.kt`, `PdfTextOverlayCanvas.kt` | `SpatialTextExtractor.kt`, `TextToSpeechManager.kt` | PdfBox-Android, Android TTS |
| **Full-Text Search** | `PdfTopBar.kt`, `PdfSearchBar.kt`, `PdfPageItem.kt` | `PdfViewerViewModel.kt`, `PdfSearchEngine.kt` | PdfBox-Android |
| **Document Outline & Bookmarks** | `PdfTopBar.kt`, `DocumentOutlineSheet.kt`, `AddEditBookmarkDialog.kt` | `PdfViewerViewModel.kt`, `PdfOutlineExtractor.kt` | PdfBox-Android |
| **Night Reading Modes** | `PdfTopBar.kt`, `ReadingModeBottomSheet.kt`, `PdfPageItem.kt` | `PdfViewerViewModel.kt`, `ReadingMode.kt` | Jetpack Compose `ColorFilter` |
| **Insert Pages** | `PdfViewerScreen.kt` | `PdfDocumentModifier.kt` | PdfBox-Android |
| **Extract Page** | `PdfViewerScreen.kt`, `ExtractPageDialog.kt` | `PdfDocumentModifier.kt`, `PdfViewerViewModel.kt` | PdfBox-Android |
| **Delete Page** | `PdfViewerScreen.kt`, `DeletePageDialog.kt` | `PdfDocumentModifier.kt` | PdfBox-Android |
| **E-Signature** | `SignPdfScreen.kt`, `PdfViewerScreen.kt` | `SignPdfViewModel.kt`, `PdfSignerEngine.kt` | PdfBox-Android |
| **Save Mode Dialog** | `SaveModeDialog.kt` | `PdfViewerViewModel.kt` | Compose Dialog |
| **OCR Scanner** | `OcrScannerScreen.kt` | `OcrScannerEngine.kt`, `OcrScannerViewModel.kt` | Google ML Kit (`16.0.1`) |
| **Merge PDFs** | `MergePdfScreen.kt` | `PdfDocumentModifier.kt`, `MergePdfViewModel.kt` | PdfBox-Android |
| **Protect & Unlock** | `PdfSecurityScreen.kt`, `ToolsScreen.kt` | `PdfSecurityViewModel.kt`, `PdfSecurityManager.kt` | PdfBox-Android |
| **PDF Utilities Transitions** | `MainAppContainer.kt`, `ToolsScreen.kt` | Compose `AnimatedVisibility` | Material 3 Motion |
| **Create PDF Speed Dial** | `CreatePdfSpeedDial.kt`, `MainAppContainer.kt` | `MainAppContainer.kt` | Compose Material 3 Animation |
| **ML Kit Document Scanner** | `ScannedDocumentDialog.kt`, `MainAppContainer.kt` | `DocumentScannerManager.kt` | Google ML Kit Document Scanner (`16.0.0`) |
| **Page Organizer** | `PageOrganizerScreen.kt`, `OrganizerPageCard.kt` | `PageOrganizerViewModel.kt`, `PdfDocumentModifier.kt` | PdfBox-Android, Native `PdfRenderer` |
| **Split PDF Utility** | `SplitPdfScreen.kt`, `ToolsScreen.kt`, `SplitSuccessDialog.kt` | `SplitPdfViewModel.kt`, `PdfSplitterEngine.kt` | PdfBox-Android, Android SAF |
| **Interactive Form Field Filler** | `PdfFormOverlayCanvas.kt`, `FormFieldsBottomSheet.kt`, `FormActionsBar.kt`, `EditFormFieldDialog.kt` | `PdfViewerViewModel.kt`, `PdfFormManager.kt` | PdfBox-Android `PDAcroForm` |
| **Permanent PDF Redaction Tool** | `PdfRedactionScreen.kt`, `RedactionConfirmDialog.kt`, `RedactionSuccessDialog.kt`, `ToolsScreen.kt` | `PdfRedactionViewModel.kt`, `PdfRedactionEngine.kt`, `RedactionModels.kt` | PdfBox-Android Vector Content Stream, Native `PdfRenderer` |
| **Watermark & Page Numbers** | `PdfWatermarkScreen.kt`, `WatermarkSuccessDialog.kt`, `ToolsScreen.kt` | `PdfWatermarkViewModel.kt`, `PdfWatermarkEngine.kt`, `WatermarkModels.kt` | PdfBox-Android Vector Content Stream, PDExtendedGraphicsState |
| **Image & PDF Studio** | `ImagePdfConverterScreen.kt`, `ImageExportSuccessDialog.kt`, `ToolsScreen.kt` | `ImagePdfConverterViewModel.kt`, `ImageToPdfEngine.kt`, `PdfToImageEngine.kt` | PdfBox-Android, Native `PdfRenderer`, Android Photo Picker |
| **Continuous Audio Reader** | `PdfViewerScreen.kt`, `AudioPlayerDock.kt`, `PdfAudioHighlightCanvas.kt` | `PdfViewerViewModel.kt`, `ContinuousAudioReader.kt`, `AudioReaderModels.kt` | Android `TextToSpeech`, `AudioManager` Audio Focus |
| **File Provider** | `AndroidManifest.xml` | `res/xml/file_paths.xml` | AndroidX Core |

---

## 19. Split PDF Utility

### Overview
A dedicated offline utility integrated into the **PDF Utilities** tab for carving and splitting PDF documents into smaller files. Supports custom range parsing, equal chunk dividing, and single-page burst extraction.

### Operating Modes
1. **Custom Range**:
   - Parses expressions such as `"1-3, 5, 8-12"`.
   - Supports saving all selected pages into a single extracted PDF or into distinct individual files per range.
   - Real-time validation warning and bounds clamping against total document pages.
2. **Fixed Interval**:
   - Divides documents into equal chunks every *N* pages (stepper controls: - / +, quick presets: 2, 5, 10, 20).
3. **Burst Pages**:
   - Explodes document into individual 1-page PDF files (`{prefix}_part_1.pdf`, `{prefix}_part_2.pdf`, ...).

### SAF Directory & File Streaming
- Sequential document processing with immediate `PDDocument.close()` to avoid `OutOfMemoryError` on 500+ page documents.
- Batch export to user-selected folders via SAF `ActivityResultContracts.OpenDocumentTree` and `DocumentsContract.createDocument`.
- Single-file export via `ActivityResultContracts.CreateDocument`.

### Key Files
- `SplitPdfModels.kt` (`com.xpdfviewer.data`)
- `PdfSplitterEngine.kt` (`com.xpdfviewer.pdf`)
- `SplitPdfViewModel.kt` (`com.xpdfviewer.viewmodel`)
- `SplitPdfScreen.kt` (`com.xpdfviewer.ui.screens`)
- `SplitSuccessDialog.kt` (`com.xpdfviewer.ui.dialogs`)
- `ToolsScreen.kt` (`com.xpdfviewer.ui.screens`)
- `MainAppContainer.kt` (`com.xpdfviewer.ui.screens`)

---

## 20. Interactive Form Field Filler (AcroForm) & PDF Flattening Engine

### Overview
Automatic detection and interactive filling of PDF forms (`PDAcroForm`) directly inside the viewer, supporting text input fields, checkboxes, radio groups, and dropdown choices, with a 1-tap option to flatten entries permanently into the PDF content stream.

### Architecture & Capabilities
1. **AcroForm Inspection & Coordinate Normalization**:
   - Parses `PDAcroForm` field trees via `PdfFormManager.kt`.
   - Maps `PDAnnotationWidget` bounding boxes into top-left normalized canvas coordinates (`NormalizedRect`).
   - Supports `PDTextField`, `PDCheckBox`, `PDRadioButton`, and `PDChoice`.
2. **Interactive Canvas Overlay (`PdfFormOverlayCanvas.kt`)**:
   - Renders field bounds with touch detection directly over `PdfPageItem.kt`.
   - Tap-to-toggle for checkboxes with vector checkmark appearance.
   - Tap-to-edit for text, radio, and choice fields opening `EditFormFieldDialog.kt`.
3. **Sequential Form Assistant Sheet (`FormFieldsBottomSheet.kt`)**:
   - Page-grouped field list with completion percentage progress indicator.
   - Jump-to-page navigation for immediate viewport focus.
   - Clear All action to reset form entries.
4. **Docked Control Deck (`FormActionsBar.kt`)**:
   - Floating action banner showing live `X/Y Filled` counter.
   - 1-tap trigger to open the assistant sheet or export dialog.
5. **Flattening & Export Engine**:
   - **Interactive Export**: Saves updated field values while preserving interactive AcroForm structure.
   - **Flattened Export**: Invokes `acroForm.flatten()` to burn text and marks permanently into page content streams, locking entries against tampering for official submission.

### Key Files
- `PdfFormFieldModels.kt` (`com.xpdfviewer.data`)
- `PdfFormManager.kt` (`com.xpdfviewer.pdf`)
- `PdfFormOverlayCanvas.kt` (`com.xpdfviewer.ui.components`)
- `FormActionsBar.kt` (`com.xpdfviewer.ui.components`)
- `FormFieldsBottomSheet.kt` (`com.xpdfviewer.ui.components`)
- `EditFormFieldDialog.kt` (`com.xpdfviewer.ui.dialogs`)
- `FormSaveChoiceDialog.kt` (`com.xpdfviewer.ui.dialogs`)
- `PdfViewerViewModel.kt` (`com.xpdfviewer.viewmodel`)
- `PdfViewerScreen.kt` (`com.xpdfviewer.ui.screens`)
- `PdfTopBar.kt` (`com.xpdfviewer.ui.components`)

---

## 21. Permanent PDF Redaction Tool

### Overview
A dedicated offline redaction studio accessible from the **PDF Utilities** hub, enabling users to permanently sanitize sensitive personally identifiable information (PII), banking details, names, addresses, or confidential text from PDF documents. Unlike simple cosmetic highlighter or markup tools, redaction in xPDF Viewer physically alters the PDF content stream and scrubs covert metadata dictionaries.

### Key Capabilities
1. **Interactive Touch-Drag Canvas (`PdfRedactionScreen.kt`)**:
   - High-resolution page rendering via background `PdfRendererCore`.
   - Intuitive touch-and-drag gesture bounding box placement with real-time drag preview.
   - Per-mark delete chips and pending redaction count tracker.
   - Dynamic page stepper with thumbnail indicator and fast navigation.
2. **Multi-Page Keyword Auto-Search & Redaction**:
   - Search bar input querying `PdfSearchEngine.searchDocument` across all document pages.
   - 1-tap "Redact All" automatically maps word bounding boxes to pending redaction marks across every page.
3. **True Vector Content Stream Blackout (`PdfRedactionEngine.kt`)**:
   - Converts normalized canvas coordinates into bottom-left PDF coordinate space (`MediaBox` / `CropBox`).
   - Appends solid vector fill operations (`PDPageContentStream.addRect()`, `fill()`) with opaque `#000000` ink in `APPEND` mode.
   - Blackout plates become an irreversible physical part of the PDF graphics stream.
4. **Annotation Pruning**:
   - Detects and prunes overlapping `PDAnnotation` objects (comments, highlights, widgets) whose rectangles intersect redaction zones.
5. **Metadata & XMP Dictionary Scrubbing**:
   - Empties all metadata attributes in `doc.documentInformation` (`title`, `author`, `subject`, `keywords`, `creator`, `producer`, `creationDate`, `modificationDate`).
   - Removes embedded XMP metadata stream from `doc.documentCatalog.metadata`.
6. **Safety Confirmation & SAF Export**:
   - Irreversibility warning prompt (`RedactionConfirmDialog.kt`).
   - Direct export to chosen directory via Android Storage Access Framework (`CreateDocument`) with `_redacted.pdf` suffix.
   - Post-export success dialog with instant Open and Share actions (`RedactionSuccessDialog.kt`).

### Key Files
- `RedactionModels.kt` (`com.xpdfviewer.data`)
- `PdfRedactionEngine.kt` (`com.xpdfviewer.pdf`)
- `PdfRedactionViewModel.kt` (`com.xpdfviewer.viewmodel`)
- `PdfRedactionScreen.kt` (`com.xpdfviewer.ui.screens`)
- `RedactionConfirmDialog.kt` (`com.xpdfviewer.ui.dialogs`)
- `RedactionSuccessDialog.kt` (`com.xpdfviewer.ui.dialogs`)
- `ToolsScreen.kt` (`com.xpdfviewer.ui.screens`)
- `MainAppContainer.kt` (`com.xpdfviewer.ui.screens`)

---

## 22. PDF Watermark & Dynamic Pagination Utility

### Overview
A dedicated offline watermarking and document pagination studio integrated into the **PDF Utilities** hub. Enables users to stamp legal and security notices (e.g. "CONFIDENTIAL", "DRAFT", "DO NOT COPY", custom text) and burn dynamic Bates numbering or page counters directly into PDF vector content streams.

### Key Capabilities
1. **Vector Content Stream Stamping (`PdfWatermarkEngine.kt`)**:
   - Uses PDFBox `PDPageContentStream` with `PDExtendedGraphicsState` for calibrated alpha transparency (`0.10f`–`0.80f`).
   - Supports diagonal (-45°), horizontal (0°), and vertical (90°) rotations centered on page geometry.
   - Dual layer support: Foreground (over page content) or Background (under existing text layer via `PREPEND` mode).
   - Dynamic text centering with mathematical transformation matrices (`Matrix`).
2. **Dynamic Page Numbering & Bates Stamping**:
   - Customizable formats: `"Page {page} of {total}"`, `"{page}"`, `"Page {page}"`, `"- {page} -"`, `"DOC-{bates}"`.
   - 6-point anchor positioning matrix (Top Left/Center/Right, Bottom Left/Center/Right).
   - Adjustable font size (8–18 pt) and border margin padding (12–48 pt).
3. **Flexible Page Scoping**:
   - All Pages, Skip Cover (First Page), or Custom Range expressions (`"1-3, 5, 8"`).
4. **SAF Export & Viewer Launch**:
   - Direct export via Storage Access Framework `CreateDocument` with `_watermarked.pdf` suffix.
   - `WatermarkSuccessDialog.kt` with immediate 1-tap launch into the core viewer.

### Key Files
- `WatermarkModels.kt` (`com.xpdfviewer.data`)
- `PdfWatermarkEngine.kt` (`com.xpdfviewer.pdf`)
- `PdfWatermarkViewModel.kt` (`com.xpdfviewer.viewmodel`)
- `PdfWatermarkScreen.kt` (`com.xpdfviewer.ui.screens`)
- `WatermarkSuccessDialog.kt` (`com.xpdfviewer.ui.dialogs`)
- `ToolsScreen.kt` (`com.xpdfviewer.ui.screens`)
- `MainAppContainer.kt` (`com.xpdfviewer.ui.screens`)

---

## 23. Bidirectional Image-PDF Conversion Studio

### Overview
A dual-mode offline conversion studio integrated into **PDF Utilities** that handles both compiling device gallery photos into standardized PDF documents and rasterizing PDF pages into high-resolution images packed into ZIP archives.

### Operating Modes
1. **Images to PDF (`ImageToPdfEngine.kt`)**:
   - Ingestion via Android Photo Picker (`PickMultipleVisualMedia`) with zero runtime permissions.
   - Draggable horizontal strip supporting 90° image rotation, deletion, and page order rearrangement.
   - Page dimensions: A4, US Letter, and Fit to Image (preserves original photo dimensions without letterboxing).
   - Layout options: Auto/Portrait/Landscape orientation, borderless or margin padding (18–36 pt), and adjustable JPEG compression quality.
2. **PDF to Images (`PdfToImageEngine.kt`)**:
   - High-fidelity rasterization powered by native Android `PdfRenderer`.
   - Resolution presets: Standard (150 DPI), High Quality (300 DPI), and Ultra Archival (450 DPI).
   - Formats: JPEG (compressed) and PNG (lossless transparent).
   - Page scoping: All Pages, Skip Cover, or Custom Range syntax.
   - Directly streams rasterized bitmaps into compressed ZIP archives via SAF `CreateDocument`.

### Key Files
- `ImagePdfModels.kt` (`com.xpdfviewer.data`)
- `ImageToPdfEngine.kt` (`com.xpdfviewer.pdf`)
- `PdfToImageEngine.kt` (`com.xpdfviewer.pdf`)
- `ImagePdfConverterViewModel.kt` (`com.xpdfviewer.viewmodel`)
- `ImagePdfConverterScreen.kt` (`com.xpdfviewer.ui.screens`)
- `ImageExportSuccessDialog.kt` (`com.xpdfviewer.ui.dialogs`)
- `ToolsScreen.kt` (`com.xpdfviewer.ui.screens`)
- `MainAppContainer.kt` (`com.xpdfviewer.ui.screens`)

---

## 24. Continuous Audio Reader (Document Audiobook Mode)

### Overview
Transforms the core PDF reading experience into an offline, continuous document audiobook reader with synchronized on-page sentence spotlighting and automated viewport page progression.

### Key Capabilities
1. **Continuous Speech Orchestration (`ContinuousAudioReader.kt`)**:
   - Chunks page text into sentences mapped directly to `SpatialWord` coordinate bounding boxes.
   - Sequential paragraph and sentence advancement with `UtteranceProgressListener`.
   - **Word-by-Word "Karaoke" Tracking (`onRangeStart`)**: Tracks character offsets in real-time, mapping active speech to exact `SpatialWord` tokens.
   - **PDF De-Hyphenation & Text Normalization (`AudioTextNormalizer.kt`)**: Automatically merges line-break hyphenated words (`inter-` + `national` $\to$ `international`), expands ligatures (`ﬁ`, `ﬂ`, `œ`), strips disruptive bracketed citations (`[1]`, `[14-16]`, `(Smith, 2024)`), and expands common abbreviations (`Dr.`, `e.g.`, `Fig.`, `vs.`, `&`, `%`).
   - **Header & Footer Margin Skipping**: Automatically detects and prunes running header text (top 8% margin `y <= 0.08f`), footer notices, and standalone page numbers (bottom 8% margin `y >= 0.92f`), preventing intrusive repetition during continuous listening.
   - Audio focus management (`AudioManager.requestAudioFocus`) automatically pausing speech on calls or interruptions.
2. **Interactive "Tap-to-Speak" (Canvas Jump Navigation)**:
   - Single-tapping any word or paragraph on the rendered page directly seeks speech playback to that sentence via normalized spatial hit-testing.
3. **Voice Engine & Pitch Customizer (`AudioVoiceSettingsSheet.kt`)**:
   - Querying and filtering installed offline TTS voice profiles.
   - Pitch modulation slider/stepper (`0.80× Deep`, `1.00× Normal`, `1.20× Crisp`) and speech rate controls (`0.75×` to `2.0×`).
4. **Background Media Service & Lock Screen Deck (`AudioPlaybackService.kt`)**:
   - Android foreground service with `FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK` maintaining uninterrupted listening with screen turned off.
   - System notification and lock screen controls with Previous, Play/Pause, Next, and Stop actions.
5. **Floating Playback Dock (`AudioPlayerDock.kt`)**:
   - Sleek Material 3 floating pill pinned above bottom navigation in `PdfViewerScreen.kt`.
   - Play / Pause controls with buffering spinner, Skip Previous / Next sentence buttons.
   - Voice and pitch settings sheet trigger button.
   - Dynamic speech rate stepper (`0.75×`, `1.0×`, `1.25×`, `1.5×`, `1.75×`, `2.0×`).
   - Sleep timer options: 15m, 30m, 45m, 60m, or End of Current Page.
6. **Synchronized Dual-Tone Spatial Highlighting (`PdfAudioHighlightCanvas.kt`)**:
   - Real-time warm amber background spotlight (`Color(0xFFF59E0B)`) over the entire active sentence.
   - Crisp, high-contrast amber focus pill (`Color(0xFFD97706)`) tracking individual words being spoken in real-time (Kindle Immersion Reading style).
7. **Automated Viewport Tracking**:
   - Viewport automatically animates scrolling to keep the active spoken sentence centered.
   - Seamlessly turns to the next page when the current page's text stream finishes.

### Key Files
- `AudioReaderModels.kt` (`com.xpdfviewer.data`)
- `ContinuousAudioReader.kt` (`com.xpdfviewer.speech`)
- `AudioTextNormalizer.kt` (`com.xpdfviewer.speech`)
- `AudioPlaybackService.kt` (`com.xpdfviewer.service`)
- `AudioSentenceSegmenterTest.kt` (`com.xpdfviewer.speech`)
- `AudioTextNormalizerTest.kt` (`com.xpdfviewer.speech`)
- `PdfAudioHighlightCanvas.kt` (`com.xpdfviewer.ui.components`)
- `AudioPlayerDock.kt` (`com.xpdfviewer.ui.components`)
- `AudioVoiceSettingsSheet.kt` (`com.xpdfviewer.ui.components`)
- `PdfTopBar.kt` (`com.xpdfviewer.ui.components`)
- `PdfPageItem.kt` (`com.xpdfviewer.ui.components`)
- `PdfViewerViewModel.kt` (`com.xpdfviewer.viewmodel`)
- `PdfViewerScreen.kt` (`com.xpdfviewer.ui.screens`)

---

*Documentation generated for xPDFViewer. All features offline, secure, and fully verified.*

