package de.privat.kalorienzaehler.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.privat.kalorienzaehler.R
import de.privat.kalorienzaehler.domain.energy.EnergyUnit
import de.privat.kalorienzaehler.domain.format.GermanFormat
import de.privat.kalorienzaehler.domain.goal.GoalPlan
import de.privat.kalorienzaehler.domain.goal.GoalProgress
import de.privat.kalorienzaehler.domain.model.GoalType

/**
 * The calculated numbers with a one-line plain-language explanation each (PROMPT.md §7.1), followed by
 * everything the safety rules changed. Used by the onboarding summary and the live preview in Profil.
 */
@Composable
fun PlanSummary(plan: GoalPlan, unit: EnergyUnit, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier.fillMaxWidth()) {
        PlanNumber(
            title = stringResource(R.string.plan_bmr),
            value = energyText(plan.bmrKcal, unit),
            explanation = stringResource(R.string.plan_bmr_explanation),
        )
        PlanNumber(
            title = stringResource(R.string.plan_tdee),
            value = energyText(plan.tdeeKcal, unit),
            explanation = stringResource(R.string.plan_tdee_explanation),
        )
        PlanNumber(
            title = stringResource(R.string.plan_target),
            value = energyText(plan.targetKcal, unit),
            explanation = targetExplanation(plan, unit),
            emphasized = true,
        )
        PlanNumber(
            title = stringResource(R.string.plan_macros),
            value = stringResource(
                R.string.plan_macros_values,
                gramsText(plan.macros.proteinG),
                gramsText(plan.macros.carbsG),
                gramsText(plan.macros.fatG),
            ),
            explanation = stringResource(R.string.plan_macros_explanation),
        )
        when (val progress = plan.progress) {
            is GoalProgress.Projected -> PlanNumber(
                title = stringResource(R.string.plan_goal_date),
                value = stringResource(R.string.plan_goal_date_value, GermanFormat.date(progress.date)),
                explanation = stringResource(R.string.plan_goal_date_explanation),
            )
            GoalProgress.Reached -> PlanNumber(
                title = stringResource(R.string.plan_goal_date),
                value = stringResource(R.string.plan_goal_reached),
                explanation = stringResource(R.string.plan_goal_reached_explanation),
            )
            GoalProgress.NoProjection -> Unit
        }
        plan.notices.forEach { notice -> NoticeCard(notice.messageText()) }
    }
}

@Composable
private fun targetExplanation(plan: GoalPlan, unit: EnergyUnit): String {
    val change = energyText(kotlin.math.abs(plan.dailyEnergyChangeKcal), unit)
    return when (plan.goalType) {
        GoalType.LOSE -> if (plan.dailyEnergyChangeKcal < 0) {
            stringResource(R.string.plan_target_lose, change)
        } else {
            stringResource(R.string.plan_target_maintain)
        }
        GoalType.GAIN -> stringResource(R.string.plan_target_gain, change)
        GoalType.MAINTAIN -> stringResource(R.string.plan_target_maintain)
    }
}

@Composable
private fun PlanNumber(title: String, value: String, explanation: String, emphasized: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = if (emphasized) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
            color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        Text(explanation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Calm container for an explanation; deliberately not an error style (PROMPT.md §6.4 rule 5). */
@Composable
private fun NoticeCard(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(16.dp),
        )
    }
}

