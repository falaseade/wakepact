# WakePact Cloud Functions — buddy push

One function, `notifyPactOnProof`. It listens on
`pacts/{pactId}/ringEvents/{eventId}` and, the moment a ring flips to
`PROOF_DONE`, sends a **data-only** FCM message to the topic `pact-{pactId}`.
Every pact member's device subscribes to that topic on join (the Android client
does this); the message wakes their phone even with the app closed, and the
client decides whether to show it — the owner's own device suppresses it.

## Prerequisites

- The Firebase project from [`../docs/FIREBASE_SETUP.md`](../docs/FIREBASE_SETUP.md)
  (Firestore + Anonymous Auth already enabled).
- The **Blaze (pay-as-you-go) plan**. Cloud Functions require it even though this
  function's traffic sits far inside the free monthly allowance — a friend group
  generates a handful of invocations a day. FCM sends are free.
- Node 20 and the Firebase CLI: `npm install -g firebase-tools`, then
  `firebase login`.

## Deploy

```bash
cd functions
npm install
cd ..
firebase use <your-project-id>     # writes .firebaserc (git-ignored)
firebase deploy --only functions
```

## Verify

Trigger a real ring (or edit a `ringEvents` doc's `state` to `PROOF_DONE` in the
console) and watch:

```bash
firebase functions:log
```

You should see `Buddy push sent` with the topic and event id, and a subscribed
buddy device should chime. If nothing arrives, check that the buddy actually
subscribed (the client subscribes on join; a token refresh re-subscribes) and
that `wakepact.firebase.senderId` is set in the app's `local.properties`.

## Why data-only, not a notification message

A notification message would be rendered by the system on every subscriber —
including the owner who just did the walking. Data-only hands the decision to
`PactPushPolicy` on the client, which drops the owner's self-notification and
keeps the copy/deep-link in one place.
