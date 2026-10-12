# HeartRateMonitor

Android phone + Galaxy Watch biosignal acquisition: real Watch acceleration,
continuous Green/IR/Red PPG, and supported Polar ECG/HR/RR data with SAF CSV export.

## Download and version history

Install **both matching 1.3.1 apps**; ordered controls and safety timeout require both updated apps. These are debug APKs, not Google Play builds.

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

Simultaneous continuous PPG uses a single `PPG_CONTINUOUS` tracker. The legacy
100 Hz IR/Red on-demand trackers cannot satisfy that continuous three-channel
contract. See [Samsung's mode/rate specification](https://developer.samsung.com/health/sensor/api-reference/com/samsung/android/service/health/tracking/data/HealthTrackerType.html).

Polar ECG capability is required: **Verity Sense is not an ECG source**.
Receipt anchoring preserves device intervals/gaps but does not prove exact
Watch–phone–Polar clock synchronization. Missing sensors/capabilities remain
unavailable, and empty channels contain no synthetic rows.

## Use

1. Install phone and Watch APKs; pair the Watch with the phone. Phone minimum
   Android 9/API 28, Watch minimum Android 11/API 30. Hardware/service capability
   and Samsung SDK access must also be satisfied.
2. On Watch, grant Body Sensors permission and configure Samsung Health Sensor
   service/SDK access for the application's package/signature. Native motion
   does not depend on Samsung PPG readiness.
3. On phone, grant required Bluetooth/location permissions, enable Bluetooth,
   and enter a Polar device ID if recording that device.
4. Choose a data directory through the system SAF picker.
5. Start; optionally pause/resume. PPG status controls include/exclude channels
   in the active session. HR still records if the device has no ECG.
6. Stop, then Save All or select ECG, PPG, acceleration, or Polar HR/RR export.
   Pending final Watch batches remain ingestible. If you select **Save partial**,
   Watch CSV metadata records `watch_transfer_complete=false`.
7. Wait for Watch Pause/End confirmation. Offline requests stay pending; an
   unreachable controller makes updated Watch software pause after 15 seconds.
   Active sessions send a heartbeat every 3 seconds. After Watch Activity
   recreation, End and start a new session; old-session Start is rejected.
8. Polar pending startup is canceled by disconnecting. Resume reconnects; ECG
   requires readiness, waits for asynchronous stop, and retries transient errors
   at most twice. Final/unsupported errors require explicit Resume or restart.
   HR notifications while paused/stopped are ignored, not recorded.

CSV timestamps use Unix milliseconds. Acceleration metadata preserves the
nominal/requested rate; actual timestamps determine achieved rate. Export
includes only received samples. Data buffers are in memory; save before closing
the Activity. Plot setup refresh preserves recorded samples. Restarting Polar
or changing its ID ends the current session before replacing the connection.

## Build and verify

JDK 17, Android SDK platform 34, Build Tools 34.0.0.
Tooling: Gradle 8.6, AGP 8.4.0, Kotlin 2.1.0; Polar SDK 4.0.0.
Set `JAVA_HOME` to your JDK 17 installation. Android Studio Jellyfish or newer
can use the same Gradle/JDK configuration.

Place the privately supplied **Samsung Health Sensor SDK 1.4.1 AAR** in
`wear/libs/`. It is ignored and is never a release asset or source commit.
See [Samsung SDK overview/setup](https://developer.samsung.com/health/sensor/overview.html).
Do not substitute synthetic trackers if the SDK/device is unavailable.

```powershell
.\gradlew.bat :shared:test :mobile:testDebugUnitTest :wear:testDebugUnitTest :mobile:lintDebug :wear:lintDebug :mobile:assembleDebug :wear:assembleDebug :mobile:assembleDebugAndroidTest --no-daemon --console=plain
.\gradlew.bat :mobile:connectedDebugAndroidTest --no-daemon --console=plain
```

Outputs: `mobile/build/outputs/apk/debug/mobile-debug.apk` and
`wear/build/outputs/apk/debug/wear-debug.apk`.
Public CI checks shared/phone tests, phone lint and phone build. Full Wear
checks require the local private SDK and are required before each release.

The merged source also retains `main` privacy controls: disabled backup/device
transfer, private services/settings, reduced Bluetooth/location permissions
and no raw signal/device-ID logs. Source changes are reviewed through a PR with
merge commit. [Main/Fix-issue merge audit](MAIN_MERGE_AUDIT.md).

Before publishing source, run the artifact/history policy and a redacted
Gitleaks scan. CI runs these gates on `main`, `Fix-issue` and PRs, using pinned
actions/checksums and a read-only token. Private AARs/APKs/keys/raw CSVs must stay
untracked; existing release APKs are assets, not source files.

```powershell
python -m unittest discover -s scripts -p 'test_*.py'
python scripts/check_publication.py --base origin/main
```

The old Polar SDK AAR remains in pre-existing Git history; this merge introduces
no AAR. Existing tags/releases are preserved. The v1.3.1 download is the immutable
previous release; merged-source verification is recorded separately in the audit.

Software checks and warning counts: [session repair plan/evidence](SESSION_CONTROL_FIX_PLAN.md).
**Galaxy Watch and Polar physical sensor/session acceptance is unverified for
this release**: those devices were unavailable during this repair. Phone
instrumentation verifies controls/service/frame scheduling, not sensor accuracy.

[Complete feature specification and agent guide](AGENTS.md).
[BDD sensor specification](openspec/specs/01-watch-accelerometer-and-vitals.md).
