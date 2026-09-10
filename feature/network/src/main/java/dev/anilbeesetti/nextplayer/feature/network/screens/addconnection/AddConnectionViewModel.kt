package dev.anilbeesetti.nextplayer.feature.network.screens.addconnection

import android.net.Uri
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.anilbeesetti.nextplayer.core.common.di.ApplicationScope
import dev.anilbeesetti.nextplayer.core.data.repository.NetworkConnectionRepository
import dev.anilbeesetti.nextplayer.core.media.network.NetworkClientFactory
import dev.anilbeesetti.nextplayer.core.media.network.keys.SshKeyStore
import dev.anilbeesetti.nextplayer.core.media.network.sftp.HostKeyConfirmationRequired
import dev.anilbeesetti.nextplayer.core.media.network.sftp.HostKeyMismatch
import dev.anilbeesetti.nextplayer.core.model.NetworkAuthentication
import dev.anilbeesetti.nextplayer.core.model.NetworkConnection
import dev.anilbeesetti.nextplayer.core.model.NetworkProtocol
import dev.anilbeesetti.nextplayer.core.ui.base.MviViewModel
import java.io.FileNotFoundException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SelectedPrivateKey(
    val stagedFileName: String,
    val displayName: String,
)

data class HostKeyConfirmation(
    val host: String,
    val port: Int,
    val algorithm: String,
    val fingerprint: String,
)

data class HostKeyMismatchDetails(
    val trustedFingerprint: String,
    val presentedFingerprint: String,
)

sealed interface SaveState {
    data object Idle : SaveState
    data object Testing : SaveState
    data class ConfirmHostKey(val confirmation: HostKeyConfirmation) : SaveState
    data class Error(
        val message: String?,
        val hostKeyMismatch: HostKeyMismatchDetails? = null,
    ) : SaveState
}

data class AddConnectionUiState(
    val isEdit: Boolean = false,
    val existingConnection: NetworkConnection? = null,
    val saveState: SaveState = SaveState.Idle,
    val selectedPrivateKey: SelectedPrivateKey? = null,
)

sealed interface AddConnectionAction {
    data class StagePrivateKey(val uri: Uri) : AddConnectionAction
    data object RemoveSelectedPrivateKey : AddConnectionAction
    data class TestAndSave(val connection: NetworkConnection) : AddConnectionAction
    data object AcceptHostKey : AddConnectionAction
    data object RejectHostKey : AddConnectionAction
    data object ClearError : AddConnectionAction
    data object Cancel : AddConnectionAction
}

