# Changelog

All notable changes to the **xPDF Viewer** application will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.2.0] - 2026-10-18

### Changed & Enhanced
- **Custom In-App Scanner Viewfinder & Auto-Detect**:
  - **Camera 3:4 Aspect Ratio**: Calibrated CameraX preview, analysis, and image capture pipelines to 3:4 (`AspectRatio.RATIO_4_3`) in portrait, removing vertical distortion and filling a dedicated 3:4 rounded viewfinder container.
  - **Dynamic Auto-Detect Bounding Box & Auto-Crop on Shot**: Real-time on-device ML Kit document and text detection tracks document bounds dynamically. Tapping the shutter button now **automatically crops** the captured high-resolution photo to the detected document bounds with safety margins, isolating the document card from desk background and cables.
  - **Re-crop from Full Camera Photo**: Saved uncropped bitmap in scan page model, enabling users to re-crop or adjust margins from the full camera capture in the Crop dialog.
  - **Removed Flip Camera Button**: Streamlined top app bar to dedicated back-camera scanning without accidental selfie flips.
- **Fixed Document Editor Tool Buttons (Enhance, Crop, Rotate, Clean)**:
  - Fixed Compose reactivity bug by updating `pages[selectedPageIndex]` with immutable copies (`.copy(...)`), guaranteeing instant recomposition and visual updates on button clicks.
  - **Enhance**: Instant contrast and sharpness boost across any filter mode.
  - **Interactive 4-Corner Crop Studio & Perspective Warp Deskew**: Scuppered rigid axis-aligned rectangles. Built full-screen touch studio featuring 4 independent draggable corner handles (Top-Left, Top-Right, Bottom-Right, Bottom-Left) that move freely without affecting neighboring corners, fitting any tilted or skewed document. On applying crop, `DocumentImageProcessor.cropPerspectiveWarp` uses `Matrix.setPolyToPoly` to stretch, unwarp, and rectify the quadrilateral into a true rectangular document with straight 90-degree corners. Fixed bottom button clipping by migrating to a root Activity Window view (`CropDocumentStudioView`) with native `navigationBarsPadding()` support.
  - **Rotate**: Dedicated rotate button executing instant 90° clockwise rotations.
  - **Clean**: Instant paper whitening and shadow noise removal.
  - **Filters**: Instant color matrix mode switching (Original, Enhance, Grayscale, B&W, Color).
  - **Cleaned Bottom Navigation Menu**: Removed obsolete "Soon" badge from the "Tools" navigation tab in `AppBottomNav.kt` now that all four primary tool suites (OCR Scanner, Merge PDFs, Compress, E-Signature) are fully built, verified, and operational.
  - **Last Opened Date & Time in Jump Back In & Recent Lists**: Integrated formatted timestamp display (`MMM d, yyyy • h:mm a`) with calendar iconography into `DocumentCard` across both "Jump Back In" on the Home tab and "Reading History" on the Recent tab. Automatically displays the exact date and time the PDF file was last opened and read.
  - **Swipe to Remove for Recents & Jump Back In**: Implemented `SwipeableRecentDocumentItem` leveraging Material 3 `SwipeToDismissBox` on both "Jump Back In" (Home) and "Reading History" (Recent) lists. Swiping any item left or right reveals an animated red removal backdrop with trash iconography, dismissing and deleting the file from recents storage while preserving bulk "Clear All".
  - **E-Signature UI Streamlining & Discard Confirmation**: Removed the "Date" and "Change" buttons from the E-Signature bottom deck, expanding "+ Signature" to full width. Added back-navigation confirmation dialog ("Discard Changes?" with "Yes" and "No" buttons) that completely clears the open file and closes `PdfRendererCore` in `SignPdfViewModel` upon exit.
  - **Cleaned Tools Header**: Removed "Tools & AI" pill badge from the PDF Utilities section header in `ToolsScreen.kt` for a minimalist, uncluttered view.
  - **Redesigned Discard Changes Dialog & Unified PDF Utilities Safe Exit**: Built `DiscardChangesDialog.kt` with modern Material 3 styling (circular warning avatar, caution banner, balanced dual action buttons with 48dp touch targets). Wired across all tools in **Tools > PDF Utilities** (Compress, Merge PDFs, OCR Scanner, E-Signature, and In-App Camera Scanner): pressing Back when a document is open prompts to discard changes ("Yes" or "No"), and confirming "Yes" clears the open file and frees allocated memory before exiting.
  - **PDF Utilities Opening & Closing Animations**: Replaced abrupt Compose early returns in `MainAppContainer.kt` with a fluid Material 3 `AnimatedVisibility` overlay system. When opening any tool in **PDF Utilities** (OCR Scanner, Merge PDFs, Compress, E-Signature, or Camera Scanner), the screen slides smoothly up from the bottom (+28% offset), expands from 92% scale, and fades in over 350ms, with spring press micro-interactions on the tool grid cards. On back navigation or discard exit, the tool screen gracefully slides down (+22% offset), scales down to 94%, and fades out over 240ms while maintaining full state retention without flickering or unmounting the underlying navigation drawer and recents list.
  - **"Add E-Signature" in Viewer Long-Press Menu**: Added "Add E-Signature" to the long-press page action bottom sheet in `PdfViewerScreen.kt` and `PageActionsBottomSheet.kt` alongside "Select Text", "Add page", and "Delete page". Selecting "Add E-Signature" launches the animated full-screen E-Signature studio pre-loaded with the current document and automatically positioned at the selected page for instant signing, stamp placement, and secure PDF export.
  - **"Extract page" in Viewer Long-Press Menu & Page Isolation Engine**: Added "Extract page" action to the viewer long-press bottom sheet between "Add page" and "Delete page". The action isolates the long-pressed page, discarding all other pages from the document via PdfBox `PDDocument.importPage` while preserving original page dimensions and embedded font dictionaries. Accompanied by a dedicated Material 3 `ExtractPageDialog.kt` confirmation dialog and the dual Overwrite / Save As Copy workflow, defaulting copy filenames to `${baseName}_page_${pageNumber}.pdf`. Guarded to prevent extraction when the document already contains only 1 page.
  - **Navigation Drawer Menu Label**: Renamed drawer action from "Open Files" to "Open document" in `AppNavigationDrawer.kt`.

