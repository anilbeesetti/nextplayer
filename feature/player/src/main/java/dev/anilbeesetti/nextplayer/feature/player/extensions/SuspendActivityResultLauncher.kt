package dev.anilbeesetti.nextplayer.feature.player.extensions

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class SuspendActivityResultLauncher<I, O>(
    activity: ComponentActivity,
    contract: ActivityResultContract<I, O>,
) {
    private var activityResultCallback: ((result: O) -> Unit)? = null
    private var activityResultLauncher: ActivityResultLauncher<I> = activity.registerForActivityResult(
        contract = contract,
        callback = { result -> activityResultCallback?.invoke(result) },
    )

    val isAwaitingResult: Boolean
        get() = activityResultCallback != null

    suspend fun launch(input: I): O = suspendCancellableCoroutine { continuation ->
        activityResultCallback = {
            continuation.resume(it)
            activityResultCallback = null
        }
        activityResultLauncher.launch(input)
        continuation.invokeOnCancellation {
            activityResultCallback = null
        }
    }
}

fun <I, O> ComponentActivity.registerForSuspendActivityResult(
    contract: ActivityResultContract<I, O>,
): SuspendActivityResultLauncher<I, O> {
    return SuspendActivityResultLauncher(this, contract)
}
