# HeartRateMonitor implementation report

Current contract: [AGENTS.md](AGENTS.md).
Current repair and validation receipts: [SESSION_CONTROL_FIX_PLAN.md](SESSION_CONTROL_FIX_PLAN.md).
Historic acquisition repairs: [SENSOR_FIX_PLAN.md](SENSOR_FIX_PLAN.md).
Downloads/history: [README.md](README.md).

The phone coordinates a session, receives Watch PPG/native motion, and connects
to supported Polar ECG/HR/RR features. Watch Green/IR/Red continuous acquisition
uses one Samsung tracker at 25 Hz, while H10 ECG uses 130 Hz. Device capability,
permission and SDK policy remain runtime gates.

Version 1.3.0 repairs service registration, native motion independence,
pause/resume/drain semantics, unique/session-aware batch delivery, sample-clock
mapping, RR epoch correction, HR recording/export and frame redraw scheduling.
Raw acquisition is separate from the existing QRS heuristic and its derived HR.

Version 1.3.1 adds ordered and acknowledged session control, Watch heartbeat
timeout and retryable sensor release, recording-scoped CPU wake locks, and
owned/cancelable Polar ECG startup with bounded error recovery.

Receipts, software test counts and lint warnings must be read from the selected
repair evidence. No physical Watch/H10 acquisition, fixed frame rate, clinical
accuracy or exact inter-device synchronization result is claimed.
