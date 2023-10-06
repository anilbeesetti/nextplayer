package dev.anilbeesetti.nextplayer.feature.player.notification

import android.content.Context
import androidx.media3.common.util.UnstableApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.anilbeesetti.nextplayer.core.common.Dispatcher
import dev.anilbeesetti.nextplayer.core.common.NextDispatchers
import kotlinx.coroutines.CoroutineDispatcher

@UnstableApi
@Module
@InstallIn(SingletonComponent::class)
object NotificationModule {

    @Provides
    fun providesNextNotification(
        @Dispatcher(NextDispatchers.Main) mainDispatcher: CoroutineDispatcher,
        @Dispatcher(NextDispatchers.IO) ioDispatcher: CoroutineDispatcher,
        @ApplicationContext context: Context
    ): NextNotificationProvider {
        return NextNotificationProvider(mainDispatcher, ioDispatcher, context)
    }
}
