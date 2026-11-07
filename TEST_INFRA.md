# ScanCraft Test Infrastructure & E2E Test Plan (TEST_INFRA.md)

## 1. Test Philosophy & Principles

ScanCraft testing adheres to an **opaque-box, requirement-driven methodology**:
- **Requirement-Driven**: Tests are derived strictly from user requirements (`ORIGINAL_REQUEST.md`) and system specifications (`PROJECT.md`). Tests verify expected inputs and outputs against documented behavioral contracts rather than internal implementation quirks.
- **Opaque-Box Verification**: The test suite exercises public contracts, APIs, ViewModels, Room DAOs, CV algorithms, and data structures.
- **100% Offline Sandbox Isolation**: No network connectivity, external servers, cloud endpoints, or analytics services may be contacted. Tests execute deterministically in airplane/isolated mode.
- **Clean-Room & IP Integrity**: Zero proprietary assets, copyrighted resources, or third-party trademarked names in test fixtures, mocks, or assertions.
- **Tiered Multi-Level Architecture**: Tests are categorized into Tiers 1 through 5, ensuring progressive coverage from fundamental primitives to complex multi-step user workflows and adversarial edge cases.

---

## 2. Test Architecture & Runner Invocation

### 2.1 Test Directory Structure
```
app/src/test/java/org/scancraft/
├── camera/
│   ├── CaptureModeTest.kt              # Tier 1 & 2: Capture modes, titles, descriptions
│   └── CameraViewModelTest.kt          # Tier 1, 2, 3: Flash, mode transitions, batch counter
├── cv/
│   ├── DocumentQuadrilateralTest.kt    # Tier 1 & 2: Quad conversions, distance math, bounds
│   ├── CollageGeneratorTest.kt         # Tier 1, 2: Layout formulas, slot aspect fitting, 2/3/4/6 pages
│   └── ImageEnhancerTest.kt            # Tier 1, 2: Bradley-Sauvola, grayscale, brightness/contrast
├── data/
│   ├── model/
│   │   └── DocumentAndPageTest.kt      # Tier 1 & 2: Entities, cascade foreign keys, EnhanceMode
│   ├── local/
│   │   └── DaosContractTest.kt         # Tier 1 & 2: Query semantics, batch updates, unlinking
│   └── repository/
│       └── ScannerRepositoryTest.kt    # Tier 1, 2, 3: Document CRUD, pagination, stitching, signing
├── pdf/
│   ├── PdfOptionsTest.kt               # Tier 1 & 2: Page sizes, compression levels, watermark options
│   └── PdfGeneratorContractTest.kt     # Tier 1, 2, 3: AES 128 encryption policy, empty handling
├── ocr/
│   └── OcrEngineContractTest.kt        # Tier 1 & 2: Offline Latin ML Kit recognition contract
├── ui/
│   ├── HomeViewModelTest.kt            # Tier 1, 2, 3: Sorting, search filtering, multi-select
│   └── DocumentDetailViewModelTest.kt  # Tier 1, 2, 3: Page reordering, rename, move, delete
└── e2e/
    └── E2EWorkflowsTest.kt             # Tier 3 & 4: Multi-step end-to-end user workflows
```

### 2.2 Test Runner Invocation
All unit and integration tests are executed via Gradle on the local JVM:
```bash
./gradlew testDebugUnitTest
```
For individual test suites:
```bash
./gradlew testDebugUnitTest --tests "com.krinry.scancraft.cv.CollageGeneratorTest"
./gradlew testDebugUnitTest --tests "com.krinry.scancraft.e2e.E2EWorkflowsTest"
```

---

## 3. Feature Inventory Mapping (32 Features across Tiers 1–4)

