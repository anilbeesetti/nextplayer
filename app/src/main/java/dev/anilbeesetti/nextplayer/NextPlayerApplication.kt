package dev.anilbeesetti.nextplayer

import android.app.Activity
import android.app.Application
import android.os.Bundle
import dagger.hilt.android.HiltAndroidApp
import dev.anilbeesetti.nextplayer.core.common.cache.StreamCacheStorage
import dev.anilbeesetti.nextplayer.core.common.di.ApplicationScope
import dev.anilbeesetti.nextplayer.core.common.logging.NextLogger
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.model.StreamCacheClearPolicy
import dev.anilbeesetti.nextplayer.feature.player.service.PlayerService
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltAndroidApp
class NextPlayerApplication : Application() {

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Volatile
    private var latestPlayerPreferences: PlayerPreferences = PlayerPreferences()

    override fun onCreate() {
        super.onCreate()
        NextLogger.init(this)

        // Preloading updated preferences to ensure that the player uses the latest preferences set by the user.
        // This resolves the issue where player use default preferences upon launching the app from a cold start.
        // See [the corresponding issue for more info](https://github.com/anilbeesetti/nextplayer/issues/392)
        applicationScope.launch {
            preferencesRepository.applicationPreferences.first()
            latestPlayerPreferences = preferencesRepository.playerPreferences.first()
            preferencesRepository.playerPreferences.collect { latestPlayerPreferences = it }
        }

        registerStreamCacheCleanupOnAppExit()
    }

    private fun registerStreamCacheCleanupOnAppExit() {
        val startedActivities = AtomicInteger(0)
        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

                override fun onActivityStarted(activity: Activity) {
                    startedActivities.incrementAndGet()
                }

                override fun onActivityResumed(activity: Activity) = Unit

                override fun onActivityPaused(activity: Activity) = Unit

                override fun onActivityStopped(activity: Activity) {
                    if (activity.isChangingConfigurations) return
                    val remaining = startedActivities.decrementAndGet()
                    if (remaining != 0) return

                    applicationScope.launch(Dispatchers.IO) {
                        delay(750)
                        if (startedActivities.get() != 0) return@launch
                        if (latestPlayerPreferences.streamCacheClearPolicy != StreamCacheClearPolicy.CLEAR_ON_APP_EXIT) return@launch
                        while (PlayerService.isServiceRunning) {
                            delay(500)
                            if (startedActivities.get() != 0) return@launch
                            if (latestPlayerPreferences.streamCacheClearPolicy != StreamCacheClearPolicy.CLEAR_ON_APP_EXIT) return@launch
                        }
                        StreamCacheStorage.clear(this@NextPlayerApplication)
                    }
                }

                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }
}
