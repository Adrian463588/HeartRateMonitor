# OpenSpec SDD: Watch Accelerometer Recording & Dual-Module Vitals Architecture

## Metadata
- **Target**: Galaxy Watch (Wear OS) & Android Smartphone (Mobile)
- **Scope**: Accelerometer recording on Watch, Wearable DataApi transport, Mobile reception, storage & CSV export, stream synchronization & state reset.
- **Methodology**: BMAD / OpenSpec SDD (Spec-Driven Development), BDD Scenarios, RTK & Ponytail Minimal Diff.
- **Status**: Active Implementation

---

## 1. System Invariants
1. **Motion Evidence Integrity**: Watch records real hardware acceleration (`Sensor.TYPE_ACCELEROMETER`) in $m/s^2$ with true timestamps. No synthetic zero-filling or synthetic motion fabrication.
2. **Lifecycle Synchronization**: Accelerometer listener MUST only register upon `START_ACTIVITY` (or resume) and MUST unregister upon `STOP_ACTIVITY`, pause, or destruction.
3. **Transport Reliability**: Buffered accelerometer data points MUST be flushed via DataApi prior to tracker shutdown to prevent end-of-session data truncation.
4. **Non-Blocking Ingestion**: High-frequency sensor ingestion must not block the UI thread or cause app freeze.
5. **Session Isolation**: Starting a new recording session resets counters and buffers across all channels (PPG Green, IR, Red, Accelerometer, ECG).

---

## 2. BDD Acceptance Scenarios (Gherkin Syntax)

### Feature 1: Galaxy Watch Accelerometer Collection & Batching
```gherkin
Feature: Galaxy Watch Accelerometer Ingestion
  Scenario: Watch receives START command and streams Accelerometer
    Given the Wear OS app is connected to the Phone app
    When the Phone initiates recording with START_ACTIVITY
    Then the Watch registers Sensor.TYPE_ACCELEROMETER listener
    And accelerometer samples (x, y, z, timestamp) are batched
    And batched AccelerometerData is sent to Phone via MessagePath.DATA_ACCELEROMETER

  Scenario: Watch receives STOP or PAUSE command
    Given the Watch is actively recording accelerometer data
    When a STOP_ACTIVITY or PAUSE_ACTIVITY message arrives
    Then any remaining buffered accelerometer samples are flushed to the Phone
    And the accelerometer sensor listener is unregistered
```

### Feature 2: Phone Accelerometer Reception & CSV Export
```gherkin
Feature: Phone Accelerometer Data Reception and CSV Storage
  Scenario: Phone receives AccelerometerData batch
    Given the Phone is in an active recording state
    When an AccelerometerData payload arrives on MessagePath.DATA_ACCELEROMETER
    Then the samples are appended to the internal accelerometer buffers
    And sample counters are updated

  Scenario: User saves session data including Accelerometer
    Given a completed recording session with recorded accelerometer samples
    When the user selects "Save All" or "Save Accelerometer Data"
    Then a CSV file named "<prefix>-Accelerometer-<timestamp>.csv" is written to SAF storage
    And the CSV header contains metadata (application version, datatype, duration, samples count)
    And the CSV rows contain "x,y,z,timestamp,formatted_time"
```

### Feature 3: Resilient Stream Connection & State Invalidation
```gherkin
Feature: Polar ECG Stream Auto-Start & State Management
  Scenario: Polar device completes BLE connection during active recording
    Given the user started recording while Polar was still connecting
    When streamingFeaturesReady reports ECG feature availability
    Then the ECG stream is automatically started without requiring manual toggling

  Scenario: Watch receives INFO message from Phone
    Given the Watch is connected to GoogleApiClient
    When an INFO message arrives on MessagePath.INFO
    Then the message is logged and handled safely without throwing NotImplementedError
```

---

## 3. Verification Gates
- [x] Gate 1: `shared` compiles with `AccelerometerData` model & passes all unit tests.
- [x] Gate 2: `wear` registers accelerometer listener, batches data, flushes on stop, passes compilation.
- [x] Gate 3: `mobile` receives accelerometer payload, stores data, exports to CSV, passes compilation & unit tests.
- [x] Gate 4: Zero unhandled TODOs or crashes on incoming Wearable messages.
- [x] Gate 5: Gradle linter / check passes without blocking errors.
