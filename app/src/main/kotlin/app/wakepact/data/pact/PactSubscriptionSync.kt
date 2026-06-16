package app.wakepact.data.pact

import app.wakepact.data.identity.IdentityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps FCM topic membership in lockstep with the persisted pact id, so a single
 * source of truth (the stored `pactId`) drives buddy push for every path —
 * create, join, leave, and a plain app relaunch — without each call site
 * remembering to (un)subscribe. Idempotent: FCM subscribe/unsubscribe are safe
 * to repeat, and in solo mode [PactMessaging] is a no-op.
 */
@Singleton
class PactSubscriptionSync @Inject constructor(
    private val identityRepository: IdentityRepository,
    private val messaging: PactMessaging,
) {
    fun start(scope: CoroutineScope) {
        scope.launch {
            var current: String? = null
            identityRepository.identity
                .map { it.pactId }
                .distinctUntilChanged()
                .collect { pactId ->
                    val previous = current
                    if (previous != null && previous != pactId) messaging.unsubscribe(previous)
                    if (pactId != null) messaging.subscribe(pactId)
                    current = pactId
                }
        }
    }
}
