package de.privat.kalorienzaehler.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.privat.kalorienzaehler.data.prefs.DataStoreProfileRepository
import de.privat.kalorienzaehler.data.repository.CustomFoodRepository
import de.privat.kalorienzaehler.data.repository.DiaryRepository
import de.privat.kalorienzaehler.data.repository.ProfileRepository
import de.privat.kalorienzaehler.data.repository.RoomCustomFoodRepository
import de.privat.kalorienzaehler.data.repository.RoomDiaryRepository
import de.privat.kalorienzaehler.data.repository.RoomWeightRepository
import de.privat.kalorienzaehler.data.repository.WeightRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindProfileRepository(impl: DataStoreProfileRepository): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindWeightRepository(impl: RoomWeightRepository): WeightRepository

    @Binds
    @Singleton
    abstract fun bindDiaryRepository(impl: RoomDiaryRepository): DiaryRepository

    @Binds
    @Singleton
    abstract fun bindCustomFoodRepository(impl: RoomCustomFoodRepository): CustomFoodRepository
}
