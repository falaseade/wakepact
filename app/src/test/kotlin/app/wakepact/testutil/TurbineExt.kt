package app.wakepact.testutil

import app.cash.turbine.ReceiveTurbine

/**
 * Awaits emissions until one satisfies [predicate], consuming intermediates.
 * Keeps assertions stable against combine() emitting per-source updates.
 */
suspend fun <T> ReceiveTurbine<T>.awaitItemWhere(predicate: (T) -> Boolean): T {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}
