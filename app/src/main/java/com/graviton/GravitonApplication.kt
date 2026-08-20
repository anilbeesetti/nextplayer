package com.graviton

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import dagger.hilt.android.HiltAndroidApp
import com.graviton.core.common.di.ApplicationScope
import com.graviton.core.common.Logger
import com.graviton.core.data.repository.NetworkConnectionRepository
import com.graviton.core.data.repository.PreferencesRepository
import com.graviton.core.media.network.keys.SshKeyStore
import com.graviton.crash.CrashActivity
import com.graviton.crash.GlobalExceptionHandler
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@HiltAndroidApp
class GravitonApplication : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    @Inject
    lateinit var imageLoader: ImageLoader

    @Inject
    lateinit var networkConnectionRepository: NetworkConnectionRepository

    @Inject
    lateinit var sshKeyStore: SshKeyStore

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler(applicationContext, CrashActivity::class.java))
        applicationScope.launch {
            runCatching {
                initializeSshKeyStore(networkConnectionRepository, sshKeyStore)
            }.onFailure { error ->
                Logger.logError(TAG, "Couldn't reconcile SSH keys: ${error.message}")
            }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoader

    private companion object {
        const val TAG = "GravitonApplication"
    }
}
