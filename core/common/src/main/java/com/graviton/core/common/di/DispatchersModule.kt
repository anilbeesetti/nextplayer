package com.graviton.core.common.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.graviton.core.common.Dispatcher
import com.graviton.core.common.NextDispatchers
import com.graviton.core.common.service.system.LocalSystemService
import com.graviton.core.common.service.system.SystemService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {
    @Provides
    @Dispatcher(NextDispatchers.IO)
    fun providesIODispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Dispatcher(NextDispatchers.Default)
    fun providesDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}

@Module
@InstallIn(SingletonComponent::class)
interface SystemUtilsModule {

    @Binds
    @Singleton
    fun bindsSystemService(
        systemService: LocalSystemService,
    ): SystemService
}
