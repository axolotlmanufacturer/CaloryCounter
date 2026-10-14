package de.privat.kalorienzaehler.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.privat.kalorienzaehler.R
import de.privat.kalorienzaehler.domain.diary.DiaryEntry
import de.privat.kalorienzaehler.domain.diary.Meal
import de.privat.kalorienzaehler.domain.energy.EnergyUnit
import de.privat.kalorienzaehler.ui.diary.detailLine
import de.privat.kalorienzaehler.ui.diary.displayName
import de.privat.kalorienzaehler.ui.diary.label
import de.privat.kalorienzaehler.ui.profile.energyText

private const val SWIPE_THRESHOLD_DP = 96

/** Meal name with its energy sum and the "+" button that adds food to the meal. */
@Composable
fun MealHeader(
    meal: Meal,
    kcal: Double,
    unit: EnergyUnit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mealName = meal.label()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = mealName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = energyText(kcal, unit),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onAdd) {
            Icon(
                painter = painterResource(R.drawable.ic_add_24),
                contentDescription = stringResource(R.string.today_add_to_meal, mealName),
            )
        }
    }
}

/** A logged food: tap to change amount or meal, swipe to delete (TalkBack gets a "Löschen" action instead). */
@Composable
fun EntryRow(
    entry: DiaryEntry,
    unit: EnergyUnit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onSwipingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnDelete by rememberUpdatedState(onDelete)
    val currentOnSwipingChange by rememberUpdatedState(onSwipingChange)
    // Deliberately not rememberSwipeToDismissBoxState(): that state is saved per list item and would
    // bring a restored row back in its swiped-away position when a delete is undone.
    val density = LocalDensity.current
    val dismissState = remember(density) {
        SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled) { with(density) { SWIPE_THRESHOLD_DP.dp.toPx() } }
    }
    // While a row is dragged, the pager that changes the day must stay out of the gesture.
    val swiping = dismissState.dismissDirection != SwipeToDismissBoxValue.Settled
    LaunchedEffect(swiping) { currentOnSwipingChange(swiping) }
    DisposableEffect(Unit) { onDispose { currentOnSwipingChange(false) } }
    // Fires once the swipe has settled at the end: the row is deleted and leaves the list.
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) currentOnDelete()
    }
    val deleteLabel = stringResource(R.string.action_delete)
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(end = 24.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete_24),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier = modifier,
    ) {
        Surface(
            onClick = onClick,
            color = MaterialTheme.colorScheme.surface,
            // Screen readers cannot swipe: the row itself offers "Löschen" (it is the node they focus).
            modifier = Modifier.semantics {
                customActions = listOf(CustomAccessibilityAction(deleteLabel) { currentOnDelete(); true })
            },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.displayName(), style = MaterialTheme.typography.bodyLarge)
                    entry.detailLine()?.let { detail ->
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = energyText(entry.nutrients.kcal, unit),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

/** "Von gestern kopieren (3)": copies the same meal of the day before. */
@Composable
fun CopyFromPreviousDayButton(
    count: Int,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(onClick = onClick, modifier = modifier.padding(start = 8.dp)) {
        Icon(
            painter = painterResource(R.drawable.ic_content_copy_24),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(if (isToday) R.string.today_copy_yesterday else R.string.today_copy_previous_day, count),
        )
    }
}