### Added
- **E-Signature & Secure Contract Signing Suite**:
  - Activated the "E-Signature" card on the Tools tab with a **Ready** status badge (`Color(0xFFDCFCE7)`).
  - Built offline on-device PDF signing suite (`SignPdfScreen.kt`, `SignPdfViewModel.kt`, `PdfSignerEngine.kt`).
  - **Drawing Pad**: Freehand stylus and touch canvas with quadratic bezier stroke smoothing, ink color picker (Black, Navy, Blue), stroke width control, and undo/clear actions.
  - **Type Signature**: Cursive calligraphy engine rendering typed names into flowing script signatures with underline baselines.
  - **Open File / Image Format Signature**: Added dedicated "Image" tab to "Create Signature" supporting image formats (PNG, JPG, JPEG, WEBP) via Storage Access Framework `ActivityResultContracts.OpenDocument`. Features automated luminance filtering to make white paper backgrounds transparent, smooth edge anti-aliasing, and tight ink-bounds cropping with live preview and toggle controls.
  - **Saved Signatures Vault**: In-app local storage for reusable signatures, enabling instant 1-tap placement on future contracts.
  - **Interactive Drag-and-Resize Overlay**: Touch handles to move, scale, or delete signatures anywhere across multi-page PDF documents.
  - **Fixed Corner Drag Resize on E-Signatures**: Resolved pointer input gesture collision where dragging the corner resize handle was intercepted by the parent movement detector. Isolated the corner handle into an independent sibling composable with a dedicated 44dp touch target, eliminated stale closure state via `rememberUpdatedState`, and implemented an accumulated proportional scale delta formula that preserves the exact handwriting aspect ratio smoothly across drag gestures.
  - **Date Stamp Generator**: 1-tap addition of dynamic date stamps.
  - **128-Bit AES Password Encryption**: Protects signed contracts with optional user/owner password security using PdfBox `StandardProtectionPolicy`.
  - **Fixed Aspect Ratio Distortion on Custom & Landscape Pages**: Fixed bug in `PdfRendererCore.renderPage` where non-zero `targetWidth` with zero `targetHeight` mixed fixed width with arbitrary `page.height * 2`, forcing landscape contracts to distort and stretch into tall portrait. Updated dimension calculation across `PdfRendererCore`, `SignPdfViewModel`, and `SignPdfScreen` to strictly enforce native page aspect ratio with `ContentScale.Fit`.
  - **Fixed Share Button in Contract Signed Dialog**: Corrected button layout in `SignSuccessDialog.kt` where oversized 24dp default content padding and uneven weights forced the label "Share" into an awkward two-line split ("Shar\ne"). Set compact horizontal padding, single-line text constraints, balanced button proportions, and added `FLAG_ACTIVITY_NEW_TASK` to chooser launch flags.
  - Storage Access Framework destination saving with custom filename prompt and completion sheet with direct "Open in Viewer" and "Share PDF" actions.
