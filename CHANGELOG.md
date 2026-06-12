# Changelog
All notable changes to WakePact. Format: Keep a Changelog (lite). Versioning: semver-lite.

## [0.1.0] - 2026-06-12
### Added
- Alarms: create/edit/delete, weekday repeat mask or one-shot, per-alarm label; exact scheduling via `setAlarmClock` with `USE_EXACT_ALARM`, reboot rescheduling, next-fire countdown on each card.
- Wake proof: hardware step-detector mission with accelerometer fallback; `StepChainValidator` anti-spoof cadence gating (chains of ≥3 rhythmic steps, 0.25–2 s intervals); configurable goal 10–100 steps.
- Ring experience: full-screen lock-screen `RingActivity` over a foreground `RingService` owning audio/vibration/wake-lock; Back cannot dismiss an unresolved ring.
- Pact (optional Firebase): create/join by 6-char invite code, live member list, buddy-only deactivation of a proven ring, shared ring feed (last 50). Anonymous auth; app runs fully solo when no Firebase project is configured.
- Unattended resolution policy: PROOF_DONE + silent grace window (1–10 min) → AUTO_CLEARED; no proof by max-ring cap (5–30 min) → MISSED; solo proof → immediate self-deactivation.
- Two-pane layout ≥ 840 dp (Pixel Fold unfolded): alarm list + inline editor with per-draft state.
- Tooling: `tools/validate_project.py`, GitHub Actions CI (lint → unit tests → debug APK artifact), Timber + StrictMode + LeakCanary debug forensics, Dependabot grouped updates.
### Fixed (during Phase 6 review)
- Overlapping second alarm is no longer silently dropped: its occurrence is skipped with a log, repeating alarms re-arm, one-shots disable (REVIEW #1).
- Notification-permission banner now re-checks the grant on resume (Settings path), and pact member chips wrap with `FlowRow`.
- Pact documents mirror a flat `memberUids` array so Firestore security rules can enforce member-only access to ring events (REVIEW #8, found writing `docs/FIREBASE_SETUP.md`).
