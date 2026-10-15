package de.privat.kalorienzaehler.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.privat.kalorienzaehler.ui.scanner.BarcodeCamera
import de.privat.kalorienzaehler.ui.scanner.CameraXBarcodeCamera

/** The camera of the barcode scanner: CameraX + zxing-cpp. Tests replace it (there is no camera in a unit test). */
@Module
@InstallIn(SingletonComponent::class)
object CameraModule {

    @Provides
    fun provideBarcodeCamera(): BarcodeCamera = CameraXBarcodeCamera
}
