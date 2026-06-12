# WakePact — Product Brief

## One-liner
An alarm clock you cannot switch off yourself: you prove you're awake by walking, and a friend in your "pact" holds the off-switch.

## Target user & job-to-be-done
**Persona:** "Serial snoozer" — student or shift-worker (18–35) who owns an Android phone, sleeps through conventional alarms or dismisses them half-asleep, and has at least one friend/partner/flatmate willing to keep them honest.
**Job-to-be-done:** *When my alarm fires, force me through enough physical activity that going back to sleep is no longer the path of least resistance — and make a person I respect witness whether I managed it.*

## Core loop
1. Evening: set/enable an alarm (optionally part of a Pact group).
2. Morning: alarm fires full-screen, loud, undismissable.
3. Owner completes the **wake-proof mission**: walk N steps (default 30), detected on-device.
4. Proof is posted to the Pact; alarm drops to a softer "pending" pulse.
5. A pact member taps **Deactivate** on their phone → owner's alarm stops; the deed is recorded in the Pact feed.
6. Feed/streaks reinforce the habit; repeat tomorrow.

## MVP scope

| In | Out (→ roadmap) |
|---|---|
| Multiple alarms: time, repeat days, label, per-alarm mission settings | Push notifications waking buddies' phones (needs FCM + Cloud Function) |
| Exact scheduling, survives reboot, fires over lock screen | QR-code "scan the bathroom barcode" mission type |
| Walk-N-steps wake-proof: hardware step detector, accelerometer fallback, anti-spoof cadence rules | Photo missions, math missions, NFC missions |
| One Pact group per user: create, join via 6-char invite code, leave | Multiple groups, contact-book invites, chat |
| Live remote deactivation: pact members see proof-done requests in real time (app open) and stop the owner's alarm | Streak leaderboards, weekly stats, charts |
| Lifecycle resolution: grace-window auto-clear, missed-alarm record (see below) | Snooze-stealing / wager features |
| Solo mode (no pact or no Firebase config): mission itself dismisses the alarm | Wear OS companion, widgets |
| Pact feed: last 50 events (deactivated by whom, auto-cleared, missed) | Custom alarm sounds / music picker |
| Dark theme, dynamic color, foldable-aware layouts | iOS |

