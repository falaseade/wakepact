# WakePact — Google Play Store Listing (draft)

*Drafted via the `aso` skill on 2026-06-16, built on the positioning in
[`PRODUCT_EDGE.md`](PRODUCT_EDGE.md). All fields honor Google Play limits and policy
(no emojis / ALL-CAPS / "best" / "#1" / "free" / CTAs in the title; the full description
is search-indexed, so keywords are woven in naturally). Search-volume and ranking figures
require paid ASO tools and are not claimed here.*

## Title — 30 char limit

**`WakePact: Wake-Proof Alarm`**  *(26 chars)*

- A/B alternative: **`WakePact – Alarm With Friends`** *(29 chars)* — trades the "wake-proof"
  brand term for the social hook + the "friends" keyword that no competitor owns. Run both as
  a Store Listing Experiment; lead with the wake-proof variant.

## Short description — 80 char limit *(indexed)*

**`The no-snooze alarm a friend switches off—after you walk to prove you're awake.`**  *(79 chars)*

Packs the differentiator + "snooze," "alarm," "walk," "awake," "friend" into one line. The
em-dash keeps it readable in the truncated search view.

## Full description — 4,000 char limit *(indexed; target ~2–3% keyword density)*

> **You can't snooze your way out of this one.**
> WakePact is the alarm clock you literally cannot switch off by yourself. To silence it, you
> first walk enough steps to prove you're really up — then a friend in your "pact" taps the
> off-switch from their phone. Snoozing isn't a button. It's letting your mates down.
>
> **Why ordinary alarms fail**
> You set three alarms. You dismiss all three half-asleep and don't remember it. The snooze
> button always wins because it's the easiest thing in the room at 6 a.m. WakePact removes the
> easy path.
>
> **How it works**
> 1. Set your alarm — pick a step goal (10–100, default 30).
> 2. When it fires, the only way to quiet it is to get up and walk. Steps are sensor-detected
>    with an anti-spoof rhythm check, so shaking the phone under the duvet does nothing.
> 3. Once you've walked your proof, a friend in your pact gets the live alert and switches your
>    alarm off — and the whole group sees you made it.
>
> **The accountability is the product**
> Other wake-up apps pick one half. "Mission" alarms make you do a task — but you grade your own
> homework and can turn it off in a groggy haze. Social alarms loop in friends — but they only
> get a notification; they can't actually verify you're awake. WakePact fuses both: a physical,
> sensor-checked proof AND an off-switch that lives in someone else's pocket.
>
> **What makes WakePact different**
> • No ads. Ever. An ad on your screen at 6 a.m. is sabotage — we will never do it.
> • You can't fake it. The anti-spoof step check means lying in bed waving your arm won't cut it.
> • A real person holds you to it. Your pact sees every win and every "slept through."
> • One fair price. The social pact is free forever. Pay once to unlock extra missions and
>   stats — no endless subscription, no features yanked behind a paywall later.
> • Built to actually fire. Exact scheduling, survives reboots, rings full-screen over the lock
>   screen, owned by a foreground service so closing the app never kills your alarm.
>
> **Flying solo? Still works.**
> No friends on board yet, no internet, no account — WakePact runs fully offline. Completing the
> walk dismisses your alarm, and your wake-up history is logged on-device. Add a pact whenever
> you're ready.
>
> **Features**
> • Wake-proof walking mission with anti-spoof cadence detection
> • Private pacts — join a friend group with a 6-character invite code
> • Live remote deactivation: your pact switches your alarm off once you've proven you're up
> • Shared feed of outcomes — deactivated, auto-cleared, or "slept through"
> • Weekday repeat or one-shot alarms, labels, next-fire countdown
> • Reboot-safe exact alarms, full-screen lock-screen ring
> • Material You dark theme, dynamic color, foldable two-pane layout
> • Solo mode with a fully offline, on-device wake-up log
>
> **Your data stays yours**
> No email, no phone number, no contacts, no location. Step counts never leave your device —
> only the fact that you finished. Solo mode sends nothing anywhere.
>
> Set your first pact tonight. Wake up for real tomorrow.

*(~2,650 chars — leaves headroom to add localized keyword phrases or social proof once you have
ratings.)*

## What's new *(first release)*

> First public release. Wake-proof walking alarm, private pacts with live remote deactivation,
> fully offline solo mode, foldable-aware Material You design. No ads — ever. Tell us what
> alarm-dodging trick we should defeat next.

## Keyword strategy

Google indexes the **title + short description + full description** — there's no hidden keyword
field, so every target term must appear in visible copy at least once, ~2–3% density, no stuffing.

| Priority | Keyword / phrase | Why it matters | Where it's placed |
|---|---|---|---|
| **Primary** | alarm clock / alarm | Highest-volume head term; must anchor the listing | Title, short desc, description ×several |
| **Primary** | can't snooze / no snooze alarm | Captures the exact intent of frustrated snoozers | Short desc, description hook |
| **Primary** | wake up / hard to turn off alarm | The job-to-be-done; "Alarmy alternative" intent | Description ×several |
| **Secondary** | alarm with friends / social alarm | **Keyword gap** — no incumbent owns it; the wedge | Alt title, description |
| **Secondary** | accountability / accountability alarm | Rising self-improvement search theme | Description |
| **Secondary** | walk / steps / step alarm | The specific mission mechanic | Short desc, description |
| **Secondary** | loud alarm / heavy sleeper | High-intent for the persona | Description |
| **Long-tail** | alarm clock no ads / one-time alarm app | Harvests Alarmy's subscription/ads complainers | Description ("No ads. Ever." / "One fair price") |
| **Long-tail** | offline alarm / foldable alarm | Niche differentiators with low competition | Features list |

**Deliberately avoided:** "best," "#1," "free" in the **title** (Google rejects these); "free" is
used sparingly in the description body where it's allowed and true ("free forever").

## Visual assets — required before publishing

Google Play **requires** these; they're ~25% of the ASO score and where conversion is won or lost:
- **Feature graphic (1024×500, mandatory for featuring):** the line *"Snooze isn't a button. It's
  letting your mates down."* over the ring screen.
- **Screenshots (min 2, max 8 — use 6+), each with a caption headline:** 1) "The alarm you can't
  turn off yourself" (ring + step dial), 2) "Walk to prove you're awake" (mission progress),
  3) "A friend holds the off-switch" (pact deactivation card), 4) "No ads. No fake-outs. One fair
  price.", 5) "Works fully offline, solo", 6) the foldable two-pane shot.
- **Preview video:** optional on Google (only ~6% tap play) — lower priority than on iOS. Skip for
  launch; revisit later.
- **Data safety section:** declare the honest minimum — no personal data collected in solo mode;
  anonymous UID + alarm/outcome metadata only when a pact is configured. This honesty is itself a
  conversion asset against Alarmy's "feels like a scam" reviews.
