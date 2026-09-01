package com.graviton.core.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import com.graviton.core.data.repository.LocalMediaRepository
import com.graviton.core.data.repository.LocalMusicRepository
import com.graviton.core.data.repository.LocalNetworkConnectionRepository
import com.graviton.core.data.repository.LocalPlaylistRepository
import com.graviton.core.data.repository.LocalPreferencesRepository
import com.graviton.core.data.repository.LocalSearchHistoryRepository
import com.graviton.core.data.repository.LocalVaultPinRepository
import com.graviton.core.data.repository.LocalVaultRepository
import com.graviton.core.data.repository.MediaRepository
import com.graviton.core.data.repository.MusicRepository
import com.graviton.core.data.repository.NetworkConnectionRepository
import com.graviton.core.data.repository.PlaylistRepository
import com.graviton.core.data.repository.PreferencesRepository
import com.graviton.core.data.repository.SearchHistoryRepository
import com.graviton.core.data.repository.VaultPinRepository
import com.graviton.core.data.repository.VaultRepository
import com.graviton.core.data.stream.AppYtDlpBinaryLocator
import com.graviton.core.data.stream.ProcessRunner
import com.graviton.core.data.stream.StreamExtractor
import com.graviton.core.data.stream.SystemProcessRunner
import com.graviton.core.data.stream.YtDlpBinaryLocator
import com.graviton.core.data.stream.YtDlpStreamExtractor
import javax.inject.Singleton
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface DataModule {

    @Binds
    @Singleton
    fun bindsPlaylistRepository(
        playlistRepository: LocalPlaylistRepository,
    ): PlaylistRepository

    @Binds
    fun bindsMediaRepository(
        videoRepository: LocalMediaRepository,
    ): MediaRepository

    @Binds
    @Singleton
    fun bindsMusicRepository(
        musicRepository: LocalMusicRepository,
    ): MusicRepository

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

    @Binds
    @Singleton
    fun bindsProcessRunner(
        runner: SystemProcessRunner,
    ): ProcessRunner

    @Binds
    @Singleton
    fun bindsYtDlpBinaryLocator(
        locator: AppYtDlpBinaryLocator,
    ): YtDlpBinaryLocator

    @Binds
    @Singleton
    fun bindsStreamExtractor(
        extractor: YtDlpStreamExtractor,
    ): StreamExtractor
}