- **PDF Compression Utility**:
  - Activated the "Compress" card on the Tools tab with a **Ready** status badge.
  - Built offline on-device PDF compression suite (`CompressPdfScreen.kt`, `CompressPdfViewModel.kt`, `PdfCompressor.kt`) using PdfBox-Android image resampling and Flate stream optimization.
  - Three customizable compression levels:
    - **Extreme Compression**: Aggressive reduction (~70% smaller) for strict email limits.
    - **Recommended Compression**: Optimal balance (~50% smaller) for everyday documents.
    - **Less Compression**: High fidelity (~30% smaller) for photo and print quality.
  - Live before-and-after size comparison dialog with percentage savings metrics (e.g. `Saved 72%`).
  - Storage Access Framework streaming to user-selected storage destination with custom filename prompt.
  - Post-compression completion sheet with direct "Open in Viewer" and native "Share PDF" options.
  - **Premium Selection Card UI**: Fixed unappealing muddy purple/gray selection background by introducing a crisp Ice Blue (`#EFF6FF`) container, confident 2dp primary stroke border, animated color transitions, and a tone-on-tone soft blue badge pill (`#DBEAFE` container with `#1D4ED8` text) to preserve clean visual hierarchy. Defined `primaryContainer` and `secondaryContainer` across light and dark color schemes in `Theme.kt`.
- **Custom In-App Compose Document Scanner & Editor**:
  - Built full-featured in-app document scanner and editor (`CustomDocumentScannerScreen.kt`) replacing Google Play Services remote activity for 100% UI and branding control.
  - **Renamed "Next" to "Create"**: Bottom action button prominently labeled **"Create"** for compiling and exporting the scanned document.
  - **Labeled "+ Add Page" Button**: Added an explicit text label `[ + Add Page ]` alongside page thumbnails to easily snap or import additional pages.
  - **Filename Prompt Dialog**: In-app `ScanFilenamePromptDialog` prompts before saving, preventing unwanted automatic file generation.
  - **Offline PdfBox Multi-Page Engine**: High-fidelity page composition via `ScannedPdfCreator.kt` with direct streaming to Storage Access Framework destination.
- **Create PDF Speed Dial FAB**:
  - Added floating action button (`+`) positioned in the bottom-right corner above the bottom navigation menu with edge-to-edge window inset awareness.
  - Implemented animated Speed Dial expansion with 45° rotation into a dismiss icon (`✕`), soft backdrop scrim overlay, and spring-staggered sub-action pill buttons.
  - **Scan a Document**: Integrated Google Play Services ML Kit Document Scanner (`16.0.0`) with real-time camera viewfinder, automatic document boundary detection (bounding box), perspective rectification, shadow/stain removal, multi-page document capture, and on-device PDF generation (zero runtime camera permissions).
  - **Merge PDF**: Direct bridge to the offline PDF merge utility (`MergePdfScreen`).
  - Added in-app filename prompt dialog (`ScanFilenamePromptDialog`) after scanning, preventing automatic file creation.
  - Added direct destination saving via Storage Access Framework (`CreateDocument`) with the user's chosen filename.
  - Added `ScannedDocumentDialog` completion sheet allowing instant "Open in Viewer" and system "Share PDF".
- **File Path Display in Jump Back In & Recent History**:
  - Enhanced `RecentDocument` data model with `filePath` and intelligent path resolution (`getResolvedPath()` and `resolveDisplayPath()`).
  - Added SAF `primary:` external storage, MediaStore `DATA` / `RELATIVE_PATH`, and download provider decoding for human-readable directory paths.
  - Updated `DocumentCard` in `HomeScreen.kt` (used by both Home "Jump Back In" and Recent "Reading History") to display a dedicated file path line with a folder icon (`Icons.Default.FolderOpen`) below the document title.
  - Preserved backward compatibility for existing reading history entries with dynamic URI fallback decoding.
