# ADR-003: Optional Firebase (programmatic init) behind PactGateway
Date: 2026-06-12   Status: Accepted

## Context
The social loop needs multi-device real-time state. The prime directive demands the project compile and run first try, with no user-supplied keys. The classic `google-services.json` + Gradle plugin approach *fails the build* when the file is absent — unacceptable.

## Decision
- Define `PactGateway` (Kotlin interface): publish/update ring events, observe pending events & updates, create/join/leave pact, feed stream.
- `FirestorePactGateway` implements it using Firebase **initialised programmatically** (`FirebaseApp.initializeApp(context, FirebaseOptions)`) with Auth (anonymous) + Firestore snapshot listeners. **No google-services plugin, no JSON file.**
- Config values (`projectId`, `applicationId`, `apiKey`) come from `local.properties` → injected into `BuildConfig` as nullable strings; all absent → Hilt binds `LocalPactGateway` (solo mode: no pact UI actions, mission completes ⇒ self-deactivate, history kept in Room).
- `docs/FIREBASE_SETUP.md` gives the 10-minute setup including Firestore security rules.

## Alternatives considered
- google-services plugin — rejected: build fails without the secret file; CI would need secrets on day one.
- Self-hosted backend (Ktor + Postgres) — rejected: an order of magnitude more scope, hosting cost, and ops burden for a friend-group app.
- P2P/local-network — rejected: buddies are asleep in other houses; NAT traversal is a product killer.
- Firebase Realtime Database — viable, but Firestore's per-document listeners map exactly onto `ringEvents/{id}` and rules are easier to write safely.

## Rex's objection & resolution
Rex: "You're shipping a social app whose social layer is off by default — most users will never flip it on. And you're hauling megabytes of Firestore SDK that solo users never use." Resolution: solo mode is a first-class product mode in the brief (the user's own spec: 'if no other user, defaults to normal alarm'); README puts the 10-minute Firebase setup front and centre. The SDK weight (~3–4 MB) is accepted as the cost of a real social MVP; a `noSync` build flavour is a roadmap option if size complaints appear.

## Consequences
Build is green with zero setup; social mode is a config away; the gateway interface gives Phase 7 a clean seam for fakes. Push-to-closed-app notifications stay out of MVP (need FCM + Cloud Function — roadmap item 1).
