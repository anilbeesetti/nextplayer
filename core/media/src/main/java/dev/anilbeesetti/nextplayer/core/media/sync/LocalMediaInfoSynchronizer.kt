package dev.anilbeesetti.nextplayer.core.media.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import coil3.ImageLoader
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.anilbeesetti.nextplayer.core.common.Dispatcher
import dev.anilbeesetti.nextplayer.core.common.NextDispatchers
import dev.anilbeesetti.nextplayer.core.common.di.ApplicationScope
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LocalMediaInfoSynchronizer @Inject constructor(
    private val imageLoader: ImageLoader,
    @ApplicationScope private val applicationScope: CoroutineScope,
    @ApplicationContext private val context: Context,
    @Dispatcher(NextDispatchers.Default) private val dispatcher: CoroutineDispatcher,
) : MediaInfoSynchronizer {

    private val activeSyncJobs = mutableMapOf<String, Job>()
    private val mutex = Mutex()

    override fun sync(uri: Uri) {
        applicationScope.launch(dispatcher) {
            val uriString = uri.toString()

            mutex.withLock {
                activeSyncJobs[uriString]
            }?.join()

            val job = applicationScope.launch(dispatcher) {
                try {
                    performSync(uri)
                } finally {
                    mutex.withLock {
                        activeSyncJobs.remove(uriString)
                    }
                }
            }

            mutex.withLock {
                activeSyncJobs[uriString] = job
            }
        }
    }

    override suspend fun clearThumbnailsCache() {
        imageLoader.diskCache?.clear()
        imageLoader.memoryCache?.clear()
    }

    private suspend fun performSync(uri: Uri) {
        // No-op for now as database entities for streams and mediums have been removed.
    }

    companion object {
        private const val TAG = "MediaInfoSynchronizer"
    }
}
