package dev.anilbeesetti.nextplayer.core.data.repository

import dev.anilbeesetti.nextplayer.core.common.di.ApplicationScope
import dev.anilbeesetti.nextplayer.core.datastore.datasource.VaultPreferencesDataSource
import dev.anilbeesetti.nextplayer.core.model.VaultPreferences
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class LocalVaultRepository @Inject constructor(
    private val vaultPreferencesDataSource: VaultPreferencesDataSource,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : VaultRepository {

    override val vaultPreferences: StateFlow<VaultPreferences> =
        vaultPreferencesDataSource.preferences.stateIn(
            scope = applicationScope,
            started = SharingStarted.Eagerly,
            initialValue = VaultPreferences(),
        )

    override suspend fun setPin(hashedPin: String?) {
        vaultPreferencesDataSource.update { it.copy(pinHash = hashedPin) }
    }

    override suspend fun verifyPin(hashedPin: String): Boolean {
        return vaultPreferences.value.pinHash == hashedPin
    }
}
