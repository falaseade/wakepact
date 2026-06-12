# ADR-004: Pluggable StepSource + pure StepChainValidator (anti-spoof)
Date: 2026-06-12   Status: Accepted

## Context
The wake-proof must be hard to perform from bed, hard to fake cheaply, accurately detectable on an unmodified phone, and must not hard-require a runtime permission (ACTIVITY_RECOGNITION can be denied).

## Decision
- `StepSource` interface emitting timestamped step events.
  - `HardwareStepSource`: `TYPE_STEP_DETECTOR` (hardware-fused, low-power) — used when ACTIVITY_RECOGNITION granted.
  - `AccelStepSource`: permission-free fallback — 50 Hz accelerometer, gravity-removed magnitude, low-pass smoothing, peak detection with adaptive threshold and 300 ms refractory period.
- `StepChainValidator` (pure Kotlin, domain): a step **counts** only when its interval from the previous step is 0.25–2.0 s **and** it extends a chain of ≥3 such steps (the first two steps of every chain are provisional and credited retroactively when the chain confirms); a gap >5 s breaks the chain. Goal default 30, per-alarm 10–100.
- Honest threat model (documented in README): this defeats half-asleep tapping, single jolts, and lazy shaking; a determined person rhythmically swinging the phone for ~30 convincing cycles is expending near-walking effort, and the pact layer (a human reviewing "did they really get up?" over time) is the second factor. Cryptographic proof of locomotion is explicitly a non-goal.

## Alternatives considered
- QR-scan-in-another-room — strongest anti-spoof, but needs camera permission + ML Kit + a setup flow (5th screen); deferred to v1.1 by MVP ceiling, not on merit.
- Math/memory/typing missions — rejected: solvable supine; fails the user's core requirement.
- Shake-count — rejected: trivially done from bed.
- TYPE_STEP_COUNTER (cumulative) — rejected: batching latency makes live progress UI laggy; DETECTOR delivers per-step events.

## Rex's objection & resolution
Rex: "The accelerometer fallback is a spoofing side-door — peak detection will count enthusiastic wrist-flicks." Resolution: both sources feed the *same* validator, so the chain/cadence rules apply uniformly; the refractory period and adaptive threshold reject high-frequency flicking; and the design treats anti-spoof as raising effort-cost, not as proof — backed by the social layer. Hardware detector is auto-preferred whenever permitted.

## Consequences
Mission works on every device with zero mandatory permissions; validator is pure and exhaustively unit-testable (US-2 ACs map 1:1 onto tests); two source implementations to maintain.