## The unattended-alarm resolution (decision the user delegated)
1. **Proof completed, no pact member reacts:** after a per-alarm **grace window** (default 3 min) the alarm **auto-clears**. The walk is the primary evidence of wakefulness; the buddy tap is accountability, not a hostage situation. Feed records `AUTO_CLEARED — no pact member confirmed`, preserving social pressure.
2. **No proof at all:** alarm rings at full volume for a per-alarm **max-ring cap** (default 10 min, range 5–30), then stops and posts `MISSED — slept through` to the feed. Endless ringing drains battery, ruins neighbours' mornings, and punishes nobody useful; a public "slept through" record is the deterrent that fits this app.
3. **Solo mode:** completing the mission stops the alarm immediately (the app's default deactivation behaviour).

## User stories

US-1: As a sleeper I want alarms that fire at the exact time, repeat on chosen weekdays, and survive reboots, so the wake-up is dependable.
  AC-1.1: Given an enabled alarm at 07:00 with Mon–Fri repeat, When "now" is Tue 06:00, Then next trigger computes to Tue 07:00.
  AC-1.2: Given an enabled one-shot alarm whose time already passed today, When next trigger is computed, Then it is tomorrow at that time.
  AC-1.3: Given enabled alarms exist, When the device reboots, Then all are rescheduled.

US-2: As a sleeper I want the ringing screen to demand a walking mission I cannot fake from bed, so I am genuinely up.
  AC-2.1: Given the mission is active, When step events arrive with inter-step intervals within 0.25–2.0 s in chains of ≥3, Then each chained step increments progress.
  AC-2.2: Given a gap of >5 s between steps, When the next step arrives, Then the cadence chain restarts (steps 1–2 of a new chain do not count until the chain reaches 3).
  AC-2.3: Given progress reaches the goal (default 30), Then the mission completes exactly once.
  AC-2.4: Given ACTIVITY_RECOGNITION is denied, When the mission starts, Then the accelerometer-based detector is used instead and the mission still works.

US-3: As a pact member I want to see a live "needs deactivation" card when a friend finishes their proof, so I can switch their alarm off.
  AC-3.1: Given a ring event in state PROOF_DONE for another member, When the Pact screen is open, Then a deactivation card appears without manual refresh.
  AC-3.2: Given I tap Deactivate, Then the event state becomes DEACTIVATED with my id, and it leaves the pending list.

US-4: As a sleeper whose proof is done, I want my phone to stop ringing the moment a pact member deactivates, and to auto-clear if nobody does.
  AC-4.1: Given state PROOF_DONE, When a remote DEACTIVATED update arrives, Then ringing stops.
  AC-4.2: Given state PROOF_DONE and grace window elapses with no remote update, Then state becomes AUTO_CLEARED and ringing stops.

US-5: As a sleeper who never completes the proof, the alarm must give up loudly but finitely.
  AC-5.1: Given state RINGING and max-ring cap elapses, Then state becomes MISSED, ringing stops, and the event is recorded.

US-6: As a solo user (no pact / no backend configured), completing the mission must dismiss the alarm directly.
  AC-6.1: Given no pact membership, When the mission completes, Then state becomes DEACTIVATED(self) and ringing stops.

US-7: As a user I want to create or join exactly one pact with a short invite code.
  AC-7.1: Given I create a pact named "Flat 4B", Then a 6-character code is generated and I am a member.
  AC-7.2: Given a valid code, When I join, Then I appear in the member list with my display name.

US-8: As a user I want a feed of recent outcomes so the social pressure is visible.
  AC-8.1: Given events exist, Then the feed lists them newest-first with outcome, owner, and (if any) deactivator.

## Screen map

| Screen | Purpose | Compact layout | Expanded/unfolded layout |
|---|---|---|---|
| Alarms (home) | List alarms, toggle, entry to editor & pact | Single-column list, FAB, pact status chip in top bar | List–detail two-pane: list left, selected alarm's editor right |
| Alarm editor | Time, repeat days, label, step goal, grace, max-ring | Full-screen form | Right pane of two-pane (or centered 600 dp column when opened alone) |
| Ring & Mission | RINGING → MISSION (live step ring) → PENDING → resolved | Full-screen state machine, giant progress ring | Same column centered, 480 dp max width; no two-pane (single focus) |
| Pact | Create/join, members, pending deactivation cards, feed | Sections stacked vertically | Members + join card left, pending cards + feed right |

## Data model sketch
Local (Room): `AlarmEntity(id, hour, minute, daysMask, label, enabled, stepGoal, graceSec, maxRingSec)`; `RingRecordEntity(id, alarmId, firedAt, outcome, deactivatedBy?)` for solo history.
Remote (Firestore, when configured): `pacts/{pactId} {name, inviteCode, members[{uid,name}]}`; `pacts/{pactId}/ringEvents/{id} {ownerUid, ownerName, label, firedAtMs, state ∈ RINGING|PROOF_DONE|DEACTIVATED|AUTO_CLEARED|MISSED, proofAtMs?, resolvedAtMs?, deactivatedByUid?, deactivatedByName?}`.
State transitions are written by the owner's device except `PROOF_DONE → DEACTIVATED`, written by a buddy; the owner's ringing service listens and obeys.

## Permissions & privacy notes
- `POST_NOTIFICATIONS` (runtime, API 33+): the ringing foreground-service notification. Requested on first alarm save.
- `ACTIVITY_RECOGNITION` (runtime, optional): hardware step detector. Denied → accelerometer fallback, no data leaves the device either way.
- `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` (manifest): exact firing — core, justified store category (alarm clock).
- `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `VIBRATE`, `FOREGROUND_SERVICE` (+ media-playback type), `USE_FULL_SCREEN_INTENT`, `INTERNET`.
- Data collected: alarm metadata and ring outcomes only. With Firebase configured, those sync to the owner's Firestore project under anonymous-auth UIDs; no email, phone number, contacts, or location. Step *counts* never leave the device — only the boolean fact that proof completed.

## Assumptions made on the user's behalf (veto any of these)
1. **Wake-proof = walking steps** (30 by default) with cadence-chain anti-spoof, chosen over QR-scan for zero-setup universality; QR is the first roadmap item. Rhythmic hand-shaking *approximating* walking for 30 chained steps costs nearly the effort of walking; combined with the buddy gate this meets "not easily faked" without claiming cryptographic proof.
2. **"Normal alarm with default deactivation" in solo mode** = the mission itself dismisses (the app's default), not a plain swipe-to-dismiss — otherwise the app is pointless solo. A plain-dismiss preference can be added if you disagree.
3. **Backend = Firebase** (Firestore + anonymous auth), initialised programmatically from optional `local.properties` values — the project builds and runs with zero Firebase setup (solo mode), and turns social on when keys are supplied. Free Spark tier suffices for a friend group.
4. Buddies see requests in real time while the app is open; OS-level push to closed apps needs an FCM server component → roadmap, documented honestly.
5. One pact per user, max 10 members, events retained 50-deep — MVP simplicity.
6. Default grace 3 min, max-ring 10 min, both per-alarm configurable.

## Open questions
None blocking — assumptions above stand unless vetoed.
