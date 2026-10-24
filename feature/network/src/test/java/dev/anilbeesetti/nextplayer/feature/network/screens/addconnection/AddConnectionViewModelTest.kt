package dev.anilbeesetti.nextplayer.feature.network.screens.addconnection

import android.net.TestUri
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import dev.anilbeesetti.nextplayer.core.data.repository.NetworkConnectionRepository
import dev.anilbeesetti.nextplayer.core.media.network.NetworkClient
import dev.anilbeesetti.nextplayer.core.media.network.NetworkClientFactory
import dev.anilbeesetti.nextplayer.core.media.network.keys.SshKeyStore
import dev.anilbeesetti.nextplayer.core.media.network.keys.StagedSshKey
import dev.anilbeesetti.nextplayer.core.media.network.sftp.HostKeyConfirmationRequired
import dev.anilbeesetti.nextplayer.core.media.network.sftp.HostKeyMismatch
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
    fun `successful save navigates through the current output after resetting state`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val started = CompletableDeferred<Unit>()
            val finish = CompletableDeferred<Unit>()
            val repository = FakeRepository()
            val viewModel = viewModel(
                repository = repository,
                clients = ArrayDeque(
                    elements = listOf(
                        element = FakeNetworkClient(Result.success(value = Unit)) {
                            started.complete(value = Unit)
                            finish.await()
                        },
                    ),
                ),
            )
            val calls = mutableListOf<String>()
            viewModel.output =
                AddConnectionViewModel.Output(navigateUp = { calls.add(element = "old") })
            viewModel.onAction(
                AddConnectionAction.TestAndSave(
                    connection = keyDraft(
                        authentication = NetworkAuthentication.PASSWORD,
                        privateKeyFileName = "",
                    ),
                ),
            )
            started.await()
            viewModel.output = AddConnectionViewModel.Output(
                navigateUp = {
                    assertEquals(
                        SaveState.Idle,
                        viewModel.state.value.saveState,
                    )
                    assertEquals(
                        1,
                        repository.upserted.size,
                    )
                    calls.add(element = "current")
                },
            )

            finish.complete(value = Unit)
            advanceUntilIdle()

            assertEquals(
                listOf(element = "current"),
                calls,
            )
        }

    @Test
    fun `staging a replacement deletes the previous staged key`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val keyStore = FakeSshKeyStore(
                stagedKeys = ArrayDeque(
                    elements = listOf(
                        StagedSshKey(
                            fileName = "first.key",
                            displayName = "first.pem",
                        ),
                        StagedSshKey(
                            fileName = "second.key",
                            displayName = "second.pem",
                        ),
                    ),
                ),
            )
            val viewModel = viewModel(keyStore = keyStore)

            viewModel.onAction(AddConnectionAction.StagePrivateKey(uri = TestUri))
            advanceUntilIdle()
            viewModel.onAction(AddConnectionAction.StagePrivateKey(uri = TestUri))
            advanceUntilIdle()

            assertEquals(
                SelectedPrivateKey(
                    stagedFileName = "second.key",
                    displayName = "second.pem",
                ),
                viewModel.state.value.selectedPrivateKey,
            )
            assertEquals(
                listOf(element = "first.key"),
                keyStore.deleted,
            )
        }

    @Test
    fun `replacement requested during test cannot replace the tested key snapshot`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val connectStarted = CompletableDeferred<Unit>()
            val allowConnect = CompletableDeferred<Unit>()
            val keyStore = FakeSshKeyStore(
                stagedKeys = ArrayDeque(
                    elements = listOf(
                        StagedSshKey(
                            fileName = "tested-a.key",
                            displayName = "a.pem",
                        ),
                        StagedSshKey(
                            fileName = "replacement-b.key",
                            displayName = "b.pem",
                        ),
                    ),
                ),
                committedName = "committed-a.key",
            )
            val repository = FakeRepository()
            val viewModel = viewModel(
                repository = repository,
                clients = ArrayDeque(
                    elements = listOf(
                        element = FakeNetworkClient(Result.success(value = Unit)) {
                            connectStarted.complete(value = Unit)
                            allowConnect.await()
                        },
                    ),
                ),
                keyStore = keyStore,
            )
            viewModel.onAction(AddConnectionAction.StagePrivateKey(uri = TestUri))
            advanceUntilIdle()

            viewModel.onAction(
                AddConnectionAction.TestAndSave(
                    connection = keyDraft(privateKeyFileName = "tested-a.key"),
                ),
            )
            connectStarted.await()
            viewModel.onAction(AddConnectionAction.StagePrivateKey(uri = TestUri))
            runCurrent()
            allowConnect.complete(value = Unit)
            advanceUntilIdle()

            assertEquals(
                1,
                keyStore.stageCalls,
            )
            assertEquals(
                listOf(element = "tested-a.key"),
                keyStore.committed,
            )
            assertEquals(
                "committed-a.key",
                repository.upserted.single().privateKeyFileName,
            )
            assertFalse("tested-a.key" in keyStore.deleteAttempts)
        }

    @Test
    fun `removal requested during test cannot delete the tested key`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val connectStarted = CompletableDeferred<Unit>()
            val allowConnect = CompletableDeferred<Unit>()
            val keyStore = FakeSshKeyStore(
                stagedKeys = ArrayDeque(
                    elements = listOf(
                        element = StagedSshKey(
                            fileName = "tested-a.key",
                            displayName = "a.pem",
                        ),
                    ),
                ),
                committedName = "committed-a.key",
            )
            val repository = FakeRepository()
            val viewModel = viewModel(
                repository = repository,
                clients = ArrayDeque(
                    elements = listOf(
                        element = FakeNetworkClient(Result.success(value = Unit)) {
                            connectStarted.complete(value = Unit)
                            allowConnect.await()
                        },
                    ),
                ),
                keyStore = keyStore,
            )
            viewModel.onAction(AddConnectionAction.StagePrivateKey(uri = TestUri))
            advanceUntilIdle()

            viewModel.onAction(
                AddConnectionAction.TestAndSave(
                    connection = keyDraft(privateKeyFileName = "tested-a.key"),
                ),
            )
            connectStarted.await()
            viewModel.onAction(action = AddConnectionAction.RemoveSelectedPrivateKey)
            runCurrent()
            allowConnect.complete(value = Unit)
            advanceUntilIdle()

            assertEquals(
                listOf(element = "tested-a.key"),
                keyStore.committed,
            )
            assertEquals(
                "committed-a.key",
                repository.upserted.single().privateKeyFileName,
            )
            assertFalse("tested-a.key" in keyStore.deleteAttempts)
        }

    @Test
    fun `staging failure requested during test cannot replace Testing state`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val connectStarted = CompletableDeferred<Unit>()
            val allowConnect = CompletableDeferred<Unit>()
            val keyStore = FakeSshKeyStore(
                stagedKeys = ArrayDeque(
                    elements = listOf(
                        element = StagedSshKey(
                            fileName = "tested-a.key",
                            displayName = "a.pem",
                        ),
                    ),
                ),
                failStageAfterCalls = 1,
            )
            val viewModel = viewModel(
                clients = ArrayDeque(
                    elements = listOf(
                        element = FakeNetworkClient(Result.success(value = Unit)) {
                            connectStarted.complete(value = Unit)
                            allowConnect.await()
                        },
                    ),
                ),
                keyStore = keyStore,
            )
            viewModel.onAction(AddConnectionAction.StagePrivateKey(uri = TestUri))
            advanceUntilIdle()

            viewModel.onAction(
                AddConnectionAction.TestAndSave(
                    connection = keyDraft(privateKeyFileName = "tested-a.key"),
                ),
            )
            connectStarted.await()
            viewModel.onAction(AddConnectionAction.StagePrivateKey(uri = TestUri))
            runCurrent()

            assertEquals(
                SaveState.Testing,
                viewModel.state.value.saveState,
            )
            assertEquals(
                1,
                keyStore.stageCalls,
            )
            allowConnect.complete(value = Unit)
            advanceUntilIdle()
        }

    @Test
    fun `cancel during connection test deletes staged key only after save is invalidated`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val connectStarted = CompletableDeferred<Unit>()
            val allowConnect = CompletableDeferred<Unit>()
            val repository = FakeRepository()
            val keyStore = FakeSshKeyStore(
                stagedKeys = ArrayDeque(
                    elements = listOf(
                        element = StagedSshKey(
                            fileName = "tested-a.key",
                            displayName = "a.pem",
                        ),
                    ),
                ),
            )
            val viewModel = viewModel(
                repository = repository,
                clients = ArrayDeque(
                    elements = listOf(
                        element = FakeNetworkClient(Result.success(value = Unit)) {
                            connectStarted.complete(value = Unit)
                            allowConnect.await()
                        },
                    ),
                ),
                keyStore = keyStore,
            )
            viewModel.onAction(AddConnectionAction.StagePrivateKey(uri = TestUri))
            advanceUntilIdle()

            viewModel.onAction(
                AddConnectionAction.TestAndSave(
                    connection = keyDraft(privateKeyFileName = "tested-a.key"),
                ),
            )
            connectStarted.await()
            viewModel.onAction(action = AddConnectionAction.Cancel)
            allowConnect.complete(value = Unit)
            advanceUntilIdle()

            assertTrue(repository.upserted.isEmpty())
            assertTrue(keyStore.committed.isEmpty())
            assertEquals(
                listOf(element = "tested-a.key"),
                keyStore.deleted,
            )
        }

    @Test
    fun `connection screen never stores password fields with rememberSaveable`() {
        val source = File(
            "src/main/java/dev/anilbeesetti/nextplayer/feature/network/screens/addconnection/AddConnectionScreen.kt",
        ).readText()

        assertFalse(source.contains(other = "var password by rememberSaveable"))
        assertFalse(source.contains(other = "var privateKeyPassphrase by rememberSaveable"))
        assertTrue(source.contains(other = "var password by remember"))
        assertTrue(source.contains(other = "var privateKeyPassphrase by remember"))
    }

    @Test
    fun `SFTP fingerprint is cleared only when its endpoint value changes`() {
        assertEquals(
            "",
            fingerprintAfterEndpointEdit(
                NetworkProtocol.SFTP,
                previousValue = "old-host",
                newValue = "new-host",
                fingerprint = "SHA256:trusted",
            ),
        )
        assertEquals(
            "SHA256:trusted",
            fingerprintAfterEndpointEdit(
                NetworkProtocol.SFTP,
                previousValue = "old-host",
                newValue = "old-host",
                fingerprint = "SHA256:trusted",
            ),
        )
        assertEquals(
            "SHA256:trusted",
            fingerprintAfterEndpointEdit(
                NetworkProtocol.SMB,
                previousValue = "old-host",
                newValue = "new-host",
                fingerprint = "SHA256:trusted",
            ),
        )
    }

    @Test
    fun `failed removal retains the staged key for retry`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val keyStore = FakeSshKeyStore(
                stagedKeys = ArrayDeque(
                    elements = listOf(
                        element = StagedSshKey(
                            fileName = "staged.key",
                            displayName = "id_ed25519",
                        ),
                    ),
                ),
                deleteFailures = mutableMapOf("staged.key" to 1),
            )
            val viewModel = viewModel(keyStore = keyStore)
            viewModel.onAction(AddConnectionAction.StagePrivateKey(uri = TestUri))
            advanceUntilIdle()

            viewModel.onAction(action = AddConnectionAction.RemoveSelectedPrivateKey)
            advanceUntilIdle()

            assertEquals(
                SelectedPrivateKey(
                    stagedFileName = "staged.key",
                    displayName = "id_ed25519",
                ),
                viewModel.state.value.selectedPrivateKey,
            )
            assertTrue(viewModel.state.value.saveState is SaveState.Error)
            assertTrue(keyStore.deleted.isEmpty())

            viewModel.onAction(action = AddConnectionAction.Cancel)
            advanceUntilIdle()
            assertEquals(
                listOf(element = "staged.key"),
                keyStore.deleted,
            )
        }

    @Test
    fun `stale draft cannot reuse a staged filename after removal completes`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val keyStore = FakeSshKeyStore(
                stagedKeys = ArrayDeque(
                    elements = listOf(
                        element = StagedSshKey(
                            fileName = "removed-a.key",
                            displayName = "a.pem",
                        ),
                    ),
                ),
            )
            val repository = FakeRepository()
            val factory = FakeNetworkClientFactory(
                clients = ArrayDeque(
                    elements = listOf(
                        element = FakeNetworkClient(
                            Result.failure(
                                exception = FileNotFoundException("Private key is missing"),
                            ),
                        ),
                    ),
                ),
            )
            val viewModel = viewModel(
                repository = repository,
                factory = factory,
                keyStore = keyStore,
            )
            viewModel.onAction(AddConnectionAction.StagePrivateKey(uri = TestUri))
            advanceUntilIdle()
            viewModel.onAction(action = AddConnectionAction.RemoveSelectedPrivateKey)
            advanceUntilIdle()

            viewModel.onAction(
                AddConnectionAction.TestAndSave(
                    connection = keyDraft(privateKeyFileName = "removed-a.key"),
                ),
            )
            advanceUntilIdle()

            assertEquals(
                "",
                factory.created.single().privateKeyFileName,
            )
            assertTrue(repository.upserted.isEmpty())
            assertEquals(
                listOf(element = "removed-a.key"),
                keyStore.deleted,
            )
        }

    @Test
    fun `edit without replacement uses only existing committed SSH key`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val repository = FakeRepository(
                existing = keyDraft(
                    id = 9,
                    privateKeyFileName = "committed-existing.key",
                ),
            )
            val factory = FakeNetworkClientFactory(
                clients = ArrayDeque(
                    elements = listOf(element = FakeNetworkClient(Result.success(value = Unit))),
                ),
            )
            val viewModel = viewModel(
                repository = repository,
                factory = factory,
                keyStore = FakeSshKeyStore(),
                connectionId = 9,
            )
            advanceUntilIdle()

            viewModel.onAction(
                AddConnectionAction.TestAndSave(
                    connection = keyDraft(privateKeyFileName = "untrusted-caller.key"),
                ),
            )
            advanceUntilIdle()

            assertEquals(
                "committed-existing.key",
                factory.created.single().privateKeyFileName,
            )
            assertEquals(
                "committed-existing.key",
                repository.upserted.single().privateKeyFileName,
            )
        }

    @Test
    fun `selected key snapshot overrides caller and existing filenames`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val repository = FakeRepository(
                existing = keyDraft(
                    id = 9,
                    privateKeyFileName = "committed-existing.key",
                ),
            )
            val keyStore = FakeSshKeyStore(
                stagedKeys = ArrayDeque(
                    elements = listOf(
                        element = StagedSshKey(
                            fileName = "selected-new.key",
                            displayName = "new.pem",
                        ),
                    ),
                ),
                committedName = "committed-new.key",
            )
            val factory = FakeNetworkClientFactory(
                clients = ArrayDeque(
                    elements = listOf(element = FakeNetworkClient(Result.success(value = Unit))),
                ),
            )
            val viewModel = viewModel(
                repository = repository,
                factory = factory,
                keyStore = keyStore,
                connectionId = 9,
            )
            advanceUntilIdle()
            viewModel.onAction(AddConnectionAction.StagePrivateKey(uri = TestUri))
            advanceUntilIdle()

            viewModel.onAction(
                AddConnectionAction.TestAndSave(
                    connection = keyDraft(privateKeyFileName = "untrusted-caller.key"),
                ),
            )
            advanceUntilIdle()

            assertEquals(
                "selected-new.key",
                factory.created.single().privateKeyFileName,
            )
            assertEquals(
                "committed-new.key",
                repository.upserted.single().privateKeyFileName,
            )
        }

    @Test
    fun `failed replacement cleanup remains tracked until ViewModel is cleared`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val keyStore = FakeSshKeyStore(
                stagedKeys = ArrayDeque(
                    elements = listOf(
                        StagedSshKey(
                            fileName = "first.key",
                            displayName = "first.pem",
                        ),
                        StagedSshKey(
                            fileName = "second.key",
                            displayName = "second.pem",
                        ),
                    ),
                ),
                deleteFailures = mutableMapOf("first.key" to 4),
            )
            val viewModel = viewModel(keyStore = keyStore)
            viewModel.onAction(AddConnectionAction.StagePrivateKey(uri = TestUri))
            advanceUntilIdle()
            viewModel.onAction(AddConnectionAction.StagePrivateKey(uri = TestUri))
            advanceUntilIdle()

            clear(viewModel = viewModel)
            advanceUntilIdle()

            assertEquals(
                setOf(
                    "first.key",
                    "second.key",
                ),
                keyStore.deleted.toSet(),
            )
        }

    @Test
    fun `unknown host requests confirmation without saving`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val confirmation = confirmation()
            val repository = FakeRepository()
            val client = FakeNetworkClient(
                Result.failure(
                    exception = IllegalStateException(
                        "wrapped",
                        confirmation,
                    ),
                ),
            )
            val viewModel = viewModel(
                repository = repository,
                clients = ArrayDeque(elements = listOf(element = client)),
            )

            viewModel.onAction(AddConnectionAction.TestAndSave(connection = keyDraft()))
            advanceUntilIdle()

            assertEquals(
                SaveState.ConfirmHostKey(
                    confirmation = HostKeyConfirmation(
                        host = "sftp.example",
                        port = 22,
                        algorithm = "EdDSA",
                        fingerprint = "SHA256:server-key",
                    ),
                ),
                viewModel.state.value.saveState,
            )
            assertTrue(repository.upserted.isEmpty())
            assertEquals(
                1,
                client.disconnectCount,
            )
        }

    @Test
    fun `wrapped passphrase failure has actionable message`() = assertConnectionError(
        error = IllegalStateException(
            "SSH failed",
            IllegalArgumentException("Incorrect private key passphrase"),
        ),
        expected = "The private key passphrase is incorrect or missing.",
    )

    @Test
    fun `wrapped malformed key failure has actionable message`() = assertConnectionError(
        error = IllegalStateException(
            "SSH failed",
            IllegalArgumentException("Invalid PEM key format"),
        ),
        expected = "The private key format isn't supported or the file is malformed.",
    )

    @Test
    fun `wrapped authentication failure has actionable message`() = assertConnectionError(
        error = IllegalStateException(
            "SSH failed",
            IllegalStateException("Exhausted available authentication methods"),
        ),
        expected = "Authentication was rejected. Check your credentials and try again.",
    )

    @Test
    fun `host key mismatch exposes trusted and presented fingerprints`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val mismatch = HostKeyMismatch(
                expectedFingerprint = "SHA256:trusted-key",
                presentedFingerprint = "SHA256:presented-key",
            )
            val viewModel = viewModel(
                clients = ArrayDeque(
                    elements = listOf(
                        element = FakeNetworkClient(
                            Result.failure(
                                exception = IllegalStateException(
                                    "SSH failed",
                                    mismatch,
                                ),
                            ),
                        ),
                    ),
                ),
            )

            viewModel.onAction(
                AddConnectionAction.TestAndSave(
                    connection = keyDraft(authentication = NetworkAuthentication.PASSWORD),
                ),
            )
            advanceUntilIdle()

            assertEquals(
                SaveState.Error(
                    message = "The SSH host key doesn't match the trusted fingerprint.",
                    hostKeyMismatch = HostKeyMismatchDetails(
                        trustedFingerprint = "SHA256:trusted-key",
                        presentedFingerprint = "SHA256:presented-key",
                    ),
                ),
                viewModel.state.value.saveState,
            )
        }

    @Test
    fun `rejecting host confirmation retains the staged key`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val keyStore = FakeSshKeyStore(
                stagedKeys = ArrayDeque(
                    elements = listOf(
                        element = StagedSshKey(
                            fileName = "staged.key",
                            displayName = "id_ed25519",
                        ),
                    ),
                ),
            )
            val viewModel = viewModel(
                keyStore = keyStore,
                clients = ArrayDeque(
                    elements = listOf(
                        element = FakeNetworkClient(Result.failure(exception = confirmation())),
                    ),
                ),
            )
            viewModel.onAction(AddConnectionAction.StagePrivateKey(uri = TestUri))
            advanceUntilIdle()
            viewModel.onAction(AddConnectionAction.TestAndSave(connection = keyDraft()))
            advanceUntilIdle()

            viewModel.onAction(action = AddConnectionAction.RejectHostKey)

            assertEquals(
                SaveState.Idle,
                viewModel.state.value.saveState,
            )
            assertEquals(
                SelectedPrivateKey(
                    stagedFileName = "staged.key",
                    displayName = "id_ed25519",
                ),
                viewModel.state.value.selectedPrivateKey,
            )
            assertTrue(keyStore.deleted.isEmpty())
        }

    @Test
    fun `trust retries the exact pending draft with only its fingerprint changed`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val draft = keyDraft().copy(
                name = "Exact draft",
                path = "/videos",
                privateKeyPassphrase = "secret",
            )
            val keyStore = FakeSshKeyStore(
                stagedKeys = ArrayDeque(
                    elements = listOf(
                        element = StagedSshKey(
                            fileName = "staged.key",
                            displayName = "id_ed25519",
                        ),
                    ),
                ),
            )
            val factory = FakeNetworkClientFactory(
                clients = ArrayDeque(
                    elements = listOf(
                        FakeNetworkClient(Result.failure(exception = confirmation())),
                        FakeNetworkClient(Result.success(value = Unit)),
                    ),
                ),
            )
            val repository = FakeRepository()
            val viewModel = viewModel(
                repository = repository,
                factory = factory,
                keyStore = keyStore,
            )
            viewModel.onAction(AddConnectionAction.StagePrivateKey(uri = TestUri))
            advanceUntilIdle()

            viewModel.onAction(AddConnectionAction.TestAndSave(connection = draft))
            advanceUntilIdle()
            viewModel.onAction(action = AddConnectionAction.AcceptHostKey)
            advanceUntilIdle()

            assertEquals(
                draft.copy(privateKeyFileName = "staged.key"),
                factory.created[0],
            )
            assertEquals(
                draft.copy(
                    privateKeyFileName = "staged.key",
                    hostKeyFingerprint = "SHA256:server-key",
                ),
                factory.created[1],
            )
            assertEquals(
                "SHA256:server-key",
                repository.upserted.single().hostKeyFingerprint,
            )
        }

    @Test
    fun `successful replacement commits then saves before deleting old key`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val events = mutableListOf<String>()
            val repository = FakeRepository(
                existing = keyDraft(
                    id = 9,
                    privateKeyFileName = "old.key",
                ),
                events = events,
            )
            val keyStore = FakeSshKeyStore(
                stagedKeys = ArrayDeque(
                    elements = listOf(
                        element = StagedSshKey(
                            fileName = "staged.key",
                            displayName = "new.pem",
                        ),
                    ),
                ),
                committedName = "new.key",
                events = events,
            )
            val viewModel = viewModel(
                repository = repository,
                clients = ArrayDeque(
                    elements = listOf(
                        element = FakeNetworkClient(
                            Result.success(value = Unit),
                            events = events,
                        ),
                    ),
                ),
                keyStore = keyStore,
                connectionId = 9,
            )
            advanceUntilIdle()
            events.clear()
            viewModel.onAction(AddConnectionAction.StagePrivateKey(uri = TestUri))
            advanceUntilIdle()

            viewModel.onAction(
                AddConnectionAction.TestAndSave(
                    connection = keyDraft(privateKeyFileName = "staged.key"),
                ),
            )
            advanceUntilIdle()

            assertEquals(
                listOf(
                    "client.connect",
                    "client.disconnect",
                    "key.commit:staged.key",
                    "repository.upsert:new.key",
                    "key.delete:old.key",
                ),
                events,
            )
            assertEquals(
                "new.key",
                repository.upserted.single().privateKeyFileName,
            )
            assertNull(viewModel.state.value.selectedPrivateKey)
        }

    @Test
    fun `failed authentication retains staged and old committed keys`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val repository = FakeRepository(
                existing = keyDraft(
                    id = 9,
                    privateKeyFileName = "old.key",
                ),
            )
            val keyStore = FakeSshKeyStore(
                stagedKeys = ArrayDeque(
                    elements = listOf(
                        element = StagedSshKey(
                            fileName = "staged.key",
                            displayName = "new.pem",
                        ),
                    ),
                ),
            )
            val viewModel = viewModel(
                repository = repository,
                clients = ArrayDeque(
                    elements = listOf(
                        element = FakeNetworkClient(
                            Result.failure(
                                exception = IllegalStateException("Authentication rejected"),
                            ),
                        ),
                    ),
                ),
                keyStore = keyStore,
                connectionId = 9,
            )
            advanceUntilIdle()
            viewModel.onAction(AddConnectionAction.StagePrivateKey(uri = TestUri))
            advanceUntilIdle()

            viewModel.onAction(
                AddConnectionAction.TestAndSave(
                    connection = keyDraft(privateKeyFileName = "staged.key"),
                ),
            )
            advanceUntilIdle()

            assertTrue(viewModel.state.value.saveState is SaveState.Error)
            assertEquals(
                SelectedPrivateKey(
                    stagedFileName = "staged.key",
                    displayName = "new.pem",
                ),
                viewModel.state.value.selectedPrivateKey,
            )
            assertTrue(repository.upserted.isEmpty())
            assertTrue(keyStore.committed.isEmpty())
            assertTrue(keyStore.deleted.isEmpty())
        }

    @Test
    fun `switching to password saves blank key fields then deletes staged and old keys`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val events = mutableListOf<String>()
            val repository = FakeRepository(
                existing = keyDraft(
                    id = 9,
                    privateKeyFileName = "old.key",
                ),
                events = events,
            )
            val keyStore = FakeSshKeyStore(
                stagedKeys = ArrayDeque(
                    elements = listOf(
                        element = StagedSshKey(
                            fileName = "staged.key",
                            displayName = "new.pem",
                        ),
                    ),
                ),
                events = events,
            )
            val viewModel = viewModel(
                repository = repository,
                clients = ArrayDeque(
                    elements = listOf(
                        element = FakeNetworkClient(
                            Result.success(value = Unit),
                            events = events,
                        ),
                    ),
                ),
                keyStore = keyStore,
                connectionId = 9,
            )
            advanceUntilIdle()
            events.clear()
            viewModel.onAction(AddConnectionAction.StagePrivateKey(uri = TestUri))
            advanceUntilIdle()

            viewModel.onAction(
                AddConnectionAction.TestAndSave(
                    connection = keyDraft(
                        authentication = NetworkAuthentication.PASSWORD,
                        password = "password",
                        privateKeyFileName = "stale.key",
                        privateKeyPassphrase = "stale passphrase",
                    ),
                ),
            )
            advanceUntilIdle()

            val saved = repository.upserted.single()
            assertEquals(
                NetworkAuthentication.PASSWORD,
                saved.authentication,
            )
            assertEquals(
                "",
                saved.privateKeyFileName,
            )
            assertEquals(
                "",
                saved.privateKeyPassphrase,
            )
            assertEquals(
                listOf(
                    "client.connect",
                    "client.disconnect",
                    "repository.upsert:",
                    "key.delete:old.key",
                    "key.delete:staged.key",
                ),
                events,
            )
        }

    @Test
    fun `save failure deletes newly committed key but preserves old key`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val repository = FakeRepository(
                existing = keyDraft(
                    id = 9,
                    privateKeyFileName = "old.key",
                ),
                upsertFailure = IllegalStateException("database unavailable"),
            )
            val keyStore = FakeSshKeyStore(
                stagedKeys = ArrayDeque(
                    elements = listOf(
                        element = StagedSshKey(
                            fileName = "staged.key",
                            displayName = "new.pem",
                        ),
                    ),
                ),
                committedName = "new.key",
            )
            val viewModel = viewModel(
                repository = repository,
                clients = ArrayDeque(
                    elements = listOf(element = FakeNetworkClient(Result.success(value = Unit))),
                ),
                keyStore = keyStore,
                connectionId = 9,
            )
            advanceUntilIdle()
            viewModel.onAction(AddConnectionAction.StagePrivateKey(uri = TestUri))
            advanceUntilIdle()

            viewModel.onAction(
                AddConnectionAction.TestAndSave(
                    connection = keyDraft(privateKeyFileName = "staged.key"),
                ),
            )
            advanceUntilIdle()

            assertTrue(viewModel.state.value.saveState is SaveState.Error)
            assertEquals(
                listOf(element = "new.key"),
                keyStore.deleted,
            )
            assertFalse("old.key" in keyStore.deleted)
            assertNull(viewModel.state.value.selectedPrivateKey)
        }

    @Test
    fun `rollback failure preserves repository error and schedules key cleanup`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val repository = FakeRepository(
                existing = keyDraft(
                    id = 9,
                    privateKeyFileName = "old.key",
                ),
                upsertFailure = IllegalStateException("database unavailable"),
            )
            val keyStore = FakeSshKeyStore(
                stagedKeys = ArrayDeque(
                    elements = listOf(
                        element = StagedSshKey(
                            fileName = "staged.key",
                            displayName = "new.pem",
                        ),
                    ),
                ),
                committedName = "new.key",
                deleteFailures = mutableMapOf("new.key" to 1),
            )
            val viewModel = viewModel(
                repository = repository,
                clients = ArrayDeque(
                    elements = listOf(element = FakeNetworkClient(Result.success(value = Unit))),
                ),
                keyStore = keyStore,
                connectionId = 9,
            )
            advanceUntilIdle()
            viewModel.onAction(AddConnectionAction.StagePrivateKey(uri = TestUri))
            advanceUntilIdle()

            viewModel.onAction(
                AddConnectionAction.TestAndSave(
                    connection = keyDraft(privateKeyFileName = "staged.key"),
                ),
            )
            advanceUntilIdle()

            assertEquals(
                "database unavailable",
                (viewModel.state.value.saveState as SaveState.Error).message,
            )
            assertEquals(
                listOf(
                    "new.key",
                    "new.key",
                ),
                keyStore.deleteAttempts,
            )
            assertEquals(
                listOf(element = "new.key"),
                keyStore.deleted,
            )
            assertFalse("old.key" in keyStore.deleteAttempts)
        }

    @Test
    fun `old key cleanup failure is retried after replacement is saved`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val repository = FakeRepository(
                existing = keyDraft(
                    id = 9,
                    privateKeyFileName = "old.key",
                ),
            )
            val keyStore = FakeSshKeyStore(
                stagedKeys = ArrayDeque(
                    elements = listOf(
                        element = StagedSshKey(
                            fileName = "staged.key",
                            displayName = "new.pem",
                        ),
                    ),
                ),
                committedName = "new.key",
                deleteFailures = mutableMapOf("old.key" to 1),
            )
            val viewModel = viewModel(
                repository = repository,
                clients = ArrayDeque(
                    elements = listOf(element = FakeNetworkClient(Result.success(value = Unit))),
                ),
                keyStore = keyStore,
                connectionId = 9,
            )
            advanceUntilIdle()
            viewModel.onAction(AddConnectionAction.StagePrivateKey(uri = TestUri))
            advanceUntilIdle()

            viewModel.onAction(
                AddConnectionAction.TestAndSave(
                    connection = keyDraft(privateKeyFileName = "staged.key"),
                ),
            )
            advanceUntilIdle()

            assertEquals(
                SaveState.Idle,
                viewModel.state.value.saveState,
            )
            assertEquals(
                listOf(
                    "old.key",
                    "old.key",
                ),
                keyStore.deleteAttempts,
            )
            assertEquals(
                listOf(element = "old.key"),
                keyStore.deleted,
            )
        }

    @Test
    fun `old key cleanup failure is retried after switching to password`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val repository = FakeRepository(
                existing = keyDraft(
                    id = 9,
                    privateKeyFileName = "old.key",
                ),
            )
            val keyStore = FakeSshKeyStore(deleteFailures = mutableMapOf("old.key" to 1))
            val viewModel = viewModel(
                repository = repository,
                clients = ArrayDeque(
                    elements = listOf(element = FakeNetworkClient(Result.success(value = Unit))),
                ),
                keyStore = keyStore,
                connectionId = 9,
            )
            advanceUntilIdle()

            viewModel.onAction(
                AddConnectionAction.TestAndSave(
                    connection = keyDraft(
                        authentication = NetworkAuthentication.PASSWORD,
                        password = "password",
                    ),
                ),
            )
            advanceUntilIdle()

            assertEquals(
                SaveState.Idle,
                viewModel.state.value.saveState,
            )
            assertEquals(
                "",
                repository.upserted.single().privateKeyFileName,
            )
            assertEquals(
                listOf(
                    "old.key",
                    "old.key",
                ),
                keyStore.deleteAttempts,
            )
            assertEquals(
                listOf(element = "old.key"),
                keyStore.deleted,
            )
        }

    @Test
    fun `clearing during successful key upsert retains the committed key`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val upsertReached = CompletableDeferred<Unit>()
            val allowUpsertToReturn = CompletableDeferred<Unit>()
            val repository = FakeRepository(
                existing = keyDraft(
                    id = 9,
                    privateKeyFileName = "old.key",
                ),
                afterUpsert = {
                    upsertReached.complete(value = Unit)
                    allowUpsertToReturn.await()
                },
            )
            val keyStore = FakeSshKeyStore(
                stagedKeys = ArrayDeque(
                    elements = listOf(
                        element = StagedSshKey(
                            fileName = "staged.key",
                            displayName = "new.pem",
                        ),
                    ),
                ),
                committedName = "new.key",
            )
            val viewModel = viewModel(
                repository = repository,
                clients = ArrayDeque(
                    elements = listOf(element = FakeNetworkClient(Result.success(value = Unit))),
                ),
                keyStore = keyStore,
                connectionId = 9,
            )
            advanceUntilIdle()
            viewModel.onAction(AddConnectionAction.StagePrivateKey(uri = TestUri))
            advanceUntilIdle()

            viewModel.onAction(
                AddConnectionAction.TestAndSave(
                    connection = keyDraft(privateKeyFileName = "staged.key"),
                ),
            )
            upsertReached.await()
            clear(viewModel = viewModel)
            allowUpsertToReturn.complete(value = Unit)
            advanceUntilIdle()

            assertEquals(
                "new.key",
                repository.upserted.single().privateKeyFileName,
            )
            assertFalse("new.key" in keyStore.deleteAttempts)
            assertEquals(
                listOf(element = "old.key"),
                keyStore.deleted,
            )
        }

    @Test
    fun `clearing during password upsert still cleans old committed key`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val upsertReached = CompletableDeferred<Unit>()
            val allowUpsertToReturn = CompletableDeferred<Unit>()
            val repository = FakeRepository(
                existing = keyDraft(
                    id = 9,
                    privateKeyFileName = "old.key",
                ),
                afterUpsert = {
                    upsertReached.complete(value = Unit)
                    allowUpsertToReturn.await()
                },
            )
            val keyStore = FakeSshKeyStore()
            val viewModel = viewModel(
                repository = repository,
                clients = ArrayDeque(
                    elements = listOf(element = FakeNetworkClient(Result.success(value = Unit))),
                ),
                keyStore = keyStore,
                connectionId = 9,
            )
            advanceUntilIdle()

            viewModel.onAction(
                AddConnectionAction.TestAndSave(
                    connection = keyDraft(
                        authentication = NetworkAuthentication.PASSWORD,
                        password = "password",
                    ),
                ),
            )
            upsertReached.await()
            clear(viewModel = viewModel)
            allowUpsertToReturn.complete(value = Unit)
            advanceUntilIdle()

            assertEquals(
                "",
                repository.upserted.single().privateKeyFileName,
            )
            assertEquals(
                listOf(element = "old.key"),
                keyStore.deleted,
            )
        }

    @Test
    fun `cancel deletes only the staged key`() = runTest(mainDispatcherRule.testDispatcher) {
        val keyStore = FakeSshKeyStore(
            stagedKeys = ArrayDeque(
                elements = listOf(
                    element = StagedSshKey(
                        fileName = "staged.key",
                        displayName = "new.pem",
                    ),
                ),
            ),
        )
        val viewModel = viewModel(
            repository = FakeRepository(
                existing = keyDraft(
                    id = 9,
                    privateKeyFileName = "old.key",
                ),
            ),
            keyStore = keyStore,
            connectionId = 9,
        )
        advanceUntilIdle()
        viewModel.onAction(AddConnectionAction.StagePrivateKey(uri = TestUri))
        advanceUntilIdle()

        viewModel.onAction(action = AddConnectionAction.Cancel)
        advanceUntilIdle()

        assertEquals(
            listOf(element = "staged.key"),
            keyStore.deleted,
        )
        assertNull(viewModel.state.value.selectedPrivateKey)
    }

    @Test
    fun `clearing ViewModel deletes only the staged key`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val keyStore = FakeSshKeyStore(
                stagedKeys = ArrayDeque(
                    elements = listOf(
                        element = StagedSshKey(
                            fileName = "staged.key",
                            displayName = "new.pem",
                        ),
                    ),
                ),
            )
            val viewModel = viewModel(
                repository = FakeRepository(
                    existing = keyDraft(
                        id = 9,
                        privateKeyFileName = "old.key",
                    ),
                ),
                keyStore = keyStore,
                connectionId = 9,
            )
            advanceUntilIdle()
            viewModel.onAction(AddConnectionAction.StagePrivateKey(uri = TestUri))
            advanceUntilIdle()
            val store = register(viewModel = viewModel)

            store.clear()
            assertTrue(keyStore.deleted.isEmpty())
            advanceUntilIdle()

            assertEquals(
                listOf(element = "staged.key"),
                keyStore.deleted,
            )
        }

    private fun viewModel(
        repository: FakeRepository = FakeRepository(),
        clients: ArrayDeque<FakeNetworkClient> = ArrayDeque(),
        keyStore: FakeSshKeyStore = FakeSshKeyStore(),
        connectionId: Long? = null,
    ) = viewModel(
        repository = repository,
        factory = FakeNetworkClientFactory(clients = clients),
        keyStore = keyStore,
        connectionId = connectionId,
    )

    private fun viewModel(
        repository: FakeRepository,
        factory: FakeNetworkClientFactory,
        keyStore: FakeSshKeyStore,
        connectionId: Long? = null,
    ) = AddConnectionViewModel(
        AddConnectionViewModel.Input(connectionId = connectionId),
        AddConnectionViewModel.Output(navigateUp = {}),
        repository = repository,
        clientFactory = factory,
        sshKeyStore = keyStore,
        applicationScope = CoroutineScope(
            context =
                SupervisorJob() + mainDispatcherRule.testDispatcher,
        ),
    )

    private fun clear(viewModel: AddConnectionViewModel) {
        register(viewModel = viewModel).clear()
    }

    private fun register(viewModel: AddConnectionViewModel): ViewModelStore {
        val store = ViewModelStore()
        val provider = ViewModelProvider(
            store = store,
            factory = object : ViewModelProvider.Factory {
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
                clients = ArrayDeque(
                    elements = listOf(
                        element = FakeNetworkClient(Result.failure(exception = error)),
                    ),
                ),
            )

            viewModel.onAction(
                AddConnectionAction.TestAndSave(
                    connection = keyDraft(authentication = NetworkAuthentication.PASSWORD),
                ),
            )
            advanceUntilIdle()

            assertEquals(
                expected,
                (viewModel.state.value.saveState as SaveState.Error).message,
            )
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

    override fun getConnections(): Flow<List<NetworkConnection>> = flowOf(value = emptyList())

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

private class FakeNetworkClientFactory(private val clients: ArrayDeque<FakeNetworkClient>) :
    NetworkClientFactory {
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

    override suspend fun listFiles(path: String): Result<List<NetworkFile>> =
        error(message = "Not used")

    override suspend fun fileSize(path: String): Long = error(message = "Not used")

    override suspend fun openStream(path: String, offset: Long): InputStream =
        error(message = "Not used")
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
        if (failStageAfterCalls != null && stageCalls > failStageAfterCalls) {
            error(message = "staging failed")
        }
        return stagedKeys.removeFirst()
    }

    override fun resolve(fileName: String): File = error(message = "Not used")

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
            error(message = "delete failed for $fileName")
        }
        deleted += fileName
    }
}
