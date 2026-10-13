package de.privat.kalorienzaehler.ui.addfood

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import de.privat.kalorienzaehler.R
import de.privat.kalorienzaehler.domain.diary.CustomFoodDraft
import de.privat.kalorienzaehler.domain.diary.CustomFoodEvaluation
import de.privat.kalorienzaehler.domain.diary.CustomFoodField
import de.privat.kalorienzaehler.domain.diary.PerUnit
import de.privat.kalorienzaehler.ui.diary.BottomActionBar
import de.privat.kalorienzaehler.ui.diary.FullScreenScaffold
import de.privat.kalorienzaehler.ui.diary.WarningList
import de.privat.kalorienzaehler.ui.diary.messageText
import de.privat.kalorienzaehler.ui.navigation.CustomFoodRoute
import de.privat.kalorienzaehler.ui.profile.DecimalField
import de.privat.kalorienzaehler.ui.profile.HintText
import de.privat.kalorienzaehler.ui.profile.SectionTitle
import de.privat.kalorienzaehler.ui.profile.SegmentedChoice

@Composable
fun CustomFoodScreen(
    route: CustomFoodRoute,
    onBack: () -> Unit,
    onSaved: (foodId: Long, isNew: Boolean) -> Unit,
    onDeleted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CustomFoodViewModel = hiltViewModel<CustomFoodViewModel, CustomFoodViewModel.Factory>(
        creationCallback = { factory -> factory.create(route) },
    ),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val failedText = stringResource(R.string.save_failed_message)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is CustomFoodEvent.Saved -> onSaved(event.foodId, event.isNew)
                CustomFoodEvent.Deleted -> onDeleted()
                CustomFoodEvent.Failed -> snackbarHostState.showSnackbar(failedText)
            }
        }
    }
    CustomFoodContent(
        isEditing = viewModel.isEditing,
        isLoaded = viewModel.isLoaded,
        notFound = viewModel.notFound,
        foodName = viewModel.storedName,
        draft = viewModel.draft,
        evaluation = viewModel.evaluation,
        canSave = viewModel.canSave,
        snackbarHostState = snackbarHostState,
        onEdit = viewModel::edit,
        onSelectPerUnit = viewModel::selectPerUnit,
        onSave = viewModel::save,
        onDelete = viewModel::delete,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
fun CustomFoodContent(
    isEditing: Boolean,
    isLoaded: Boolean,
    notFound: Boolean,
    foodName: String?,
    draft: CustomFoodDraft,
    evaluation: CustomFoodEvaluation,
    canSave: Boolean,
    snackbarHostState: SnackbarHostState,
    onEdit: ((CustomFoodDraft) -> CustomFoodDraft) -> Unit,
    onSelectPerUnit: (PerUnit) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    FullScreenScaffold(
        title = stringResource(if (isEditing) R.string.custom_title_edit else R.string.custom_title_new),
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            BottomActionBar {
                Button(onClick = onSave, enabled = canSave, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_save))
                }
            }
        },
        modifier = modifier,
    ) { innerPadding ->
        FormColumn(innerPadding) {
            if (notFound) {
                HintText(stringResource(R.string.custom_not_found))
                return@FormColumn
            }
            if (!isLoaded) return@FormColumn
            TextInputField(
                label = stringResource(R.string.custom_name),
                value = draft.name,
                onValueChange = { text -> onEdit { it.copy(name = text) } },
                error = evaluation.errors[CustomFoodField.NAME],
            )
            TextInputField(
                label = stringResource(R.string.custom_brand),
                value = draft.brand,
                onValueChange = { text -> onEdit { it.copy(brand = text) } },
                error = evaluation.errors[CustomFoodField.BRAND],
            )
            TextInputField(
                label = stringResource(R.string.custom_barcode),
                value = draft.barcode,
                onValueChange = { text -> onEdit { it.copy(barcode = text.filter { char -> char in '0'..'9' }) } },
                error = evaluation.errors[CustomFoodField.BARCODE],
                keyboardType = KeyboardType.Number,
            )
            SectionTitle(stringResource(R.string.custom_per_unit))
            SegmentedChoice(
                options = PerUnit.entries,
                selected = draft.perUnit,
                label = { unit ->
                    stringResource(if (unit == PerUnit.MILLILITER) R.string.custom_per_100ml else R.string.custom_per_100g)
                },
                onSelect = onSelectPerUnit,
            )
            SectionTitle(
                stringResource(
                    if (draft.perUnit == PerUnit.MILLILITER) R.string.custom_values_title_ml else R.string.custom_values_title_g,
                ),
            )
            HintText(stringResource(R.string.custom_values_hint))
            NutrientField(R.string.field_energy_kcal, draft.kcal, CustomFoodField.KCAL, evaluation) { text -> onEdit { it.copy(kcal = text) } }
            NutrientField(R.string.field_fat_g, draft.fat, CustomFoodField.FAT, evaluation) { text -> onEdit { it.copy(fat = text) } }
            NutrientField(R.string.field_carbs_g, draft.carbs, CustomFoodField.CARBS, evaluation) { text -> onEdit { it.copy(carbs = text) } }
            NutrientField(R.string.field_sugar_g, draft.sugar, CustomFoodField.SUGAR, evaluation) { text -> onEdit { it.copy(sugar = text) } }
            NutrientField(R.string.field_fiber_g, draft.fiber, CustomFoodField.FIBER, evaluation) { text -> onEdit { it.copy(fiber = text) } }
            NutrientField(R.string.field_protein_g, draft.protein, CustomFoodField.PROTEIN, evaluation) { text -> onEdit { it.copy(protein = text) } }
            NutrientField(R.string.field_salt_g, draft.salt, CustomFoodField.SALT, evaluation) { text -> onEdit { it.copy(salt = text) } }
            WarningList(evaluation.warnings)
            SectionTitle(stringResource(R.string.custom_serving_title))
            DecimalField(
                label = stringResource(
                    if (draft.perUnit == PerUnit.MILLILITER) R.string.custom_serving_ml else R.string.custom_serving_g,
                ),
                value = draft.servingG,
                onValueChange = { text -> onEdit { it.copy(servingG = text) } },
                error = evaluation.errors[CustomFoodField.SERVING]?.messageText(),
                maxFractionDigits = 1,
                imeAction = ImeAction.Done,
            )
            if (isEditing) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.custom_delete))
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.custom_delete_title)) },
            text = { Text(stringResource(R.string.custom_delete_message, foodName.orEmpty())) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun NutrientField(
    label: Int,
    value: String,
    field: CustomFoodField,
    evaluation: CustomFoodEvaluation,
    onValueChange: (String) -> Unit,
) {
    DecimalField(
        label = stringResource(label),
        value = value,
        onValueChange = onValueChange,
        error = evaluation.errors[field]?.messageText(),
        maxFractionDigits = 2,
    )
}
