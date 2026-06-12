package app.wakepact.core.util

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Minimal suspend bridge for Play Services [Task]s, so we don't need the
 * kotlinx-coroutines-play-services artifact for two call sites.
 */
suspend fun <T> Task<T>.awaitTask(): T {
    if (isComplete) {
        val e = exception
        if (e != null) throw e
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
    return suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            val e = task.exception
            if (e != null) {
                cont.resumeWithException(e)
            } else if (task.isCanceled) {
                cont.cancel()
            } else {
                @Suppress("UNCHECKED_CAST")
                cont.resume(task.result as T)
            }
        }
    }
}
