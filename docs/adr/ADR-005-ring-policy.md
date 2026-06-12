# ADR-005: RingPolicy — grace auto-clear and finite max-ring
Date: 2026-06-12   Status: Accepted

## Context
The user delegated the "nobody deactivates" decision. Constraints: the sleeper must not hold a self-serve off-switch (the product's point), yet an alarm that nobody can stop is hostile (sleeping pact members, dead batteries, neighbours, alarm anxiety) and would get the app uninstalled day one.

## Decision
A pure `RingPolicy` (domain) drives two timers, evaluated by the RingService:
1. **Grace window** (per-alarm, default 3 min, 1–10): starts at `PROOF_DONE`. If no pact member deactivates in time → `AUTO_CLEARED`, ringing stops, feed records "auto-cleared — no pact member confirmed". The walk is the primary wakefulness evidence; the buddy tap is accountability, not a hostage mechanism.
2. **Max-ring cap** (per-alarm, default 10 min, 5–30): starts at fire. If still `RINGING` (no proof) when it expires → `MISSED`, ringing stops, feed records "slept through". Social visibility is the deterrent; infinite ringing punishes neighbours, not the sleeper.
3. Solo mode: `PROOF_DONE` ⇒ immediate `DEACTIVATED(self)` (no grace).

## Alternatives considered
- Ring-until-buddy-acts, no timeout — rejected: hostage design; one offline pact and the phone screams for hours.
- Escalating re-snooze after MISSED — rejected for MVP: re-waking someone who slept through 10 loud minutes has diminishing returns and doubles the state machine; roadmap "smart escalation" covers it.
- Auto-clear with *silent* feed — rejected: removing the social record removes the entire incentive structure.

## Rex's objection & resolution
Rex: "Auto-clear is a loophole: walk 30 steps, wait out 3 minutes in bed, sleep on. You've rebuilt Alarmy with extra steps." Resolution: partially conceded — and embraced. The 30-step walk is itself the anti-return-to-bed mechanism (the user's stated requirement); the buddy layer adds witnessing, streak pressure, and the *chance* of a human tap, not a guarantee. The grace window is per-alarm configurable up to 10 min for hard-mode users, and the feed entry distinguishes `DEACTIVATED by Sam` from `AUTO_CLEARED`, so pacts can see who is gaming it. Hard-mode "no auto-clear" toggle noted as a possible v1.1 setting.

## Consequences
Deterministic, fully unit-testable resolution (US-4/US-5 map directly); every alarm terminates in bounded time with an attributable outcome; product stays humane.
