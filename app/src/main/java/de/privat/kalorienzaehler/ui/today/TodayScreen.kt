package de.privat.kalorienzaehler.ui.today

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import de.privat.kalorienzaehler.R
import de.privat.kalorienzaehler.ui.components.EmptyState
import de.privat.kalorienzaehler.ui.components.TabScaffold

@Composable
fun TodayScreen(modifier: Modifier = Modifier) {
    TabScaffold(title = stringResource(R.string.nav_today), modifier = modifier) { innerPadding ->
        EmptyState(
            icon = R.drawable.ic_today_24,
            title = stringResource(R.string.today_empty_title),
            message = stringResource(R.string.today_empty_message),
            modifier = Modifier.padding(innerPadding),
        )
    }
}
