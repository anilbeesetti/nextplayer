package dev.anilbeesetti.nextplayer.core.common.service

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class SuspendActivityResultLauncher<I, O>(
    private val activity: ComponentActivity,
    contract: ActivityResultContract<I, O>,
) {
    private var continuation: CancellableContinuation<O>? = null

    // A canceled caller still owns the platform request until its result arrives.
    private var inFlight = false
    private val activityResultLauncher: ActivityResultLauncher<I> =
        activity.registerForActivityResult(
            contract = contract,
            callback = { result ->
                val pending = continuation
                continuation = null
                inFlight = false
                pending?.resume(value = result)
            },
        )

    init {
        activity.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    continuation?.cancel()
                    continuation = null
                    inFlight = false
                }
            },
        )
    }

    val isAwaitingResult: Boolean
        get() = continuation?.isActive == true

    suspend fun launch(input: I): O = withContext(Dispatchers.Main.immediate) {
        if (activity.lifecycle.currentState == Lifecycle.State.DESTROYED || inFlight) {
            throw CancellationException("Activity result launcher is unavailable")
        }
        suspendCancellableCoroutine { pending ->
            inFlight = true
            continuation = pending
            pending.invokeOnCancellation {
                activity.runOnUiThread {
                    if (continuation === pending) continuation = null
                }
            }
            try {
                activityResultLauncher.launch(input = input)
            } catch (error: Exception) {
                inFlight = false
                continuation = null
                throw error
            }
        }
    }
}

fun <I, O> ComponentActivity.registerForSuspendActivityResult(
    contract: ActivityResultContract<I, O>,
): SuspendActivityResultLauncher<I, O> = SuspendActivityResultLauncher(
    activity = this,
    contract = contract,
)
