package de.privat.kalorienzaehler.testing

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import de.privat.kalorienzaehler.data.repository.CustomFoodRepository
import de.privat.kalorienzaehler.data.repository.DefaultFoodCatalog
import de.privat.kalorienzaehler.data.repository.DiaryRepository
import de.privat.kalorienzaehler.data.repository.FavoriteRepository
import de.privat.kalorienzaehler.data.repository.FoodCatalog
import de.privat.kalorienzaehler.data.repository.FoodRepository
import de.privat.kalorienzaehler.data.repository.ProfileRepository
import de.privat.kalorienzaehler.data.repository.WeightRepository
import de.privat.kalorienzaehler.di.CameraModule
import de.privat.kalorienzaehler.di.ClockModule
import de.privat.kalorienzaehler.di.RepositoryModule
import de.privat.kalorienzaehler.ui.scanner.BarcodeCamera
import java.time.Clock

/** Replaces the DataStore/Room repositories with in-memory fakes in Hilt tests. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [RepositoryModule::class])
abstract class TestRepositoryModule {
    @Binds abstract fun bindProfileRepository(impl: FakeProfileRepository): ProfileRepository

    @Binds abstract fun bindWeightRepository(impl: FakeWeightRepository): WeightRepository

    @Binds abstract fun bindDiaryRepository(impl: FakeDiaryRepository): DiaryRepository

    @Binds abstract fun bindCustomFoodRepository(impl: FakeCustomFoodRepository): CustomFoodRepository

    @Binds abstract fun bindFoodRepository(impl: FakeFoodRepository): FoodRepository

    @Binds abstract fun bindFavoriteRepository(impl: FakeFavoriteRepository): FavoriteRepository

    /** The real catalog (search, ranking) on top of the fakes. */
    @Binds abstract fun bindFoodCatalog(impl: DefaultFoodCatalog): FoodCatalog
}

/** A fixed "today" in Hilt tests. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [ClockModule::class])
object TestClockModule {
    @Provides fun provideClock(): Clock = TEST_CLOCK
}

/** A stand-in for the camera in Hilt tests (see [FakeBarcodeCamera]). */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [CameraModule::class])
object TestCameraModule {
    @Provides fun provideBarcodeCamera(): BarcodeCamera = FakeBarcodeCamera
}
