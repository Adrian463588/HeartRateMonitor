# Session-control repair plan — 2026-10-13

## Scope and delivery

- Preserve `Fix-issue`; publish matching phone/Watch debug APKs as v1.3.1 (versionCode 5).
- Galaxy Watch and Polar H10 are unavailable. Software checks do not count as physical acquisition acceptance.
- Keep existing sensor formats, native acceleration, continuous Green/IR/Red PPG at 25 Hz and per-sample Polar timestamps. No invented measurements or SDK AAR publication.

## Verified starting defects

- Phone control uses a cached node list and one-shot MessageApi sends without sensor-state acknowledgement or delivery retry.
- Watch accepts a delayed Start for the same session after Stop. Commands lack ordering; a mismatched Stop is silently ignored.
- ECG start/stop is implemented as a toggle. Inspect asynchronous settings/start/disposal, readiness and reconnection to prevent overlapping starts and callbacks after cancellation.
- Watch listener cleanup suppresses callbacks but currently discards unregistration errors; do not report confirmed sensor-off after a failed release.

## Plan / acceptance checklist

- [x] Inspect current source, caller paths, repository state and official SDK guidance.
- [x] Add ordered, idempotent session control: explicit Start/Pause/Resume/End intent, current peers on every send, acknowledgement/retry and visible pending/offline state.
- [x] Add a Watch safety timeout for a lost controller; expired or older commands cannot restart a stopped session. Release PPG/native sensors and wake lock on Pause/End/destruction.
- [x] Separate Polar start from stop; cancel pending startup, reject stale callbacks, prevent duplicate subscriptions and allow controlled restart after transient failure. Keep unsupported capabilities explicit.
- [x] Add executable BDD regressions covering reordered/duplicate commands, offline End, pause/resume retention, failed/pending Polar startup, cancel/reconnect and unchanged timing/batch contracts.
- [x] Update OpenSpec acceptance, AGENTS feature/maintenance guide, download history and limitations.
- [x] Pass all shared/mobile/wear unit tests, Android lint, both APK builds and available phone instrumentation.
- [ ] Review scoped diff and publication contents; commit/push `Fix-issue`, verify CI and publish both v1.3.1 APKs with checksums/direct links.
- [ ] Physical Watch sensors-off/PPG/acceleration and real Polar ECG validation (pending devices; report explicitly).

## Method

Adapt [OpenSpec](https://github.com/Fission-AI/OpenSpec) proposal/spec/tasks and [BMAD](https://github.com/bmad-code-org/BMAD-METHOD) context/spec/build/review for a bounded bug fix. Write Given/When/Then behavior before implementation; attach executable checks and release evidence. RTK/Ponytail/Caveman guide authoring only. No framework installation or application runtime dependency is needed.

## Planned BDD

```gherkin
Given a Watch has received Start for session A
When Pause or End is requested and a message is lost
Then the phone retries its latest intent against currently connected nodes
And an acknowledgement identifies the same session and command revision
And an unreachable controller causes the Watch to stop acquisition within the documented safety timeout

Given End has been applied to session A
When an older Start or duplicate End arrives
Then acquisition remains stopped and final counters are preserved

Given Polar settings/start is pending or an ECG stream is active
When Pause, End, disconnect or Activity destruction occurs
Then its subscription is disposed and obsolete callbacks cannot restart or alter the current stream

Given an active session and a transient Polar ECG error
When ECG capability remains ready
Then recovery is bounded and cannot create overlapping subscriptions
And an unsupported device or final failure stays visible without substitute data
```

## Evidence

Historic v1.3.0 receipt remains in `SENSOR_FIX_PLAN.md`.

- Final source verification: full required Gradle tasks passed (`build/session-full-verification.log`). The first lint run was invalidated by ongoing source edits; a stable-source rerun passed with no baseline/suppression added for lint errors.
- JVM unit results: shared **79**, mobile **59**, wear **2** = **140**, failures/errors/skips **0**. Added 27 regressions for ordered control/lease, tracker ownership and ECG cancellation/retry.
- Android lint: mobile **0 errors / 51 warnings**, wear **0 errors / 32 warnings**; existing and current warnings remain visible, total **83**. Passing lint is not a zero-warning claim.
- Both `assembleDebug` tasks and phone Android-test APK assembly passed. Both apps are **1.3.1 / versionCode 5**.
- `connectedDebugAndroidTest`: **4 passed**, zero failures/errors/skips on **SM-G988B / Android 13 / RRCN3008VYE** (`build/session-phone-verification.log`). Actual phone wake lock was asserted held on Start/Resume and released on Pause/End. No Watch/Polar data were generated or validated by these tests.
- Updated phone APK installed using `adb install --user 0 -r`; device package confirms versionCode 5 / versionName 1.3.1. Watch APK was built, not installed on unavailable hardware.
- Matching unchanged debug signer SHA-256: `fc6cc5cec1eb93984bc0cb0842b6d2f9c4bb6549f97617d42f457dc8983b55b4`.
- `git diff --check` passed. Current tree/staged source excludes APK/AAR/CSV/signing material; no new binaries or credentials are part of this repair. Selected HEAD contains no Samsung AAR; the pre-existing historic Polar AAR was not rewritten out of history.

| Artifact | Size (bytes) | SHA-256 |
| --- | ---: | --- |
| `mobile-debug.apk` | 18,769,311 | `d9f81def5cf1700525536a1dd59f730f13b12bf9b5c38c73425b82877cefe5d3` |
| `wear-debug.apk` | 6,632,180 | `dcaec23a262d6346bc843d43669d8c78120a11138ab63415a9cd39b59e3a9657` |

## Implementation decisions and regression boundary

- Reused MessageApi, Gson, Handler, native SensorManager and installed RxJava; no new runtime dependency or methodology installer. Three small control/ownership classes carry the executable state invariants.
- Phone sends only its latest immutable intent after current-peer lookup; old ACKs cannot change newer intent. Active heartbeats run under the recording CPU wake lock; Pause/End awaiting confirmation stays visible.
- Watch stores control metadata only; End and Activity destruction close a session. Duplicate Start renews a live lease without clearing recordings. Listener registration runs on the controlling looper and callback wrappers retain their registration generation.
- Samsung release failure keeps its registered flag, disables acquisition callbacks and retries; off is acknowledged only after release succeeds. Native XYZ and all continuous PPG sample/batch formats are preserved.
- Polar API/device callbacks are identity-guarded and marshaled onto the UI looper. A pending SDK start is disconnected on cancellation because SDK 4.0 installs its stop finalizer after the start handshake. Active disposal is followed by a one-second restart delay. Two consecutive retries reset only on received ECG or explicit Start/Resume; persistent and unsupported failures remain errors.
- Tests protect the reported software failure paths. Hardware LEDs-off timing, sustained screen-off throughput, real PPG/XYZ/ECG, BLE recovery and SDK-internal final-tail completeness still need the physical Watch/H10.

## Publication

Pending commit/push/CI/release verification; intended immutable tag `v1.3.1`. Preserve `v1.3.0` and `debug-2026-10-01` unchanged.
