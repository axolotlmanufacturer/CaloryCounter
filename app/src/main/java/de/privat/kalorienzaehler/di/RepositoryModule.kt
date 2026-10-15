package de.privat.kalorienzaehler.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.privat.kalorienzaehler.data.fooddb.AssetFoodDbContentGuard
import de.privat.kalorienzaehler.data.fooddb.FoodDbContentGuard
import de.privat.kalorienzaehler.data.prefs.DataStoreProfileRepository
import de.privat.kalorienzaehler.data.repository.CustomFoodRepository
import de.privat.kalorienzaehler.data.repository.DefaultFoodCatalog
import de.privat.kalorienzaehler.data.repository.DiaryRepository
import de.privat.kalorienzaehler.data.repository.FavoriteRepository
import de.privat.kalorienzaehler.data.repository.FoodCatalog
import de.privat.kalorienzaehler.data.repository.FoodRepository
import de.privat.kalorienzaehler.data.repository.ProfileRepository
import de.privat.kalorienzaehler.data.repository.RoomCustomFoodRepository
import de.privat.kalorienzaehler.data.repository.RoomDiaryRepository
import de.privat.kalorienzaehler.data.repository.RoomFavoriteRepository
import de.privat.kalorienzaehler.data.repository.RoomFoodRepository
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

    @Binds
    @Singleton
    abstract fun bindFoodRepository(impl: RoomFoodRepository): FoodRepository

    @Binds
    @Singleton
    abstract fun bindFavoriteRepository(impl: RoomFavoriteRepository): FavoriteRepository

    @Binds
    @Singleton
    abstract fun bindFoodCatalog(impl: DefaultFoodCatalog): FoodCatalog

    @Binds
    abstract fun bindFoodDbContentGuard(impl: AssetFoodDbContentGuard): FoodDbContentGuard
}
