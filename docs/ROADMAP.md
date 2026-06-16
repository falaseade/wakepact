# Roadmap

## Now — MVP (this build)
Alarms (exact, repeating, reboot-safe, full-screen) · walk-N-steps wake-proof with anti-spoof cadence rules and permission-free fallback · one Pact per user with invite codes · live remote deactivation via Firestore listeners · grace-window auto-clear · missed-alarm feed records · solo mode with zero backend setup · Material 3, dark theme, foldable layouts.

## Next — v1.1 candidates (ordered by value/effort)
1. **Buddy push notifications** — FCM + a small Cloud Function on `ringEvents` writes, so a pact member's phone chimes even with the app closed. Highest-impact missing piece; needs `google-services.json` and a deployed function (docs/FIREBASE_SETUP.md sketches it).
2. **QR/barcode mission** — at setup, scan any barcode in the house (shampoo bottle, cereal box); at alarm time you must walk to it and re-scan. Strongest anti-spoof; CameraX + ML Kit bundled barcode model.
3. **Plain-dismiss preference for solo users** who explicitly want a normal alarm.
4. **Streaks & per-member stats** on the Pact screen (data already in the feed).
5. **Multiple pacts** and per-alarm pact selection.
6. **Alarm queueing for overlaps** — today a second alarm firing mid-ring skips its occurrence (re-armed, logged; see REVIEW #1); queue and chain-ring instead.
7. **Two-pane chrome polish** — single shared top bar in the ≥840 dp layout instead of nested editor Scaffold (REVIEW #4).
8. **Accessibility pass** — alarm rows become `toggleable()` with full semantics; TalkBack walkthrough of the ring screen (REVIEW #5).

## Later — v2 ideas
- Mission variety: photo-of-registered-object (on-device matching), typing drills, sunlight-lux check.
- Wear OS: buddy deactivation from the wrist.
- Smart escalation: grace window shrinks as your missed-count grows.
- Quiet-hours etiquette: pact members can mark "don't route to me before 08:00".
- Home-screen widget with next alarm + pact pending count.

## Moonshots
- Wager mode: missed alarm donates £1 to a cause the group picks (payments = heavy compliance lift).
- Cross-platform pacts (iOS client, shared Firestore schema already neutral).
- "Alarm roulette": a random pact member's voice note plays as your alarm sound (YouUp-style, but with our verification loop).

## Monetization — decided (see [`PRODUCT_EDGE.md`](PRODUCT_EDGE.md), 2026-06-16)

**The call: be the anti-Alarmy. Keep the social pact free forever; monetize capability, never the social loop.** Alarmy's loudest reviews are about an aggressive subscription, ads at wake time, and missions that used to be free getting paywalled — so a fair *one-time* price is itself the positioning. The pact requires inviting a friend, so it *is* the growth engine; gating it would throttle the one thing that makes WakePact spread (Galarm proves free social scales).

- **Free forever**: unlimited basic alarms, the step mission, **one pact**, solo mode, the feed. No ads, ever. (Note: do **not** cap alarm count — that's the petty Alarmy move; gate capability instead.)
- **WakePact Pro — one-time £6.99 / $7.99** (or £1.49/mo for the subscription-minded): QR/photo/math missions, streaks & stats, widgets, multiple pacts, custom sound packs.
- **Pact Pro (group unlock)**: one member pays → the whole pact is unlocked. Fits the social DNA and the payer evangelizes the invite — the cleverest revenue lever.
- **Cosmetics**: sound packs, feed reactions, app icons (fold into Pro or sell à la carte).
- **Ads rejected**: an ad on the ring screen at 6 a.m. is product sabotage — and "no ads, ever" is a load-bearing part of the positioning.

*Earlier draft (superseded): free = 1 pact + 3 alarms; the 3-alarm cap is dropped — gating alarm count annoys users and undercuts the anti-Alarmy stance.*
