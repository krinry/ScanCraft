# Project: ScanCraft Full Feature Parity

## Architecture
ScanCraft follows Modern Android Architecture (MVVM) with 100% offline-first local isolation, clean-room Jetpack Compose UI, and native NDK C++ computer vision algorithms:

- **UI Layer (`com.krinry.scancraft.ui.*`)**:
  - Jetpack Compose screens: `HomeScreen` (Document list, hierarchical folder navigation, multi-select batch bar), `HomeToolsTab` (Dedicated Tools Hub dashboard), `CameraScreen` (Single, Batch burst queue, ID Card 2-in-1, Book curvature), `BatchReviewScreen` (Multi-image review, reorder, rotate, deferred warp), `DocumentDetailScreen` (Page Manager grid, reorder, rotate, delete, add), `PageViewerScreen` (Single page viewer, interactive signature overlay), `PdfViewerScreen` (Native `PdfRenderer` multi-page viewer), `OcrViewerScreen` (Split preview + editable text, copy, export), `CollageStudioScreen` (2, 3, 4, 6 page layout generator).
  - Stateless composables, Unidirectional Data Flow (UDF), Material 3 themes (`Theme.kt`, `#000000` AMOLED dark theme).
- **ViewModel Layer (`*ViewModel.kt`)**:
  - Exposes state via `StateFlow`, handles UI events, coroutine scopes (`viewModelScope`, `Dispatchers.IO`, `Dispatchers.Default`).
- **Repository Layer (`com.krinry.scancraft.data.repository.*`)**:
  - `ScannerRepository`: Single source of truth managing `AppDatabase`, file I/O (`context.filesDir/scans/`, `context.filesDir/thumbs/`, `context.cacheDir/temp_scans/`), stitching, signature stamping, PDF compilation.
- **Data Layer (`com.krinry.scancraft.data.local.*`, `com.krinry.scancraft.data.model.*`)**:
  - Room entities: `Document`, `Page`, `Folder` (with hierarchical `parentFolderId` and `path`).
  - DAOs: `DocumentDao`, `PageDao`, `FolderDao`.
- **Engine / CV Layer (`com.krinry.scancraft.cv.*`, native C++ in `app/src/main/cpp`)**:
  - Native C++: `libscancraft.so` (Sobel contour detection, 3x3 homography perspective warp, Sauvola/Magic Color adaptive binarization, Retinex shadow removal).
  - Kotlin CV: `EdgeDetector`, `PerspectiveTransformer`, `ImageEnhancer`, `CollageGenerator` (2, 3, 4, 6 page layouts), `BookSpineDetector` (vertical luminance valley profiling & curvature unwarp), `OcrEngine` (bundled offline ML Kit).
  - PDF Engine: `PdfGenerator` (PDFBox Android + native `PdfDocument`, AES 128-bit encryption, configurable watermark text/opacity/rotation, compression presets).

