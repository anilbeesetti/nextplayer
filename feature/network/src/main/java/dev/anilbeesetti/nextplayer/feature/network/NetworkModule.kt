package dev.anilbeesetti.nextplayer.feature.network

import dev.anilbeesetti.nextplayer.core.data.DataModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module(includes = [DataModule::class])
@ComponentScan
class NetworkModule
