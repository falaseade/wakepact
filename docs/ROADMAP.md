# Roadmap

## Now — MVP (this build)
Alarms (exact, repeating, reboot-safe, full-screen) · walk-N-steps wake-proof with anti-spoof cadence rules and permission-free fallback · one Pact per user with invite codes · live remote deactivation via Firestore listeners · grace-window auto-clear · missed-alarm feed records · solo mode with zero backend setup · Material 3, dark theme, foldable layouts.

## Next — v1.1 candidates (ordered by value/effort)
1. **Buddy push notifications** — FCM + a small Cloud Function on `ringEvents` writes, so a pact member's phone chimes even with the app closed. Highest-impact missing piece; needs `google-services.json` and a deployed function (docs/FIREBASE_SETUP.md sketches it).
2. **QR/barcode mission** — at setup, scan any barcode in the house (shampoo bottle, cereal box); at alarm time you must walk to it and re-scan. Strongest anti-spoof; CameraX + ML Kit bundled barcode model.
3. **Plain-dismiss preference for solo users** who explicitly want a normal alarm.
4. **Streaks & per-member stats** on the Pact screen (data already in the feed).
5. **Multiple pacts** and per-alarm pact selection.

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

## Monetization options considered
- **Freemium**: free = 1 pact, 3 alarms, steps mission; Pro (one-off £6.99 or £1.49/mo) = unlimited alarms, QR/photo missions, stats, widgets.
- **Group plan**: one member pays, whole pact unlocked (fits the social DNA).
- **Cosmetics**: sound packs, feed reactions, app icons.
- Ads rejected: an ad on the ring screen at 6 a.m. is product sabotage.
