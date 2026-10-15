package de.privat.kalorienzaehler.data.repository

import de.privat.kalorienzaehler.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

/** The user's profile and settings. */
interface ProfileRepository {

    /** The saved profile; `null` until onboarding has been completed. */
    val profile: Flow<UserProfile?>

    suspend fun save(profile: UserProfile)

    suspend fun clear()
}
