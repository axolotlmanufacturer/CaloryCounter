package de.privat.kalorienzaehler.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.privat.kalorienzaehler.data.fooddb.FoodDao
import de.privat.kalorienzaehler.data.fooddb.FoodDatabase
import de.privat.kalorienzaehler.data.userdb.CustomFoodDao
import de.privat.kalorienzaehler.data.userdb.DiaryDao
import de.privat.kalorienzaehler.data.userdb.FavoriteDao
import de.privat.kalorienzaehler.data.userdb.UserDatabase
import de.privat.kalorienzaehler.data.userdb.WeightDao
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Storage: the settings DataStore, the Room database `user.db` and the bundled `foods.db`. */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            // A damaged settings file means "start over with onboarding", not a crash.
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { context.preferencesDataStoreFile("settings") },
        )

    @Provides
    @Singleton
    fun provideUserDatabase(@ApplicationContext context: Context): UserDatabase =
        // No destructive fallback: a schema change needs an explicit migration.
        Room.databaseBuilder(context, UserDatabase::class.java, UserDatabase.NAME).build()

    @Provides
    @Singleton
    fun provideFoodDatabase(@ApplicationContext context: Context): FoodDatabase =
        // The food database is a read-only copy of an asset, with no user data in it. A new schema version replaces
        // the copy here; a rebuilt asset with the same schema is caught by FoodDbContentGuard before the first query.
        Room.databaseBuilder(context, FoodDatabase::class.java, FoodDatabase.NAME)
            .createFromAsset(FoodDatabase.ASSET_PATH)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideFoodDao(database: FoodDatabase): FoodDao = database.foodDao()

    @Provides
    fun provideFavoriteDao(database: UserDatabase): FavoriteDao = database.favoriteDao()

    @Provides
    fun provideWeightDao(database: UserDatabase): WeightDao = database.weightDao()

    @Provides
    fun provideDiaryDao(database: UserDatabase): DiaryDao = database.diaryDao()

    @Provides
    fun provideCustomFoodDao(database: UserDatabase): CustomFoodDao = database.customFoodDao()
}
