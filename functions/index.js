"use strict";

/**
 * WakePact buddy push.
 *
 * Fires on the RINGING -> PROOF_DONE edge of a ring event and sends a data-only
 * FCM message to the pact topic (`pact-{pactId}`). The Android client decides
 * presentation and suppresses the owner's own ring (see PactPushPolicy), so the
 * function stays dumb: it only carries the facts.
 */

const { onDocumentUpdated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getMessaging } = require("firebase-admin/messaging");
const logger = require("firebase-functions/logger");

initializeApp();

exports.notifyPactOnProof = onDocumentUpdated(
  "pacts/{pactId}/ringEvents/{eventId}",
  async (event) => {
    const before = event.data?.before?.data();
    const after = event.data?.after?.data();
    if (!after) return;

    // Only the moment proof lands — not re-writes, not other transitions.
    const justProved = after.state === "PROOF_DONE" && (!before || before.state !== "PROOF_DONE");
    if (!justProved) return;

    const { pactId, eventId } = event.params;
    const topic = `pact-${pactId}`;

    const message = {
      topic,
      data: {
        pactId: String(pactId),
        eventId: String(eventId),
        ownerUid: String(after.ownerUid || ""),
        ownerName: String(after.ownerName || ""),
        label: String(after.label || ""),
        state: "PROOF_DONE",
      },
      android: { priority: "high" },
    };

    try {
      const id = await getMessaging().send(message);
      logger.info("Buddy push sent", { topic, eventId, id });
    } catch (err) {
      logger.error("Buddy push failed", { topic, eventId, err });
      throw err; // let the platform retry
    }
  }
);
