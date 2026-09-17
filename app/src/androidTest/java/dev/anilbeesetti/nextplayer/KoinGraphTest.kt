package dev.anilbeesetti.nextplayer

import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.media.network.datasource.NextDataSourceFactory
import dev.anilbeesetti.nextplayer.core.media.services.MediaOperationsService
import dev.anilbeesetti.nextplayer.core.media.services.MediaService
import dev.anilbeesetti.nextplayer.core.media.sync.MediaSynchronizer
import dev.anilbeesetti.nextplayer.core.model.ApplicationPreferences
import dev.anilbeesetti.nextplayer.core.model.PlayerPreferences
import dev.anilbeesetti.nextplayer.core.model.SearchHistory
import dev.anilbeesetti.nextplayer.feature.more.screens.history.HistoryViewModel
import dev.anilbeesetti.nextplayer.feature.more.screens.more.MoreViewModel
import dev.anilbeesetti.nextplayer.feature.more.screens.trash.TrashViewModel
import dev.anilbeesetti.nextplayer.feature.network.screens.addconnection.AddConnectionViewModel
import dev.anilbeesetti.nextplayer.feature.network.screens.browse.NetworkBrowseViewModel
import dev.anilbeesetti.nextplayer.feature.network.screens.list.NetworkViewModel
import dev.anilbeesetti.nextplayer.feature.player.PlayerViewModel
import dev.anilbeesetti.nextplayer.feature.playlist.screens.detail.PlaylistDetailViewModel
import dev.anilbeesetti.nextplayer.feature.playlist.screens.list.PlaylistListViewModel
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.mediapicker.MediaPickerViewModel
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.search.SearchViewModel
import dev.anilbeesetti.nextplayer.feature.videopicker.screens.vault.VaultViewModel
import dev.anilbeesetti.nextplayer.settings.screens.about.AboutPreferencesViewModel
import dev.anilbeesetti.nextplayer.settings.screens.about.LibrariesViewModel
import dev.anilbeesetti.nextplayer.settings.screens.appearance.AppearancePreferencesViewModel
import dev.anilbeesetti.nextplayer.settings.screens.audio.AudioPreferencesViewModel
import dev.anilbeesetti.nextplayer.settings.screens.general.GeneralPreferencesViewModel
import dev.anilbeesetti.nextplayer.settings.screens.gesture.GesturePreferencesViewModel
import dev.anilbeesetti.nextplayer.settings.screens.medialibrary.FolderPreferencesViewModel
import dev.anilbeesetti.nextplayer.settings.screens.medialibrary.MediaLibraryPreferencesViewModel
import dev.anilbeesetti.nextplayer.settings.screens.player.PlayerPreferencesViewModel
import dev.anilbeesetti.nextplayer.settings.screens.subtitle.SubtitlePreferencesViewModel
import dev.anilbeesetti.nextplayer.settings.screens.thumbnail.ThumbnailPreferencesViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.context.GlobalContext
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.viewmodel.resolveViewModel

@RunWith(AndroidJUnit4::class)
class KoinGraphTest {
    private val koin get() = GlobalContext.get()

    @Test
    fun sharedServicesAndAllThreePreferenceStoresResolve() {
        runBlocking {
            assertSame(koin.get<PreferencesRepository>(), koin.get<PreferencesRepository>())
            assertSame(koin.get<MediaOperationsService>(), koin.get<MediaOperationsService>())
            assertSame(koin.get<MediaService>(), koin.get<MediaService>())
            assertSame(koin.get<MediaSynchronizer>(), koin.get<MediaSynchronizer>())
            assertSame(koin.get<NextDataSourceFactory>(), koin.get<NextDataSourceFactory>())
            withTimeout(10_000) {
                // Read the typed values to catch collisions between erased DataStore keys.
                koin.get<DataStore<ApplicationPreferences>>(named("appPreferences")).data.first().mediaViewMode
                koin.get<DataStore<PlayerPreferences>>(named("playerPreferences")).data.first().resume
                koin.get<DataStore<SearchHistory>>(named("searchHistory")).data.first().queries.size
            }
        }
    }

