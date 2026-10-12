# Session control and Polar recovery — v1.3.1

Plan/evidence: [SESSION_CONTROL_FIX_PLAN.md](../../SESSION_CONTROL_FIX_PLAN.md).
Scope: software behavior, with physical Watch/H10 acceptance pending.

## Requirements and design

- SHALL send explicit Start/Pause/Resume/End intent using a positive session ID
  and increasing command revision; fresh Start uses a newer ID. End is terminal.
- SHALL query current peers for each send, retry the latest unacknowledged intent
  every 3 seconds and discard obsolete asynchronous lookups/acknowledgements.
  MessageApi send success alone is not sensor-state confirmation.
- SHALL acknowledge Pause/End only when the same session/revision/state reports
  `sensorsStopped=true`. Failed unregistration retains ownership and is retried.
  Final four-channel count reconciliation remains a separate transfer gate.
- SHALL heartbeat active acquisition every 3 seconds. A Watch with no valid
  command for 15 seconds SHALL pause acquisition/drain application buffers.
  The timeout uses monotonic time; rejected stale commands cannot extend it.
  Actual timeout/LED behavior requires hardware verification.
- SHALL retain the last Watch control intent in private preferences. Activity
  destruction closes that session; delayed Start cannot restart it after
  recreation. Preferences do not provide durable biosignal storage.
- SHALL release native and Samsung listeners and CPU wake locks on Pause/End.
  CPU stays awake during active acquisition to deliver screen-off heartbeats.
  Bind SDK callbacks to their registration so canceled callbacks cannot enter
  a resumed buffer. Keep service for paused UI/final transport as needed.
- SHALL own both pending and active ECG with one disposable and identity guard;
  duplicate Start cannot overlap or toggle off a current subscription. A
  synchronous settings/start error must not leave an active stale handle.
- SHALL capture the Polar API/device for each attempt and ignore obsolete API
  callbacks after restart/device change. Accept a configured ID or address;
  do not rely on exact case when interpreting SDK callback identity.
- SHALL dispose ECG on Pause/End/disconnect/destruction. In SDK 4.0, cancellation
  before the start handshake completes SHALL close the BLE connection so a
  pending sensor start cannot remain orphaned. Resume reconnects and waits for
  readiness; allow one second for asynchronous stop before restarting.
- SHALL time out missing ECG delivery after 10 seconds. Permit at most two
  consecutive transient-error retries, with a one-second delay; received ECG
  or explicit Start/Resume resets the budget. Unsupported settings/permission
  errors do not retry; final errors remain visible for manual recovery.
- SHALL preserve 130 Hz per-sample Polar timing, pause gaps, existing recordings
  and independently acquired real HR/RR. HR emitted by the device while the
  application is paused/stopped SHALL not enter the recording.

## BDD and executable mapping

```gherkin
Feature: Ordered Watch control
  Scenario: Delayed Start after End
    Given Start revision 1 and End revision 3 for session A were applied
    When Start revision 1 or revision 4 arrives for session A
    Then acquisition remains stopped
    And a newer session B can start

  Scenario: Lost Start and offline End
    Given Start was lost or Watch temporarily cannot be reached
    When the phone requests End
    Then it keeps the latest intent pending and queries current peers on retry
    And End is accepted even when Start did not arrive
    And sensor-off status waits for the matching successful-release ACK

  Scenario: Disconnected controller
    Given a valid Start heartbeat was last received at monotonic time T
    When no valid command is received for 15 seconds
    Then Watch pauses and releases producers and wake lock
    And stale commands do not postpone the deadline
    And reconnection can resume the same still-live in-memory session

  Scenario: Failed sensor release
    Given the SDK rejects an unregister call
    When Pause or End is applied
    Then callbacks are invalidated but registration ownership is retained
    And release is retried and not acknowledged as off before success

Feature: Polar stream ownership
  Scenario: Pending start canceled
    Given ECG settings or start is pending
    When Pause or End occurs
    Then subscription disposal prevents late settings from starting a stream
    And a pending SDK start is canceled by closing its connection
    And Resume reconnects only for an active unpaused session

  Scenario: Synchronous error and immediate retry
    Given the SDK fails settings before subscribe returns
    When error recovery is considered
    Then no stale disposable blocks the next attempt
    And retries are bounded and no subscriptions overlap

  Scenario: Late callback after Resume
    Given a previous subscription or API was canceled
    When its queued data or error arrives during a new attempt
    Then the new stream and recordings remain unchanged
```

`WatchControlTest` covers ordering, restored End, heartbeat, lease and ACK
requirements. `TrackerLifecycleTest` covers registration/release/callback
ownership. `EcgStreamControllerTest` covers synchronous/pending startup,
disposal, completion, timeout and retry budget. Phone `RecordingLifecycleTest`
covers actual Android menu/service/wake-lock wiring. Existing timestamp,
recording-buffer, batch-validation and transfer tests remain required.

## Physical acceptance (unverified)

Use matching 1.3.1 APKs on actual Watch/H10. Repeat rapid Start/Pause/Resume/End;
observe PPG illumination and live native counts cease after acknowledged Pause/
End, including a deliberately lost command and disconnected phone. Check the
15-second nominal lease deadline and screen-off recording. Interrupt Polar
during pending settings/start and active streaming; Resume/reconnect must not
produce overlapping streams or erase preceding samples. Export actual CSVs,
record per-channel counts/rates/timestamp gaps and compare final transfer counts.
No device outcomes are inferred from JVM or phone instrumentation.

## Sources

- [Samsung HealthTracker registration, unregistration and flush](https://developer.samsung.com/health/sensor/api-reference/com/samsung/android/service/health/tracking/HealthTracker.html).
- [Polar SDK 4.0 streaming implementation](https://github.com/polarofficial/polar-ble-sdk/blob/4.0.0/sources/Android/android-communications/library/src/sdk/java/com/polar/sdk/impl/BDBleApiImpl.kt): stop is attached after the start handshake and runs asynchronously.
- [OpenSpec implementation skill](https://github.com/Fission-AI/OpenSpec/blob/main/skills/openspec-apply-change/SKILL.md) and [BMAD build skill](https://github.com/bmad-code-org/BMAD-METHOD/blob/main/skills/bmad-build/SKILL.md): workflow research, adapted to this bounded repair; no CLI/framework installation claimed.