| # | Feature | Milestone | Tier 1 (Unit / Happy Path) | Tier 2 (Boundary & Corner Cases) | Tier 3 (Cross-Feature Integrations) | Tier 4 (Real-World E2E Scenarios) |
|---|---------|-----------|----------------------------|----------------------------------|-------------------------------------|-----------------------------------|
| 1 | Batch Burst Capture | M2 | `CameraViewModelTest`: batch count increments on shutter tap | Rapid multi-tap bursts; 0-count initial state | Batch queue -> temporary cache file persistence | Office document multi-page batch scanning session |
| 2 | Deferred Batch Review & Edit | M2 | Mode switching to BATCH; batch counter verification | Empty batch validation; maximum batch limit | Batch capture -> Page Manager grid import | Bulk invoice intake with post-capture review |
| 3 | ID Card 2-in-1 Viewfinder Guide | M2 | Frame ratio contract (1.586 ID-1 standard) | Aspect ratio scaling across screen widths | Guide overlay coordinates -> crop bounding box | Government ID card scanning workflow |
| 4 | ID Card Two-Step Sequential Capture | M2 | Sequential step tracking (Front -> Back) | Back cancellation resets front state | Front capture + back capture in sequence | Driver's license front & back intake |
| 5 | ID Card Auto-Compositing | M2 | `stitchIdCardPages()` creates A4 (2480x3508) canvas | Contrasting card orientations; card margin bounds | Front + Back bitmaps -> A4 composite page | Single-page legal ID record export |
| 6 | Book Curvature Viewfinder & Spine Guide | M2 | Spine guide midpoint positioning | Spine off-center offsets; landscape spread | Central spine split guide overlay | Dual-page textbook scanning |
| 7 | Book Dual-Page Automatic Splitting | M2 | `splitAndSaveBookPages()` creates 2 consecutive pages | Single blank page spread; odd aspect ratios | Book split -> Page Manager consecutive index | Book chapter digitization |
| 8 | Book Curvature Correction | M2 | 1D cylindrical transformation math | Flat page vs high curvature distortion | Curvature unwarp -> edge enhancement | Thick hardcover binding crease correction |
| 9 | Room Hierarchical Folders Schema | M1 | `DocumentDao`, `FolderDao`: folder insert, queries | Root folder (`folderId = -1`); deep folder paths | Folder creation -> Document move -> Doc count | Multi-level corporate document archive |
| 10 | Bundled Offline ML Kit Integration | M1 | `OcrEngineContractTest`: bundled client instantiation | Empty bitmap / white page; corrupted image | Scanned bitmap -> OCR extraction | Offline legal contract text transcription |
| 11 | Collage Studio 3-Page Layouts | M1 | `THREE_VERTICAL` & `THREE_TOP_TWO_BOTTOM` layout math | 3 pages in 2-page or 4-page slots | 3-page selection -> collage generation | Triple receipt A4 collage sheet |
| 12 | Page Manager Reordering | M3 | `reorderPages()` batch index updates in DAO | Single page reorder; reverse order sequence | Capture -> Reorder -> PDF compilation | Inverted scan sequence correction |
| 13 | Page Manager 90° Rotation | M3 | 90°, 180°, 270° rotation degrees tracking | Modulo 360° rotation (4x 90° returns to 0°) | Rotate page -> Thumbnail regeneration | Mixed orientation packet alignment |
| 14 | Page Manager Page Deletion | M3 | `deletePage()` removes files and updates pageCount | Deleting only page deletes document; last page | Batch capture -> delete spoiled scan -> save | Document curation and cleanup |
| 15 | Page Manager Add Page | M3 | Appending page increments `pageCount` and index | Insert at end of document; gallery import | Camera capture -> Append to existing doc | Appending addendum to existing contract |
| 16 | Collage Studio Screen | M3 | `CollageLayout` enum (2, 3, 4, 6 configurations) | Minimum page count enforcement; layout change | Multi-select pages -> Collage Studio export | Medical report and lab result compilation |
| 17 | In-App PDF Viewer | M3 | `PdfRenderer` page count & rendering contract | Password-protected PDF prompt; single page PDF | PDF export -> In-app preview verification | Reviewing signed contract before sharing |
| 18 | Custom Security Text Watermark | M3 | Watermark text rendering; opacity (0.1-0.9) | Extreme opacity (0.05, 0.95); angles (-45°, 0°, 90°) | Watermark text + rotation angle -> PDF output | Confidential corporate document watermarking |
| 19 | PDF Password Encryption | M3 | PDFBox AES 128-bit StandardProtectionPolicy | Special characters in password; blank password | Encrypt PDF -> Verify password required to open | Encrypted bank statement archive |
| 20 | PDF Compression Level Controls | M3 | `PdfQuality`: LOW (50%), MEDIUM (75%), HIGH (92%) | Extreme dimensions (8K downscaling); high DPI | Downsampled JPEG bytes -> PDF file size reduction | Email attachment compression workflow |
| 21 | Vector Digital Signature Drawing | M3 | Bezier curve path recording; stroke widths | Blank signature canvas validation; color selection | Draw vector signature -> Render ARGB_8888 bitmap | Authorizing and signing scanned paperwork |
| 22 | Interactive Signature Placement & Scale | M3 | Signature normalized bounds `(normX, normY, normW, normH)` | Page edge clamping; minimum scale limits | Burn signature overlay onto page bitmap | Official legal sign-and-stamp workflow |
| 23 | Offline OCR Text Extraction Engine | M4 | ML Kit text recognition on `Dispatchers.Default` | Non-Latin text; low contrast / noisy text | Document page -> OCR -> Text persistence | Receipt expense report parsing |
| 24 | OCR Text Viewer & Proofreading Editor | M4 | Editable text field with page preview | Text edits saved to `Page.ocrText` | OCR extraction -> Edit text -> Persist to DB | Transcribed meeting notes proofreading |
| 25 | OCR Copy & TXT File Export | M4 | Clipboard copy & `.txt` file export intent | Empty text copy prevention; long document text | OCR text -> UTF-8 TXT export in cache | Text extraction to external text editor |
| 26 | Hierarchical Folder Navigation & Breadcrumbs | M4 | Breadcrumb path string calculation ("Root > Invoices") | Special characters in folder name; duplicate names | Folder hierarchy traversal -> Filter document list | Enterprise folder tree navigation |
| 27 | Document Move & Copy Dialogs | M4 | Move updates `folderId`; deep copy duplicates pages | Move to current folder (no-op); move to root | Multi-document move -> Folder count refresh | Archiving tax documents into fiscal year folders |
| 28 | Multi-Select Batch Operations | M4 | Selection set tracking (`toggleSelectDoc`, `selectAll`) | Select all when empty; clear selection | Select 5 docs -> Batch move / Batch delete | End-of-year document purge and cleanup |
| 29 | Dedicated Tools Hub Tab | M4 | Launcher cards for all 7 tools (ID, Book, Batch, etc.) | Tool launch backstack state reset | Tools tab -> Mode select -> Dedicated capture UI | Direct access to specialized scanner modes |
| 30 | 100% Offline Sandbox & Clean-Room Standards | M4 | Zero network permissions in `AndroidManifest.xml` | Cache / files directory isolation verification | Camera -> DB -> PDF without socket/HTTP calls | Airplane mode mission-critical scanning |
| 31 | Full E2E Test Suite (Tiers 1-4) | M5 | Comprehensive test suite passes with 0 failures | Boundary and corner cases across all modules | Cross-feature pipelines execute end-to-end | Complete app operational verification |
| 32 | Adversarial Hardening (Tier 5) | M5 | Fault injection: corrupt bitmaps, missing files | OOM recovery; concurrent DB writes; null states | Cascade failure isolation and recovery | Mission-critical resiliency validation |