    @Test
    fun viewModelsResolveWithRuntimeParametersAndRespectTheirStore() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val store = ViewModelStore()
            val secondStore = ViewModelStore()
            try {
                viewModel<MainViewModel>(store)
                viewModel<PlayerViewModel>(store)
                viewModel<HistoryViewModel>(store, HistoryViewModel.Output({}, {}))
                viewModel<MoreViewModel>(store, MoreViewModel.Output({}, {}, {}, {}, {}))
                viewModel<TrashViewModel>(store, TrashViewModel.Output({}, {}))
                viewModel<NetworkViewModel>(store, NetworkViewModel.Output({}, {}, {}, {}, {}))
                viewModel<AddConnectionViewModel>(store, AddConnectionViewModel.Input(null), AddConnectionViewModel.Output({}))
                viewModel<NetworkBrowseViewModel>(store, NetworkBrowseViewModel.Input(-1, "/"), NetworkBrowseViewModel.Output({}, {}, { _, _ -> }))
                viewModel<PlaylistListViewModel>(store, PlaylistListViewModel.Output({}, {}))
                viewModel<PlaylistDetailViewModel>(store, PlaylistDetailViewModel.Input(-1), PlaylistDetailViewModel.Output({}, { _, _ -> }))
                viewModel<SearchViewModel>(store, SearchViewModel.Output({}, {}, {}))
                viewModel<VaultViewModel>(store, VaultViewModel.Output({}, {}, {}))
                viewModel<AboutPreferencesViewModel>(store, AboutPreferencesViewModel.Output({}, {}))
                viewModel<LibrariesViewModel>(store, LibrariesViewModel.Output({}))
                viewModel<AppearancePreferencesViewModel>(store, AppearancePreferencesViewModel.Output({}))
                viewModel<AudioPreferencesViewModel>(store, AudioPreferencesViewModel.Output({}))
                viewModel<GeneralPreferencesViewModel>(store, GeneralPreferencesViewModel.Output({}))
                viewModel<GesturePreferencesViewModel>(store, GesturePreferencesViewModel.Output({}))
                viewModel<FolderPreferencesViewModel>(store, FolderPreferencesViewModel.Output({}))
                viewModel<MediaLibraryPreferencesViewModel>(store, MediaLibraryPreferencesViewModel.Output({}, {}, {}))
                viewModel<PlayerPreferencesViewModel>(store, PlayerPreferencesViewModel.Output({}))
                viewModel<SubtitlePreferencesViewModel>(store, SubtitlePreferencesViewModel.Output({}))
                viewModel<ThumbnailPreferencesViewModel>(store, ThumbnailPreferencesViewModel.Output({}))

                val output = MediaPickerViewModel.Output({}, {}, {}, {}, {}, {}, {})
                val first = viewModel<MediaPickerViewModel>(store, MediaPickerViewModel.Input("/first"), output)
                assertEquals("/first", first.folderPath)
                assertSame(first, viewModel<MediaPickerViewModel>(store, MediaPickerViewModel.Input("/ignored"), output))
                val second = viewModel<MediaPickerViewModel>(secondStore, MediaPickerViewModel.Input("/second"), output)
                assertNotSame(first, second)
                assertEquals("/second", second.folderPath)
            } finally {
                store.clear()
                secondStore.clear()
            }
        }
    }

    @OptIn(KoinInternalApi::class)
    private inline fun <reified T : ViewModel> viewModel(store: ViewModelStore, vararg parameters: Any): T = resolveViewModel(
        vmClass = T::class,
        viewModelStore = store,
        extras = CreationExtras.Empty,
        scope = koin.scopeRegistry.rootScope,
        parameters = { parametersOf(*parameters) },
    )
}
