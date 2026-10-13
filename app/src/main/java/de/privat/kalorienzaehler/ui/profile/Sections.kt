package de.privat.kalorienzaehler.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import de.privat.kalorienzaehler.R
import de.privat.kalorienzaehler.domain.energy.EnergyUnit
import de.privat.kalorienzaehler.domain.format.DecimalInput
import de.privat.kalorienzaehler.domain.format.GermanFormat
import de.privat.kalorienzaehler.domain.goal.GoalPlanner
import de.privat.kalorienzaehler.domain.model.ActivityLevel
import de.privat.kalorienzaehler.domain.model.BmrFormula
import de.privat.kalorienzaehler.domain.model.GoalType
import de.privat.kalorienzaehler.domain.model.Sex
import de.privat.kalorienzaehler.domain.profile.DraftEvaluation
import de.privat.kalorienzaehler.domain.profile.DraftField
import de.privat.kalorienzaehler.domain.profile.MacroMode
import de.privat.kalorienzaehler.domain.profile.ProfileDraft

// Form sections shared by the onboarding steps and the Profil screen. Each reads the draft and its
// evaluation and reports edits as transformations of the draft.

@Composable
private fun DraftEvaluation.errorText(field: DraftField): String? = errors[field]?.messageText(field)

@Composable
fun SexSection(draft: ProfileDraft, edit: DraftEdit, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        SectionTitle(stringResource(R.string.field_sex))
        SegmentedChoice(
            options = Sex.entries,
            selected = draft.sex,
            label = { stringResource(if (it == Sex.MALE) R.string.sex_male else R.string.sex_female) },
            onSelect = { sex -> edit { it.copy(sex = sex) } },
        )
        HintText(stringResource(R.string.field_sex_hint))
    }
}

@Composable
fun BirthDateField(draft: ProfileDraft, evaluation: DraftEvaluation, edit: DraftEdit, modifier: Modifier = Modifier) {
    DateField(
        label = stringResource(R.string.field_birth_date),
        placeholder = stringResource(R.string.field_birth_date_hint),
        value = draft.birthDate,
        onValueChange = { text -> edit { it.copy(birthDate = text) } },
        error = evaluation.errorText(DraftField.BIRTH_DATE),
        modifier = modifier,
    )
}

@Composable
fun HeightField(draft: ProfileDraft, evaluation: DraftEvaluation, edit: DraftEdit, modifier: Modifier = Modifier) {
    DecimalField(
        label = stringResource(R.string.field_height),
        value = draft.heightCm,
        onValueChange = { text -> edit { it.copy(heightCm = text) } },
        error = evaluation.errorText(DraftField.HEIGHT),
        modifier = modifier,
    )
}

@Composable
fun WeightField(
    draft: ProfileDraft,
    evaluation: DraftEvaluation,
    edit: DraftEdit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    imeAction: ImeAction = ImeAction.Next,
) {
    DecimalField(
        label = stringResource(R.string.field_weight),
        value = draft.weightKg,
        onValueChange = { text -> edit { it.copy(weightKg = text) } },
        error = evaluation.errorText(DraftField.WEIGHT),
        hint = hint,
        imeAction = imeAction,
        modifier = modifier,
    )
}

@Composable
fun BodyFatField(draft: ProfileDraft, evaluation: DraftEvaluation, edit: DraftEdit, modifier: Modifier = Modifier) {
    DecimalField(
        label = stringResource(R.string.field_body_fat),
        value = draft.bodyFatPercent,
        onValueChange = { text -> edit { it.copy(bodyFatPercent = text) } },
        error = evaluation.errorText(DraftField.BODY_FAT),
        hint = stringResource(R.string.field_body_fat_hint),
        modifier = modifier,
    )
}

@Composable
fun ActivitySection(draft: ProfileDraft, edit: DraftEdit, modifier: Modifier = Modifier) {
    RadioChoice(
        options = ActivityLevel.entries,
        selected = draft.activity,
        label = { stringResource(it.labelRes()) },
        onSelect = { level -> edit { it.copy(activity = level) } },
        modifier = modifier,
    )
}

private fun ActivityLevel.labelRes(): Int = when (this) {
    ActivityLevel.SEDENTARY -> R.string.activity_sedentary
    ActivityLevel.LIGHT -> R.string.activity_light
    ActivityLevel.MODERATE -> R.string.activity_moderate
    ActivityLevel.HIGH -> R.string.activity_high
    ActivityLevel.EXTREME -> R.string.activity_extreme
}

