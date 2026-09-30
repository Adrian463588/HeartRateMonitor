# Merge and release audit — 2026-10-01

## Provenance

- Destination: the public fork `Adrian463588/HeartRateMonitor`, branch `main`. Its upstream is `aditydcp/sample-wear-phone-app`; upstream was not a push target.
- Requested source: the checked-out `fix-issue-2` branch in `BenarJanganDiubah2`, originally `3ea4345787b54d5775bd1f527fb8d2e4fcfcdefa7c`. The newer `rejuvenatev2` branch was excluded.
- Fork `main` before the merge: `63c4067c37f0ea3ff51de5ea06bdfc967e8dfa7c`.
- Samsung Health Sensor SDK AAR objects were removed from the reachable Git history before merging. This changed the base to `cd6859202699247128e9dd006f3d2054c1907f4a` and the requested source to `da7cb38456011b89e44e589d45bd1779cda2ab96`.
- The actual two-parent merge is `2d8ff0509ef16d018d4c4f26f831b24395790564`, with the rewritten fork base and `fix-issue-2` as its parents. The build, privacy, and documentation fixes follow that merge.

## Code and repository changes

- Updated the Gradle wrapper, Android Gradle Plugin, and Kotlin compiler so the requested branch builds locally with JDK 21 and Android SDK 34. Incremented debug app versions to phone `1.2.0` (`3`) and watch `1.1.0` (`2`).
- Corrected Android Bluetooth runtime permission selection, nullable preference handling for the Polar device ID, and Bluetooth broadcast registration. Registered the phone recording service with the required foreground service type. Restricted the settings activity to internal use.
- Disabled application data backup and device transfer for both apps. Removed raw HR/PPG and device identifier values from debug logs. Reduced unnecessary location permissions on newer Android versions.
- Removed tracked IDE state, generated classes, and old build logs. Ignored local AARs, APKs, signing keys, build outputs, and machine configuration.
- Rewrote the README with developer setup, Samsung SDK acquisition/import instructions, device requirements, direct debug APK links, and a version log.

## Verification

Command: `./gradlew :mobile:assembleDebug :wear:assembleDebug :mobile:lintDebug :wear:lintDebug :mobile:testDebugUnitTest :wear:testDebugUnitTest :shared:test --no-daemon`

Result: **BUILD SUCCESSFUL**. Across 26 JUnit XML suites: **235 tests, 0 failures, 0 errors, 0 skipped**. Android lint: phone **0 errors / 49 warnings**; watch **0 errors / 27 warnings**. Remaining warnings mainly concern unused resources, older dependencies and APIs, and launcher icon variants; they are recorded in local lint reports. `git diff --check` passed.

Both APKs passed `apksigner verify` and share debug signer SHA-256 `fc6cc5cec1eb93984bc0cb0842b6d2f9c4bb6549f97617d42f457dc8983b55b4`.

| APK | SHA-256 |
| --- | --- |
| `mobile-debug.apk` | `B527E5040D37D616EE818BB02240001157B0C12EE78D9E7F51B4ECD163EF8821` |
| `wear-debug.apk` | `CDB614DB4C4CBAB79E1E7E890188BB136516500C37FDFDBC4978F6E13F20E450` |

Phone smoke check: `adb install -r` succeeded on `SM_G988B`; `MainActivity` started and the app process remained running. This preserved existing app data. No watch appeared in `adb devices`, so Watch installation, Samsung PPG acquisition, Polar ECG acquisition, and end-to-end recording were **not physically verified**.

## Security and distribution boundary

- No AAR, APK, or signing key is tracked in the current source tree. The locally supplied Samsung SDK v1.4.1 AAR is ignored and used only to compile the Watch APK.
- The Samsung `priv-health-tracking` / `samsung-health-sensor-api` AAR paths have **zero reachable Git objects** across all rewritten branch heads. There are no remote tags to retain those objects. A legacy Polar SDK AAR object remains in older history; it is not a Samsung SDK object and is not present in the current tree.
- A pattern scan of reachable text blobs found no GitHub tokens, AWS keys, Google API keys, private-key PEM blocks, Slack tokens, or assigned password/secret values. This is a heuristic scan, not a guarantee that every possible secret format is absent.
- The published APK pair is for developer testing. Samsung's partner authorization and package/signature registration for public Samsung sensor access were not verified. An installed APK or passing build does not establish sensor availability or clinical validity.