---

## 4. Tier Classification & Test Level Definitions

- **Tier 1 (Feature Primitives & Happy Paths)**:
  - Verifies core functionality of every individual class, method, DAO, and model under standard valid inputs.
- **Tier 2 (Boundary & Corner Cases)**:
  - Exercises extreme dimensions, 0-page documents, single-page collections, special characters, whitespace trimming, 360° rotations, and boundary clamping.
- **Tier 3 (Cross-Feature Integrations)**:
  - Verifies multi-component pipelines: Batch Capture -> Page Manager Reorder -> Watermarked PDF Export; ID Card Capture -> Signature Overlay -> Encrypted PDF; Folder Hierarchy -> Batch Move -> Search.
- **Tier 4 (Real-World Application Scenarios)**:
  - Full end-to-end user journeys simulating complete real-world office and mobile workflows from intake to archive.
- **Tier 5 (Adversarial Hardening)**:
  - Stress testing against corrupt disk files, missing parent directories, concurrent modifications, and resource starvation.

---

## 5. Verification Gate & Pass Criteria

The E2E Test Suite is considered **READY** when:
1. `./gradlew testDebugUnitTest` completes with **BUILD SUCCESSFUL**.
2. Total test count covers all 32 features across Tiers 1–4 with **0 failures** and **0 skipped required tests**.
3. Zero network calls or external dependencies are invoked during execution.
4. `TEST_READY.md` is generated and published at the project root documenting verification results.
