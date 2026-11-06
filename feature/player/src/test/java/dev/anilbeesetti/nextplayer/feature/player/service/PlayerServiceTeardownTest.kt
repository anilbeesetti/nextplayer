package dev.anilbeesetti.nextplayer.feature.player.service

import android.net.Uri
import dev.anilbeesetti.nextplayer.core.common.di.DiQualifiers
import dev.anilbeesetti.nextplayer.core.data.repository.MediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.PreferencesRepository
import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakeMediaRepository
import dev.anilbeesetti.nextplayer.core.data.repository.fake.FakePreferencesRepository
import dev.anilbeesetti.nextplayer.core.media.network.NetworkClient
import dev.anilbeesetti.nextplayer.core.media.network.NetworkClientFactory
import dev.anilbeesetti.nextplayer.core.media.network.NetworkConnectionResolver
import dev.anilbeesetti.nextplayer.core.media.network.datasource.NetworkSessions
import dev.anilbeesetti.nextplayer.core.media.network.datasource.NextDataSourceFactory
import dev.anilbeesetti.nextplayer.core.model.NetworkFile
import java.io.InputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PlayerServiceTeardownTest {
    @Test(timeout = 10000)
    fun releaseWaitsOffMainAndPrecedesNextConnection() = runBlocking {
        val connecting = CompletableDeferred<Unit>()
        val continueConnecting = CompletableDeferred<Unit>()
        var created = 0
        val sessions = NetworkSessions(
            resolver = NetworkConnectionResolver { null },
            clientFactory = NetworkClientFactory {
                val first = created++ == 0
                object : NetworkClient {
                    override val rootPath = "/"
                    override suspend fun connect(): Result<Unit> {
                        if (first) {
                            connecting.complete(Unit)
                            continueConnecting.await()
                        }
                        return Result.success(Unit)
                    }
                    override suspend fun disconnect() = Unit
                    override fun isConnected() = true
                    override suspend fun listFiles(path: String): Result<List<NetworkFile>> =
                        error("Not used")
                    override suspend fun fileSize(path: String): Long = error("Not used")
                    override suspend fun openStream(path: String, offset: Long): InputStream =
                        error("Not used")
                }
            },
        )
        val applicationDispatcher = StandardTestDispatcher()
        val applicationScope = CoroutineScope(SupervisorJob() + applicationDispatcher)
        startKoin {
            modules(
                module {
                    single<CoroutineScope>(named(DiQualifiers.APPLICATION_SCOPE)) {
                        applicationScope
                    }
                    single<PreferencesRepository> { FakePreferencesRepository() }
                    single<MediaRepository> { FakeMediaRepository() }
                    single {
                        NextDataSourceFactory(
                            RuntimeEnvironment.getApplication(),
                            sessions,
                        )
                    }
                },
            )
        }
        try {
            val service = Robolectric.buildService(PlayerService::class.java).create()
            val uri = Uri.parse("ftp://example.com/video.mp4")
            val oldTarget = async(start = CoroutineStart.UNDISPATCHED) { sessions.target(uri) }
            connecting.await()
            service.destroy()
            val newTarget = async(start = CoroutineStart.UNDISPATCHED) { sessions.target(uri) }
            continueConnecting.complete(Unit)
            val oldClient = withTimeout(5000) { oldTarget.await().client }
            applicationDispatcher.scheduler.runCurrent()
            val newClient = withTimeout(5000) { newTarget.await().client }
            assertNotSame(
                oldClient,
                newClient,
            )
            assertSame(
                newClient,
                sessions.target(uri).client,
            )
        } finally {
            continueConnecting.complete(Unit)
            applicationScope.cancel()
            stopKoin()
        }
    }
}
