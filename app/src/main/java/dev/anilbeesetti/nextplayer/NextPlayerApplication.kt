package dev.anilbeesetti.nextplayer

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import dev.anilbeesetti.nextplayer.core.data.MediaSynchronizer
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class NextPlayerApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