- **PDF Merge Utility**:
  - Implemented offline multi-document PDF merge engine using PdfBox `PDDocument.importPage()`.
  - Added multi-file picker launcher via `ActivityResultContracts.OpenMultipleDocuments()`.
  - Added "+ Add More" document picker to append additional PDFs incrementally.
  - Added reorder controls (Move Up ▲ / Move Down ▼) and item removal on each document card.
  - Added automatic document metadata resolution (page count and formatted file size).
  - Added in-app filename prompt dialog allowing users to enter a custom name before merging.
  - Added direct destination merging via Storage Access Framework (`CreateDocument`) without creating automatic unwanted files.
  - Added automatic clearing of the selected document list once the merge successfully completes.
  - Added interactive post-merge actions: **Open in Viewer** and **Share PDF**.
  - Activated "Merge PDFs" card under the **Tools** tab with a green "Ready" badge.
- **On-Device OCR Scanner**:
  - Integrated Google ML Kit Text Recognition (`com.google.mlkit:text-recognition:16.0.1`) with offline `libmlkit_google_ocr_pipeline.so`.
  - Added "Take Document Photo" camera preview capture via `ActivityResultContracts.TakePicturePreview` (zero runtime camera permissions required).
  - Added "Choose from Gallery" via `ActivityResultContracts.GetContent`.
  - Added live scanning progress overlay during OCR neural network inference.
  - Added document thumbnail preview with live character and word count statistics.
  - Added editable text area (`OutlinedTextField`) with one-tap "Copy Text" and "Share" sheet actions.
  - Activated "OCR Scanner" card under the **Tools** tab with a green "Ready" badge.
- **FileProvider Sharing Infrastructure**:
  - Configured `androidx.core.content.FileProvider` in `AndroidManifest.xml` (`com.xpdfviewer.provider`).
  - Added `app/src/main/res/xml/file_paths.xml` allowing cache and internal files sharing to prevent `FileUriExposedException`.
- **Comprehensive Documentation**:
  - Created `FEATURES.md` detailing all capabilities, architecture, user flows, and class mappings.

### Fixed
- **PDF Merge `COSStream has been closed` Error**:
  - Resolved PdfBox stream lifetime bug where source `PDDocument` instances and input streams were closed inside the merge iteration loop before `targetDoc.save()` completed serialization.
  - Deferred closing of all source streams and documents until after `save()` completes in a `finally` block, ensuring all COS stream references remain open during document generation.
  - Added auto-decryption flag (`setAllSecurityToBeRemoved`) for password-free encrypted source documents.
  - Hardened `insertPdfDocument` against premature stream disposal.

---

## [1.1.0] - 2026-10-18

### Added
- **Multi-Function Long-Press Menu**:
  - Replaced single-action press-and-hold copy with `PageActionBottomSheet`.
  - Added **Select Text** action to launch interactive on-screen selection.
  - Added **Add Page** action to insert external PDF pages.
  - Added **Delete Page** action with single-page safety protection.
- **Insert PDF Pages**:
  - Integrated `PdfDocumentModifier.insertPdfDocument()` with `ActivityResultContracts.OpenDocument`.
  - Slices and appends imported PDF pages directly after the current page.
- **Save Mode Dialog ("Overwrite" vs "Save As Copy")**:
  - Created `SaveModeDialog.kt` prompting whenever document mutations occur.
  - Supports in-place overwrite or exporting a new copy via `ActivityResultContracts.CreateDocument`.

### Fixed
- **On-Screen Text Selection Header Bug**:
  - Added `.statusBarsPadding()` and `shadowElevation = 8.dp` to the selection top bar in `PdfViewerScreen.kt` to prevent camera cutouts, clock, and battery icons from overlapping action controls.

### Changed
- Polished long-press action buttons with enhanced Material 3 elevations, tonal backgrounds, and iconography.

---

## [1.0.0] - Initial Release

### Added
- Modern Jetpack Compose Material 3 PDF Viewer.
- Dual theme engine with dynamic Light and Dark mode switching.
- Asynchronous PDF page rendering via Android's native `android.graphics.pdf.PdfRenderer`.
- Full edge-to-edge layout support with Compose window insets.
- Navigation Drawer with dark mode toggle and open file shortcuts.
- Bottom Navigation Bar with **Home**, **Recent**, and **Tools** tabs.
- Recent documents persistence with last-read page tracking and timestamps.
- Bottom page navigation scrubber bar, jump-to-page dialog, and thumbnail preview.
- Native Android `PrintManager` integration for PDF printing.
- Storage Access Framework integration with persistent URI permissions.
