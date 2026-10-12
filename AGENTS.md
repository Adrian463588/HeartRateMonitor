# AGENTS.md — HeartRateMonitor specification and maintenance guide

Version contract: phone and Watch **1.3.0 / versionCode 4**.
Modules: `mobile` (Android hub), `wear` (Galaxy Watch acquisition),
`shared` (transport, buffers, clocks, transfer accounting).
Current repair and evidence: [SENSOR_FIX_PLAN.md](SENSOR_FIX_PLAN.md).
Detailed scenarios: [OpenSpec sensor specification](openspec/specs/01-watch-accelerometer-and-vitals.md).

## Authoring rules

Apply existing local authoring guidance when available:

- `C:\Users\HP OMEN\.codex\RTK.md`: use RTK for concise command output.
- `C:\Users\HP OMEN\.codex\skills\caveman\SKILL.md`: concise, technically exact communication.
- `C:\Users\HP OMEN\.agents\skills\ponytail\SKILL.md`: KISS/YAGNI/DRY, root-cause fixes and focused regressions.

These guide development only. They are not app dependencies; never claim an
unavailable executable. Preserve security/accessibility controls, dirty files
and explicit user delivery targets. Do not fabricate sensor samples, results
or completed hardware checks. Deterministic test fixtures are not measurements.

## Feature inventory and required behavior

| Feature | Implementation / contract |
| --- | --- |
| Watch acceleration | Native `Sensor.TYPE_ACCELEROMETER`; X/Y/Z in m/s², requested 25 Hz; 100-sample batches. Honor registration result; no Samsung connection requirement. |
| Watch PPG Green/IR/Red | One Samsung `PPG_CONTINUOUS` tracker with all three PPG types, **25 Hz each**, 300-sample batches. Only normal SDK status samples are accepted. |
| PPG channel controls | Phone status controls include/exclude individual channels from acquisition buffers within the active session. The shared hardware tracker remains active while any channel is enabled. |
| Polar connection | Manual device ID, Bluetooth permission/setup, connected/disconnected state, device name, firmware and battery information. |
| Polar ECG | Auto-start only after ECG capability readiness during an active session. H10 ECG uses 130 Hz; select/validate that rate for the QRS processor. |
| Polar HR/RR | Record SDK HR independently of ECG. Optional `rrsMs` already uses milliseconds; unavailable RR stays empty. Verity Sense must not be described as an ECG source. |
| Clock conversion | Watch PPG retains SDK Unix milliseconds. Native acceleration maps elapsed-realtime event timestamps to Unix milliseconds from a captured clock anchor. |
| ECG timing | Preserve per-sample Polar nanoseconds and gaps. Anchor the last sample of the first packet to phone receipt time once per subscription. Clear the anchor on resume/reconnect. |
| HR/RR timing | HR rows use notification receipt time. RR plot intervals are back-calculated from that receipt and the SDK millisecond intervals; never add an epoch timestamp as a time correction. |
| QRS analysis | Existing derivative/adaptive-threshold detector, R-peak markers and derived HR/RR. Peak indices continue to address the complete ECG recording after resume. No measured clinical accuracy claim. |
| Start/Pause/Resume/Stop | A fresh Start resets recordings; Pause drains buffered samples and unregisters sensors; Resume preserves session identity and existing data; Stop drains and ends acquisition. |
| Watch transport | Session ID, unique channel/session/batch paths, serialized uploads with retry, copied callback payloads, shape validation and duplicate suppression. Final counts cover Green/IR/Red/acceleration. |
| Transfer completion | Stop receipt and received counts must match before a Watch transfer is complete. Late flushes remain acceptable after pause/stop. Explicit partial export remains marked incomplete. |
| CSV export | SAF-selected directory and filename; ECG, each PPG channel, acceleration, Polar HR/RR, All PPG and All. Empty channels have zero rows, not padded samples. |
| Plotting | Separate ECG, Green/IR/Red, QRS and HR/RR graphs; pan while stopped, visibility/analysis preferences. Repeated redraw requests are coalesced through Choreographer. No measured 60 FPS claim. |
| Foreground lifecycle | Private declared phone/Watch services, ongoing notifications. Watch wake lock during active acquisition; sensors/wake lock released on pause, service retained for resume; Stop ends it after uploads drain. |
| Reconfiguration | Plot setup refresh preserves samples. Polar API restart/device ID change ends an active session before replacing the connection, retaining received recordings for export. |
| Settings/help | Device ID, plot/analysis visibility, configured preferences, SAF directory selection, device information and actionable in-app help. |
| Distribution | Matching versioned debug APKs, immutable release tags, direct phone/Watch download links, preserved historical releases; Samsung AAR stays local/ignored. |

