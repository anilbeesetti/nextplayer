package dev.anilbeesetti.nextplayer.feature.network.screens.addconnection

import android.net.Uri
import android.net.TestUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import dev.anilbeesetti.nextplayer.core.data.repository.NetworkConnectionRepository
import dev.anilbeesetti.nextplayer.core.media.network.NetworkClient
import dev.anilbeesetti.nextplayer.core.media.network.NetworkClientFactory
import dev.anilbeesetti.nextplayer.core.media.network.keys.SshKeyStore
import dev.anilbeesetti.nextplayer.core.media.network.keys.StagedSshKey
import dev.anilbeesetti.nextplayer.core.media.network.sftp.HostKeyConfirmationRequired
import dev.anilbeesetti.nextplayer.core.model.NetworkAuthentication
import dev.anilbeesetti.nextplayer.core.model.NetworkConnection
import dev.anilbeesetti.nextplayer.core.model.NetworkFile
import dev.anilbeesetti.nextplayer.core.model.NetworkProtocol
import dev.anilbeesetti.nextplayer.feature.network.MainDispatcherRule
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddConnectionViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `staging a replacement deletes the previous staged key`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val keyStore = FakeSshKeyStore(
                stagedKeys = ArrayDeque(
                    listOf(
                        StagedSshKey("first.key", "first.pem"),
                        StagedSshKey("second.key", "second.pem"),
                    ),
                ),
            )
            val viewModel = viewModel(keyStore = keyStore)

            viewModel.stagePrivateKey(TestUri)
            advanceUntilIdle()
            viewModel.stagePrivateKey(TestUri)
            advanceUntilIdle()

            assertEquals(SelectedPrivateKey("second.key", "second.pem"), viewModel.selectedPrivateKey.value)
            assertEquals(listOf("first.key"), keyStore.deleted)
        }

    @Test
    fun `replacement requested during test cannot replace the tested key snapshot`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val connectStarted = CompletableDeferred<Unit>()
            val allowConnect = CompletableDeferred<Unit>()
            val keyStore = FakeSshKeyStore(
                stagedKeys = ArrayDeque(
                    listOf(
                        StagedSshKey("tested-a.key", "a.pem"),
                        StagedSshKey("replacement-b.key", "b.pem"),
                    ),
                ),
                committedName = "committed-a.key",
            )
            val repository = FakeRepository()
            val viewModel = viewModel(
                repository = repository,
                clients = ArrayDeque(
                    listOf(
                        FakeNetworkClient(Result.success(Unit)) {
                            connectStarted.complete(Unit)
                            allowConnect.await()
                        },
                    ),
                ),
                keyStore = keyStore,
            )
            viewModel.stagePrivateKey(TestUri)
            advanceUntilIdle()

            viewModel.testAndSave(keyDraft(privateKeyFileName = "tested-a.key"))
            connectStarted.await()
            viewModel.stagePrivateKey(TestUri)
            runCurrent()
            allowConnect.complete(Unit)
            advanceUntilIdle()

            assertEquals(1, keyStore.stageCalls)
            assertEquals(listOf("tested-a.key"), keyStore.committed)
            assertEquals("committed-a.key", repository.upserted.single().privateKeyFileName)
            assertFalse("tested-a.key" in keyStore.deleteAttempts)
        }

    @Test
    fun `removal requested during test cannot delete the tested key`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val connectStarted = CompletableDeferred<Unit>()
            val allowConnect = CompletableDeferred<Unit>()
            val keyStore = FakeSshKeyStore(
                stagedKeys = ArrayDeque(listOf(StagedSshKey("tested-a.key", "a.pem"))),
                committedName = "committed-a.key",
            )
            val repository = FakeRepository()
            val viewModel = viewModel(
                repository = repository,
                clients = ArrayDeque(
                    listOf(
                        FakeNetworkClient(Result.success(Unit)) {
                            connectStarted.complete(Unit)
                            allowConnect.await()
                        },
                    ),
                ),
                keyStore = keyStore,
            )
            viewModel.stagePrivateKey(TestUri)
            advanceUntilIdle()

            viewModel.testAndSave(keyDraft(privateKeyFileName = "tested-a.key"))
            connectStarted.await()
            viewModel.removeSelectedPrivateKey()
            runCurrent()
            allowConnect.complete(Unit)
            advanceUntilIdle()

            assertEquals(listOf("tested-a.key"), keyStore.committed)
            assertEquals("committed-a.key", repository.upserted.single().privateKeyFileName)
            assertFalse("tested-a.key" in keyStore.deleteAttempts)
        }

    @Test
    fun `staging failure requested during test cannot replace Testing state`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val connectStarted = CompletableDeferred<Unit>()
            val allowConnect = CompletableDeferred<Unit>()
            val keyStore = FakeSshKeyStore(
                stagedKeys = ArrayDeque(listOf(StagedSshKey("tested-a.key", "a.pem"))),
                failStageAfterCalls = 1,
            )
            val viewModel = viewModel(
                clients = ArrayDeque(
                    listOf(
                        FakeNetworkClient(Result.success(Unit)) {
                            connectStarted.complete(Unit)
                            allowConnect.await()
                        },
                    ),
                ),
                keyStore = keyStore,
            )
            viewModel.stagePrivateKey(TestUri)
            advanceUntilIdle()

            viewModel.testAndSave(keyDraft(privateKeyFileName = "tested-a.key"))
            connectStarted.await()
            viewModel.stagePrivateKey(TestUri)
            runCurrent()

            assertEquals(SaveState.Testing, viewModel.saveState.value)
            assertEquals(1, keyStore.stageCalls)
            allowConnect.complete(Unit)
            advanceUntilIdle()
        }

    @Test
    fun `cancel during connection test deletes staged key only after save is invalidated`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val connectStarted = CompletableDeferred<Unit>()
            val allowConnect = CompletableDeferred<Unit>()
            val repository = FakeRepository()
            val keyStore = FakeSshKeyStore(
                stagedKeys = ArrayDeque(listOf(StagedSshKey("tested-a.key", "a.pem"))),
            )
            val viewModel = viewModel(
                repository = repository,
                clients = ArrayDeque(
                    listOf(
                        FakeNetworkClient(Result.success(Unit)) {
                            connectStarted.complete(Unit)
                            allowConnect.await()
                        },
                    ),
                ),
                keyStore = keyStore,
            )
            viewModel.stagePrivateKey(TestUri)
            advanceUntilIdle()

            viewModel.testAndSave(keyDraft(privateKeyFileName = "tested-a.key"))
            connectStarted.await()
            viewModel.cancel()
            allowConnect.complete(Unit)
            advanceUntilIdle()

            assertTrue(repository.upserted.isEmpty())
            assertTrue(keyStore.committed.isEmpty())
            assertEquals(listOf("tested-a.key"), keyStore.deleted)
        }

    @Test
    fun `connection screen never stores password fields with rememberSaveable`() {
        val source = File(
            "src/main/java/dev/anilbeesetti/nextplayer/feature/network/screens/addconnection/AddConnectionScreen.kt",
        ).readText()

        assertFalse(source.contains("var password by rememberSaveable"))
        assertFalse(source.contains("var privateKeyPassphrase by rememberSaveable"))
        assertTrue(source.contains("var password by remember"))
        assertTrue(source.contains("var privateKeyPassphrase by remember"))
    }

    @Test
    fun `SFTP fingerprint is cleared only when its endpoint value changes`() {
        assertEquals(
            "",
            fingerprintAfterEndpointEdit(NetworkProtocol.SFTP, "old-host", "new-host", "SHA256:trusted"),
        )
        assertEquals(
            "SHA256:trusted",
            fingerprintAfterEndpointEdit(NetworkProtocol.SFTP, "old-host", "old-host", "SHA256:trusted"),
        )
        assertEquals(
            "SHA256:trusted",
            fingerprintAfterEndpointEdit(NetworkProtocol.SMB, "old-host", "new-host", "SHA256:trusted"),
        )
    }

    @Test
    fun `failed removal retains the staged key for retry`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val keyStore = FakeSshKeyStore(
                stagedKeys = ArrayDeque(listOf(StagedSshKey("staged.key", "id_ed25519"))),
                deleteFailures = mutableMapOf("staged.key" to 1),
            )
            val viewModel = viewModel(keyStore = keyStore)
            viewModel.stagePrivateKey(TestUri)
            advanceUntilIdle()

            viewModel.removeSelectedPrivateKey()
            advanceUntilIdle()

            assertEquals(SelectedPrivateKey("staged.key", "id_ed25519"), viewModel.selectedPrivateKey.value)
            assertTrue(viewModel.saveState.value is SaveState.Error)
            assertTrue(keyStore.deleted.isEmpty())

            viewModel.cancel()
            advanceUntilIdle()
            assertEquals(listOf("staged.key"), keyStore.deleted)
        }

    @Test
    fun `stale draft cannot reuse a staged filename after removal completes`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val keyStore = FakeSshKeyStore(
                stagedKeys = ArrayDeque(listOf(StagedSshKey("removed-a.key", "a.pem"))),
            )
            val repository = FakeRepository()
            val factory = FakeNetworkClientFactory(
                ArrayDeque(
                    listOf(
                        FakeNetworkClient(Result.failure(FileNotFoundException("Private key is missing"))),
                    ),
                ),
            )
            val viewModel = viewModel(repository, factory, keyStore)
            viewModel.stagePrivateKey(TestUri)
            advanceUntilIdle()
            viewModel.removeSelectedPrivateKey()
            advanceUntilIdle()

            viewModel.testAndSave(keyDraft(privateKeyFileName = "removed-a.key"))
            advanceUntilIdle()

            assertEquals("", factory.created.single().privateKeyFileName)
            assertTrue(repository.upserted.isEmpty())
            assertEquals(listOf("removed-a.key"), keyStore.deleted)
        }

    @Test
    fun `edit without replacement uses only existing committed SSH key`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val repository = FakeRepository(
                existing = keyDraft(id = 9, privateKeyFileName = "committed-existing.key"),
            )
            val factory = FakeNetworkClientFactory(
                ArrayDeque(listOf(FakeNetworkClient(Result.success(Unit)))),
            )
            val viewModel = viewModel(
                repository = repository,
                factory = factory,
                keyStore = FakeSshKeyStore(),
                connectionId = 9,
            )
            advanceUntilIdle()

            viewModel.testAndSave(keyDraft(privateKeyFileName = "untrusted-caller.key"))
            advanceUntilIdle()

            assertEquals("committed-existing.key", factory.created.single().privateKeyFileName)
            assertEquals("committed-existing.key", repository.upserted.single().privateKeyFileName)
        }

    @Test
    fun `selected key snapshot overrides caller and existing filenames`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val repository = FakeRepository(
                existing = keyDraft(id = 9, privateKeyFileName = "committed-existing.key"),
            )
            val keyStore = FakeSshKeyStore(
                stagedKeys = ArrayDeque(listOf(StagedSshKey("selected-new.key", "new.pem"))),
                committedName = "committed-new.key",
            )
            val factory = FakeNetworkClientFactory(
                ArrayDeque(listOf(FakeNetworkClient(Result.success(Unit)))),
            )
            val viewModel = viewModel(
                repository = repository,
                factory = factory,
                keyStore = keyStore,
                connectionId = 9,
            )
            advanceUntilIdle()
            viewModel.stagePrivateKey(TestUri)
            advanceUntilIdle()

            viewModel.testAndSave(keyDraft(privateKeyFileName = "untrusted-caller.key"))
            advanceUntilIdle()

            assertEquals("selected-new.key", factory.created.single().privateKeyFileName)
            assertEquals("committed-new.key", repository.upserted.single().privateKeyFileName)
        }

    @Test
    fun `failed replacement cleanup remains tracked until ViewModel is cleared`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val keyStore = FakeSshKeyStore(
                stagedKeys = ArrayDeque(
                    listOf(
                        StagedSshKey("first.key", "first.pem"),
                        StagedSshKey("second.key", "second.pem"),
                    ),
                ),
                deleteFailures = mutableMapOf("first.key" to 4),
            )
            val viewModel = viewModel(keyStore = keyStore)
            viewModel.stagePrivateKey(TestUri)
            advanceUntilIdle()
            viewModel.stagePrivateKey(TestUri)
            advanceUntilIdle()

            clear(viewModel)
            advanceUntilIdle()

            assertEquals(setOf("first.key", "second.key"), keyStore.deleted.toSet())
        }

    @Test
    fun `unknown host requests confirmation without saving`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val confirmation = confirmation()
            val repository = FakeRepository()
            val client = FakeNetworkClient(Result.failure(IllegalStateException("wrapped", confirmation)))
            val viewModel = viewModel(repository = repository, clients = ArrayDeque(listOf(client)))

            viewModel.testAndSave(keyDraft())
            advanceUntilIdle()

            assertEquals(
                SaveState.ConfirmHostKey(
                    HostKeyConfirmation("sftp.example", 22, "EdDSA", "SHA256:server-key"),
                ),
                viewModel.saveState.value,
            )
            assertTrue(repository.upserted.isEmpty())
            assertEquals(1, client.disconnectCount)
        }

    @Test
    fun `wrapped passphrase failure has actionable message`() =
        assertConnectionError(
            IllegalStateException("SSH failed", IllegalArgumentException("Incorrect private key passphrase")),
            "The private key passphrase is incorrect or missing.",
        )

    @Test
    fun `wrapped malformed key failure has actionable message`() =
        assertConnectionError(
            IllegalStateException("SSH failed", IllegalArgumentException("Invalid PEM key format")),
            "The private key format isn't supported or the file is malformed.",
        )

    @Test
    fun `wrapped authentication failure has actionable message`() =
        assertConnectionError(
            IllegalStateException("SSH failed", IllegalStateException("Exhausted available authentication methods")),
            "Authentication was rejected. Check your credentials and try again.",
        )

    @Test
    fun `rejecting host confirmation retains the staged key`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val keyStore = FakeSshKeyStore(
                stagedKeys = ArrayDeque(listOf(StagedSshKey("staged.key", "id_ed25519"))),
            )
            val viewModel = viewModel(
                keyStore = keyStore,
                clients = ArrayDeque(listOf(FakeNetworkClient(Result.failure(confirmation())))),
            )
            viewModel.stagePrivateKey(TestUri)
            advanceUntilIdle()
            viewModel.testAndSave(keyDraft())
            advanceUntilIdle()

            viewModel.rejectHostKey()

            assertEquals(SaveState.Idle, viewModel.saveState.value)
            assertEquals(SelectedPrivateKey("staged.key", "id_ed25519"), viewModel.selectedPrivateKey.value)
            assertTrue(keyStore.deleted.isEmpty())
        }

    @Test
    fun `trust retries the exact pending draft with only its fingerprint changed`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val draft = keyDraft().copy(name = "Exact draft", path = "/videos", privateKeyPassphrase = "secret")
            val keyStore = FakeSshKeyStore(
                stagedKeys = ArrayDeque(listOf(StagedSshKey("staged.key", "id_ed25519"))),
            )
            val factory = FakeNetworkClientFactory(
                ArrayDeque(
                    listOf(
                        FakeNetworkClient(Result.failure(confirmation())),
                        FakeNetworkClient(Result.success(Unit)),
                    ),
                ),
            )
            val repository = FakeRepository()
            val viewModel = viewModel(repository, factory, keyStore)
            viewModel.stagePrivateKey(TestUri)
            advanceUntilIdle()

            viewModel.testAndSave(draft)
            advanceUntilIdle()
            viewModel.acceptHostKey()
            advanceUntilIdle()

            assertEquals(draft.copy(privateKeyFileName = "staged.key"), factory.created[0])
            assertEquals(
                draft.copy(privateKeyFileName = "staged.key", hostKeyFingerprint = "SHA256:server-key"),
                factory.created[1],
            )
            assertEquals("SHA256:server-key", repository.upserted.single().hostKeyFingerprint)
        }

    @Test
    fun `successful replacement commits then saves before deleting old key`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val events = mutableListOf<String>()
            val repository = FakeRepository(existing = keyDraft(id = 9, privateKeyFileName = "old.key"), events = events)
            val keyStore = FakeSshKeyStore(
                stagedKeys = ArrayDeque(listOf(StagedSshKey("staged.key", "new.pem"))),
                committedName = "new.key",
                events = events,
            )
            val viewModel = viewModel(
                repository = repository,
                clients = ArrayDeque(listOf(FakeNetworkClient(Result.success(Unit), events))),
                keyStore = keyStore,
                connectionId = 9,
            )
            advanceUntilIdle()
            events.clear()
            viewModel.stagePrivateKey(TestUri)
            advanceUntilIdle()

            viewModel.testAndSave(keyDraft(privateKeyFileName = "staged.key"))
            advanceUntilIdle()

            assertEquals(
                listOf("client.connect", "client.disconnect", "key.commit:staged.key", "repository.upsert:new.key", "key.delete:old.key"),
                events,
            )
            assertEquals("new.key", repository.upserted.single().privateKeyFileName)
            assertNull(viewModel.selectedPrivateKey.value)
        }

    @Test
    fun `failed authentication retains staged and old committed keys`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val repository = FakeRepository(existing = keyDraft(id = 9, privateKeyFileName = "old.key"))
            val keyStore = FakeSshKeyStore(
                stagedKeys = ArrayDeque(listOf(StagedSshKey("staged.key", "new.pem"))),
            )
            val viewModel = viewModel(
                repository = repository,
                clients = ArrayDeque(listOf(FakeNetworkClient(Result.failure(IllegalStateException("Authentication rejected"))))),
                keyStore = keyStore,
                connectionId = 9,
            )
            advanceUntilIdle()
            viewModel.stagePrivateKey(TestUri)
            advanceUntilIdle()

            viewModel.testAndSave(keyDraft(privateKeyFileName = "staged.key"))
            advanceUntilIdle()

            assertTrue(viewModel.saveState.value is SaveState.Error)
            assertEquals(SelectedPrivateKey("staged.key", "new.pem"), viewModel.selectedPrivateKey.value)
            assertTrue(repository.upserted.isEmpty())
            assertTrue(keyStore.committed.isEmpty())
            assertTrue(keyStore.deleted.isEmpty())
        }

    @Test
    fun `switching to password saves blank key fields then deletes staged and old keys`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val events = mutableListOf<String>()
            val repository = FakeRepository(existing = keyDraft(id = 9, privateKeyFileName = "old.key"), events = events)
            val keyStore = FakeSshKeyStore(
                stagedKeys = ArrayDeque(listOf(StagedSshKey("staged.key", "new.pem"))),
                events = events,
            )
            val viewModel = viewModel(
                repository = repository,
                clients = ArrayDeque(listOf(FakeNetworkClient(Result.success(Unit), events))),
                keyStore = keyStore,
                connectionId = 9,
            )
            advanceUntilIdle()
            events.clear()
            viewModel.stagePrivateKey(TestUri)
            advanceUntilIdle()

            viewModel.testAndSave(
                keyDraft(
                    authentication = NetworkAuthentication.PASSWORD,
                    password = "password",
                    privateKeyFileName = "stale.key",
                    privateKeyPassphrase = "stale passphrase",
                ),
            )
            advanceUntilIdle()

            val saved = repository.upserted.single()
            assertEquals(NetworkAuthentication.PASSWORD, saved.authentication)
            assertEquals("", saved.privateKeyFileName)
            assertEquals("", saved.privateKeyPassphrase)
            assertEquals(
                listOf("client.connect", "client.disconnect", "repository.upsert:", "key.delete:old.key", "key.delete:staged.key"),
                events,
            )
        }

    @Test
    fun `save failure deletes newly committed key but preserves old key`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val repository = FakeRepository(
                existing = keyDraft(id = 9, privateKeyFileName = "old.key"),
                upsertFailure = IllegalStateException("database unavailable"),
            )
            val keyStore = FakeSshKeyStore(
                stagedKeys = ArrayDeque(listOf(StagedSshKey("staged.key", "new.pem"))),
                committedName = "new.key",
            )
            val viewModel = viewModel(
                repository = repository,
                clients = ArrayDeque(listOf(FakeNetworkClient(Result.success(Unit)))),
                keyStore = keyStore,
                connectionId = 9,
            )
            advanceUntilIdle()
            viewModel.stagePrivateKey(TestUri)
            advanceUntilIdle()

            viewModel.testAndSave(keyDraft(privateKeyFileName = "staged.key"))
            advanceUntilIdle()

            assertTrue(viewModel.saveState.value is SaveState.Error)
            assertEquals(listOf("new.key"), keyStore.deleted)
            assertFalse("old.key" in keyStore.deleted)
            assertNull(viewModel.selectedPrivateKey.value)
        }

    @Test
    fun `rollback cleanup failure preserves repository error and schedules committed key cleanup`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val repository = FakeRepository(
                existing = keyDraft(id = 9, privateKeyFileName = "old.key"),
                upsertFailure = IllegalStateException("database unavailable"),
            )
            val keyStore = FakeSshKeyStore(
                stagedKeys = ArrayDeque(listOf(StagedSshKey("staged.key", "new.pem"))),
                committedName = "new.key",
                deleteFailures = mutableMapOf("new.key" to 1),
            )
            val viewModel = viewModel(
                repository = repository,
                clients = ArrayDeque(listOf(FakeNetworkClient(Result.success(Unit)))),
                keyStore = keyStore,
                connectionId = 9,
            )
            advanceUntilIdle()
            viewModel.stagePrivateKey(TestUri)
            advanceUntilIdle()

            viewModel.testAndSave(keyDraft(privateKeyFileName = "staged.key"))
            advanceUntilIdle()

            assertEquals("database unavailable", (viewModel.saveState.value as SaveState.Error).message)
            assertEquals(listOf("new.key", "new.key"), keyStore.deleteAttempts)
            assertEquals(listOf("new.key"), keyStore.deleted)
            assertFalse("old.key" in keyStore.deleteAttempts)
        }

    @Test
    fun `old key cleanup failure is retried after replacement is saved`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val repository = FakeRepository(existing = keyDraft(id = 9, privateKeyFileName = "old.key"))
            val keyStore = FakeSshKeyStore(
                stagedKeys = ArrayDeque(listOf(StagedSshKey("staged.key", "new.pem"))),
                committedName = "new.key",
                deleteFailures = mutableMapOf("old.key" to 1),
            )
            val viewModel = viewModel(
                repository = repository,
                clients = ArrayDeque(listOf(FakeNetworkClient(Result.success(Unit)))),
                keyStore = keyStore,
                connectionId = 9,
            )
            advanceUntilIdle()
            viewModel.stagePrivateKey(TestUri)
            advanceUntilIdle()

            viewModel.testAndSave(keyDraft(privateKeyFileName = "staged.key"))
            advanceUntilIdle()

            assertEquals(SaveState.Idle, viewModel.saveState.value)
            assertEquals(listOf("old.key", "old.key"), keyStore.deleteAttempts)
            assertEquals(listOf("old.key"), keyStore.deleted)
        }

    @Test
    fun `old key cleanup failure is retried after switching to password`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val repository = FakeRepository(existing = keyDraft(id = 9, privateKeyFileName = "old.key"))
            val keyStore = FakeSshKeyStore(deleteFailures = mutableMapOf("old.key" to 1))
            val viewModel = viewModel(
                repository = repository,
                clients = ArrayDeque(listOf(FakeNetworkClient(Result.success(Unit)))),
                keyStore = keyStore,
                connectionId = 9,
            )
            advanceUntilIdle()

            viewModel.testAndSave(keyDraft(authentication = NetworkAuthentication.PASSWORD, password = "password"))
            advanceUntilIdle()

            assertEquals(SaveState.Idle, viewModel.saveState.value)
            assertEquals("", repository.upserted.single().privateKeyFileName)
            assertEquals(listOf("old.key", "old.key"), keyStore.deleteAttempts)
            assertEquals(listOf("old.key"), keyStore.deleted)
        }

    @Test
    fun `clearing during successful key upsert retains the committed key`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val upsertReached = CompletableDeferred<Unit>()
            val allowUpsertToReturn = CompletableDeferred<Unit>()
            val repository = FakeRepository(
                existing = keyDraft(id = 9, privateKeyFileName = "old.key"),
                afterUpsert = {
                    upsertReached.complete(Unit)
                    allowUpsertToReturn.await()
                },
            )
            val keyStore = FakeSshKeyStore(
                stagedKeys = ArrayDeque(listOf(StagedSshKey("staged.key", "new.pem"))),
                committedName = "new.key",
            )
            val viewModel = viewModel(
                repository = repository,
                clients = ArrayDeque(listOf(FakeNetworkClient(Result.success(Unit)))),
                keyStore = keyStore,
                connectionId = 9,
            )
            advanceUntilIdle()
            viewModel.stagePrivateKey(TestUri)
            advanceUntilIdle()

            viewModel.testAndSave(keyDraft(privateKeyFileName = "staged.key"))
            upsertReached.await()
            clear(viewModel)
            allowUpsertToReturn.complete(Unit)
            advanceUntilIdle()

            assertEquals("new.key", repository.upserted.single().privateKeyFileName)
            assertFalse("new.key" in keyStore.deleteAttempts)
            assertEquals(listOf("old.key"), keyStore.deleted)
        }

    @Test
    fun `clearing during password upsert still cleans old committed key`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val upsertReached = CompletableDeferred<Unit>()
            val allowUpsertToReturn = CompletableDeferred<Unit>()
            val repository = FakeRepository(
                existing = keyDraft(id = 9, privateKeyFileName = "old.key"),
                afterUpsert = {
                    upsertReached.complete(Unit)
                    allowUpsertToReturn.await()
                },
            )
            val keyStore = FakeSshKeyStore()
            val viewModel = viewModel(
                repository = repository,
                clients = ArrayDeque(listOf(FakeNetworkClient(Result.success(Unit)))),
                keyStore = keyStore,
                connectionId = 9,
            )
            advanceUntilIdle()

            viewModel.testAndSave(keyDraft(authentication = NetworkAuthentication.PASSWORD, password = "password"))
            upsertReached.await()
            clear(viewModel)
            allowUpsertToReturn.complete(Unit)
            advanceUntilIdle()

            assertEquals("", repository.upserted.single().privateKeyFileName)
            assertEquals(listOf("old.key"), keyStore.deleted)
        }

    @Test
    fun `cancel deletes only the staged key`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val keyStore = FakeSshKeyStore(
                stagedKeys = ArrayDeque(listOf(StagedSshKey("staged.key", "new.pem"))),
            )
            val viewModel = viewModel(
                repository = FakeRepository(existing = keyDraft(id = 9, privateKeyFileName = "old.key")),
                keyStore = keyStore,
                connectionId = 9,
            )
            advanceUntilIdle()
            viewModel.stagePrivateKey(TestUri)
            advanceUntilIdle()

            viewModel.cancel()
            advanceUntilIdle()

            assertEquals(listOf("staged.key"), keyStore.deleted)
            assertNull(viewModel.selectedPrivateKey.value)
        }

    @Test
    fun `clearing ViewModel deletes only the staged key`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val keyStore = FakeSshKeyStore(
                stagedKeys = ArrayDeque(listOf(StagedSshKey("staged.key", "new.pem"))),
            )
            val viewModel = viewModel(
                repository = FakeRepository(existing = keyDraft(id = 9, privateKeyFileName = "old.key")),
                keyStore = keyStore,
                connectionId = 9,
            )
            advanceUntilIdle()
            viewModel.stagePrivateKey(TestUri)
            advanceUntilIdle()
            val store = register(viewModel)

            store.clear()
            assertTrue(keyStore.deleted.isEmpty())
            advanceUntilIdle()

            assertEquals(listOf("staged.key"), keyStore.deleted)
        }

    private fun viewModel(
        repository: FakeRepository = FakeRepository(),
        clients: ArrayDeque<FakeNetworkClient> = ArrayDeque(),
        keyStore: FakeSshKeyStore = FakeSshKeyStore(),
        connectionId: Long? = null,
    ) = viewModel(repository, FakeNetworkClientFactory(clients), keyStore, connectionId)

    private fun viewModel(
        repository: FakeRepository,
        factory: FakeNetworkClientFactory,
        keyStore: FakeSshKeyStore,
        connectionId: Long? = null,
    ) = AddConnectionViewModel(
        connectionId,
        repository,
        factory,
        keyStore,
        CoroutineScope(SupervisorJob() + mainDispatcherRule.testDispatcher),
    )

    private fun clear(viewModel: AddConnectionViewModel) {
        register(viewModel).clear()
    }

    private fun register(viewModel: AddConnectionViewModel): ViewModelStore {
        val store = ViewModelStore()
        val provider = ViewModelProvider(
            store,
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return viewModel as T
                }
            },
        )
        provider[AddConnectionViewModel::class.java]
        return store
    }

    private fun confirmation() = HostKeyConfirmationRequired(
        host = "sftp.example",
        port = 22,
        algorithm = "EdDSA",
        fingerprint = "SHA256:server-key",
    )

    private fun assertConnectionError(error: Throwable, expected: String) =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = viewModel(
                clients = ArrayDeque(listOf(FakeNetworkClient(Result.failure(error)))),
            )

            viewModel.testAndSave(keyDraft(authentication = NetworkAuthentication.PASSWORD))
            advanceUntilIdle()

            assertEquals(expected, (viewModel.saveState.value as SaveState.Error).message)
        }

    private fun keyDraft(
        id: Long = 0,
        authentication: NetworkAuthentication = NetworkAuthentication.SSH_KEY,
        password: String = "",
        privateKeyFileName: String = "staged.key",
        privateKeyPassphrase: String = "",
    ) = NetworkConnection(
        id = id,
        name = "SFTP",
        protocol = NetworkProtocol.SFTP,
        host = "sftp.example",
        username = "media",
        password = password,
        authentication = authentication,
        privateKeyFileName = privateKeyFileName,
        privateKeyPassphrase = privateKeyPassphrase,
    )
}

