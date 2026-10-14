package de.privat.kalorienzaehler.data.fooddb

/** Whether the bundled food database can be used yet: its first use copies it out of the app package. */
sealed interface FoodDbStatus {
    /** Being copied and checked in the background ("Lebensmitteldatenbank wird vorbereitet …"). */
    data object Preparing : FoodDbStatus

    data object Ready : FoodDbStatus

    /** The database could not be opened (e.g. the device is out of storage). Own foods keep working. */
    data object Failed : FoodDbStatus
}
