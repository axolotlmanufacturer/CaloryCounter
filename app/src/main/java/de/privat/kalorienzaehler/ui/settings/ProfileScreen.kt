package de.privat.kalorienzaehler.ui.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.privat.kalorienzaehler.R
import de.privat.kalorienzaehler.ui.components.EmptyState
import de.privat.kalorienzaehler.ui.components.TabScaffold

@Composable
fun ProfileScreen(modifier: Modifier = Modifier) {
    TabScaffold(title = stringResource(R.string.nav_profile), modifier = modifier) { innerPadding ->
        EmptyState(
            icon = R.drawable.ic_person_24,
            title = stringResource(R.string.profile_empty_title),
            message = stringResource(R.string.profile_empty_message),
            modifier = Modifier.padding(innerPadding),
        )
    }
}
