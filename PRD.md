# ScanCraft — Product Requirements Document (PRD)

## 1. Executive Summary & Product Vision

**ScanCraft** is a modern, high-performance, open-source document scanning application built natively for Android. It delivers an offline-first, privacy-respecting alternative to closed-source commercial scanner apps without advertisements, tracking beacons, forced account logins, watermarks, or cloud subscriptions.

ScanCraft leverages modern Android architecture (Jetpack Compose, CameraX, Room) coupled with high-throughput native C++ image processing algorithms (Homography Perspective Warp, CLAHE Contrast Enhancement, Bradley-Roth Adaptive Binarization) and Google ML Kit on-device Text Recognition.

---

## 2. Target Audience & Core Value Proposition

### 2.1 Target Personas
* **Privacy-Minded Users:** Individuals unwilling to upload confidential contracts, IDs, or financial records to third-party servers.
* **Professionals & Accountants:** Users needing crisp, multi-page PDF generation, password-protected documentation, custom watermarks, and collage layouts (e.g., identity cards on a single sheet).
* **Students & Researchers:** Users needing high-speed batch document capture, offline text extraction (OCR), and organized folder hierarchies.

### 2.2 Core Differentiators
1. **100% Local Processing:** Zero network requests required. All scanning, enhancement, and PDF generation occur strictly in-memory and within the app's private sandbox.
2. **Zero Commercial Bloat:** No ads, no popups, no tracking analytics, no feature paywalls.
3. **Native Speed:** Real-time 60fps edge detection on viewfinder frames and sub-second native C++ document transformation.

---

## 3. Functional Requirements (FR)

### FR-1: High-Speed Camera Viewfinder (CameraX)
* **FR-1.1 Real-Time Document Detection:** Continuous analysis of camera frames using Sobel gradient contour calculation, rendering a high-contrast emerald green laser polygon over detected document boundaries.
* **FR-1.2 Multi-Capture Modes:**
  * **Single Mode:** Standard capture with immediate post-capture crop and filter workflow.
  * **Batch Mode:** Rapid continuous shutter for digitizing multi-page books and dossiers without navigating away from the camera.
  * **ID Card Mode:** Guided dual-sided (front and back) capture automatically aligned onto a single standard A4 canvas.
  * **Book Mode:** Dual-page split scanning with curvature correction.
  * **QR & Barcode Mode:** Real-time decoding of standard 1D/2D barcodes with instant action dispatch.
* **FR-1.3 Viewfinder Controls:** Flash modes (Off, On, Auto, Torch), Rule-of-Thirds grid overlay, and touch-to-focus indicator.

### FR-2: Precision 8-Point Crop Editor
* **FR-2.1 Interactive Handles:** 4 corner circular handles plus 4 edge midpoint pill handles allowing non-rigid quadrilateral deformation.
* **FR-2.2 Magnifier Loupe:** Floating circular loupe displaying 2x zoomed content centered above the touch contact point, featuring an emerald crosshair to enable millimeter-accurate corner placement.
* **FR-2.3 Auto-Crop & Reset:** One-tap auto-crop boundary calculation and "Select All" full-frame fallback.

### FR-3: High-Performance Image Enhancement (C++ NDK)
* **FR-3.1 Filter Suite:**
  * **Magic Color:** Local adaptive histogram equalization (CLAHE) + paper background whitening + vivid saturation boost.
  * **B&W / B&W 2:** Bradley-Roth and Sauvola adaptive local thresholding for binarized text contrast.
  * **Grayscale:** Contrast-stretched 8-bit monochromatic output.
  * **Lighten / No-Shadow:** Coarse-grid background illumination estimation for shadow correction.
* **FR-3.2 Manual Parameter Sliders:** Granular manual tuning of Brightness (-100 to +100), Contrast (-100 to +100), and Sharpness.

### FR-4: Document & Folder Organization
* **FR-4.1 Folder Hierarchy:** Custom folders with document counts, Manila folder vector previews, creation, rename, and deletion capabilities.
* **FR-4.2 Sort & View Modes:**
  * Sort by Date (Newest/Oldest), Name (A-Z), and Page Count.
  * Instant toggle between 2-Column Responsive Grid and Informative Row List views.
* **FR-4.3 Batch Selection:** Multi-select mode for moving documents across folders, bulk deletion, or merged PDF export.
* **FR-4.4 Real-Time Search:** Instant filtering across document titles and extracted OCR text.

### FR-5: Document Detail & Page Studio
* **FR-5.1 2-Column Page Grid:** Page thumbnails with high-contrast bottom badges ("01", "02", etc.) and pinch-to-zoom full-screen viewer.
* **FR-5.2 5-Action Bottom Navigation:**
  1. **Add:** Launch batch camera to append pages to the active document.
  2. **Collage:** Multi-page layout studio supporting 2-in-1, 4-in-1, and 6-in-1 arrangements on an A4 sheet.
  3. **Share:** Native Android Share Sheet dispatch for PDFs or images.
  4. **Edit PDF:** Comprehensive PDF configuration dialog (Page size, compression, watermark, encryption).
  5. **More:** Digital signature placement, rename, and folder migration.
* **FR-5.3 E-Signature & Paper Stamp:** Vector signature canvas supporting black, blue, and red ink with transparency masking.

### FR-6: Professional PDF Generation Engine
* **FR-6.1 Paper Geometry:** Standard **A4**, **US Letter**, **US Legal**, and **Fit to Image**.
* **FR-6.2 Quality Presets:** High (lossless/minimal compression), Medium (balanced), and Small (maximum compression for email sharing).
* **FR-6.3 Security & Watermarking:** AES 128-bit PDF encryption with password protection and custom diagonal text watermarks.

### FR-7: On-Device Machine Learning OCR
* **FR-7.1 Offline Text Extraction:** Google ML Kit Text Recognition running entirely on-device.
* **FR-7.2 Text Actions:** One-tap clipboard copy, search indexing, and plain text export.

---

## 4. Non-Functional Requirements (NFR)

* **NFR-1 Security & Privacy:** Zero internet access permissions (`android.permission.INTERNET` is omitted from `AndroidManifest.xml`). Data stored in app-private sandbox.
* **NFR-2 Latency & Performance:** Viewfinder boundary detection must maintain >= 30 FPS. Native homography warp on 12MP images must complete within <= 150ms on modern ARM64 chipsets.
* **NFR-3 Memory Safety:** Proactive bitmap recycling, downsampled preview buffers, and background coroutine offloading to eliminate `OutOfMemoryError`.
* **NFR-4 Android OS Compatibility:** Supports Android 8.0 (API level 26) through Android 15+ (API level 35).
* **NFR-5 Accessibility & Theme:** Full compliance with Material Design 3, dynamic Edge-to-Edge window insets, AMOLED Dark Theme (`#000000`), and Clean Paper Light Theme (`#F8F9FA`).
