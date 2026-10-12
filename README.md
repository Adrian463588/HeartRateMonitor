# HeartRateMonitor: Dual-Module Vitals Tracking


> **Elevator Pitch:** A robust, dual-module Android system (Mobile + Wear OS) that provides real-time sensing and synchronization of PPG (via Samsung Galaxy Watch) and ECG (via Polar sensors) data for advanced vital signs monitoring.

![Kotlin](https://img.shields.io/badge/Kotlin-1.8-blue.svg) 
![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20Wear%20OS-green.svg) 
![Status](https://img.shields.io/badge/Status-Active-success.svg)

## 📂 Project Structure

This project is organized into three main modules:

| Module | Path | Description |
| :--- | :--- | :--- |
| **Wear App** | `/wear` | The data producer. Runs on the Samsung Galaxy Watch, collecting PPG data via Samsung Health SDK. |
| **Mobile App** | `/mobile` | The data consumer. Runs on the Android Phone, connecting to Polar ECG via BLE and receiving PPG data from the Watch. |
| **Shared** | `/shared` | Contains common data models, constants, and utility classes shared between Mobile and Wear modules. |

## 🚀 How to Run this Project

Follow these steps to set up and run the application on your devices.

### Step 1: Prerequisites

*   **Android Studio Hedgehog** (or newer).
*   **Samsung Health SDK Library**: You must have the Samsung Health SDK `.aar` library.
*   **Devices**:
    *   Samsung Galaxy Watch 4/5/6+ (with Developer Mode enabled).
    *   Android Phone (Android 10+).

### Step 2: Installation

1.  **Clone the Repository**
    ```bash
    git clone https://github.com/Adrian463588/HeartRateMonitor.git
    ```
2.  **Open in Android Studio**
    *   Select the root directory of the cloned project.
    *   Allow Gradle to sync completely (this may take a few minutes).

### Step 3: Configure Samsung SDK Library

> [!CAUTION]
> **Library Requirement:** This project depends on the Samsung Health SDK.
> You must manually place the SDK library file (`.aar`) in the project if it is not already present.

1.  Navigate to `wear/libs/` in your project view.
2.  **Verify** that the Samsung Health SDK `.aar` file is present (specifically `priv-health-tracking-v1.2.0.aar` or similar).
3.  If missing, download it from the Samsung Developer website and paste it into `wear/libs/`.
4.  Sync Gradle.

### Step 4: Deploy to Devices

**Deploying the Mobile App:**
1.  Connect your Android Phone via USB or Wi-Fi debugging.
2.  In Android Studio toolbar, select the **mobile** run configuration.
3.  Click the **Run (Green Play)** button.

**Deploying the Wear App:**
1.  Connect your Galaxy Watch via Wireless Debugging (*Settings -> Developer Options -> Wireless Debugging*).
2.  In Android Studio toolbar, select the **wear** run configuration.
3.  Click the **Run (Green Play)** button.

### Step 5: Grant Permissions

*   **On Watch:** Launch the app. You **MUST** accept the **Body Sensors** permission prompt immediately.
*   **On Phone:** Launch the app. Accept **Bluetooth** and **Location** permissions to enable scanning.

## ✨ Key Features

*   **Multi-Channel PPG:** Simultaneous recording of Green, Infrared (IR), and Red light photoplethysmography.
*   **High-Fidelity ECG:** Direct integration with Polar H10 and Verity Sense sensors.
*   **Real-Time Sync:** Sub-second latency synchronization (state & data) between Watch and Phone.
*   **Live Plotting:** Dynamic visualization of waveforms on the mobile dashboard.
*   **Background Recording:** Robust foreground services ensure data collection continues even when the screen is off.

## 🔐 Critical Permissions

The app requires specific permissions to function correctly.

### Wear OS
| Permission | Reason |
| :--- | :--- |
| `BODY_SENSORS` | **Critical:** Access raw PPG sensor data. |
| `FOREGROUND_SERVICE` | Keep recording alive in the background. |
| `WAKE_LOCK` | Prevent CPU sleep during critical sampling. |

### Mobile
| Permission | Reason |
| :--- | :--- |
| `BLUETOOTH_CONNECT` | Connect to Polar and Watch devices. |
| `BLUETOOTH_SCAN` | Discover BLE peripherals. |
| `ACCESS_FINE_LOCATION` | Required for BLE scanning (Android 11 and below). |

## 🛠️ Troubleshooting

*   **Build Fails (Missing Library):**
    *   **Cause:** The Samsung Health SDK `.aar` file (e.g., `priv-health-tracking-v1.2.0.aar`) is missing from `wear/libs/`.
    *   **Fix:** Download the SDK from Samsung and place it in the `libs` folder.
*   **Data Not Syncing:**
    *   **Cause:** Bluetooth disconnected or Battery Optimization pending.
    *   **Fix:** Ensure both devices are connected. Check if "Battery Saver" is interfering with the background service.

---

## 📦 Version History & Release Downloads

Download the latest APK builds directly from [GitHub Releases](https://github.com/Adrian463588/HeartRateMonitor/releases) or build them locally.

| Version | Target Module | Code | Date | Highlights & Changes | Download Artifact |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **v1.2** | `mobile` | `3` | Oct 2026 | • Perekaman & visualisasi Galaxy Watch Accelerometer (3-Axis X/Y/Z @ 25 Hz).<br>• Ekspor CSV Accelerometer via Android SAF (`-Accelerometer-.csv`).<br>• Auto-start ECG streaming ketika koneksi Polar H10 terdeteksi.<br>• Buffer drain & off-by-one flush safety fix. | [mobile-debug.apk](https://github.com/Adrian463588/HeartRateMonitor/releases) |
| **v1.1** | `wear` | `2` | Oct 2026 | • SensorEventListener native `TYPE_ACCELEROMETER` terintegrasi.<br>• Pengiriman batching 100 data poin ke path `/dataaccelerometer`.<br>• Safe lifecycle start/pause/stop and memory buffer cleanup.<br>• Penghapusan unhandled TODO crash pada rute pesan `INFO`. | [wear-debug.apk](https://github.com/Adrian463588/HeartRateMonitor/releases) |
| **v1.0** | `mobile` & `wear` | `1` | Initial | • Multi-channel optical PPG (Green 25 Hz, IR 100 Hz, Red 100 Hz).<br>• Polar H10 ECG streaming (130 Hz) & Pan-Tompkins QRS peak detection.<br>• Ekspor CSV multi-kanal. | Initial Release |

### 🔨 Cara Membangun (Build) APK Mandiri

Jalankan perintah berikut di terminal root proyek untuk mengompilasi APK debug:
```powershell
# Kompilasi kedua aplikasi (Mobile dan Wear OS)
.\gradlew :mobile:assembleDebug :wear:assembleDebug

# Lokasi Berkas Output:
# Mobile: mobile/build/outputs/apk/debug/mobile-debug.apk
# Wear:   wear/build/outputs/apk/debug/wear-debug.apk
```

---

*Verified for Android 13 (API 33) and Wear OS 3.5+.*