Samsung's old `PPG_IR`/`PPG_RED` types are on-demand measurements at 100 Hz.
They do not satisfy simultaneous continuous Green/IR/Red recording. Do not
reintroduce three independent trackers or label the continuous data 100 Hz.
Unsupported capability, permission/policy denial, missing sensors and
registration failures must remain visible unavailable/error states.

ECG/HR clocks are receipt-anchored approximations: BLE delay and device clock
differences limit absolute alignment. Native accelerometer rate is requested,
not guaranteed; retain actual event times. Recording buffers live in memory.
Activity destruction ends acquisition; process-death recovery and durable
session storage are not implemented. The legacy ECG image helper has no active
export menu. Do not advertise inactive helper code as a delivered feature.

## Non-negotiable requirements

1. Values and timestamps correspond one-to-one. Only `size` samples may be
   consumed, and invalid shapes, nonfinite acceleration, bad timestamp order
   or mismatched PPG types must not crash ingestion or create substitute data.
2. All acquisition/buffer/control changes are serialized on the main looper;
   SDK/Wearable callbacks copy payloads before leaving their owning callback.
   Rendering is deferred/coalesced separately; throughput needs device testing.
3. Duplicate Start for the same active session is idempotent. Resume never
   resets counters. Old session packets and duplicate unique batch keys do not
   increase current counts. Callback generations suppress canceled listeners.
4. Stop/Pause unregister producers and snapshot/drain application buffers.
   A stopped phone must continue to ingest the same session's late final data.
   Do not mark transfer complete solely because a stop command was sent.
5. Device HR and QRS HR are distinct. Never gate real HR notifications on
   `isEcgRunning`. ECG running means ECG data has actually arrived.
6. Preserve physical sample gaps; no sample-index clock substitution, stale
   anchors, synthetic zeros or forced timestamps claiming exact synchronization.
7. A mandatory `setLocalTime` must not block ECG. Unsupported devices must
   remain usable for their supported features.
8. SDK AARs, real session CSVs, APKs, keystores and credentials do not belong in
   source commits. Publish only the requested APK assets; never rewrite history
   or change upstream as a routine repair.

## BDD acceptance and executable mapping

```gherkin
Feature: Watch acquisition without silent data loss
  Scenario: Native motion remains available when Samsung PPG is unavailable
    Given a Watch with a native accelerometer and unavailable Samsung PPG
    When the phone starts a new session
    Then acceleration uses real sensor event timestamps
    And PPG reports unavailable without blocking acceleration

  Scenario: Pause tail and resume remain in one session
    Given 42 buffered Watch samples
    When the phone pauses and subsequently resumes
    Then all 42 samples are transmitted and may arrive after pause
    And resume retains the session and does not erase received samples

  Scenario: Full batch followed by one sample
    Given 301 buffered PPG samples or 101 acceleration samples
    When one full batch is drained
    Then exactly one complete value/timestamp tuple remains

  Scenario: Stop acknowledgement precedes the final data
    Given a stopped session expecting 42 acceleration samples
    When its count receipt arrives before its final batch
    Then transfer completion remains false
    And accepting that unique batch makes completion true
    And replaying it cannot increase sample counts

Feature: Polar timing
  Scenario: ECG clock preserves rate and dropped data
    Given 131 Polar sample timestamps spanning one second at 130 Hz
    When those nanoseconds are mapped to Unix milliseconds
    Then the timestamp span remains one second
    And later sample gaps remain gaps regardless of packet delay

  Scenario: RR display resumes after a notification gap
    Given RR intervals already expressed in milliseconds
    When a packet arrives after a pause
    Then its last beat is anchored to that packet receipt
    And the epoch is never added to itself

Feature: Android controls
  Scenario: Start Pause Resume Stop
    Given the installed phone app
    When Start Pause Resume Stop are invoked
    Then the same session resumes
    And its declared private foreground service stops at Stop

  Scenario: A sensor burst requests many redraws
    Given 300 redraw requests for one plot before a frame
    When Choreographer renders that frame
    Then that plot redraws once
```

