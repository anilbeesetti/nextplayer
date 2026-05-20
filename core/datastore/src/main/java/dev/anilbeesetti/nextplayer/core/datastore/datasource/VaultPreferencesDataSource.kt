package dev.anilbeesetti.nextplayer.core.datastore.datasource

import androidx.datastore.core.DataStore
import dev.anilbeesetti.nextplayer.core.model.VaultPreferences
import javax.inject.Inject

class VaultPreferencesDataSource @Inject constructor(
    private val vaultPreferences: DataStore<VaultPreferences>,
) {
    val preferences = vaultPreferences.data

    suspend fun update(transform: suspend (VaultPreferences) -> VaultPreferences) {
        vaultPreferences.updateData(transform)
    }
}
