package de.privat.kalorienzaehler.testing

import android.content.Context
import androidx.annotation.StringRes
import androidx.test.core.app.ApplicationProvider

/** Resolves a string resource the way the app would show it. */
fun string(@StringRes id: Int, vararg args: Any): String =
    ApplicationProvider.getApplicationContext<Context>().getString(id, *args)
