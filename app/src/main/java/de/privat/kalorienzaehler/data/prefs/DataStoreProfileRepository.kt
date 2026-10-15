package de.privat.kalorienzaehler.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import de.privat.kalorienzaehler.data.repository.ProfileRepository
import de.privat.kalorienzaehler.domain.model.UserProfile
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class DataStoreProfileRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ProfileRepository {

    override val profile: Flow<UserProfile?> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map(ProfilePreferences::read)
        .distinctUntilChanged()

    override suspend fun save(profile: UserProfile) {
        dataStore.edit { ProfilePreferences.write(it, profile) }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}