@Composable
fun GoalSection(draft: ProfileDraft, evaluation: DraftEvaluation, edit: DraftEdit, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier) {
        SegmentedChoice(
            options = GoalType.entries.sortedBy { listOf(GoalType.LOSE, GoalType.MAINTAIN, GoalType.GAIN).indexOf(it) },
            selected = draft.goalType,
            label = {
                stringResource(
                    when (it) {
                        GoalType.LOSE -> R.string.goal_lose
                        GoalType.MAINTAIN -> R.string.goal_maintain
                        GoalType.GAIN -> R.string.goal_gain
                    },
                )
            },
            enabled = { type ->
                type == GoalType.MAINTAIN || (evaluation.goalAllowed && (type != GoalType.LOSE || evaluation.loseOffered))
            },
            onSelect = { type -> edit { it.withGoalType(type) } },
        )
        when {
            !evaluation.goalAllowed -> HintText(stringResource(R.string.goal_not_allowed_hint))
            !evaluation.loseOffered -> HintText(stringResource(R.string.goal_lose_not_offered_hint))
        }

        val goalApplies = draft.goalType != GoalType.MAINTAIN && evaluation.goalAllowed &&
            (draft.goalType != GoalType.LOSE || evaluation.loseOffered)
        if (goalApplies) {
            SectionTitle(stringResource(R.string.field_goal_rate))
            ChipChoice(
                options = if (draft.goalType == GoalType.LOSE) GoalPlanner.LOSE_RATES_KG else GoalPlanner.GAIN_RATES_KG,
                selected = draft.weeklyRateKg,
                label = { stringResource(R.string.goal_rate_chip, GermanFormat.upTo(it, 2)) },
                onSelect = { rate -> edit { it.copy(weeklyRateKg = rate) } },
            )
            DecimalField(
                label = stringResource(R.string.field_goal_weight),
                value = draft.goalWeightKg,
                onValueChange = { text -> edit { it.copy(goalWeightKg = text) } },
                error = evaluation.errorText(DraftField.GOAL_WEIGHT),
                hint = minGoalWeightHint(DecimalInput.parse(draft.heightCm)),
                imeAction = ImeAction.Done,
            )
        }
    }
}

@Composable
fun FormulaSection(draft: ProfileDraft, evaluation: DraftEvaluation, edit: DraftEdit, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
        SectionTitle(stringResource(R.string.section_formula))
        RadioChoice(
            options = BmrFormula.entries,
            selected = draft.formula,
            label = {
                stringResource(if (it == BmrFormula.MIFFLIN_ST_JEOR) R.string.formula_mifflin else R.string.formula_katch)
            },
            onSelect = { formula -> edit { it.copy(formula = formula) } },
        )
        evaluation.errorText(DraftField.FORMULA)?.let { HintText(it) }
    }
}

@Composable
fun MacrosSection(draft: ProfileDraft, evaluation: DraftEvaluation, edit: DraftEdit, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        SectionTitle(stringResource(R.string.section_macros))
        RadioChoice(
            options = MacroMode.entries,
            selected = draft.macroMode,
            label = {
                stringResource(
                    when (it) {
                        MacroMode.DEFAULT -> R.string.macro_mode_default
                        MacroMode.PERCENT -> R.string.macro_mode_percent
                        MacroMode.PER_KG -> R.string.macro_mode_per_kg
                    },
                )
            },
            onSelect = { mode -> edit { it.copy(macroMode = mode) } },
        )
        when (draft.macroMode) {
            MacroMode.DEFAULT -> Unit
            MacroMode.PERCENT -> {
                DecimalField(
                    label = stringResource(R.string.macro_protein_percent),
                    value = draft.proteinPercent,
                    onValueChange = { text -> edit { it.copy(proteinPercent = text) } },
                )
                DecimalField(
                    label = stringResource(R.string.macro_carbs_percent),
                    value = draft.carbsPercent,
                    onValueChange = { text -> edit { it.copy(carbsPercent = text) } },
                )
                DecimalField(
                    label = stringResource(R.string.macro_fat_percent),
                    value = draft.fatPercent,
                    onValueChange = { text -> edit { it.copy(fatPercent = text) } },
                    error = evaluation.errorText(DraftField.MACROS),
                    imeAction = ImeAction.Done,
                )
            }
            MacroMode.PER_KG -> {
                DecimalField(
                    label = stringResource(R.string.macro_protein_per_kg),
                    value = draft.proteinGPerKg,
                    onValueChange = { text -> edit { it.copy(proteinGPerKg = text) } },
                    maxFractionDigits = 2,
                )
                DecimalField(
                    label = stringResource(R.string.macro_fat_per_kg),
                    value = draft.fatGPerKg,
                    onValueChange = { text -> edit { it.copy(fatGPerKg = text) } },
                    maxFractionDigits = 2,
                    error = evaluation.errorText(DraftField.MACROS),
                    hint = stringResource(R.string.macro_carbs_remainder_hint),
                    imeAction = ImeAction.Done,
                )
            }
        }
    }
}

@Composable
fun EnergyUnitSection(draft: ProfileDraft, edit: DraftEdit, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = modifier) {
        SectionTitle(stringResource(R.string.section_energy_unit))
        RadioChoice(
            options = EnergyUnit.entries,
            selected = draft.energyUnit,
            label = { stringResource(if (it == EnergyUnit.KCAL) R.string.energy_unit_kcal else R.string.energy_unit_kj) },
            onSelect = { unit -> edit { it.copy(energyUnit = unit) } },
        )
    }
}
