# ScanCraft — Design System & UI/UX Guidelines (DESIGN.md)

## 1. Visual Philosophy & Identity

ScanCraft's visual identity balances **modern minimalism, high-speed utility, and extreme readability**. 

* **Clean-Room Vector Visuals:** 100% of custom graphics (such as Manila folder vectors and crop loupes) are drawn programmatically using Jetpack Compose `Canvas` and standard Google Material 3 vector assets. Zero copyrighted icons or commercial graphic assets are used.
* **Dual-Theme First:** First-class support for **High-Contrast AMOLED Dark Mode** (`#000000`) for OLED power savings, alongside an ultra-clean **Paper Light Mode** (`#F8F9FA`) engineered for daylight document readability.
* **Instant Tactile Feedback:** High-contrast emerald interactive indicators, smooth gesture drags, and live visual confirmations.

---

## 2. Color System & Semantic Tokens

### 2.1 Core Brand Colors
| Token | Hex Value | Semantic Role |
| :--- | :--- | :--- |
| `ScannerGreen` | `#00BA66` | Primary brand emerald green, shutter buttons, active toggles |
| `ScannerGreenLight` | `#00D675` | Highlight green, active chips, laser viewfinder borders |
| `ScannerGreenDark` | `#008C4C` | Pressed green, active container text |
| `ScannerFolderYellow`| `#F0AF3A` | Warm Manila folder body & tab color |
| `ScannerBlueAccent` | `#0D7DFC` | PDF tools, import accents |
| `ScannerRed` | `#EE3F3F` | Destructive actions, delete confirmations |

### 2.2 AMOLED Dark Theme Tokens
* **Background (`#000000`):** Pure OLED black for maximum contrast and battery efficiency.
* **Surface / Card (`#141414`):** Elevated dark graphite card surface.
* **Surface Secondary (`#1E1E1E`):** Secondary chips, thumbnail placeholder boxes, and subtle pill containers.
* **Card Border (`#242424`):** Crisp 0.5dp card outline for structural clarity.
* **Text Primary (`#FFFFFF`):** High-contrast white for headings and document titles.
* **Text Secondary (`#8E8E93`):** Neutral gray for timestamps, page counters, and subtitles.
* **Text Muted (`#636366`):** Low-contrast gray for search placeholders and disabled states.
* **Primary Container (`#1E2822`):** Subtle emerald-tinted dark container.

### 2.3 Paper Light Theme Tokens
* **Background (`#F8F9FA`):** Soft, non-glare off-white background mimicking high-grade paper.
* **Surface / Card (`#FFFFFF`):** Pure white document cards with subtle elevation.
* **Surface Secondary (`#F1F3F5`):** Soft gray container for chips and inactive buttons.
* **Card Border (`#E9ECEF`):** Subtle light border for card delineation.
* **Text Primary (`#17202B`):** Deep slate black for crisp typographic readability.
* **Text Secondary (`#6C757D`):** Balanced neutral slate for secondary metadata.
* **Text Muted (`#ADB5BD`):** Light slate for placeholder inputs.
* **Primary Container (`#DCF5E8`):** Mint green container tint for selected items.

---

## 3. Typographic Hierarchy

| Level | Size | Weight | Line Height | Usage |
| :--- | :--- | :--- | :--- | :--- |
| **Headline Large** | 21sp | ExtraBold | 28sp | Top App Header ("ScanCraft") |
| **Headline Medium**| 19sp | Bold | 26sp | Folder title headers ("All docs (12) ▾") |
| **Title Medium** | 16-17sp | Bold | 22sp | Section headers, dialog titles, top bar titles |
| **Body Large** | 15sp | SemiBold | 20sp | Document title cards, tool cards |
| **Body Medium** | 13-14sp | Medium / Normal | 18sp | Folder item names, input text fields |
| **Caption** | 11-12sp | Normal / Bold | 16sp | Timestamps, page counters ("01", "02"), bottom navigation labels |

---

## 4. Key Reusable Components & Interaction Patterns

### 4.1 Manila Folder Vector (`FolderGraphic.kt`)
Drawn programmatically using Compose `Path`:
* Base tab and backing sheet with a 6dp corner radius.
* Internal white sheet peeking out from the folder tab (`#F8F9FA`).
* Distinct front flap with a gentle bezier curve.

### 4.2 Signature Floating Emerald Shutter FAB (`HomeBottomBar.kt`)
* Size: 60x60dp circle centered above the bottom navigation bar.
* Elevation: 10dp drop shadow with an offset of `-14dp` on the Y-axis.
* Color: `ScannerGreen` (`#00BA66`) with a crisp white camera glyph.

### 4.3 Interactive 8-Point Crop Overlay & Magnifier Loupe (`DraggablePolygonOverlay.kt`, `MagnifierLoupe.kt`)
* **Corner Handles (4):** 16dp radius circular touch targets with a white center and emerald 3.5dp stroke.
* **Edge Midpoint Handles (4):** 9dp radius pill handles for single-axis edge dragging.
* **Magnifier Loupe:** 110dp diameter floating circular loupe positioned above the user's active touch finger to prevent occlusion, rendering a 2x magnified bitmap preview with centered emerald crosshairs.

### 4.4 Verified Scanned Badge
* Displayed on the bottom-right corner of document preview cards.
* 15-18dp emerald circle featuring a crisp white checkmark (`Icons.Default.Check`) indicating the document has been successfully processed and stored locally.

