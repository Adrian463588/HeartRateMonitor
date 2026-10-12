# Main/Fix-issue comparison and DevSecOps integration — 2026-10-13

## Target and plan

- Fork: `Adrian463588/HeartRateMonitor`; upstream is not a write target.
- Initial main: `db5d56f60dc4ee71be4084623e643af872de984f`; Fix-issue: `713c2b93279430bfbba6fde7f6926128df766cb5`.
- Common ancestor: `c52ebe558b437f302139d9648b84df95501c24a2`; main-only **5** commits, Fix-only **7**.
- Preserve both histories. Integrate main into the candidate with a two-parent merge, resolve conflicts, test/audit before publishing, create PR base main/head Fix-issue, then use merge commit without squash/rebase/admin bypass or deleting branches.

## Comparison and resulting behavior

| Area | Main baseline | Fix-issue contribution / resolution |
| --- | --- | --- |
| Privacy | Disabled backup/device transfer, private settings/services, removal of raw signal/device-ID logs | Keep these controls while retaining the new sensor and transport lifecycle. No background-location request for modern Bluetooth. |
| Watch | Older individual tracking/control paths | Single continuous Green/IR/Red 25 Hz, native acceleration, ordered control/ACK/retry, 15-second lease, registration ownership and callback generations. |
| Polar | Older stream/timestamp handling | Pending/running ECG owner, capability/readiness gates, cancel/reconnect and bounded recovery, per-sample 130 Hz clock mapping, independent HR/RR. |
| Recording time | Millisecond display | Preserve three-component display using monotonic accumulated duration; keep Unix startTime/session identity unchanged across Pause/Resume/End. |
| Build/test | AGP 8.4.0, Kotlin 2.1.0, compile SDK 34, newer Wear libraries and legacy unit suites | Retain baseline tools/dependencies/SDK targets and tests. Both app versionCode 5/versionName 1.3.1; old on-demand 100 Hz assertions updated to the actual continuous 25 Hz contract. |
| Repository | Generated IDE/bin/build logs removed and SDK/key ignores | Preserve removal/ignores; add checked artifact/history policy, redacted Gitleaks and checks on main/Fix-issue/PRs. |

## Security boundaries and checks

- [x] Current source has no tracked AAR/APK/AAB, signing key, raw recording or machine/build output. Local private Samsung SDK only supplies Wear compilation.
- [x] Gitleaks **8.30.1**, verified Windows release SHA-256 `d29144deff3a68aa93ced33dddf84b7fdc26070add4aa0f4513094c8332afc4e`: initial main/Fix **112 commits / 1.25 MB**, all local branch/tag refs **153 commits / 2.12 MB**, no secret findings; output/reports redacted.
- [x] GitHub secret scanning/push protection enabled; no open secret alerts at inspection. Dependabot vulnerability alerts enabled for the fork; no open alerts returned at inspection. This is not a comprehensive dependency/SAST certification.
- [x] CI uses full action commit SHAs, `contents: read`, no persisted checkout credentials, bounded jobs, Gitleaks binary SHA-256 and Gradle distribution SHA-256.
- [x] Publication policy rejects current tracked forbidden files and every incoming commit's added/modified/renamed paths, including deleted-later bundles. Five policy tests passed.
- [x] Staged tracked-tree Gitleaks scan passed (no findings); publication policy passed for **201 paths / 7 incoming commits** before the integration commit. `git diff --cached --check` passed after normalizing the two inherited theme XML line endings; resource values are unchanged.
- [ ] Exact prepared-head and PR CI.
- [ ] PR creation, non-squash merge and resulting main/post-merge CI verification.

No AAR exists in either initial source tree. Both histories already contain
Polar AAR `3526ed159b8c210662b0cc8bd904167f772eda3c` at historical
`mobile/libs/polar-ble-sdk.aar`. It is pre-existing destination history, not an
incoming SDK object. Existing branches/tags are not rewritten; this audit does
not claim complete erasure of historic binaries or undetectable secret formats.
No new Samsung/Polar AAR or recording may enter this PR.

## Merged-candidate software gates

- Full shared/mobile/wear unit, both lint/APK builds and phone test-APK assembly: **passed** (`build/merge-full-verification.log`).
- JVM results: shared **113**, mobile **142**, wear **38** = **293**, failures/errors/skips **0**. Five publication-policy tests also passed.
- Lint: phone **0 errors / 53 warnings**; Wear **0 errors / 34 warnings**. Existing/current warnings remain visible.
- Phone instrumentation: **4 passed, 0 failures/errors/skips** on Samsung SM-G988B / Android 13 (`build/merge-phone-verification.log`). No Watch/H10 is available; actual optical/native/ECG acquisition and sensor-off/BLE recovery remain **unverified**.
- Immutable releases `v1.3.1`, `v1.3.0`, `debug-2026-10-01` stay unchanged. Candidate builds are source verification, not a replacement of those APK assets.

## Delivery receipt

Pending exact candidate commit, PR and resulting main merge SHA/parents/checks.