---

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | Batch Burst Capture | Continuous multi-page photo capture with live thumbnail queue and page count badge without leaving camera | M2 | Survey / R1 |
| 2 | Deferred Batch Review & Edit | Multi-image review screen with rotate, crop, filter, delete, and deferred background warp pipeline | M2 | Survey / R1 |
| 3 | ID Card 2-in-1 Viewfinder Guide | Fixed aspect-ratio frame overlay (1.586 ISO/IEC 7810 ID-1 standard) occupying 85% width | M2 | Survey / R1 |
| 4 | ID Card Two-Step Sequential Capture | Captures front side, shows front thumbnail, prompts "Back page", captures back side | M2 | Survey / R1 |
| 5 | ID Card Auto-Compositing | Localized card cropping and compositing front (top) and back (bottom) onto single 300 DPI A4 page | M2 | Survey / R1 |
| 6 | Book Curvature Viewfinder & Spine Guide | Viewfinder overlay with vertical dashed spine guide and Page 1 (Left) / Page 2 (Right) indicators | M2 | Survey / R1 |
| 7 | Book Dual-Page Automatic Splitting | Detects center spine via vertical luminance valley profiling and splits into two consecutive document pages | M2 | Survey / R1 |
| 8 | Book Curvature Correction | Clean-room 1D cylindrical transformation correcting text compression and crease shadow near book spine | M2 | Survey / R1 |
| 9 | Room Hierarchical Folders Schema | Adds `parentFolderId` and `path` to `Folder` entity with CASCADE deletion and recursive queries | M1 | Survey / R4 |
| 10 | Bundled Offline ML Kit Integration | Migrates to `com.google.mlkit:text-recognition:16.0.0` ensuring 100% offline Latin text recognition | M1 | Survey / R3 |
| 11 | Collage Studio 3-Page Layouts | Expands `CollageGenerator` to support 3-page layouts (`THREE_VERTICAL`, `THREE_TOP_TWO_BOTTOM`) alongside 2, 4, 6 | M1 | Survey / R2 |
| 12 | Page Manager Reordering | Drag-and-drop or move controls in document page grid to reorder `pageIndex` in Room DB | M3 | Survey / R2 |
| 13 | Page Manager 90° Rotation | Rotates individual page in 90° increments, updates scanned image on disk, and regenerates thumbnail | M3 | Survey / R2 |
| 14 | Page Manager Page Deletion | Deletes individual page files and Room records, renumbering subsequent pages | M3 | Survey / R2 |
| 15 | Page Manager Add Page | Appends new pages to existing document from Camera capture or Gallery storage import | M3 | Survey / R2 |
| 16 | Collage Studio Screen | Interactive multi-page collage creation UI supporting 2, 3, 4, and 6 page configurations | M3 | Survey / R2 |
| 17 | In-App PDF Viewer | Full in-app PDF document viewer using native `PdfRenderer` with pan, pinch-zoom, and page navigation | M3 | Survey / R2 |
| 18 | Custom Security Text Watermark | Configurable watermark overlay with custom text, opacity slider (0.1-0.9), and rotation angle presets/slider | M3 | Survey / R2 |
| 19 | PDF Password Encryption | PDFBox AES 128-bit encryption with user-specified access password | M3 | Survey / R2 |
| 20 | PDF Compression Level Controls | Compression quality presets: Low (small file, 50% JPEG), Medium (75%), High (92%) | M3 | Survey / R2 |
| 21 | Vector Digital Signature Drawing | Compose Canvas touch drawing with Bézier curve smoothing, ink colors, and stroke thickness | M3 | Survey / R2 |
| 22 | Interactive Signature Placement & Scale | Draggable, resizable overlay over document page to position and scale signature before burning to bitmap | M3 | Survey / R2 |
| 23 | Multi-Action Share & Export Sheet | Bottom sheet with Share PDF, Share JPG, Long Image stitch, Extract Text, Split PDF, Save to Gallery, Email | M3 | Survey / R2 |
| 24 | Page Quick Edit Toolbar | Toolbar with Anti-theft watermark, Signature, PDF Password, and Compression controls | M3 | Survey / R2 |
| 25 | Document More & PDF Formatting Sheet | Detailed settings for Page Number position, Direction, and Paper Size grid (A4, A3, Letter, Legal, etc.) | M3 | Survey / R2 |
| 26 | Offline OCR Text Extraction Engine | Extracts text from page bitmap using bundled ML Kit on `Dispatchers.Default` | M4 | Survey / R3 |
| 27 | OCR Text Viewer & Proofreading Editor | Split/modal screen displaying scanned page above and editable `TextField` below for proofreading | M4 | Survey / R3 |
| 28 | OCR Copy & TXT File Export | One-tap clipboard copy and `.txt` file generation with Android share sheet export | M4 | Survey / R3 |
| 29 | Hierarchical Folder Navigation & Breadcrumbs | Folder tree navigation with interactive breadcrumbs ("Root > Invoices > 2026") and folder creation | M4 | Survey / R4 |
| 30 | Document Move & Copy Dialogs | Ability to move or copy documents between hierarchical folders | M4 | Survey / R4 |
| 31 | Multi-Select Batch Operations | Bulk delete, bulk move to folder, and bulk share / multi-document PDF compilation | M4 | Survey / R4 |
| 32 | Dedicated Tools Hub Tab | Bottom navigation tab with cards for ID Card, Book, Batch, Collage, OCR, PDF Tools, Signature | M4 | Survey / R4 |
| 33 | 100% Offline Sandbox & Clean-Room Standards | Zero network permissions, app-private storage, zero proprietary assets, zero competitor branding | M4 | Survey / R5 |
| 34 | Full E2E Test Suite (Tiers 1-4) | Comprehensive opaque-box test suite verifying 100% of features and boundaries | M5 | Project Spec |
| 35 | Adversarial Hardening (Tier 5) | White-box stress testing and edge-case verification for unhandled paths | M5 | Project Spec |

---

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Data Layer, Bundled ML Kit & Collage Math | Room hierarchical folders (`parentFolderId`, `path`), bundled offline ML Kit `16.0.0`, 3-page collage layout math | none | DONE |
| M2 | Camera Capture Suite (Batch, ID Card, Book) | Batch burst queue & deferred warp review, ID Card 1.586 guide & A4 composite, Book spine detection & split | M1 | PLANNED |
| M3 | Document Detail, Page Manager & PDF Studio | Page Manager reorder/rotate/delete/add, in-app PDF viewer, watermark opacity/angle, password, signature overlay, Collage UI | M1 | PLANNED |
| M4 | Folder Hierarchy UI, Batch Operations & Tools Hub | Breadcrumb folder navigation, move/copy dialogs, multi-select batch bar, OCR viewer/editor/export, Tools tab | M1, M2, M3 | PLANNED |
| M5 | Final Milestone: E2E Test Pass & Hardening | Pass 100% of E2E tests (Tiers 1-4) published by E2E Testing Track, adversarial coverage hardening (Tier 5) | M1, M2, M3, M4 | PLANNED |