private class FakeRepository(
    private val existing: NetworkConnection? = null,
    private val upsertFailure: Throwable? = null,
    private val events: MutableList<String> = mutableListOf(),
    private val afterUpsert: suspend () -> Unit = {},
) : NetworkConnectionRepository {
    val upserted = mutableListOf<NetworkConnection>()

    override fun getConnections(): Flow<List<NetworkConnection>> = flowOf(emptyList())

    override suspend fun getConnection(id: Long): NetworkConnection? = existing

    override suspend fun upsert(connection: NetworkConnection): Long {
        events += "repository.upsert:${connection.privateKeyFileName}"
        upserted += connection
        upsertFailure?.let { throw it }
        afterUpsert()
        return connection.id
    }

    override suspend fun delete(id: Long) = Unit
}

private class FakeNetworkClientFactory(
    private val clients: ArrayDeque<FakeNetworkClient>,
) : NetworkClientFactory {
    val created = mutableListOf<NetworkConnection>()

    override fun create(connection: NetworkConnection): NetworkClient {
        created += connection
        return clients.removeFirst()
    }
}

private class FakeNetworkClient(
    private val connectResult: Result<Unit>,
    private val events: MutableList<String> = mutableListOf(),
    private val beforeConnect: suspend () -> Unit = {},
) : NetworkClient {
    var disconnectCount = 0

    override val rootPath: String = "/"

    override suspend fun connect(): Result<Unit> {
        events += "client.connect"
        beforeConnect()
        return connectResult
    }

    override suspend fun disconnect() {
        disconnectCount++
        events += "client.disconnect"
    }

    override fun isConnected(): Boolean = false

    override suspend fun listFiles(path: String): Result<List<NetworkFile>> = error("Not used")

    override suspend fun fileSize(path: String): Long = error("Not used")

    override suspend fun openStream(path: String, offset: Long): InputStream = error("Not used")
}

