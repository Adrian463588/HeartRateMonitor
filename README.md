# HeartRateMonitor

Android phone + Galaxy Watch biosignal acquisition platform: real Watch acceleration, continuous Green/IR/Red PPG, and supported Polar H10 ECG/HR/RR data with SAF CSV export.

## Architecture overview

| Module | Scope / Role | Target SDK |
| --- | --- | --- |
| `:mobile` | Android Phone application hub (UI plotting, Polar BLE manager, SAF CSV export) | Android 9+ (API 28–34) |
| `:wear` | Samsung Galaxy Watch Wear OS application (PPG continuous & native accelerometer) | Wear OS 3+ (API 30–34) |
| `:shared` | Common models, protocols, buffers, clock alignment, and lifecycle control | JVM / Android |

## Download and version history

Install **both matching 1.3.1 apps** (versionCode 5); ordered session controls and safety timeouts require both updated apps. These are debug APKs, not Google Play builds.

### Direct APK downloads (Latest v1.3.1)

- 📱 [**Download Phone APK (v1.3.1)**](https://github.com/Adrian463588/HeartRateMonitor/releases/download/v1.3.1/mobile-debug.apk) (`mobile-debug.apk`)
- ⌚ [**Download Watch APK (v1.3.1)**](https://github.com/Adrian463588/HeartRateMonitor/releases/download/v1.3.1/wear-debug.apk) (`wear-debug.apk`)

### Version history

| Release | Phone APK | Watch APK | Notes |
| --- | --- | --- | --- |
| [1.3.1 — 2026-10-13](https://github.com/Adrian463588/HeartRateMonitor/releases/tag/v1.3.1) | [mobile-debug.apk](https://github.com/Adrian463588/HeartRateMonitor/releases/download/v1.3.1/mobile-debug.apk) | [wear-debug.apk](https://github.com/Adrian463588/HeartRateMonitor/releases/download/v1.3.1/wear-debug.apk) | Latest: confirmed session controls, Watch timeout, Polar startup/recovery fixes; hardware unverified. |
| [1.3.0 — 2026-10-12](https://github.com/Adrian463588/HeartRateMonitor/releases/tag/v1.3.0) | [Download phone](https://github.com/Adrian463588/HeartRateMonitor/releases/download/v1.3.0/mobile-debug.apk) | [Download Watch](https://github.com/Adrian463588/HeartRateMonitor/releases/download/v1.3.0/wear-debug.apk) | Both versionCode 4. Acquisition/lifecycle, late flush, duplicate/session isolation, Polar clocks, HR/RR CSV and frame redraw repairs. |
| [Historical debug — 2026-10-01](https://github.com/Adrian463588/HeartRateMonitor/releases/tag/debug-2026-10-01) | [Previous phone APK](https://github.com/Adrian463588/HeartRateMonitor/releases/download/debug-2026-10-01/mobile-debug.apk) | [Previous Watch APK](https://github.com/Adrian463588/HeartRateMonitor/releases/download/debug-2026-10-01/wear-debug.apk) | Existing release preserved. Legacy transport/rates differ from 1.3.0. |

[All releases](https://github.com/Adrian463588/HeartRateMonitor/releases).
Each new repair must use a new release tag; do not overwrite historical APKs.

## Sensor contracts

| Source | Recorded data | Rate / timestamp |
| --- | --- | --- |
| Galaxy Watch native accelerometer | X/Y/Z, m/s² | Requested 25 Hz; actual elapsed-realtime sensor event time mapped to Unix ms |
| Samsung continuous PPG | Green, IR, Red ADC values, normal SDK status only | **25 Hz each**, SDK Unix ms |
| Polar H10 ECG | Voltage (SDK µV converted to mV), QRS markers | 130 Hz; per-sample device nanoseconds, receipt-anchored Unix ms |
| Polar HR / optional RR | Device BPM and `rrsMs` | Notification-driven; receipt time, RR intervals in ms |

### Key sensor invariants

- **Continuous PPG**: Simultaneous continuous PPG uses a single `PPG_CONTINUOUS` tracker (**25 Hz each** for Green, IR, Red). The legacy 100 Hz IR/Red on-demand trackers cannot satisfy that continuous three-channel contract. See [Samsung mode/rate specification](https://developer.samsung.com/health/sensor/api-reference/com/samsung/android/service/health/tracking/data/HealthTrackerType.html).
- **Polar ECG source**: Polar ECG capability is required; **Verity Sense is not an ECG source**.
- **Clock alignment**: Receipt anchoring preserves device intervals/gaps but does not prove exact Watch–phone–Polar clock synchronization.
- **Missing sensor data**: Missing sensors/capabilities remain unavailable; empty channels contain no synthetic or padded zero rows.

## How to use

### 1. Prerequisites and permissions
- **Phone**: Android 9+ (API 28+). Enable Bluetooth and grant Bluetooth/Nearby devices and Location permissions.
- **Watch**: Android 11+ (API 30+). Grant **Body Sensors** permission and configure Samsung Health Sensor SDK/service privileged access for the package signature. Native accelerometer motion does not depend on Samsung PPG readiness.
- **Polar**: Polar H10 chest strap (optional; required if recording ECG/HR).

### 2. Step-by-step operation
1. **Pair devices**: Pair Galaxy Watch with Phone via the Galaxy Wearable app. If recording Polar H10, enter its device ID in the phone app.
2. **Select storage directory**: Choose an output directory using the system SAF (Storage Access Framework) folder picker.
3. **Start acquisition**: Tap **Start**. Watch starts accelerometer and continuous PPG; Phone connects to Polar sensor. Active PPG channels (Green/IR/Red) can be toggled on/off in real-time. HR still records if device lacks ECG.
4. **Pause and resume**: Optionally use **Pause** and **Resume**. In-memory buffers, session identity, and monotonic duration are preserved across pause/resume cycles.
5. **Stop acquisition**: Tap **Stop**. Wait for Watch Pause/End confirmation before closing the application.
6. **Export CSV**: Tap **Save All** or select individual exports (ECG, PPG, acceleration, or Polar HR/RR). If **Save partial** is selected before all Watch batches drain, CSV metadata records `watch_transfer_complete=false`.

### 3. Fail-safe and lifecycle controls
- **Lease timeout**: Phone sends a heartbeat every 3 seconds. An unreachable controller causes Watch software to pause sensors automatically after 15 seconds.
- **Terminal End**: Stop/End for a session is terminal. After Watch Activity recreation, End and start a new session (old-session Start is rejected).
- **Polar error recovery**: Disconnecting cancels pending Polar startup. Resume reconnects; ECG waits for readiness, waits for asynchronous stop, and retries transient errors at most twice. HR notifications received while paused or stopped are ignored.
- **In-memory data**: Data buffers reside in memory; export to CSV before exiting the Activity.

## Build and verify

### Tooling requirements
- **JDK 17**: Set `JAVA_HOME` to your JDK 17 installation.
- **Android SDK**: Platform 34, Build Tools 34.0.0.
- **Build toolchain**: Gradle 8.6, AGP 8.4.0, Kotlin 2.1.0, Polar SDK 4.0.0. Compatible with Android Studio Jellyfish or newer.

### Samsung SDK setup
Place the privately supplied **Samsung Health Sensor SDK 1.4.1 AAR** in `wear/libs/`.
- It is git-ignored and must never be committed or included as a release asset.
- See [Samsung SDK overview/setup](https://developer.samsung.com/health/sensor/overview.html).
- Do not substitute synthetic trackers if the SDK/device is unavailable.

### Verification commands

#### Run full JVM tests, lint, and assemble debug APKs:
```powershell
.\gradlew.bat :shared:test :mobile:testDebugUnitTest :wear:testDebugUnitTest :mobile:lintDebug :wear:lintDebug :mobile:assembleDebug :wear:assembleDebug :mobile:assembleDebugAndroidTest --no-daemon --console=plain
```

Outputs:
- Phone APK: `mobile/build/outputs/apk/debug/mobile-debug.apk`
- Watch APK: `wear/build/outputs/apk/debug/wear-debug.apk`

Public CI checks shared/phone tests, phone lint, and phone build. Full Wear checks require the local private SDK and are required before each release.

#### Run phone instrumented tests:
```powershell
.\gradlew.bat :mobile:connectedDebugAndroidTest --no-daemon --console=plain
```

#### Run publication policy and secret scanning checks:
Before publishing source, run the artifact/history policy and redacted Gitleaks scan:
```powershell
python -m unittest discover -s scripts -p 'test_*.py'
python scripts/check_publication.py --base origin/main
```

## Security, DevSecOps and references

- **Privacy controls**: Disabled backup/device transfer, private services/settings, reduced Bluetooth/location permissions, and zero raw signal or device-ID logs.
- **Git & publication policy**: Private AARs, APKs, signing keys, and raw CSV recordings must stay untracked. Release APKs are release assets, not source files.
- **Merge audit receipt**: [Main/Fix-issue merge audit](MAIN_MERGE_AUDIT.md).
- **Session repair evidence**: [Session repair plan/evidence](SESSION_CONTROL_FIX_PLAN.md).
- **Application contracts**: [Complete feature specification and agent guide](AGENTS.md).
- **BDD specifications**:
  - [BDD sensor specification](openspec/specs/01-watch-accelerometer-and-vitals.md)
  - [BDD session control & Polar recovery specification](openspec/specs/02-session-control-and-polar-recovery.md)

> **Hardware acceptance boundary**: Galaxy Watch and Polar physical sensor/session acceptance is **unverified without physical hardware**. Software instrumentation verifies controls, services, and frame scheduling, but does not substitute for clinical or hardware testing.
