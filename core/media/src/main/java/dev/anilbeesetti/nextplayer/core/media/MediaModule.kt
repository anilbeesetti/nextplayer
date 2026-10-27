package dev.anilbeesetti.nextplayer.core.media

import dev.anilbeesetti.nextplayer.core.common.di.DispatchersModule
import dev.anilbeesetti.nextplayer.core.database.DatabaseModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module(includes = [DatabaseModule::class, DispatchersModule::class])
@ComponentScan
class MediaModule
