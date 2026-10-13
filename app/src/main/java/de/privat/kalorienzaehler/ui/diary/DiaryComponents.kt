package de.privat.kalorienzaehler.ui.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.privat.kalorienzaehler.R
import de.privat.kalorienzaehler.domain.diary.FoodWarning
import de.privat.kalorienzaehler.domain.diary.Meal
import de.privat.kalorienzaehler.domain.diary.NutrientsPer100
import de.privat.kalorienzaehler.domain.diary.PerUnit
import de.privat.kalorienzaehler.domain.energy.Energy
import de.privat.kalorienzaehler.domain.format.GermanFormat
import de.privat.kalorienzaehler.ui.profile.ChipChoice
import de.privat.kalorienzaehler.ui.profile.SectionTitle
import de.privat.kalorienzaehler.ui.theme.overTargetColor

/**
 * Scaffold for the full-screen steps of logging food. The top bar grows with the text instead of
 * having a fixed height, so long titles stay readable at 200 % font size.
 */
@Composable
fun FullScreenScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        // The top bar pads for the status bar itself; the content only has to avoid the navigation
        // bar and the keyboard (a bottom bar pads for them on its own, see BottomActionBar).
        contentWindowInsets = WindowInsets.navigationBars.union(WindowInsets.ime),
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .padding(horizontal = 4.dp),
                ) {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back_24), stringResource(R.string.action_back))
                    }
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.semantics { heading() },
                        )
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    actions()
                }
            }
        },
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        content = content,
    )
}

/** Choose the meal (Frühstück … Snacks). */
@Composable
fun MealChoice(selected: Meal, onSelect: (Meal) -> Unit, modifier: Modifier = Modifier) {
    ChipChoice(
        options = Meal.entries,
        selected = selected,
        label = { meal -> meal.label() },
        onSelect = onSelect,
        modifier = modifier,
    )
}

/** Hints about values that do not add up. Calm amber, and they never keep anyone from saving. */
@Composable
fun WarningList(warnings: List<FoodWarning>, modifier: Modifier = Modifier) {
    if (warnings.isEmpty()) return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.warning_title),
                style = MaterialTheme.typography.titleSmall,
                color = overTargetColor(),
                modifier = Modifier.semantics { heading() },
            )
            warnings.forEach { warning ->
                Text(text = warning.messageText(), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** The Nährwerttabelle in German label style, per 100 g or 100 ml. Unknown values read "–". */
@Composable
fun NutritionTable(nutrients: NutrientsPer100, perUnit: PerUnit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionTitle(
            text = stringResource(
                if (perUnit == PerUnit.MILLILITER) R.string.nutrition_table_ml else R.string.nutrition_table_g,
            ),
            modifier = Modifier.padding(bottom = 8.dp),
        )
        NutritionRow(
            label = stringResource(R.string.nutrition_energy),
            value = stringResource(
                R.string.nutrition_energy_value,
                stringResource(R.string.unit_kj_value, GermanFormat.wholeNumber(Energy.kcalToKj(nutrients.kcal))),
                stringResource(R.string.unit_kcal_value, GermanFormat.wholeNumber(nutrients.kcal)),
            ),
        )
        NutritionRow(stringResource(R.string.nutrition_fat), gramsOrMissing(nutrients.fat))
        NutritionRow(stringResource(R.string.nutrition_carbs), gramsOrMissing(nutrients.carbs))
        NutritionRow(stringResource(R.string.nutrition_sugar), gramsOrMissing(nutrients.sugar), indented = true)
        NutritionRow(stringResource(R.string.nutrition_fiber), gramsOrMissing(nutrients.fiber))
        NutritionRow(stringResource(R.string.nutrition_protein), gramsOrMissing(nutrients.protein))
        NutritionRow(stringResource(R.string.nutrition_salt), gramsOrMissing(nutrients.salt), divider = false)
    }
}

/** Grams with one decimal (the detail views of PROMPT.md), or "–" when unknown. */
@Composable
fun gramsOrMissing(grams: Double?): String =
    if (grams == null) {
        stringResource(R.string.value_missing)
    } else {
        stringResource(R.string.unit_g_value, GermanFormat.decimal(grams, 1))
    }

@Composable
private fun NutritionRow(label: String, value: String, indented: Boolean = false, divider: Boolean = true) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .padding(start = if (indented) 16.dp else 0.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.End,
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(start = 12.dp),
        )
    }
    if (divider) HorizontalDivider()
}

/** The save button at the bottom of a form; it stays above the keyboard and the navigation bar. */
@Composable
fun BottomActionBar(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(tonalElevation = 3.dp, modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(16.dp),
        ) {
            content()
        }
    }
}
