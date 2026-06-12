# ADR-002: Dedicated RingActivity + RingService-owned session
Date: 2026-06-12   Status: Accepted

## Context
The ring/mission flow must: appear over the lock screen, turn the screen on, keep ringing across rotation/posture changes/activity death, keep counting steps with the screen off in a pocket-walk, and obey remote deactivation while backgrounded. Foldables make configuration changes routine mid-ring.

## Decision
A foreground `RingService` (type `mediaPlayback` — it plays alarm audio) owns the `RingSession` state machine (RINGING → PROOF_DONE → DEACTIVATED | AUTO_CLEARED | MISSED), the MediaPlayer/Vibrator, the `StepSource` subscription, the `RingPolicy` timers, and the `PactGateway` event document. A separate `RingActivity` (`showWhenLocked`, `turnScreenOn`, `excludeFromRecents`) is launched by full-screen intent and is a *dumb window* onto the session via `RingViewModel`; killing it changes nothing about the alarm.

## Alternatives considered
- Single-activity app, ring as a nav destination — rejected: lock-screen window flags are activity-wide; mixing them into MainActivity corrupts normal navigation, and back-stack isolation gets fiddly.
- Session state in the ViewModel — rejected: ViewModel dies with the activity; alarm state must outlive UI (user pockets the phone while walking their 30 steps).

## Rex's objection & resolution
Rex: "Two activities in 2026 is a legacy smell, and `mediaPlayback` FGS type is a dodge — `systemExempted` exists for alarms." Resolution: dedicated lock-screen activities remain the documented pattern for alarm/incall apps (Google Clock does the same). On the FGS type: `systemExempted` carries extra declaration constraints while `mediaPlayback` is squarely honest — the service's job is playing alarm audio; this is the widely shipped pattern for alarm apps. Service is started from `AlarmManager.setAlarmClock` delivery, which is exempt from background-start restrictions.

## Consequences
Ring flow is immune to UI lifecycle; slightly more plumbing (service ↔ VM via an injected singleton `RingSessionHolder`). RingActivity must guard against being resumed with no live session (finish immediately).
