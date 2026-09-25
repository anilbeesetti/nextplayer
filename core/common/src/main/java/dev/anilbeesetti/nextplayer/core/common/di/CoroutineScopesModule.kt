package dev.anilbeesetti.nextplayer.core.common.di

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single

@Module
class CoroutineScopesModule {
    @Single
    @Named(DiQualifiers.APPLICATION_SCOPE)
    fun providesCoroutineScope(
        @Named(DiQualifiers.DEFAULT_DISPATCHER) dispatcher: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
}
