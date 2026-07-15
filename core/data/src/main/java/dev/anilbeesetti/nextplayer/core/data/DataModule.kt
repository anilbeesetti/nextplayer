package dev.anilbeesetti.nextplayer.core.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.anilbeesetti.nextplayer.core.data.playlist.LocalPlaylistSourceReader
import dev.anilbeesetti.nextplayer.core.data.playlist.PlaylistSourceReader
import dev.anilbeesetti.nextplayer.core.data.repository.AndroidPersistedPlaylistFileGrantAccess
import dev.anilbeesetti.nextplayer.core.data.repository.LocalMediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.LocalNetworkConnectionRepository
import dev.anilbeesetti.nextplayer.core.data.repository.LocalPreferencesRepository
import dev.anilbeesetti.nextplayer.core.data.repository.LocalPlaylistFileGrantRepository
import dev.anilbeesetti.nextplayer.core.data.repository.LocalPlaylistRepository
import dev.anilbeesetti.nextplayer.core.data.repository.LocalSearchHistoryRepository
import dev.anilbeesetti.nextplayer.core.data.repository.LocalVaultPinRepository
import dev.anilbeesetti.nextplayer.core.data.repository.LocalVaultRepository
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.NetworkConnectionRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PersistedPlaylistFileGrantAccess
import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistFileGrantRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PlaylistRepository
import dev.anilbeesetti.nextplayer.core.data.repository.SearchHistoryRepository
import dev.anilbeesetti.nextplayer.core.data.repository.VaultPinRepository
import dev.anilbeesetti.nextplayer.core.data.repository.VaultRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface DataModule {

    @Binds
    @Singleton
    fun bindsPlaylistFileGrantRepository(
        repository: LocalPlaylistFileGrantRepository,
    ): PlaylistFileGrantRepository

    @Binds
    fun bindsPersistedPlaylistFileGrantAccess(
        access: AndroidPersistedPlaylistFileGrantAccess,
    ): PersistedPlaylistFileGrantAccess

    @Binds
    @Singleton
    fun bindsPlaylistRepository(
        repository: LocalPlaylistRepository,
    ): PlaylistRepository

    @Binds
    fun bindsPlaylistSourceReader(
        sourceReader: LocalPlaylistSourceReader,
    ): PlaylistSourceReader

    @Binds
    fun bindsMediaRepository(
        videoRepository: LocalMediaRepository,
    ): MediaRepository

    @Binds
    @Singleton
    fun bindsPreferencesRepository(
        preferencesRepository: LocalPreferencesRepository,
    ): PreferencesRepository

    @Binds
    @Singleton
    fun bindsSearchHistoryRepository(
        searchHistoryRepository: LocalSearchHistoryRepository,
    ): SearchHistoryRepository

    @Binds
    @Singleton
    fun bindsVaultRepository(
        vaultRepository: LocalVaultRepository,
    ): VaultRepository

    @Binds
    @Singleton
    fun bindsVaultPinRepository(
        vaultPinRepository: LocalVaultPinRepository,
    ): VaultPinRepository

    @Binds
    @Singleton
    fun bindsNetworkConnectionRepository(
        networkConnectionRepository: LocalNetworkConnectionRepository,
    ): NetworkConnectionRepository
}
