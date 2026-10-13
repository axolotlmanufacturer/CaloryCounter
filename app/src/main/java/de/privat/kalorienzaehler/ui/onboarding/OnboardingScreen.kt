package de.privat.kalorienzaehler.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import de.privat.kalorienzaehler.R
import de.privat.kalorienzaehler.domain.profile.DraftEvaluation
import de.privat.kalorienzaehler.domain.profile.ProfileDraft
import de.privat.kalorienzaehler.ui.profile.ActivitySection
import de.privat.kalorienzaehler.ui.profile.BirthDateField
import de.privat.kalorienzaehler.ui.profile.DraftEdit
import de.privat.kalorienzaehler.ui.profile.GoalSection
import de.privat.kalorienzaehler.ui.profile.HeightField
import de.privat.kalorienzaehler.ui.profile.HintText
import de.privat.kalorienzaehler.ui.profile.PlanSummary
import de.privat.kalorienzaehler.ui.profile.SexSection
import de.privat.kalorienzaehler.ui.profile.WeightField
import androidx.compose.ui.text.input.ImeAction

@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel = hiltViewModel()) {
    BackHandler(enabled = viewModel.step != OnboardingStep.WELCOME) { viewModel.back() }
    OnboardingContent(
        step = viewModel.step,
        draft = viewModel.draft,
        evaluation = viewModel.evaluation,
        canContinue = viewModel.canContinue,
        isSaving = viewModel.isSaving,
        saveFailed = viewModel.saveFailed,
        edit = viewModel::edit,
        onNext = viewModel::next,
        onBack = { viewModel.back() },
        onFinish = viewModel::finish,
    )
}

@Composable
fun OnboardingContent(
    step: OnboardingStep,
    draft: ProfileDraft,
    evaluation: DraftEvaluation,
    canContinue: Boolean,
    isSaving: Boolean,
    saveFailed: Boolean,
    edit: DraftEdit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.imePadding(),
            topBar = { if (step != OnboardingStep.WELCOME) ProgressHeader(step) },
            bottomBar = {
                NavigationButtons(
                    step = step,
                    canContinue = canContinue,
                    isSaving = isSaving,
                    onNext = onNext,
                    onBack = onBack,
                    onFinish = onFinish,
                )
            },
        ) { innerPadding ->
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                StepContent(step, draft, evaluation, saveFailed, edit)
            }
        }
    }
}

@Composable
private fun ProgressHeader(step: OnboardingStep) {
    val steps = OnboardingStep.entries
    // The welcome page is not counted: "Schritt 1 von 5" is the first question.
    val current = step.ordinal
    val total = steps.size - 1
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.onboarding_progress, current, total),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(progress = { current.toFloat() / total }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun NavigationButtons(
    step: OnboardingStep,
    canContinue: Boolean,
    isSaving: Boolean,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onFinish: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        if (step != OnboardingStep.WELCOME) {
            OutlinedButton(onClick = onBack, enabled = !isSaving) { Text(stringResource(R.string.action_back)) }
        }
        val isLast = step == OnboardingStep.SUMMARY
        Button(
            onClick = if (isLast) onFinish else onNext,
            enabled = canContinue && !isSaving,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                stringResource(
                    when (step) {
                        OnboardingStep.WELCOME -> R.string.onboarding_welcome_start
                        OnboardingStep.SUMMARY -> R.string.action_done
                        else -> R.string.action_next
                    },
                ),
            )
        }
    }
}

@Composable
private fun StepTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun StepContent(
    step: OnboardingStep,
    draft: ProfileDraft,
    evaluation: DraftEvaluation,
    saveFailed: Boolean,
    edit: DraftEdit,
) {
    when (step) {
        OnboardingStep.WELCOME -> {
            StepTitle(stringResource(R.string.onboarding_welcome_title))
            Text(stringResource(R.string.onboarding_welcome_body), style = MaterialTheme.typography.bodyLarge)
            HintText(stringResource(R.string.onboarding_welcome_hint))
        }
        OnboardingStep.BASICS -> {
            StepTitle(stringResource(R.string.onboarding_basics_title))
            SexSection(draft, edit)
            BirthDateField(draft, evaluation, edit)
        }
        OnboardingStep.BODY -> {
            StepTitle(stringResource(R.string.onboarding_body_title))
            HeightField(draft, evaluation, edit)
            WeightField(draft, evaluation, edit, imeAction = ImeAction.Done)
        }
        OnboardingStep.ACTIVITY -> {
            StepTitle(stringResource(R.string.activity_title))
            HintText(stringResource(R.string.activity_hint))
            ActivitySection(draft, edit)
        }
        OnboardingStep.GOAL -> {
            StepTitle(stringResource(R.string.onboarding_goal_title))
            GoalSection(draft, evaluation, edit)
        }
        OnboardingStep.SUMMARY -> {
            StepTitle(stringResource(R.string.onboarding_summary_title))
            evaluation.plan?.let { PlanSummary(it, draft.energyUnit) }
            HintText(stringResource(R.string.onboarding_summary_disclaimer))
            if (saveFailed) Text(stringResource(R.string.save_failed_message), color = MaterialTheme.colorScheme.error)
        }
    }
}
