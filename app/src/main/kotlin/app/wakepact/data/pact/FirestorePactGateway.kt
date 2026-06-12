package app.wakepact.data.pact

import app.wakepact.core.util.InviteCodes
import app.wakepact.core.util.awaitTask
import app.wakepact.data.identity.IdentityRepository
import app.wakepact.domain.model.Pact
import app.wakepact.domain.model.PactMember
import app.wakepact.domain.model.RingEvent
import app.wakepact.domain.model.RingState
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber

/**
 * Firestore-backed pact layer. Layout (see docs/FIREBASE_SETUP.md):
 *   pacts/{pactId}                       -> { name, inviteCode, members: [{uid, name}] }
 *   pacts/{pactId}/ringEvents/{eventId}  -> RingEvent fields
 *
 * Owners write every transition except PROOF_DONE -> DEACTIVATED, which a
 * buddy writes. Every write is bounded by a timeout so Firestore's offline
 * queueing can never stall the alarm flow (the queued write still syncs later).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FirestorePactGateway(
    app: FirebaseApp,
    private val identityRepository: IdentityRepository,
    private val ioDispatcher: CoroutineDispatcher,
) : PactGateway {

    private val auth = FirebaseAuth.getInstance(app)
    private val db = FirebaseFirestore.getInstance(app)

    override val isLive: Boolean = true

    override suspend fun selfUid(): String =
        auth.currentUser?.uid
            ?: runCatching { withTimeout(AUTH_TIMEOUT_MS) { auth.signInAnonymously().awaitTask().user?.uid } }
                .onFailure { if (it is CancellationException && it !is TimeoutCancellationException) throw it }
                .onFailure { Timber.e(it, "Anonymous sign-in failed; using local uid") }
                .getOrNull()
            ?: identityRepository.ensureUid()

    override fun pact(): Flow<Pact?> = pactIdFlow().flatMapLatest { pactId ->
        if (pactId == null) flowOf(null) else pactDocFlow(pactId)
    }

    override fun ringEvents(): Flow<List<RingEvent>> = pactIdFlow().flatMapLatest { pactId ->
        if (pactId == null) flowOf(emptyList()) else ringEventsFlow(pactId)
    }

    override fun ringEvent(eventId: String): Flow<RingEvent?> = pactIdFlow().flatMapLatest { pactId ->
        if (pactId == null) flowOf(null) else ringEventDocFlow(pactId, eventId)
    }

    override suspend fun createPact(pactName: String, displayName: String): GatewayResult<Pact> = runGateway {
        val uid = selfUid()
        val code = InviteCodes.random()
        val docRef = db.collection(PACTS).document()
        docRef.set(
            mapOf(
                FIELD_NAME to pactName,
                FIELD_INVITE to code,
                FIELD_MEMBERS to listOf(mapOf(FIELD_UID to uid, FIELD_NAME to displayName)),
                // Flat uid array mirrored from members so security rules can
                // check membership (rules can't inspect arrays of maps).
                FIELD_MEMBER_UIDS to listOf(uid),
            ),
        ).awaitTask()
        identityRepository.setPactId(docRef.id)
        Pact(docRef.id, pactName, code, listOf(PactMember(uid, displayName)))
    }

    override suspend fun joinPact(inviteCode: String, displayName: String): GatewayResult<Pact> = runGateway {
        val uid = selfUid()
        val code = InviteCodes.normalize(inviteCode)
        val snap = db.collection(PACTS).whereEqualTo(FIELD_INVITE, code).limit(1).get().awaitTask()
        val doc = snap.documents.firstOrNull()
            ?: throw NoSuchElementException("No pact found for invite code")
        doc.reference.update(
            mapOf(
                FIELD_MEMBERS to FieldValue.arrayUnion(mapOf(FIELD_UID to uid, FIELD_NAME to displayName)),
                FIELD_MEMBER_UIDS to FieldValue.arrayUnion(uid),
            ),
        ).awaitTask()
        identityRepository.setPactId(doc.id)
        val base = doc.toPact() ?: error("Pact document could not be parsed")
        if (base.members.any { it.uid == uid }) base
        else base.copy(members = base.members + PactMember(uid, displayName))
    }

    override suspend fun leavePact(): GatewayResult<Unit> = runGateway {
        val uid = selfUid()
        val pactId = identityRepository.current().pactId ?: return@runGateway
        val docRef = db.collection(PACTS).document(pactId)
        val snap = docRef.get().awaitTask()
        val mine = (snap.get(FIELD_MEMBERS) as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            ?.filter { it[FIELD_UID] == uid }
            .orEmpty()
        if (mine.isNotEmpty()) {
            docRef.update(
                mapOf(
                    FIELD_MEMBERS to FieldValue.arrayRemove(*mine.toTypedArray()),
                    FIELD_MEMBER_UIDS to FieldValue.arrayRemove(uid),
                ),
            ).awaitTask()
        }
        identityRepository.setPactId(null)
    }

    override suspend fun publishRingEvent(event: RingEvent): GatewayResult<Unit> = runGateway {
        val pactId = identityRepository.current().pactId ?: error("Not in a pact")
        db.collection(PACTS).document(pactId).collection(RING_EVENTS).document(event.id)
            .set(event.toMap())
            .awaitTask()
    }

    override suspend fun updateRingEvent(
        eventId: String,
        state: RingState,
        proofAtMs: Long?,
        resolvedAtMs: Long?,
        deactivatedByUid: String?,
        deactivatedByName: String?,
    ): GatewayResult<Unit> = runGateway {
        val pactId = identityRepository.current().pactId ?: error("Not in a pact")
        val updates = mutableMapOf<String, Any>(FIELD_STATE to state.name)
        proofAtMs?.let { updates[FIELD_PROOF_AT] = it }
        resolvedAtMs?.let { updates[FIELD_RESOLVED_AT] = it }
        deactivatedByUid?.let { updates[FIELD_BY_UID] = it }
        deactivatedByName?.let { updates[FIELD_BY_NAME] = it }
        db.collection(PACTS).document(pactId).collection(RING_EVENTS).document(eventId)
            .update(updates.toMap())
            .awaitTask()
    }

    // --- Flows ---

    private fun pactIdFlow(): Flow<String?> =
        identityRepository.identity.map { it.pactId }

    private fun pactDocFlow(pactId: String): Flow<Pact?> = callbackFlow {
        val registration = db.collection(PACTS).document(pactId)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Timber.e(error, "Pact listener error")
                    trySend(null)
                } else {
                    trySend(snap?.toPact())
                }
            }
        awaitClose { registration.remove() }
    }

    private fun ringEventsFlow(pactId: String): Flow<List<RingEvent>> = callbackFlow {
        val registration = db.collection(PACTS).document(pactId).collection(RING_EVENTS)
            .orderBy(FIELD_FIRED_AT, Query.Direction.DESCENDING)
            .limit(FEED_LIMIT)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Timber.e(error, "Ring events listener error")
                    trySend(emptyList())
                } else {
                    trySend(snap?.documents?.mapNotNull { it.toRingEvent() }.orEmpty())
                }
            }
        awaitClose { registration.remove() }
    }

    private fun ringEventDocFlow(pactId: String, eventId: String): Flow<RingEvent?> = callbackFlow {
        val registration = db.collection(PACTS).document(pactId).collection(RING_EVENTS).document(eventId)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Timber.e(error, "Ring event listener error")
                    trySend(null)
                } else {
                    trySend(snap?.toRingEvent())
                }
            }
        awaitClose { registration.remove() }
    }

    // --- Error boundary ---

    private suspend fun <T> runGateway(block: suspend () -> T): GatewayResult<T> =
        withContext(ioDispatcher) {
            try {
                GatewayResult.Success(withTimeout(WRITE_TIMEOUT_MS) { block() })
            } catch (e: TimeoutCancellationException) {
                Timber.w(e, "Gateway operation timed out — treating as offline (write may sync later)")
                GatewayResult.Offline
            } catch (e: CancellationException) {
                throw e
            } catch (e: FirebaseFirestoreException) {
                Timber.e(e, "Gateway operation failed: %s", e.code)
                if (e.code == FirebaseFirestoreException.Code.UNAVAILABLE) GatewayResult.Offline
                else GatewayResult.Failed(e.code.name)
            } catch (e: FirebaseNetworkException) {
                Timber.e(e, "Gateway offline")
                GatewayResult.Offline
            } catch (e: Exception) {
                Timber.e(e, "Gateway operation failed")
                GatewayResult.Failed(e.message ?: "unknown")
            }
        }

    // --- Mapping ---

    private fun DocumentSnapshot.toPact(): Pact? {
        if (!exists()) return null
        val name = getString(FIELD_NAME) ?: return null
        val invite = getString(FIELD_INVITE) ?: return null
        val members = (get(FIELD_MEMBERS) as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            ?.mapNotNull { m ->
                val uid = m[FIELD_UID] as? String ?: return@mapNotNull null
                val memberName = m[FIELD_NAME] as? String ?: return@mapNotNull null
                PactMember(uid, memberName)
            }
            .orEmpty()
        return Pact(id, name, invite, members)
    }

    private fun DocumentSnapshot.toRingEvent(): RingEvent? {
        if (!exists()) return null
        val ownerUid = getString(FIELD_OWNER_UID) ?: return null
        val firedAt = getLong(FIELD_FIRED_AT) ?: return null
        val stateName = getString(FIELD_STATE) ?: return null
        return RingEvent(
            id = id,
            ownerUid = ownerUid,
            ownerName = getString(FIELD_OWNER_NAME).orEmpty(),
            label = getString(FIELD_LABEL).orEmpty(),
            firedAtMs = firedAt,
            state = RingState.entries.firstOrNull { it.name == stateName } ?: RingState.MISSED,
            proofAtMs = getLong(FIELD_PROOF_AT),
            resolvedAtMs = getLong(FIELD_RESOLVED_AT),
            deactivatedByUid = getString(FIELD_BY_UID),
            deactivatedByName = getString(FIELD_BY_NAME),
        )
    }

    private fun RingEvent.toMap(): Map<String, Any?> = mapOf(
        FIELD_OWNER_UID to ownerUid,
        FIELD_OWNER_NAME to ownerName,
        FIELD_LABEL to label,
        FIELD_FIRED_AT to firedAtMs,
        FIELD_STATE to state.name,
        FIELD_PROOF_AT to proofAtMs,
        FIELD_RESOLVED_AT to resolvedAtMs,
        FIELD_BY_UID to deactivatedByUid,
        FIELD_BY_NAME to deactivatedByName,
    )

    private companion object {
        const val PACTS = "pacts"
        const val RING_EVENTS = "ringEvents"
        const val FIELD_NAME = "name"
        const val FIELD_INVITE = "inviteCode"
        const val FIELD_MEMBERS = "members"
        const val FIELD_MEMBER_UIDS = "memberUids"
        const val FIELD_UID = "uid"
        const val FIELD_OWNER_UID = "ownerUid"
        const val FIELD_OWNER_NAME = "ownerName"
        const val FIELD_LABEL = "label"
        const val FIELD_FIRED_AT = "firedAtMs"
        const val FIELD_STATE = "state"
        const val FIELD_PROOF_AT = "proofAtMs"
        const val FIELD_RESOLVED_AT = "resolvedAtMs"
        const val FIELD_BY_UID = "deactivatedByUid"
        const val FIELD_BY_NAME = "deactivatedByName"
        const val FEED_LIMIT = 50L
        const val WRITE_TIMEOUT_MS = 8_000L
        const val AUTH_TIMEOUT_MS = 5_000L
    }
}