@HiltViewModel(assistedFactory = AddConnectionViewModel.Factory::class)
class AddConnectionViewModel @AssistedInject constructor(
    @Assisted private val connectionId: Long?,
    private val repository: NetworkConnectionRepository,
    private val clientFactory: NetworkClientFactory,
    private val sshKeyStore: SshKeyStore,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : MviViewModel<AddConnectionUiState, AddConnectionAction>() {

    @AssistedFactory
    interface Factory {
        fun create(connectionId: Long?): AddConnectionViewModel
    }

    private val internalState = MutableStateFlow(AddConnectionUiState(isEdit = connectionId != null))
    override val state: StateFlow<AddConnectionUiState> = internalState.asStateFlow()

    private val _savedEvents = Channel<Unit>(Channel.BUFFERED)
    val savedEvents = _savedEvents.receiveAsFlow()

    private data class SaveOperation(
        val id: Long,
        val draft: NetworkConnection,
        val selectedPrivateKey: SelectedPrivateKey?,
    )

    private var nextOperationId = 0L
    private var activeOperation: SaveOperation? = null
    private var pendingOperation: SaveOperation? = null
    private var activeSaveJob: Job? = null
    private var activeKeyMutationJob: Job? = null
    private val lifecycleLock = Any()
    private var lifecycleEpoch = 0L
    private var cleanupRequested = false
    private val keyOwnershipLock = Any()
    private val sessionOwnedKeys = mutableSetOf<String>()
    private val keysBeingPersisted = mutableSetOf<String>()

    @Volatile
    private var isCleared = false

    init {
        if (connectionId != null) {
            viewModelScope.launch {
                val connection = repository.getConnection(connectionId)
                internalState.update { it.copy(existingConnection = connection) }
            }
        }
    }

    override fun onAction(action: AddConnectionAction) {
        when (action) {
            is AddConnectionAction.StagePrivateKey -> stagePrivateKey(action.uri)
            AddConnectionAction.RemoveSelectedPrivateKey -> removeSelectedPrivateKey()
            is AddConnectionAction.TestAndSave -> testAndSave(action.connection)
            AddConnectionAction.AcceptHostKey -> acceptHostKey()
            AddConnectionAction.RejectHostKey -> rejectHostKey()
            AddConnectionAction.ClearError -> clearError()
            AddConnectionAction.Cancel -> invalidateAndCleanup()
        }
    }

    private fun stagePrivateKey(uri: Uri) {
        if (keyMutationBlocked()) return
        val mutationEpoch = lifecycleEpoch
        activeKeyMutationJob = viewModelScope.launch {
            val result = runCatching { sshKeyStore.stage(uri) }
            if (!keyMutationMayComplete(mutationEpoch)) {
                result.getOrNull()?.let { staged ->
                    trackSessionKey(staged.fileName)
                    scheduleTrackedCleanup(staged.fileName)
                }
                return@launch
            }
            result
                .onSuccess { staged ->
                    val previous = state.value.selectedPrivateKey
                    trackSessionKey(staged.fileName)
                    internalState.update {
                        it.copy(selectedPrivateKey = SelectedPrivateKey(staged.fileName, staged.displayName))
                    }
                    if (previous != null && previous.stagedFileName != staged.fileName) {
                        runCatching { sshKeyStore.delete(previous.stagedFileName) }
                            .onSuccess { untrackSessionKey(previous.stagedFileName) }
                            .onFailure { error ->
                                internalState.update { it.copy(saveState = SaveState.Error(error.actionableMessage())) }
                                scheduleTrackedCleanup(previous.stagedFileName)
                            }
                    }
                }
                .onFailure { error ->
                    internalState.update { it.copy(saveState = SaveState.Error(error.actionableMessage())) }
                }
        }
    }

    private fun removeSelectedPrivateKey() {
        if (keyMutationBlocked()) return
        val selected = state.value.selectedPrivateKey ?: return
        val mutationEpoch = lifecycleEpoch
        activeKeyMutationJob = viewModelScope.launch {
            runCatching { sshKeyStore.delete(selected.stagedFileName) }
                .onSuccess {
                    untrackSessionKey(selected.stagedFileName)
                    if (keyMutationMayComplete(mutationEpoch) && state.value.selectedPrivateKey == selected) {
                        internalState.update { it.copy(selectedPrivateKey = null) }
                    }
                }
                .onFailure { error ->
                    if (keyMutationMayComplete(mutationEpoch)) {
                        internalState.update { it.copy(saveState = SaveState.Error(error.actionableMessage())) }
                    }
                }
        }
    }

    /** Tests [connection] by connecting, and persists it (with the existing id when editing) on success. */
    private fun testAndSave(connection: NetworkConnection) {
        if (cleanupRequested || activeOperation != null || activeKeyMutationJob?.isActive == true) return
        val selected = state.value.selectedPrivateKey
        val existingPrivateKeyFileName = state.value.existingConnection
            ?.takeIf { it.authentication == NetworkAuthentication.SSH_KEY }
            ?.privateKeyFileName
            .orEmpty()
        val draft = connection
            .copy(id = connectionId ?: 0)
            .sanitizeForAuthentication(selected, existingPrivateKeyFileName)
        val operation = SaveOperation(++nextOperationId, draft, selected)
        activeOperation = operation
        pendingOperation = null
        startSave(operation)
    }

    private fun acceptHostKey() {
        val confirmation = (state.value.saveState as? SaveState.ConfirmHostKey)?.confirmation ?: return
        val pending = pendingOperation ?: return
        val retry = pending.copy(draft = pending.draft.copy(hostKeyFingerprint = confirmation.fingerprint))
        activeOperation = retry
        pendingOperation = null
        startSave(retry)
    }

    private fun rejectHostKey() {
        if (state.value.saveState !is SaveState.ConfirmHostKey) return
        pendingOperation = null
        activeOperation = null
        internalState.update { it.copy(saveState = SaveState.Idle) }
    }

    private fun clearError() {
        internalState.update {
            if (it.saveState is SaveState.Error) it.copy(saveState = SaveState.Idle) else it
        }
    }

    override fun onCleared() {
        isCleared = true
        invalidateAndCleanup()
        super.onCleared()
    }

    private fun startSave(operation: SaveOperation) {
        internalState.update { it.copy(saveState = SaveState.Testing) }
        activeSaveJob = viewModelScope.launch { connectAndSave(operation) }
    }

    private suspend fun connectAndSave(operation: SaveOperation) {
        val result = runCatching {
            val client = clientFactory.create(operation.draft)
            try {
                client.connect().getOrThrow()
            } finally {
                withContext(NonCancellable) { runCatching { client.disconnect() } }
            }
            if (!isCurrent(operation)) throw CancellationException("Save operation was cancelled")
            withContext(NonCancellable) { persist(operation) }
        }
        if (!isCurrent(operation)) return
        val error = result.exceptionOrNull()
        if (error == null) {
            pendingOperation = null
            activeOperation = null
            _savedEvents.send(Unit)
            internalState.update { it.copy(saveState = SaveState.Idle) }
            return
        }

        val hostConfirmation = error.findCause<HostKeyConfirmationRequired>()
        if (hostConfirmation != null) {
            pendingOperation = operation
            internalState.update {
                it.copy(
                    saveState = SaveState.ConfirmHostKey(
                        HostKeyConfirmation(
                            host = hostConfirmation.host,
                            port = hostConfirmation.port,
                            algorithm = hostConfirmation.algorithm,
                            fingerprint = hostConfirmation.fingerprint,
                        ),
                    ),
                )
            }
        } else {
            pendingOperation = null
            activeOperation = null
            internalState.update { it.copy(saveState = error.toSaveError()) }
        }
    }

    private suspend fun persist(operation: SaveOperation) {
        val draft = operation.draft
        val oldKey = state.value.existingConnection
            ?.takeIf { it.authentication == NetworkAuthentication.SSH_KEY }
            ?.privateKeyFileName
            .orEmpty()
        val selected = operation.selectedPrivateKey

        if (draft.protocol == NetworkProtocol.SFTP && draft.authentication == NetworkAuthentication.SSH_KEY) {
            if (selected == null) {
                repository.upsert(draft)
                return
            }

            markKeyBeingPersisted(selected.stagedFileName)
            val committed = try {
                sshKeyStore.commit(selected.stagedFileName)
            } catch (error: Throwable) {
                finishKeyPersistence(selected.stagedFileName)
                if (isCleared) scheduleTrackedCleanup(selected.stagedFileName)
                throw error
            }
            internalState.update {
                if (it.selectedPrivateKey == selected) it.copy(selectedPrivateKey = null) else it
            }
            replacePersistingKey(selected.stagedFileName, committed)
            val saved = draft.copy(privateKeyFileName = committed)
            try {
                repository.upsert(saved)
            } catch (primaryError: Throwable) {
                runCatching { sshKeyStore.delete(committed) }
                    .onSuccess { untrackSessionKey(committed) }
                    .onFailure { cleanupError ->
                        primaryError.addSuppressed(cleanupError)
                        finishKeyPersistence(committed)
                        scheduleTrackedCleanup(committed)
                    }
                throw primaryError
            }
            untrackSessionKey(committed)
            if (oldKey.isNotBlank() && oldKey != committed) cleanupAfterSuccessfulSave(oldKey)
            return
        }

        repository.upsert(draft)
        if (oldKey.isNotBlank()) cleanupAfterSuccessfulSave(oldKey)
        val unusedStage = operation.selectedPrivateKey
        internalState.update {
            if (it.selectedPrivateKey == unusedStage) it.copy(selectedPrivateKey = null) else it
        }
        if (unusedStage != null) cleanupAfterSuccessfulSave(unusedStage.stagedFileName)
    }

    private fun keyMutationBlocked(): Boolean =
        cleanupRequested || activeOperation != null || activeKeyMutationJob?.isActive == true

    private fun keyMutationMayComplete(epoch: Long): Boolean =
        !cleanupRequested && lifecycleEpoch == epoch && activeOperation == null

    private fun isCurrent(operation: SaveOperation): Boolean = activeOperation?.id == operation.id

    private fun invalidateAndCleanup() {
        val jobs = synchronized(lifecycleLock) {
            lifecycleEpoch++
            activeOperation = null
            pendingOperation = null
            if (cleanupRequested) return
            cleanupRequested = true
            listOfNotNull(activeSaveJob, activeKeyMutationJob)
        }
        internalState.update { it.copy(selectedPrivateKey = null) }
        jobs.forEach(Job::cancel)
        applicationScope.launch {
            jobs.joinAll()
            scheduleAllTrackedCleanup()
        }
    }

    private suspend fun cleanupAfterSuccessfulSave(fileName: String) {
        trackSessionKey(fileName)
        runCatching { sshKeyStore.delete(fileName) }
            .onSuccess { untrackSessionKey(fileName) }
            .onFailure { scheduleTrackedCleanup(fileName) }
    }

    private fun scheduleTrackedCleanup(fileName: String) {
        applicationScope.launch {
            repeat(CLEANUP_ATTEMPTS) {
                if (runCatching { sshKeyStore.delete(fileName) }.isSuccess) {
                    untrackSessionKey(fileName)
                    return@launch
                }
            }
        }
    }

    private fun scheduleAllTrackedCleanup() {
        synchronized(keyOwnershipLock) {
            sessionOwnedKeys.filterNot(keysBeingPersisted::contains)
        }.forEach(::scheduleTrackedCleanup)
    }

    private fun trackSessionKey(fileName: String) = synchronized(keyOwnershipLock) {
        sessionOwnedKeys += fileName
    }

    private fun untrackSessionKey(fileName: String) = synchronized(keyOwnershipLock) {
        sessionOwnedKeys -= fileName
        keysBeingPersisted -= fileName
    }

    private fun markKeyBeingPersisted(fileName: String) = synchronized(keyOwnershipLock) {
        keysBeingPersisted += fileName
    }

    private fun finishKeyPersistence(fileName: String) = synchronized(keyOwnershipLock) {
        keysBeingPersisted -= fileName
    }

    private fun replacePersistingKey(stagedFileName: String, committedFileName: String) =
        synchronized(keyOwnershipLock) {
            sessionOwnedKeys -= stagedFileName
            keysBeingPersisted -= stagedFileName
            sessionOwnedKeys += committedFileName
            keysBeingPersisted += committedFileName
        }

    private fun NetworkConnection.sanitizeForAuthentication(
        selected: SelectedPrivateKey?,
        existingPrivateKeyFileName: String,
    ): NetworkConnection = when {
        protocol != NetworkProtocol.SFTP -> copy(
            authentication = NetworkAuthentication.PASSWORD,
            privateKeyFileName = "",
            privateKeyPassphrase = "",
            hostKeyFingerprint = "",
        )
        authentication == NetworkAuthentication.PASSWORD -> copy(
            privateKeyFileName = "",
            privateKeyPassphrase = "",
        )
        else -> copy(
            password = "",
            privateKeyFileName = selected?.stagedFileName ?: existingPrivateKeyFileName,
        )
    }

    private inline fun <reified T : Throwable> Throwable.findCause(): T? =
        generateSequence(this) { it.cause }.filterIsInstance<T>().firstOrNull()

    private fun Throwable.actionableMessage(): String = when {
        findCause<HostKeyMismatch>() != null ->
            "The SSH host key doesn't match the trusted fingerprint."
        findCause<FileNotFoundException>() != null ->
            "The private key is missing. Choose it again."
        causeMessages().any { message ->
            message.contains("passphrase", ignoreCase = true) ||
                message.contains("decrypt", ignoreCase = true)
        } ->
            "The private key passphrase is incorrect or missing."
        causeMessages().any { message ->
            message.contains("key", ignoreCase = true) &&
                (
                    message.contains("format", ignoreCase = true) ||
                        message.contains("malformed", ignoreCase = true) ||
                        message.contains("invalid pem", ignoreCase = true)
                    )
        } ->
            "The private key format isn't supported or the file is malformed."
        causeMessages().any { message ->
            message.contains("auth", ignoreCase = true) ||
                message.contains("exhausted available", ignoreCase = true)
        } ->
            "Authentication was rejected. Check your credentials and try again."
        else -> message ?: "Couldn't connect. Check the details and try again."
    }

    private fun Throwable.toSaveError(): SaveState.Error {
        val mismatch = findCause<HostKeyMismatch>()
        return SaveState.Error(
            message = actionableMessage(),
            hostKeyMismatch = mismatch?.let {
                HostKeyMismatchDetails(
                    trustedFingerprint = it.expectedFingerprint,
                    presentedFingerprint = it.presentedFingerprint,
                )
            },
        )
    }

    private fun Throwable.causeMessages(): List<String> =
        generateSequence(this) { it.cause }.mapNotNull(Throwable::message).toList()

    private companion object {
        const val CLEANUP_ATTEMPTS = 3
    }
}