---

## Interface Contracts

### `ScannerRepository` ↔ UI ViewModels
- `reorderPages(documentId: Long, orderedPages: List<Page>)`: Transactional batch update of `pageIndex`.
- `rotatePage(pageId: Long, clockwise: Boolean = true)`: Rotates disk bitmap 90°, updates thumbnail, writes `rotationDegrees`.
- `deletePage(pageId: Long)`: Deletes disk files, deletes page row, re-indexes remaining pages, updates `document.pageCount`.
- `stitchIdCardPages(frontBitmap: Bitmap, backBitmap: Bitmap, targetDocId: Long?): Long`: Creates 300 DPI A4 composite and saves as new page.
- `splitAndSaveBookPages(spreadBitmap: Bitmap, spineX: Int, targetDocId: Long?): Pair<Long, Long>`: Splits spread into Left and Right pages and saves consecutively.
- `stampSignatureOnPage(pageId: Long, signatureBitmap: Bitmap, normX: Float, normY: Float, normW: Float, normH: Float)`: Composites signature at normalized bounds onto page bitmap.
- `moveDocumentToFolder(docId: Long, targetFolderId: Long)`: Updates `folderId`.
- `copyDocumentToFolder(docId: Long, targetFolderId: Long): Long`: Deep-copies document and all page files.

### `CollageGenerator`
- `generateCollage(pages: List<Bitmap>, layout: CollageLayout): Bitmap`:
  - `TWO_VERTICAL`, `TWO_HORIZONTAL`, `THREE_VERTICAL`, `THREE_TOP_TWO_BOTTOM`, `FOUR_GRID`, `SIX_GRID`.
  - Output: 300 DPI A4 bitmap ($2480 \times 3508$).

### `PdfGenerator` & `PdfExportOptions`
- `PdfExportOptions`:
  - `pageSize: PdfPageSize` (A4)
  - `quality: PdfQuality` (LOW, MEDIUM, HIGH)
  - `password: String? = null`
  - `watermarkText: String? = null`
  - `watermarkOpacity: Float = 0.30f`
  - `watermarkRotationAngle: Float = -45f`

### `OcrEngine`
- `recognizeText(bitmap: Bitmap): String`: Suspends coroutine using bundled `com.google.mlkit:text-recognition` without network calls.

---

## Code Layout
- `app/src/main/java/org/scancraft/`:
  - `camera/`: `CameraScreen.kt`, `CameraViewModel.kt`, `CaptureMode.kt`, `ViewfinderOverlay.kt`, `BatchReviewScreen.kt`, `BookSpineDetector.kt`
  - `cv/`: `NativeScannerEngine.kt`, `EdgeDetector.kt`, `PerspectiveTransformer.kt`, `ImageEnhancer.kt`, `CollageGenerator.kt`, `OcrEngine.kt`
  - `data/local/`: `AppDatabase.kt`, `DocumentDao.kt`, `PageDao.kt`, `FolderDao.kt`
  - `data/model/`: `DocumentAndPage.kt`, `Folder.kt`
  - `data/repository/`: `ScannerRepository.kt`
  - `pdf/`: `PdfGenerator.kt`, `PdfExportOptions.kt`
  - `ui/`:
    - `home/`: `HomeScreen.kt`, `HomeViewModel.kt`, `HomeTopBar.kt`, `HomeToolsTab.kt`, `BatchSelectionBottomBar.kt`, `FolderBreadcrumbs.kt`, `MoveCopyDialog.kt`
    - `detail/`: `DocumentDetailScreen.kt`, `DocumentDetailViewModel.kt`, `PageGridCard.kt`
    - `viewer/`: `PageViewerScreen.kt`, `PdfViewerScreen.kt`, `OcrViewerScreen.kt`, `SignatureOverlay.kt`
    - `collage/`: `CollageStudioScreen.kt`
    - `signature/`: `SignatureDialog.kt`
    - `theme/`: `Theme.kt`, `Color.kt`
- `app/src/test/java/org/scancraft/`:
  - Unit tests for DAOs, algorithms, collage, PDF, and repository.
- `app/src/androidTest/java/org/scancraft/`:
  - E2E and instrumentation test suites.