private class FakeSshKeyStore(
    private val stagedKeys: ArrayDeque<StagedSshKey> = ArrayDeque(),
    private val committedName: String = "committed.key",
    private val events: MutableList<String> = mutableListOf(),
    private val deleteFailures: MutableMap<String, Int> = mutableMapOf(),
    private val failStageAfterCalls: Int? = null,
) : SshKeyStore {
    val committed = mutableListOf<String>()
    val deleted = mutableListOf<String>()
    val deleteAttempts = mutableListOf<String>()
    var stageCalls = 0
        private set

    override suspend fun stage(uri: Uri): StagedSshKey {
        stageCalls++
        if (failStageAfterCalls != null && stageCalls > failStageAfterCalls) error("staging failed")
        return stagedKeys.removeFirst()
    }

    override fun resolve(fileName: String): File = error("Not used")

    override suspend fun commit(fileName: String): String {
        events += "key.commit:$fileName"
        committed += fileName
        return committedName
    }

    override suspend fun delete(fileName: String) {
        events += "key.delete:$fileName"
        deleteAttempts += fileName
        val remainingFailures = deleteFailures[fileName] ?: 0
        if (remainingFailures > 0) {
            deleteFailures[fileName] = remainingFailures - 1
            error("delete failed for $fileName")
        }
        deleted += fileName
    }
}
