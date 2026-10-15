package de.privat.kalorienzaehler.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import de.privat.kalorienzaehler.R
import de.privat.kalorienzaehler.domain.profile.DraftEvaluation
import de.privat.kalorienzaehler.domain.profile.ProfileDraft
import de.privat.kalorienzaehler.ui.components.TabScaffold
import de.privat.kalorienzaehler.ui.profile.ActivitySection
import de.privat.kalorienzaehler.ui.profile.BirthDateField
import de.privat.kalorienzaehler.ui.profile.BodyFatField
import de.privat.kalorienzaehler.ui.profile.DraftEdit
import de.privat.kalorienzaehler.ui.profile.EnergyUnitSection
import de.privat.kalorienzaehler.ui.profile.FormulaSection
import de.privat.kalorienzaehler.ui.profile.GoalSection
import de.privat.kalorienzaehler.ui.profile.HeightField
import de.privat.kalorienzaehler.ui.profile.HintText
import de.privat.kalorienzaehler.ui.profile.MacrosSection
import de.privat.kalorienzaehler.ui.profile.PlanSummary
import de.privat.kalorienzaehler.ui.profile.SectionTitle
import de.privat.kalorienzaehler.ui.profile.SexSection
import de.privat.kalorienzaehler.ui.profile.WeightField
import de.privat.kalorienzaehler.ui.profile.energyText

/** Test tag of the daily target shown in the always-visible bar. */
const val PROFILE_TARGET_TAG = "profile-target"

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val savedText = stringResource(R.string.saved_message)
    val failedText = stringResource(R.string.save_failed_message)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            snackbarHostState.showSnackbar(if (event == ProfileEvent.SAVED) savedText else failedText)
        }
    }
    ProfileContent(
        isLoaded = viewModel.isLoaded,
        draft = viewModel.draft,
        evaluation = viewModel.evaluation,
        canSave = viewModel.canSave,
        edit = viewModel::edit,
        onSave = viewModel::save,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@Composable
fun ProfileContent(
    isLoaded: Boolean,
    draft: ProfileDraft,
    evaluation: DraftEvaluation,
    canSave: Boolean,
    edit: DraftEdit,
    onSave: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    TabScaffold(
        title = stringResource(R.string.nav_profile),
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) { data -> Snackbar(data) } },
        bottomBar = { if (isLoaded) SaveBar(draft, evaluation, canSave, onSave) },
    ) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            if (!isLoaded) {
                HintText(stringResource(R.string.profile_loading_hint))
                return@Column
            }

            SectionTitle(stringResource(R.string.plan_preview_title))
            val plan = evaluation.plan
            if (plan != null) {
                PlanSummary(plan, draft.energyUnit)
            } else {
                HintText(stringResource(R.string.plan_preview_incomplete))
            }
            HorizontalDivider()

            SectionTitle(stringResource(R.string.profile_section_body))
            SexSection(draft, edit)
            BirthDateField(draft, evaluation, edit)
            HeightField(draft, evaluation, edit)
            WeightField(draft, evaluation, edit, hint = stringResource(R.string.profile_weight_hint), imeAction = ImeAction.Next)
            BodyFatField(draft, evaluation, edit)
            HorizontalDivider()

            SectionTitle(stringResource(R.string.profile_section_activity))
            ActivitySection(draft, edit)
            HorizontalDivider()

            SectionTitle(stringResource(R.string.profile_section_goal))
            GoalSection(draft, evaluation, edit)
            HorizontalDivider()

            FormulaSection(draft, evaluation, edit)
            HorizontalDivider()
            MacrosSection(draft, evaluation, edit)
            HorizontalDivider()
            EnergyUnitSection(draft, edit)
        }
    }
}

/** Always visible: today's target at a glance, and the save button once there is something to save. */
@Composable
private fun SaveBar(draft: ProfileDraft, evaluation: DraftEvaluation, canSave: Boolean, onSave: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            val plan = evaluation.plan
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.plan_target), style = MaterialTheme.typography.labelMedium)
                Text(
                    text = if (plan != null) energyText(plan.targetKcal, draft.energyUnit) else "–",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.testTag(PROFILE_TARGET_TAG),
                )
            }
            Button(onClick = onSave, enabled = canSave) { Text(stringResource(R.string.action_save)) }
        }
    }
}
