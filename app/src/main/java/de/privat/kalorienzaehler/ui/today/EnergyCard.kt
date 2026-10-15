package de.privat.kalorienzaehler.ui.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.privat.kalorienzaehler.R
import de.privat.kalorienzaehler.domain.diary.EnergyBalance
import de.privat.kalorienzaehler.domain.energy.EnergyUnit
import de.privat.kalorienzaehler.ui.diary.energyNumber
import de.privat.kalorienzaehler.ui.diary.energyUnitLabel
import de.privat.kalorienzaehler.ui.profile.energyText
import de.privat.kalorienzaehler.ui.theme.overTargetColor
import kotlin.math.abs

private const val BASE_RING_DP = 176
private const val MAX_RING_FONT_SCALE = 1.6f
private const val RING_STROKE_DP = 14

/**
 * Ziel − Gegessen = Übrig: a progress ring with the remaining energy in the middle, and target and
 * eaten below. Over the target the ring turns amber (calm, never alarm red). Without a target
 * (no weight known yet) only the eaten energy is shown.
 */
@Composable
fun EnergyCard(
    eatenKcal: Double,
    targetKcal: Double?,
    unit: EnergyUnit,
    modifier: Modifier = Modifier,
) {
    val balance = targetKcal?.let { EnergyBalance(it, eatenKcal) }
    val over = balance?.isOver == true
    val accent = if (over) overTargetColor() else MaterialTheme.colorScheme.primary
    val unitLabel = energyUnitLabel(unit)

    val centerNumber: String
    val centerLabel: String
    val description: String
    if (balance == null) {
        centerNumber = energyNumber(eatenKcal, unit)
        centerLabel = stringResource(R.string.today_ring_eaten, unitLabel)
        description = stringResource(R.string.today_ring_description_no_target, energyText(eatenKcal, unit))
    } else if (over) {
        centerNumber = energyNumber(abs(balance.remainingKcal), unit)
        centerLabel = stringResource(R.string.today_ring_over, unitLabel)
        description = stringResource(
            R.string.today_ring_description_over,
            energyText(abs(balance.remainingKcal), unit),
            energyText(balance.targetKcal, unit),
            energyText(eatenKcal, unit),
        )
    } else {
        centerNumber = energyNumber(balance.remainingKcal, unit)
        centerLabel = stringResource(R.string.today_ring_remaining, unitLabel)
        description = stringResource(
            R.string.today_ring_description_remaining,
            energyText(balance.remainingKcal, unit),
            energyText(balance.targetKcal, unit),
            energyText(eatenKcal, unit),
        )
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = description },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Ring(
                fraction = balance?.fraction?.toFloat() ?: 0f,
                accent = accent,
                number = centerNumber,
                label = centerLabel,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (balance != null) {
                    Stat(stringResource(R.string.today_target), energyText(balance.targetKcal, unit), Modifier.weight(1f))
                }
                Stat(stringResource(R.string.today_eaten), energyText(eatenKcal, unit), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Ring(fraction: Float, accent: androidx.compose.ui.graphics.Color, number: String, label: String) {
    // The ring grows with the font size so that the number always fits inside (up to 200 %).
    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, MAX_RING_FONT_SCALE)
    val track = MaterialTheme.colorScheme.surfaceVariant
    BoxWithConstraints(contentAlignment = Alignment.Center) {
        val ringSize = minOf((BASE_RING_DP * fontScale).dp, maxWidth)
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(ringSize)) {
            Canvas(Modifier.size(ringSize)) {
                val stroke = RING_STROKE_DP.dp.toPx()
                val topLeft = Offset(stroke / 2, stroke / 2)
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
                if (fraction > 0f) {
                    drawArc(
                        color = accent,
                        startAngle = -90f,
                        sweepAngle = 360f * fraction,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = (RING_STROKE_DP + 8).dp),
            ) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}
