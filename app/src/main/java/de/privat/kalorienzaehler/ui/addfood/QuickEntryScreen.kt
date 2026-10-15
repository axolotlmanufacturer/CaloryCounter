package de.privat.kalorienzaehler.ui.addfood

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import de.privat.kalorienzaehler.R
import de.privat.kalorienzaehler.domain.diary.Meal
import de.privat.kalorienzaehler.domain.diary.QuickEntryDraft
import de.privat.kalorienzaehler.domain.diary.QuickEntryEvaluation
import de.privat.kalorienzaehler.domain.diary.QuickEntryField
import de.privat.kalorienzaehler.ui.diary.BottomActionBar
import de.privat.kalorienzaehler.ui.diary.FullScreenScaffold
import de.privat.kalorienzaehler.ui.diary.MealChoice
import de.privat.kalorienzaehler.ui.diary.WarningList
import de.privat.kalorienzaehler.ui.diary.dateLabel
import de.privat.kalorienzaehler.ui.diary.label
import de.privat.kalorienzaehler.ui.navigation.QuickEntryRoute
import de.privat.kalorienzaehler.ui.profile.DecimalField
import de.privat.kalorienzaehler.ui.profile.HintText
import de.privat.kalorienzaehler.ui.profile.SectionTitle
import de.privat.kalorienzaehler.ui.diary.messageText

@Composable
fun QuickEntryScreen(
    route: QuickEntryRoute,
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QuickEntryViewModel = hiltViewModel<QuickEntryViewModel, QuickEntryViewModel.Factory>(
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
    val defaultLabel = stringResource(R.string.entry_quick_default_label)
    QuickEntryContent(
        isEditing = viewModel.isEditing,
        isLoaded = viewModel.isLoaded,
        notFound = viewModel.notFound,
        subtitle = stringResource(R.string.add_subtitle, viewModel.meal.label(), dateLabel(viewModel.date, viewModel.today)),
        draft = viewModel.draft,
        evaluation = viewModel.evaluation,
        meal = viewModel.meal,
        canSave = viewModel.canSave,
        snackbarHostState = snackbarHostState,
        onEdit = viewModel::edit,
        onSelectMeal = viewModel::selectMeal,
        onSave = { viewModel.save(defaultLabel) },
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun QuickEntryContent(
    isEditing: Boolean,
    isLoaded: Boolean,
    notFound: Boolean,
    subtitle: String,
    draft: QuickEntryDraft,
    evaluation: QuickEntryEvaluation,
    meal: Meal,
    canSave: Boolean,
    snackbarHostState: SnackbarHostState,
    onEdit: ((QuickEntryDraft) -> QuickEntryDraft) -> Unit,
    onSelectMeal: (Meal) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FullScreenScaffold(
        title = stringResource(if (isEditing) R.string.quick_title_edit else R.string.quick_title),
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
                HintText(stringResource(R.string.quick_not_found))
                return@FormColumn
            }
            if (!isLoaded) return@FormColumn
            TextInputField(
                label = stringResource(R.string.quick_label),
                value = draft.label,
                onValueChange = { text -> onEdit { it.copy(label = text) } },
                error = evaluation.errors[QuickEntryField.LABEL],
            )
            HintText(stringResource(R.string.quick_label_hint))
            DecimalField(
                label = stringResource(R.string.field_energy_kcal),
                value = draft.kcal,
                onValueChange = { text -> onEdit { it.copy(kcal = text) } },
                error = evaluation.errors[QuickEntryField.KCAL]?.messageText(),
            )
            SectionTitle(stringResource(R.string.quick_macros_title))
            HintText(stringResource(R.string.quick_macros_hint))
            DecimalField(
                label = stringResource(R.string.field_protein_g),
                value = draft.protein,
                onValueChange = { text -> onEdit { it.copy(protein = text) } },
                error = evaluation.errors[QuickEntryField.PROTEIN]?.messageText(),
            )
            DecimalField(
                label = stringResource(R.string.field_carbs_g),
                value = draft.carbs,
                onValueChange = { text -> onEdit { it.copy(carbs = text) } },
                error = evaluation.errors[QuickEntryField.CARBS]?.messageText(),
            )
            DecimalField(
                label = stringResource(R.string.field_fat_g),
                value = draft.fat,
                onValueChange = { text -> onEdit { it.copy(fat = text) } },
                error = evaluation.errors[QuickEntryField.FAT]?.messageText(),
                imeAction = ImeAction.Done,
            )
            WarningList(evaluation.warnings)
            SectionTitle(stringResource(R.string.field_meal))
            MealChoice(selected = meal, onSelect = onSelectMeal)
            Spacer(Modifier.height(8.dp))
        }
    }
}
