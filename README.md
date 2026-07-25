Disclaimer: Credits to the owner of the System.. Used as a reference in the creation.

A commercial-grade, full-screen kiosk photobooth desktop application built with Java 17 + JavaFX 21.

## Features

- **Full-screen Kiosk Mode** — Immersive photobooth experience
- **Live Camera Feed** — Real-time webcam preview with template overlay guide
- **Multi-Photo Capture** — Visual countdown (3, 2, 1) with per-photo accept/retake support
- **Visual Slot Editor** — Admin places up to 3 photo slots manually with independently draggable quad corners, zoom, and magnifier loupe for precision
- **Photo Strip Collage** — Composites photos into template slots using bilinear quad warp
- **Google Drive Upload** — Async upload via OAuth 2.0 with public sharing
- **QR Code Generation** — Scannable QR code linking to the uploaded photo
- **Printing Support** — Send photo strips directly to the system printer
- **Admin Dashboard** — Password-protected management panel

## Tech Stack

| Component | Technology |
|---|---|
| Language | Java 17+ |
| GUI | JavaFX 21 + FXML |
| Camera | JavaCV / OpenCV |
| QR Codes | ZXing (Zebra Crossing) |
| Cloud | Google Drive API v3 (OAuth 2.0) |
| Printing | Java Print Service API |
| Build | Maven |

## Prerequisites

- **Java 17** or higher (`java -version`)
- **Maven 3.8+** (`mvn -version`)
- **Webcam** connected and accessible
- **macOS**: Grant camera permission to Terminal/IDE in System Preferences → Privacy & Security → Camera

## How to Run

### 1. Clone & Build
```bash
git clone https://github.com/yourusername/BoothYeah.git
cd BoothYeah
mvn clean compile
```

### 2. Run
```bash
mvn javafx:run
```

The app launches in full-screen mode. Press `Esc` to exit fullscreen.

### 3. Access Admin Dashboard
From the Idle screen, press **`Ctrl + Shift + A`** and enter the PIN (default: `1234`).

## Admin Dashboard

| Tab | Features |
|---|---|
| **Templates** | Add/delete photo strip templates (PNG). Opens visual slot editor on add. |
| **Google Drive** | Set folder ID, load OAuth credentials, enable/disable uploads, test connection. |
| **Printer** | Enable/disable printing, select printer from system list, test print. |
| **Settings** | Countdown duration, auto-reset timeout, change admin PIN. |
| **History** | View session count, output folder size, clear all output. |

## Templates

Templates are PNG images. After adding a template, the admin uses the **Slot Editor** to manually define 1–3 photo slot regions as quadrilaterals (each corner independently draggable). Slot layout is saved as a sidecar `<templateId>.layout.json` file alongside the template PNG.

Place templates in `data/templates/` — the app loads them on startup.

Recommended specs:
- Format: PNG (transparent cutout areas as guide, overridden by slot editor)
- Example: 600×1800px vertical photo strip with 3 slots

## Google Drive Setup

1. Create a [Google Cloud Project](https://console.cloud.google.com/)
2. Enable the **Google Drive API**
3. Go to **APIs & Services → Credentials** → Create **OAuth 2.0 Client ID**
   - Application type: **Desktop app**
4. Download the credentials file and save it as `data/credentials/client_secret.json`
5. Run the app — on first upload, a browser window opens to authorize access
6. After authorization, the token is saved to `data/credentials/token.json` for future use
7. Enter the target Google Drive **folder ID** in Admin Dashboard → Google Drive tab

## Project Structure

```
src/main/java/com/boothyeah/
├── App.java                        # Entry point + service locator
├── AppConfig.java                  # Constants and paths
├── model/
│   ├── CaptureState.java           # State machine enum
│   ├── CaptureSession.java         # Session data (photos + template)
│   ├── PhotoTemplate.java          # Template with slot regions
│   ├── SlotRegion.java             # Photo cutout quad coordinates
│   ├── SlotLayout.java             # Sidecar JSON POJO (Gson)
│   └── AppSettings.java            # Persisted settings
├── controller/
│   ├── IdleController.java
│   ├── TemplateController.java
│   ├── CaptureController.java      # Camera feed + countdown + retake + template overlay
│   ├── ProcessingController.java
│   ├── ResultController.java
│   └── AdminController.java
├── service/
│   ├── CameraService.java          # Webcam management (JavaCV)
│   ├── CaptureSessionManager.java  # Capture state machine
│   ├── TemplateService.java        # Template CRUD + sidecar layout persistence
│   ├── CollageService.java         # Image compositing (rect + quad warp)
│   ├── GoogleDriveService.java     # Upload + sharing
│   ├── QRCodeService.java          # QR generation
│   ├── PhotoPrintService.java      # System printing
│   ├── SettingsService.java        # JSON config persistence
│   └── NavigationService.java      # Screen transitions
└── util/
    ├── ImageUtils.java             # AWT ↔ JavaFX conversion
    ├── QuadWarpRenderer.java       # Bilinear inverse warp (Newton-Raphson)
    └── ThreadPool.java             # Shared background threads
```

## User Flow

```
Idle Screen → Template Selection → Capture (×N photos, each with accept/retake) → Processing → Result
     ↑                                                                                           │
     └─────────────────────────────── Done / Auto-reset ─────────────────────────────────────────┘
```
