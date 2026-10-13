package de.privat.kalorienzaehler.ui.addfood

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import de.privat.kalorienzaehler.R
import de.privat.kalorienzaehler.domain.diary.AmountUnit
import de.privat.kalorienzaehler.domain.diary.FormError
import de.privat.kalorienzaehler.domain.diary.Meal
import de.privat.kalorienzaehler.domain.diary.NutrientAmounts
import de.privat.kalorienzaehler.domain.energy.EnergyUnit
import de.privat.kalorienzaehler.ui.diary.BottomActionBar
import de.privat.kalorienzaehler.ui.diary.FullScreenScaffold
import de.privat.kalorienzaehler.ui.diary.MealChoice
import de.privat.kalorienzaehler.ui.diary.NutritionTable
import de.privat.kalorienzaehler.ui.diary.dateLabel
import de.privat.kalorienzaehler.ui.diary.gramsOrMissing
import de.privat.kalorienzaehler.ui.diary.label
import de.privat.kalorienzaehler.ui.diary.messageText
import de.privat.kalorienzaehler.ui.navigation.PortionRoute
import de.privat.kalorienzaehler.ui.profile.DecimalField
import de.privat.kalorienzaehler.ui.profile.HintText
import de.privat.kalorienzaehler.ui.profile.SectionTitle
import de.privat.kalorienzaehler.ui.profile.SegmentedChoice
import de.privat.kalorienzaehler.ui.profile.energyText

@Composable
fun PortionScreen(
    route: PortionRoute,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PortionViewModel = hiltViewModel<PortionViewModel, PortionViewModel.Factory>(
        creationCallback = { factory -> factory.create(route) },
    ),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val failedText = stringResource(R.string.save_failed_message)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                FormEvent.SAVED -> onDone()
                FormEvent.FAILED -> snackbarHostState.showSnackbar(failedText)
            }
        }
    }
    PortionContent(
        isEditing = viewModel.isEditing,
        isLoaded = viewModel.isLoaded,
        notFound = viewModel.notFound,
        subtitle = stringResource(R.string.add_subtitle, viewModel.meal.label(), dateLabel(viewModel.date, viewModel.today)),
        subject = viewModel.subject,
        amountText = viewModel.amountText,
        amountError = viewModel.amountError,
        unitIndex = viewModel.unitIndex,
        meal = viewModel.meal,
        energyUnit = viewModel.energyUnit,
        preview = viewModel.preview,
        canSave = viewModel.canSave,
        snackbarHostState = snackbarHostState,
        onAmountChange = viewModel::onAmountChange,
        onSelectUnit = viewModel::selectUnit,
        onSelectMeal = viewModel::selectMeal,
        onSave = viewModel::save,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun PortionContent(
    isEditing: Boolean,
    isLoaded: Boolean,
    notFound: Boolean,
    subtitle: String,
    subject: PortionSubject?,
    amountText: String,
    amountError: FormError?,
    unitIndex: Int,
    meal: Meal,
    energyUnit: EnergyUnit,
    preview: NutrientAmounts?,
    canSave: Boolean,
    snackbarHostState: SnackbarHostState,
    onAmountChange: (String) -> Unit,
    onSelectUnit: (Int) -> Unit,
    onSelectMeal: (Meal) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FullScreenScaffold(
        title = stringResource(if (isEditing) R.string.portion_title_edit else R.string.portion_title),
        subtitle = subtitle,
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            BottomActionBar {
                Button(onClick = onSave, enabled = canSave, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(if (isEditing) R.string.action_save else R.string.portion_add))
                }
            }
        },
        modifier = modifier,
    ) { innerPadding ->
        FormColumn(innerPadding) {
            if (notFound) {
                HintText(stringResource(R.string.portion_not_found))
                return@FormColumn
            }
            if (!isLoaded || subject == null) return@FormColumn

            Column {
                Text(
                    text = subject.name,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                subject.brand?.takeIf { it.isNotBlank() }?.let { brand ->
                    Text(
                        text = brand,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val singleUnit = subject.units.singleOrNull()
            DecimalField(
                label = stringResource(R.string.portion_amount),
                value = amountText,
                onValueChange = onAmountChange,
                error = amountError?.messageText(),
                maxFractionDigits = 2,
                imeAction = ImeAction.Done,
                // With one unit the field names it; with several the chooser below does.
                suffix = singleUnit?.let { unitSuffix(it) },
            )
            if (subject.units.size > 1) {
                SegmentedChoice(
                    options = subject.units.indices.toList(),
                    selected = unitIndex,
                    label = { index -> subject.units[index].key },
                    onSelect = onSelectUnit,
                )
            }

            PreviewCard(preview = preview, energyUnit = energyUnit)

            SectionTitle(stringResource(R.string.field_meal))
            MealChoice(selected = meal, onSelect = onSelectMeal)

            if (subject.showTable) {
                NutritionTable(
                    nutrients = subject.per100,
                    perUnit = subject.perUnit,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** The unit shown inside the amount field; portion labels such as "Portion (30 g)" are shown whole. */
private fun unitSuffix(unit: AmountUnit): String = unit.key

@Composable
private fun PreviewCard(preview: NutrientAmounts?, energyUnit: EnergyUnit, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.portion_preview),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = if (preview == null) stringResource(R.string.value_missing) else energyText(preview.kcal, energyUnit),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = stringResource(
                    R.string.plan_macros_values,
                    gramsOrMissing(preview?.protein),
                    gramsOrMissing(preview?.carbs),
                    gramsOrMissing(preview?.fat),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
