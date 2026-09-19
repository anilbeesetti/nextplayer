package dev.anilbeesetti.nextplayer.feature.playlist

import dev.anilbeesetti.nextplayer.core.domain.DomainModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module(includes = [DomainModule::class])
@ComponentScan
class PlaylistModule
