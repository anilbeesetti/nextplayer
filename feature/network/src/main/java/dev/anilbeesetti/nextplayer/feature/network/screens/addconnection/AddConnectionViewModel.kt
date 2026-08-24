package dev.anilbeesetti.nextplayer.feature.network.screens.addconnection

import android.net.Uri
import androidx.lifecycle.ViewModel
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

@HiltViewModel(assistedFactory = AddConnectionViewModel.Factory::class)
class AddConnectionViewModel @AssistedInject constructor(
    @Assisted private val connectionId: Long?,
    private val repository: NetworkConnectionRepository,
    private val clientFactory: NetworkClientFactory,
    private val sshKeyStore: SshKeyStore,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(connectionId: Long?): AddConnectionViewModel
    }

    val isEdit: Boolean = connectionId != null

    private val _existingConnection = MutableStateFlow<NetworkConnection?>(null)
    val existingConnection: StateFlow<NetworkConnection?> = _existingConnection.asStateFlow()

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    private val _selectedPrivateKey = MutableStateFlow<SelectedPrivateKey?>(null)
    val selectedPrivateKey: StateFlow<SelectedPrivateKey?> = _selectedPrivateKey.asStateFlow()

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
            viewModelScope.launch { _existingConnection.value = repository.getConnection(connectionId) }
        }
    }

    fun stagePrivateKey(uri: Uri) {
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
                    val previous = _selectedPrivateKey.value
                    trackSessionKey(staged.fileName)
                    _selectedPrivateKey.value = SelectedPrivateKey(staged.fileName, staged.displayName)
                    if (previous != null && previous.stagedFileName != staged.fileName) {
                        runCatching { sshKeyStore.delete(previous.stagedFileName) }
                            .onSuccess { untrackSessionKey(previous.stagedFileName) }
                            .onFailure {
                                _saveState.value = SaveState.Error(it.actionableMessage())
                                scheduleTrackedCleanup(previous.stagedFileName)
                            }
                    }
                }
                .onFailure { _saveState.value = SaveState.Error(it.actionableMessage()) }
        }
    }

    fun removeSelectedPrivateKey() {
        if (keyMutationBlocked()) return
        val selected = _selectedPrivateKey.value ?: return
        val mutationEpoch = lifecycleEpoch
        activeKeyMutationJob = viewModelScope.launch {
            runCatching { sshKeyStore.delete(selected.stagedFileName) }
                .onSuccess {
                    untrackSessionKey(selected.stagedFileName)
                    if (keyMutationMayComplete(mutationEpoch) && _selectedPrivateKey.value == selected) {
                        _selectedPrivateKey.value = null
                    }
                }
                .onFailure {
                    if (keyMutationMayComplete(mutationEpoch)) {
                        _saveState.value = SaveState.Error(it.actionableMessage())
                    }
                }
        }
    }

    /** Tests [connection] by connecting, and persists it (with the existing id when editing) on success. */
    fun testAndSave(connection: NetworkConnection) {
        if (cleanupRequested || activeOperation != null || activeKeyMutationJob?.isActive == true) return
        val selected = _selectedPrivateKey.value
        val existingPrivateKeyFileName = _existingConnection.value
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

    fun acceptHostKey() {
        val confirmation = (_saveState.value as? SaveState.ConfirmHostKey)?.confirmation ?: return
        val pending = pendingOperation ?: return
        val retry = pending.copy(draft = pending.draft.copy(hostKeyFingerprint = confirmation.fingerprint))
        activeOperation = retry
        pendingOperation = null
        startSave(retry)
    }

    fun rejectHostKey() {
        if (_saveState.value !is SaveState.ConfirmHostKey) return
        pendingOperation = null
        activeOperation = null
        _saveState.value = SaveState.Idle
    }

    fun clearError() {
        if (_saveState.value is SaveState.Error) _saveState.value = SaveState.Idle
    }

    fun cancel() {
        invalidateAndCleanup()
    }

    override fun onCleared() {
        isCleared = true
        invalidateAndCleanup()
        super.onCleared()
    }

    private fun startSave(operation: SaveOperation) {
        _saveState.value = SaveState.Testing
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
            _saveState.value = SaveState.Idle
            return
        }

        val hostConfirmation = error.findCause<HostKeyConfirmationRequired>()
        if (hostConfirmation != null) {
            pendingOperation = operation
            _saveState.value = SaveState.ConfirmHostKey(
                HostKeyConfirmation(
                    host = hostConfirmation.host,
                    port = hostConfirmation.port,
                    algorithm = hostConfirmation.algorithm,
                    fingerprint = hostConfirmation.fingerprint,
                ),
            )
        } else {
            pendingOperation = null
            activeOperation = null
            _saveState.value = error.toSaveError()
        }
    }

    private suspend fun persist(operation: SaveOperation) {
        val draft = operation.draft
        val oldKey = _existingConnection.value
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
            if (_selectedPrivateKey.value == selected) _selectedPrivateKey.value = null
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
        if (_selectedPrivateKey.value == unusedStage) _selectedPrivateKey.value = null
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
        _selectedPrivateKey.value = null
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
                (message.contains("format", ignoreCase = true) ||
                    message.contains("malformed", ignoreCase = true) ||
                    message.contains("invalid pem", ignoreCase = true))
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
