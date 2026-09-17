package dev.anilbeesetti.nextplayer.core.common.di

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named

@Module(includes = [CoroutineScopesModule::class])
@ComponentScan("dev.anilbeesetti.nextplayer.core.common")
class DispatchersModule {
    @Factory
    @Named(DiQualifiers.IO_DISPATCHER)
    fun providesIODispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Factory
    @Named(DiQualifiers.DEFAULT_DISPATCHER)
    fun providesDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
