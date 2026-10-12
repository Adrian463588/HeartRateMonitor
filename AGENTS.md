# AGENTS.md — application contract and maintenance

## Scope and authoring

- Modules: `mobile` hub, `wear` Galaxy Watch acquisition, `shared` protocols/buffers/clocks/control. Both APKs: **1.3.1 / versionCode 5**.
- Apply existing RTK (`C:\Users\HP OMEN\.codex\RTK.md`), Caveman and Ponytail guidance; authoring only, no app dependencies or unavailable executable claims.
- Preserve dirty files, security/accessibility controls and scoped delivery. Do not fabricate samples, metrics or device receipts; deterministic test fixtures are not measurements.
- Before changing behavior read this file, [sensor spec](openspec/specs/01-watch-accelerometer-and-vitals.md) and [session spec](openspec/specs/02-session-control-and-polar-recovery.md). `PRD.md`/`DESIGN.md` are absent here.
- Use [session repair plan/checklist](SESSION_CONTROL_FIX_PLAN.md); historic v1.3.0 evidence remains in [SENSOR_FIX_PLAN.md](SENSOR_FIX_PLAN.md).
- Adapt [OpenSpec](https://github.com/Fission-AI/OpenSpec) proposal/spec/tasks and [BMAD](https://github.com/bmad-code-org/BMAD-METHOD) context/spec/build/review. Write BDD behavior, implement the smallest fix, verify, review and attach receipts; these CLIs are not installed dependencies.

## Complete feature inventory

| Feature | Implementation / contract |
| --- | --- |
| Watch acceleration | Native `Sensor.TYPE_ACCELEROMETER`; X/Y/Z in m/s², requested 25 Hz; 100-sample batches. Honor registration result; no Samsung connection requirement. |
| Watch PPG Green/IR/Red | One Samsung `PPG_CONTINUOUS` tracker with all three PPG types, **25 Hz each**, 300-sample batches. Only normal SDK status samples are accepted. |
| PPG channel controls | Phone status controls include/exclude individual channels from acquisition buffers within the active session. The shared hardware tracker remains active while any channel is enabled. |
| Polar connection | Manual device ID, Bluetooth permission/setup, connected/disconnected state, device name, firmware and battery information. |
| Polar ECG | ECG-ready H10 at 130 Hz; one owned pending/running subscription, timeout, two transient retries. Cancel pending start by disconnecting; wait for asynchronous stop before Resume. |
| Polar HR/RR | Record SDK HR independently of ECG. Optional `rrsMs` already uses milliseconds; unavailable RR stays empty. Verity Sense must not be described as an ECG source. |
| Clock conversion | Watch PPG retains SDK Unix milliseconds. Native acceleration maps elapsed-realtime event timestamps to Unix milliseconds from a captured clock anchor. |
| ECG timing | Preserve per-sample Polar nanoseconds and gaps. Anchor the last sample of the first packet to phone receipt time once per subscription. Clear the anchor on resume/reconnect. |
| HR/RR timing | HR rows use notification receipt time. RR plot intervals are back-calculated from that receipt and the SDK millisecond intervals; never add an epoch timestamp as a time correction. |
| QRS analysis | Existing derivative/adaptive-threshold detector, R-peak markers and derived HR/RR. Peak indices continue to address the complete ECG recording after resume. No measured clinical accuracy claim. |
| Start/Pause/Resume/End | Ordered revisions, idempotent requests, fresh peer lookup, ACK/retry. Pause/End confirm successful sensor release; Resume keeps identity and buffers; End is terminal. |
| Watch transport | Session ID, unique channel/session/batch paths, serialized uploads with retry, copied callback payloads, shape validation and duplicate suppression. Final counts cover Green/IR/Red/acceleration. |
| Transfer completion | Stop receipt and received counts must match before a Watch transfer is complete. Late flushes remain acceptable after pause/stop. Explicit partial export remains marked incomplete. |
| CSV export | SAF-selected directory and filename; ECG, each PPG channel, acceleration, Polar HR/RR, All PPG and All. Empty channels have zero rows, not padded samples. |
| Plotting | Separate ECG, Green/IR/Red, QRS and HR/RR graphs; pan while stopped, visibility/analysis preferences. Repeated redraw requests are coalesced through Choreographer. No measured 60 FPS claim. |
| Foreground lifecycle | Private phone/Watch services and ongoing notifications. Active acquisition holds CPU wake locks; Pause/End release them. Watch stops service after final uploads drain. |
| Reconfiguration | Plot setup refresh preserves samples. Polar API restart/device ID change ends an active session before replacing the connection, retaining received recordings for export. |
| Settings/help | Device ID, plot/analysis visibility, configured preferences, SAF directory selection, device information and actionable in-app help. |
| Distribution | Matching versioned debug APKs, immutable release tags, direct phone/Watch download links, preserved historical releases; Samsung AAR stays local/ignored. |
| Lost controller | Phone heartbeat every 3 s; Watch pauses sensors after 15 s without a valid command. Stop-release failure is retried, never acknowledged as off. Last control state persists to reject a delayed Start after Activity recreation. |

## Invariants and limits

- Values/timestamps stay paired. Validate size/shape/type/order/finite XYZ; reject bad packets and duplicates without synthetic zero rows. Accept late current-session application-buffer tails after Pause/End.
- Serialize control/acquisition on the main looper. Bind Samsung callbacks to registration generations; keep ownership after failed unregistration and retry release. Cancel pending/running ECG and obsolete API callbacks on lifecycle changes.
- End for a session is terminal. New Start needs a newer ID; Resume only follows Pause. Do not mark Watch off before matching revision and `sensorsStopped=true`; final counts must also match before transfer completion.
- Continuous Green/IR/Red uses one `PPG_CONTINUOUS`, **25 Hz each**. Legacy IR/Red 100 Hz trackers are on-demand; never substitute them for continuous acquisition.
- H10 supplies ECG; Verity Sense does not. Supported HR is independent of ECG. `rrsMs` is already milliseconds; no second scaling or epoch doubling.
- Preserve real Polar per-sample nanoseconds, dropped samples and pause gaps; reset receipt anchors only for a new subscription. Do not replace sensor time with sample-index clocks or require `setLocalTime` before ECG.
- CPU wake locks exist only for active recording. Watch lease expiration pauses acquisition; reconnection can resume the same in-memory session. Activity destruction ends Watch acquisition; a new session is required after recreation.
- Receipt anchoring approximates absolute alignment and includes BLE delay/clock drift. Requested accelerometer rate is not guaranteed; retain actual event times.
- Session samples/unsent queues are in memory; process-death recovery/durable recording is absent. Pause/End drain application buffers, not an independently proven Samsung SDK-internal tail. Save before closing; physical completeness needs hardware receipts.
- Unsupported capability/permission/policy, registration failure and final ECG failure stay visible. Never promote build/test/CI success to physiological, clinical, measured frame-rate or hardware acceptance.
- Legacy ECG image helper has no active export menu; do not advertise inactive helpers as delivered features.

## Executable BDD mapping

| Contract | Regression |
| --- | --- |
| Ordered commands, terminal End, lease/heartbeat, ACK and restored End | `shared/.../WatchControlTest.kt` |
| Failed/partial registration, retry ownership, callback invalidation | `shared/.../TrackerLifecycleTest.kt` |
| Pending/synchronous ECG startup, disposal, timeout, duplicate start, retry budget | `mobile/.../EcgStreamControllerTest.kt` |
| Clock anchoring, 130 Hz span/gaps, RR/native event times | `shared/.../TimestampHelperTest.kt` |
| Full/partial batches, no off-by-one drain or malformed data | `shared/.../RecordingBufferTest.kt`, `SensorBatchValidationTest.kt` |
| Late tails, duplicate suppression and final four-channel counts | `shared/.../WatchSessionTest.kt` |
| Missing Samsung readiness and continuous rate metadata | `wear/.../ListenerReadinessTest.kt`, `RecordingBufferTest.kt` |
| Phone controls/service/wake-lock release and frame scheduling | `mobile/src/androidTest/.../RecordingLifecycleTest.kt` |

## Build and release gates

- Gradle 8.6 / AGP 8.4.0 / Kotlin 2.1.0 / JDK 17 via `JAVA_HOME`; no absolute JDK path in `gradle.properties`.
- Samsung Health Sensor SDK 1.4.1 AAR belongs only in ignored `wear/libs/`. Public CI checks shared/mobile; full Wear gates must run locally with the private SDK.
- Focused regressions: `:shared:test --tests '*WatchControlTest'` or `:mobile:testDebugUnitTest --tests '*EcgStreamControllerTest'`; Android lint has module/task scope.
- Full required gates before release:

```powershell
.\gradlew.bat :shared:test :mobile:testDebugUnitTest :wear:testDebugUnitTest :mobile:lintDebug :wear:lintDebug :mobile:assembleDebug :wear:assembleDebug :mobile:assembleDebugAndroidTest --no-daemon --console=plain
.\gradlew.bat :mobile:connectedDebugAndroidTest --no-daemon --console=plain
git diff --check
```

- Record exact test/error/warning counts and APK hashes in the plan. Explicitly allowlist staged paths, scan publication content, deliver only to the authorized fork/branches; update `main` through a tested PR and merge commit when requested. Preserve upstream and existing history/tags.
- Run `python -m unittest discover -s scripts -p 'test_*.py'` and `python scripts/check_publication.py --base origin/main` before publishing. Scan staged/current files and reachable history with redacted Gitleaks; CI repeats both gates on `main`, `Fix-issue`, and PRs. Keep action SHAs, tool/distribution checksums and read-only workflow permissions.
- Backup/device transfer must remain disabled; settings/services private. Do not log raw sensor values, device IDs or received message contents. SDK 34 Wear health service requires Body Sensors or granted Activity Recognition; denial stays visible.
- Existing Polar AAR is retained only in pre-existing Git history. No AAR may be added to current/incoming source; history cleanup requires explicit rewrite authorization. Merge evidence: [MAIN_MERGE_AUDIT.md](MAIN_MERGE_AUDIT.md).
- Keep AARs, real session CSVs, APKs, credentials and keystores out of commits. Publish only matching phone/Watch debug APK assets; preserve old releases and direct download links in README.
- AI commits include `Co-Authored-By: Codex (GPT-6) <noreply@openai.com>`.
- Physical Watch/H10 acceptance remains **unchecked** without the devices: record actual model/firmware/permissions, rates/counts/CSV hashes, timestamp gaps, repeated rapid controls, LEDs off, reconnect and absence/error behavior.