| Contract | Regression |
| --- | --- |
| Clock interval, gaps, reset, event time, RR epoch | `shared/.../TimestampHelperTest.kt` |
| Buffer boundaries / final tails / empty reset | `shared/.../RecordingBufferTest.kt` |
| Late stop/pause, replay, old session, complete counts | `shared/.../WatchSessionTest.kt` |
| Transport validation, capacity vs size, session equality | `shared/.../SensorBatchValidationTest.kt` |
| Missing Samsung readiness, continuous rate metadata | `wear/.../ListenerReadinessTest.kt`, `RecordingBufferTest.kt` |
| Manifest/service lifecycle/frame redraw | `mobile/src/androidTest/.../RecordingLifecycleTest.kt` |

## SDD workflow and release gates

Use the smallest investigation–specification–plan–implementation–review loop.
Before editing, read this file, the sensor specification, and applicable
`PRD.md`/`DESIGN.md` if present; these last two are not separate files here.
Record scope, root causes, BDD contracts, risks and test mapping in the plan.
Update contracts when evidence changes the design; link each fix to verification.

JDK 17 via `JAVA_HOME`; Gradle 8.6, AGP 8.4.0, Kotlin 1.9.24. Never restore a
developer-specific absolute JDK path in `gradle.properties`. Privately obtain
Samsung Health Sensor SDK 1.4.1 in `wear/libs/` for full Watch checks.

```powershell
.\gradlew.bat :shared:test :mobile:testDebugUnitTest :wear:testDebugUnitTest :mobile:lintDebug :wear:lintDebug :mobile:assembleDebug :wear:assembleDebug :mobile:assembleDebugAndroidTest --no-daemon --console=plain
.\gradlew.bat :mobile:connectedDebugAndroidTest --no-daemon --console=plain
git diff --check
```

Public CI runs shared/phone unit tests, phone lint and phone APK build. Samsung
SDK confidentiality requires full Watch compilation/tests/lint locally before
each release. Passing CI is not a substitute for that Watch gate.

Software verification and exact warning/test counts are recorded in the repair
plan. Hardware acceptance remains separate and unchecked when no Watch/H10 is
available. Required hardware receipt: actual devices/firmware/SDK permission,
session duration, per-channel counts and CSV hashes, timestamp intervals/gaps,
pause/resume/stop tails, reconnection behavior and absent-sensor behavior.
Do not fabricate receipts or claim production/device acceptance from compilation.

Workflow references: [OpenSpec](https://github.com/Fission-AI/OpenSpec),
[BMAD](https://github.com/bmad-code-org/BMAD-METHOD),
[Spec Kit contracts](https://github.com/github/spec-kit/blob/main/docs/guides/contract-driven-development.md).
Sensor contract sources: [Samsung tracker modes/rates](https://developer.samsung.com/health/sensor/api-reference/com/samsung/android/service/health/tracking/data/HealthTrackerType.html),
[Samsung PPG keys/status](https://developer.samsung.com/health/sensor/api-reference/com/samsung/android/service/health/tracking/data/ValueKey.PpgSet.html),
[Polar SDK](https://github.com/polarofficial/polar-ble-sdk).