### 4.5 Two-Digit Page Badges ("01", "02") (`PageGridCard.kt`)
* Positioned on a translucent black bottom strip (`Color.Black.copy(alpha = 0.60f)`) spanning the thumbnail card.
* Displays high-contrast formatted page indices (`01`, `02`, `03`...) matching professional document archive layouts.

### 4.6 Theme Switcher Control (☀️ / 🌙)
* Quick-toggle icon button in `HomeTopBar` providing instantaneous transition between AMOLED Dark and Paper Light modes.
* Full-width theme control card in `HomeToolsTab` with live status indicator and switch control.

---

## 5. Reverse-Engineered Reference Screen Specifications (100% Clean-Room)

### 5.1 Collage Studio Screen (`CollageStudioScreen.kt`)
* **Top App Bar**: Back navigation icon, "Collage" bold title, and top-right green pill action button (`ScannerGreen`, "Done").
* **Preview Canvas**: Dynamic real-time preview of stacked/arranged document pages rendered onto a dark canvas with subtle drop shadows.
* **Template Selector Carousel**:
  - Horizontal scrollable template cards with graphical slot outlines:
    - `Question Book`: Multi-column exam/question paper layout.
    - `Driver License` (ID Card 2-in-1): Top-and-bottom stacked cards for standard identification cards.
    - `Passport`: Side-by-side photo + biographical text blocks.
    - `2x1`: Two pages stacked vertically.
    - `1x2`: Two pages placed side-by-side horizontally.
    - `4-Grid`: $2 \times 2$ quadrant grid.
    - `6-Grid`: $3 \times 2$ compact overview.
* **Bottom Tool Tabs**:
  - `Page` (+ icon): Add or swap pages in the collage.
  - `Add a watermark` (Wave icon): Opens security text watermark overlay dialog.
  - `Template` (Grid icon - active emerald): Toggles the template carousel.
  - `Size` (Paper icon): Dropdown/dialog to pick paper size (A4, Letter, Legal, Auto).

### 5.2 Multi-Page Share & Export Bottom Sheet (`ShareExportBottomSheet.kt`)
* **Header**: Title formatted as `"[N] picture(s) selected"` with close `(X)` icon on the top-right.
* **Horizontal Thumbnail Carousel**:
  - Displays selected page thumbnails with subtle rounded corners and page badges.
  - Subtle footer text: `"Processed locally with ScanCraft"`.
* **Export Action Menu Items**:
  - `Share PDF`: Shows estimated PDF file size (e.g., `(7.10MB)`) with emerald `"Often use"` pill badge.
  - `Share JPG`: Shows estimated total image archive file size (e.g., `(7.09MB)`).
  - `Share as long image`: Merges all selected pages vertically into a seamless continuous image.
  - `Extract Text`: Triggers offline on-device OCR and displays estimated text payload size (e.g., `(2.64KB)`).
  - `Split PDF into Individual Pages`: Splits multi-page PDF into single-page documents.
  - `Save to Gallery`: Exports high-resolution JPEGs directly to the public device Pictures/Gallery album.
  - `Email to Myself`: Quick action opening email client with pre-attached PDF/images.

### 5.3 Page Detail Quick Edit Toolbar (`PageViewerScreen.kt`)
* **Top Header**: Document title ("New Doc..."), Back arrow, and prominent green `"Save"` pill button on the right.
* **Page Counter Badge**: High-contrast pill badge on the top-left showing `"1/N"` (e.g., `"1/9"`).
* **Center High-Res Preview**: Vertical gesture-zoomable bitmap rendering.
* **Bottom Quick Edit Action Bar**:
  - `Anti-theft` (Shield/Watermark icon): Opens custom security watermark overlay dialog (text, opacity, angle).
  - `Signature` (Pen icon): Opens signature canvas to sign and stamp vector signatures directly on the page.
  - `PDF Password` (Lock icon): Sets password protection for PDF export.
  - `Compression` (Compress icon): Configures image/PDF compression levels (Low, Medium, High).

### 5.4 Document "More" Settings & PDF Formatting Sheet (`DocumentMoreDialog.kt`)
* **Top Header**: "More" screen with Back arrow.
* **Menu Options**:
  - `Batch edit` (> chevron): Multi-page batch cropping, rotation, and filter application.
  - `Set Tag` (> chevron): Document tagging system.
  - `View by`: Sort order switch with `"First on top"` and `"Last on top"` toggle buttons.
  - `PDF Password`: Quick access to set or remove PDF password encryption.
  - `Page Number`: Segmented visual selector for page numbering:
    - `No page number`
    - `the lower-left`
    - `the bottom center`
    - `the lower-right`
  - `PDF Direction`: Segmented selector:
    - `Auto-adjust`
    - `Landscape`
    - `Portrait`
  - `PDF Page Size`: Grid of standard paper size buttons with dimensions in cm/inches:
    - `Auto Fit`
    - `A3` ($29.7\text{cm} \times 42.0\text{cm}$)
    - `A4` ($21.0\text{cm} \times 29.7\text{cm}$, default selected)
    - `A5` ($14.8\text{cm} \times 21.0\text{cm}$)
    - `B4` ($25.0\text{cm} \times 35.3\text{cm}$)
    - `B5` ($17.6\text{cm} \times 25.0\text{cm}$)
    - `Letter` ($21.6\text{cm} \times 27.9\text{cm}$)
    - `Tabloid` ($27.9\text{cm} \times 43.2\text{cm}$)
    - `Legal` ($21.6\text{cm} \times 35.6\text{cm}$)
    - `Executive` ($18.4\text{cm} \times 26.7\text{cm}$)
    - `Postcard` ($10.0\text{cm} \times 14.7\text{cm}$)

