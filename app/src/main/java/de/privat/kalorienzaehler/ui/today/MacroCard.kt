package de.privat.kalorienzaehler.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.privat.kalorienzaehler.R
import de.privat.kalorienzaehler.domain.diary.DiaryTotals
import de.privat.kalorienzaehler.domain.diary.MacroProgress
import de.privat.kalorienzaehler.domain.nutrition.MacroTargets
import de.privat.kalorienzaehler.ui.profile.HintText
import de.privat.kalorienzaehler.ui.profile.gramsText
import de.privat.kalorienzaehler.ui.theme.overTargetColor

/**
 * Three progress bars: protein, carbs and fat against the daily macro targets. Without targets only
 * the eaten grams are shown. Macros that entries do not state are not guessed: the hint under the
 * bars says the sums are incomplete.
 */
@Composable
fun MacroCard(totals: DiaryTotals, targets: MacroTargets?, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
            MacroRow(stringResource(R.string.macro_protein), totals.proteinG, targets?.proteinG)
            MacroRow(stringResource(R.string.macro_carbs), totals.carbsG, targets?.carbsG)
            MacroRow(stringResource(R.string.macro_fat), totals.fatG, targets?.fatG)
            if (totals.entriesWithoutAllMacros > 0) {
                HintText(
                    pluralStringResource(
                        R.plurals.today_incomplete_macros,
                        totals.entriesWithoutAllMacros,
                        totals.entriesWithoutAllMacros,
                    ),
                )
            }
        }
    }
}

@Composable
private fun MacroRow(label: String, eatenG: Double, targetG: Double?) {
    val progress = targetG?.let { MacroProgress(eatenG, it) }
    val color = if (progress?.isOver == true) overTargetColor() else MaterialTheme.colorScheme.primary
    val value = if (targetG == null) {
        gramsText(eatenG)
    } else {
        stringResource(R.string.macro_progress_value, gramsText(eatenG), gramsText(targetG))
    }
    Column {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (progress != null) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { progress.fraction.toFloat() },
                color = color,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
            )
        }
    }
}
