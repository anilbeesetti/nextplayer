package dev.anilbeesetti.nextplayer

import android.app.Application
import android.app.UiModeManager
import android.content.res.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.anilbeesetti.nextplayer.core.common.di.ApplicationScope
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltAndroidApp
class NextPlayerApplication : Application() {

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()

        //Check if the device is tv
        val uiModeManager = getSystemService(UI_MODE_SERVICE) as UiModeManager?
        val isTvLayout = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

        // Preloading updated preferences to ensure that the player uses the latest preferences set by the user.
        // This resolves the issue where player use default preferences upon launching the app from a cold start.
        // See [the corresponding issue for more info](https://github.com/anilbeesetti/nextplayer/issues/392)
        applicationScope.launch {
            preferencesRepository.applicationPreferences.first()
            preferencesRepository.applicationPreferences.first().isTvLayout = isTvLayout
            preferencesRepository.playerPreferences.first()
        }

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
