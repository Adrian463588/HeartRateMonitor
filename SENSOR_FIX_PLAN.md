# Sensor acquisition repair — 2026-10-12

## Scope and evidence

Repair the existing `mobile`, `wear`, and `shared` modules on `Fix-issue`.
The user explicitly confirmed this branch and accepted unavailable Watch/H10 hardware verification.
Keep Samsung SDK AARs local/ignored. Publish versioned debug APKs to the
user's fork, `Adrian463588/HeartRateMonitor`. Preserve previous releases.
No runtime dependency or CLI installation is needed for the authoring workflow.

Investigation found: both foreground services are missing from manifests;
Watch commands depend on Samsung readiness even for native acceleration;
legacy IR/Red on-demand trackers violate the continuous acquisition contract;
resume clears recordings; pause does not flush; the phone drops final batches
as soon as it pauses/stops; ECG uses sample-count timestamps and a stale
session anchor instead of device sample timestamps. ECG setup also makes
streaming depend on the optional Polar clock-setting command.

## Specification / BDD acceptance

1. Given a Watch with a native accelerometer, when Start is received, register
   the sensor independently of Samsung PPG readiness. Send real X/Y/Z in m/s²,
   using sensor event time converted from elapsed realtime to Unix milliseconds.
   Missing sensors or failed registration must report unavailable, not success.
2. Given supported Samsung PPG channels and permission, Start records Green,
   IR and Red independently. Never convert an empty callback into a zero sample.
   Batch by buffered sample count, not lifetime counter modulo.
3. Given 42 buffered samples, Pause/Stop sends exactly 42. Resume preserves the
   same session; a fresh Start resets it. Late final batches remain ingestible
   on the phone. Callback state changes and buffers use one thread.
4. Given Polar ECG samples, preserve the SDK's per-sample nanosecond intervals
   (epoch 2000) and gaps; map the first packet's last sample to phone receipt
   time once per stream. Never increment timestamps twice or collapse pauses.
   Unsupported clock-setting must not prevent ECG. H10 supports ECG;
   Verity Sense supports HR but must not be advertised as an ECG source.
5. Given HR support without ECG, record real device HR while the session runs.
   Do not mark ECG running until ECG data arrives; stop/disconnect/error clears
   stream readiness. Export retains real sample values and timestamp units.
6. Given malformed/partial/duplicate transport data, do not crash or append
   padded values. Batches use unique paths so final flush cannot overwrite the
   previous batch. No clinical, fixed FPS, or physical-sensor acceptance claim
   may be inferred from build/unit-test success.

## Plan and checklist

- [x] Inspect repository, SDK contracts, remote state, and device availability.
- [x] Write this plan before application changes.
- [x] Register services; repair permissions and native Watch acquisition.
- [x] Repair PPG lifecycle, batching, flush, and safe phone ingestion.
- [x] Repair Polar stream readiness, HR acquisition, and timestamp mapping.
- [x] Add focused executable regressions for these contracts.
- [x] Run all unit tests, Android lint, and both APK builds; resolve failures.
- [x] Verify the phone's install/start/pause/stop with available real hardware.
- [x] Replace `AGENTS.md` with the feature inventory, invariants, BDD contracts,
      maintenance gates, and honest software/hardware acceptance status.
- [x] Bump both application versions; document current/historical download links.
- [x] Audit scoped diff/publication files, commit, push, publish APKs and verify assets.
- [ ] Physical Galaxy Watch PPG/accelerometer and Polar ECG/HR CSV acceptance.

The last item requires those devices and real session exports. Leave it open
if unavailable; never fabricate a pass. Test fixtures are regression inputs,
not recorded physiological data.

## Workflow sources

Use the smallest investigation–plan–implementation–review loop, with BDD
requirements linked to executable tests and verification evidence:

- [OpenSpec](https://github.com/Fission-AI/OpenSpec): proposal/spec/design/tasks/verification.
- [BMAD](https://github.com/bmad-code-org/BMAD-METHOD): scoped build and review workflow.
- [Spec Kit](https://github.com/github/spec-kit): specifications and interaction contracts before implementation.
- [Polar SDK](https://github.com/polarofficial/polar-ble-sdk): sample timestamp and device capability contract.

## Verification results

Final source verification on 2026-10-12:

- `./gradlew.bat :shared:test :mobile:testDebugUnitTest :wear:testDebugUnitTest :mobile:lintDebug :wear:lintDebug :mobile:assembleDebug :wear:assembleDebug :mobile:connectedDebugAndroidTest --console=plain --no-daemon --max-workers=2`: **BUILD SUCCESSFUL**.
- Unit tests: **113**, shared **63**, phone **48**, Watch **2**;
  failures/errors/skips **0**.
- Android lint: **0 errors**, phone **49 warnings**, Watch **31 warnings**.
  AGP/Kotlin metadata compatibility repaired; no skipped-analysis compiler
  metadata errors in the final log.
- Installed phone app with `adb install --user 0 -r` (no uninstall/data wipe);
  observed versionCode **4**, versionName **1.3.0** on **SM-G988B / Android 13**.
- Phone instrumentation: **4 tests**, failures/errors/skips **0**:
  app context, private declared service, Start/Pause/Resume/Stop,
  300 redraw requests coalesced into one per frame.
- Both debug APK signatures verified and match certificate SHA-256:
  `fc6cc5cec1eb93984bc0cb0842b6d2f9c4bb6549f97617d42f457dc8983b55b4`.
- APK SHA-256: phone `50fd294dcd64fffc6827601c5c4f20c5378b75766a895918dd114d411187c337`;
  Watch `4627f91428e186894cc9478e3ad65ee39975bb629d464abdb78027b854fefd88`.
- Publication tree/history scan: no tracked APK/AAR/keystore/real CSV;
  Samsung SDK is local/ignored. Scoped staging and secret-pattern scan precede push.
- Public CI covers shared sensor clocks/buffers/session validation and phone
  tests/lint/build; Watch SDK checks are local because the AAR must stay private.
- Physical Watch acceleration/PPG and Polar ECG/HR are **not verified**:
  user confirmed those devices unavailable. No fabricated acquisition result.

Local machine receipts (ignored, not source artifacts):
`build/release-acceptance.log`, `build/verification-summary.json`,
JUnit XML reports and Android lint XML/HTML reports under module build folders.
Publication verified:

- Source fix commit: `efe9438ef7dbf360c987c1ef95542d3a52a52e4f`.
- Release target: `563d6c14b0892b3ae1e60ed9af9342f7407bac9d` on `Fix-issue`;
  subsequent commits only provision/fix the public CI environment.
- Public shared/phone CI **passed**:
  [run 38280205468](https://github.com/Adrian463588/HeartRateMonitor/actions/runs/38280205468).
  Earlier SDK setup failures (`sdkmanager` missing, retired `tools` package)
  were corrected; final job ran tests/lint/build successfully.
- Published prerelease [v1.3.0](https://github.com/Adrian463588/HeartRateMonitor/releases/tag/v1.3.0)
  contains exactly `mobile-debug.apk` and `wear-debug.apk`.
  Both GitHub asset digests/byte lengths match the local artifacts above.
- [Phone download](https://github.com/Adrian463588/HeartRateMonitor/releases/download/v1.3.0/mobile-debug.apk)
  and [Watch download](https://github.com/Adrian463588/HeartRateMonitor/releases/download/v1.3.0/wear-debug.apk).
- Historical `debug-2026-10-01` release and both previous APKs retained.
- This final receipt update changes documentation only. App source remains the
  validated/tagged release source. Source changes trigger CI; docs-only pushes
  skip redundant application builds.
- The remaining unchecked hardware item is intentionally **unverified**, as
  explicitly agreed with the user; it is not a fabricated pass.
