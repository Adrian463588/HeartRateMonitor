# Watch/Polar acquisition specification

Status: implemented software contract for **1.3.0**; physical sensor acceptance
pending. Feature inventory: [AGENTS.md](../../AGENTS.md).
Plan and verification evidence: [SENSOR_FIX_PLAN.md](../../SENSOR_FIX_PLAN.md).

## Requirements

- SHALL acquire real native Watch acceleration independently of Samsung readiness,
  honor registration failure, and retain event-time-based Unix milliseconds.
- SHALL use one `PPG_CONTINUOUS` tracker for supported Green/IR/Red at 25 Hz;
  reject abnormal status/empty callbacks without zero substitution.
- SHALL batch 300 PPG or 100 acceleration samples by current buffer size;
  final smaller batches have exactly their recorded samples.
- SHALL serialize callbacks/control/buffers, cancel old callback generations,
  and separate sensor-triggered rendering through Choreographer coalescing.
- SHALL use one session ID across pause/resume, unique batch paths and counts
  to distinguish replay, previous sessions and incomplete final transfers.
- SHALL retain late data for the current session after Stop/Pause. Explicit
  partial export SHALL mark incomplete transfer; Stop alone is not a receipt.
- SHALL stream ECG only when connected, active and ECG-ready; preserve Polar
  sample nanoseconds/gaps and anchor one clock per subscription. Optional
  device clock setting SHALL NOT block ECG.
- SHALL acquire supported HR independently of ECG. RR uses SDK milliseconds;
  packet receipt anchoring SHALL NOT add an epoch to itself or collapse pauses.
- SHALL retain raw samples across plot setup. Changing/restarting a Polar
  connection SHALL end an active session safely before replacing its API.
- SHALL export real received rows via SAF with datatype, sample count, nominal
  rate/units and clock/transfer metadata. Absence means an empty channel.
- SHALL publish matching versioned APKs while keeping Samsung AAR private.

## BDD scenarios

```gherkin
Feature: Independent Watch motion and continuous PPG
  Scenario: Missing Samsung service
    Given a Watch with a native accelerometer and no usable Samsung service
    When a session starts
    Then native acceleration registration is attempted
    And PPG reports unavailable without fabricating values

  Scenario: Three supported continuous PPG channels
    Given PPG_CONTINUOUS capability and Body Sensors permission
    When a session starts
    Then one tracker requests GREEN IR RED together
    And only normal-status samples are recorded at the SDK's 25 Hz contract

Feature: Batch lifecycle and delivery
  Scenario: Flush after pause
    Given 42 buffered samples
    When Pause unregisters producers and drains their application buffers
    Then exactly 42 samples enter transport
    And the paused phone can accept the late batch
    And Resume keeps its session and prior samples

  Scenario: No off-by-one drain
    Given a full batch plus one buffered sample
    When the full batch is removed
    Then the next complete value and timestamp tuple remains

  Scenario: Stop received before tail batch
    Given Stop expects 42 acceleration samples
    When the count receipt arrives first
    Then completion remains false until the unique tail arrives
    And replay does not increase current counts
    And a new session rejects the previous session's late batch

Feature: Polar clocks and capabilities
  Scenario: Unsupported ECG
    Given a Polar device advertising HR without ECG
    When the session records
    Then HR notifications are retained
    And ECG is reported unavailable without a fabricated stream

  Scenario: Correct ECG interval
    Given 131 SDK timestamps over one second at 130 Hz
    When the device nanoseconds are mapped to receipt-anchored Unix milliseconds
    Then first-to-last span remains 1000 milliseconds
    And dropped samples and resume gaps remain represented

  Scenario: RR clock after pause
    Given millisecond RR intervals and a delayed notification
    When beat plot times are derived
    Then the packet's last beat uses receipt time
    And absolute epoch values are not added together
```

## Verification layers

- Source/JVM: clock/buffer/transport validation/session regressions.
- Android build: all modules' tests, phone/Watch lint and both debug APKs.
- Phone runtime: private service declaration, Start/Pause/Resume/Stop and one
  redraw per plot/frame after a burst. No physical acquisition inference.
- Hardware (pending): actual Galaxy Watch PPG/acceleration and Polar H10/HR
  sessions/CSVs, sample counts, measured intervals, pauses/tails, reconnect,
  missing sensor/permission policy and SDK authorization.

Never promote source/build/runtime checks to a physical acquisition pass.
Do not claim continuous 100 Hz IR/Red, exact synchronization, clinical accuracy
or process-death recovery under this contract.
