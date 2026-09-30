# HeartRateMonitor

Android phone and Galaxy Watch app for recording Polar ECG and Samsung Watch PPG. The watch collects green, infrared, and red PPG; the phone connects to Polar over Bluetooth, shows live plots, and can save recordings as CSV. This is a development tool for biosignal collection, not a medical diagnosis app.

## Debug APKs

| Device | Version | Download |
| --- | --- | --- |
| Android phone | `1.2.0` (`versionCode 3`) | [mobile-debug.apk](https://github.com/Adrian463588/HeartRateMonitor/releases/download/debug-2026-10-01/mobile-debug.apk) |
| Galaxy Watch | `1.1.0` (`versionCode 2`) | [wear-debug.apk](https://github.com/Adrian463588/HeartRateMonitor/releases/download/debug-2026-10-01/wear-debug.apk) |

These are debug builds for development and testing. Install each APK on its corresponding device. Both APKs must be signed with the same key for Wear OS Data Layer communication; the linked pair is built together. The Samsung Health Sensor SDK works on supported Galaxy Watch4 or newer hardware, not a watch emulator. Sensor access may return `SDK_POLICY_ERROR` until Samsung authorizes the app's package and signing certificate. Do not treat APK installation alone as proof that PPG or ECG collection works.

## Build from source

1. Install Android Studio, Android SDK platform 34, and JDK 21. The project uses Gradle 8.6, Android Gradle Plugin 8.4.0, and Kotlin 2.1.0. Open the repository root in Android Studio.
2. Download **Samsung Health Sensor SDK v1.4.1** from the [Samsung Developer SDK page](https://developer.samsung.com/health/sensor/overview.html) under your own Samsung license. Extract `samsung-health-sensor-api.aar` and place it at `wear/libs/samsung-health-sensor-api-1.4.1.aar`. The existing `wear/build.gradle` loads `wear/libs/*.aar`. Do not commit the AAR, SDK archive, signing keys, or local configuration. See Samsung's [AAR import guide](https://developer.samsung.com/health/sensor/guide/app-module.html) and [app creation process](https://developer.samsung.com/health/sensor/process.html).
3. Build and check both modules:

   ```powershell
   .\gradlew.bat :mobile:assembleDebug :wear:assembleDebug :mobile:lintDebug :wear:lintDebug :mobile:testDebugUnitTest :wear:testDebugUnitTest
   ```

4. Find local outputs at `mobile/build/outputs/apk/debug/mobile-debug.apk` and `wear/build/outputs/apk/debug/wear-debug.apk`. Build them on the same machine, or use the linked pair, so their debug signing certificates match.

The SDK AAR is intentionally absent from Git. Without it, the watch module cannot compile; obtain it from Samsung rather than copying an old AAR from Git history. Samsung requires partner approval and package/signature registration for public sensor access; its developer mode is for development testing. See the [Samsung app verification guide](https://developer.samsung.com/health/sensor/guide/app-verification.html).

## Run and use

- **Phone:** Android 9+ (`minSdk 28`), Bluetooth enabled, and a compatible Polar ECG device such as Polar H10. Grant Bluetooth permissions; Android 11 and earlier also require location permission for BLE scanning.
- **Watch:** Samsung Galaxy Watch4 or newer with Wear OS (`minSdk 30`), paired with the phone. Grant Body Sensors and Activity Recognition permissions. On supported versions, background sensor access is requested separately.
- Select a Polar device on the phone, connect the watch, start recording, and inspect the live plots. Choose an export folder when saving CSV. Check sensor and connection status on both devices if data is missing.
- The watch SDK does not run on an emulator. A successful Gradle build verifies compilation, tests, and static checks; real PPG/ECG acquisition still needs physical devices and authorized sensor access.

## Repository layout

| Module | Purpose |
| --- | --- |
| `mobile/` | Phone UI, Polar BLE, plotting, CSV export, Wear Data Layer consumer |
| `wear/` | Galaxy Watch UI, Samsung Health Sensor SDK trackers, Wear Data Layer producer |
| `shared/` | Shared message and data types |

## Version log

| Date | Phone | Watch | Changes |
| --- | --- | --- | --- |
| 2026-10-01 | `1.2.0` (3) | `1.1.0` (2) | Merged the checked-out `fix-issue-2` implementation into this fork's `main`; updated build tools and privacy settings; removed generated files and Samsung AAR from reachable Git history. |
| Before merge | `1.1` (2) | `1.0` (1) | Fork `main` baseline; see Git history for earlier changes. |

For merge provenance, test results, and remaining device verification, see [MERGE_AUDIT.md](MERGE_AUDIT.md). Report bugs with device model, Android/Wear OS version, steps, and relevant error text. Do not include recordings, patient names, signing keys, or SDK AARs in issues.
