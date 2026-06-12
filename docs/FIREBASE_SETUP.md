# Firebase setup (optional — enables pacts)

WakePact runs fully solo with zero configuration. To unlock pacts — friends
seeing your ring live and deactivating it for you — wire up a free Firebase
project. About 10 minutes, no billing required (Spark plan covers a friend
group's traffic by orders of magnitude).

The app discovers Firebase at runtime through three values injected from
`local.properties` into `BuildConfig` (ADR-003). No `google-services.json`,
no Google Services Gradle plugin — absence of the keys simply selects solo mode.

## 1. Create the project

1. Go to [console.firebase.google.com](https://console.firebase.google.com) → **Add project**. Name it anything (e.g. `wakepact`). Google Analytics: off (not used).
2. In the project, click the **Android** icon to add an app. Package name: `app.wakepact`. Nickname/SHA-1: leave blank. Register.
3. The console offers `google-services.json` — you can download it just to read values from, but **do not add it to the project**; the next step is all the app needs.

## 2. Wire the keys into `local.properties`

Open the file Android Studio created at the repo root (`local.properties`, already git-ignored) and add:

```properties
wakepact.firebase.projectId=<your-project-id>
wakepact.firebase.applicationId=<your-mobilesdk-app-id>
wakepact.firebase.apiKey=<your-api-key>
```

Where to find each (Console → ⚙ **Project settings** → **General**):
- **projectId** — "Project ID" near the top (e.g. `wakepact-4f2a1`).
- **applicationId** — under *Your apps*, the **App ID** (looks like `1:1234567890:android:abc123…`). In `google-services.json` it's `mobilesdk_app_id`.
- **apiKey** — under *Your apps*, **API key** (`current_key` in the json). This is a client identifier, not a secret — real protection comes from the security rules below — but keep it out of git anyway (it already is).

Rebuild. The Pact tab now shows live status instead of solo mode.

## 3. Enable Anonymous Authentication

Console → **Build → Authentication → Get started → Sign-in method → Anonymous → Enable**.

WakePact signs each install in anonymously on first pact use; that auth uid is what identifies you in `members` and on ring events. No emails, no passwords. (Caveat: anonymous identity is per-install — reinstalling makes you a new member; old feed entries keep your old name. Roadmap has account linking.)

## 4. Create the Firestore database

Console → **Build → Firestore Database → Create database** → choose a location near your pact (e.g. `eur3` for Europe) → start in **production mode** (locked) — the next step opens exactly what's needed.

## 5. Publish the security rules

Console → **Firestore Database → Rules**, replace the contents with the block below, **Publish**.

These rules match the document shapes the app writes:

```
pacts/{pactId}                       { name, inviteCode, members: [{uid, name}], memberUids: [uid] }
pacts/{pactId}/ringEvents/{eventId}  { ownerUid, ownerName, label, firedAtMs, state,
                                       proofAtMs, resolvedAtMs, deactivatedByUid, deactivatedByName }
```

```js
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {

    function signedIn() {
      return request.auth != null;
    }

    // The app mirrors members[] into a flat memberUids[] precisely so rules
    // can check membership (rules can't inspect arrays of maps).
    function isPactMember(pactId) {
      return signedIn() &&
        request.auth.uid in get(/databases/$(database)/documents/pacts/$(pactId)).data.memberUids;
    }

    match /pacts/{pactId} {
      // Any signed-in user may read/query pacts: joining works by querying
      // inviteCode before you are a member, and Firestore rules are not
      // filters — the query itself needs list permission. Exposure is the
      // pact name, code and member display names, guarded by the
      // ~887-million-combination code space. (MVP tradeoff, noted below.)
      allow read: if signedIn();

      // Creating a pact: you must be its first and only member, and the
      // two membership arrays must agree.
      allow create: if signedIn()
        && request.resource.data.memberUids == [request.auth.uid]
        && request.resource.data.members.size() == 1
        && request.resource.data.members[0].uid == request.auth.uid
        && request.resource.data.keys().hasOnly(['name', 'inviteCode', 'members', 'memberUids']);

      // Join/leave: membership arrays are the only thing that may change.
      // MVP looseness: any signed-in user can edit them (join needs exactly
      // that; the cost is a member could remove another). v1.1 hardening:
      // move membership writes behind a Cloud Function.
      allow update: if signedIn()
        && request.resource.data.diff(resource.data).affectedKeys()
             .hasOnly(['members', 'memberUids']);

      allow delete: if false;

      match /ringEvents/{eventId} {
        // Who's awake/asleep is the sensitive bit — members only.
        allow read: if isPactMember(pactId);

        // Only the owner publishes their own ring, and only into their pact.
        allow create: if isPactMember(pactId)
          && request.auth.uid == request.resource.data.ownerUid;

        allow update: if
          // Owner drives every transition of their own event…
          (isPactMember(pactId) && request.auth.uid == resource.data.ownerUid)
          ||
          // …except the one a buddy writes: PROOF_DONE → DEACTIVATED,
          // signed with the buddy's own auth uid, touching only the
          // resolution fields (the owner's proofAtMs is preserved).
          (isPactMember(pactId)
            && resource.data.state == 'PROOF_DONE'
            && request.resource.data.state == 'DEACTIVATED'
            && request.resource.data.deactivatedByUid == request.auth.uid
            && request.resource.data.diff(resource.data).affectedKeys()
                 .hasOnly(['state', 'resolvedAtMs', 'deactivatedByUid', 'deactivatedByName']));

        allow delete: if false;
      }
    }
  }
}
```

What the rules guarantee:
- Nobody outside the pact can read ring events or write anything into them.
- A ring event can only be created by the phone that's actually ringing (`ownerUid` must be the caller's auth uid).
- A buddy can flip a ring **only** from `PROOF_DONE` to `DEACTIVATED` — never silence an alarm whose owner hasn't walked their proof — and the write is signed with the buddy's real auth uid, so "deactivated by Marco" in the feed can't be forged.
- Nothing is ever deleted; history stays honest.

Known MVP tradeoffs (also in `docs/REVIEW.md` / roadmap): pact docs are readable by any signed-in user (required for invite-code join), and membership edits aren't self-only. Both move behind a Cloud Function in v1.1 if the app grows beyond friend groups.

## 6. Try it

Two devices (or device + emulator): create a pact on one (Pact tab → name it → **Create**), read the 6-character code aloud, join on the other. Set an alarm a minute out on device A, let it fire, walk the steps — device B's Pact tab shows the pending ring and the **Deactivate** button. Tap it; device A's countdown ends with "Deactivated by …".

## v1.1 sketch: push notifications (FCM)

Today buddies see pending rings when they open the app (Firestore live listeners). The natural next step is a heads-up push the moment a pact-mate's proof lands:

1. Add `firebase-messaging` (already version-managed by the BoM in the catalog).
2. A Cloud Function on `pacts/{pactId}/ringEvents/{eventId}` updates: when `state` becomes `PROOF_DONE`, send to the topic `pact-{pactId}` (members subscribe on join, unsubscribe on leave).
3. Notification deep-links to the Pact tab with the deactivate card front and centre.

Roadmap item 1 in `docs/ROADMAP.md` — a good first iteration session.
