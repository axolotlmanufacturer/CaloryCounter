package de.privat.kalorienzaehler

import android.app.Application
import android.os.StrictMode
import dagger.hilt.android.HiltAndroidApp
import de.privat.kalorienzaehler.data.repository.FoodRepository
import javax.inject.Inject

@HiltAndroidApp
class KalorienzaehlerApp : Application() {

    @Inject lateinit var foodRepository: FoodRepository

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) enableStrictMode()
        // The first start copies the food database out of the app package: do it in the background, during onboarding.
        foodRepository.prepare()
    }

    /**
     * Debug builds log disk and network access on the main thread, so accidental
     * main-thread database work shows up early (PROMPT.md §8).
     */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedSqlLiteObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build(),
        )
    }
}
