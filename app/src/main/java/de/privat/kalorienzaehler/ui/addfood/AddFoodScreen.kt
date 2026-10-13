package de.privat.kalorienzaehler.ui.addfood

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import de.privat.kalorienzaehler.R
import de.privat.kalorienzaehler.domain.diary.CustomFood
import de.privat.kalorienzaehler.domain.diary.PerUnit
import de.privat.kalorienzaehler.ui.diary.FullScreenScaffold
import de.privat.kalorienzaehler.ui.diary.dateLabel
import de.privat.kalorienzaehler.ui.diary.label
import de.privat.kalorienzaehler.ui.navigation.AddFoodRoute
import de.privat.kalorienzaehler.ui.profile.HintText
import de.privat.kalorienzaehler.ui.profile.energyText

/** Lebensmittel hinzufügen: pick one of the user's foods, or go to Schnelleintrag / a new custom food. */
@Composable
fun AddFoodScreen(
    route: AddFoodRoute,
    onBack: () -> Unit,
    onQuickEntry: () -> Unit,
    onCreateCustom: () -> Unit,
    onLogFood: (CustomFood) -> Unit,
    onEditFood: (CustomFood) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddFoodViewModel = hiltViewModel<AddFoodViewModel, AddFoodViewModel.Factory>(
        creationCallback = { factory -> factory.create(route) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AddFoodContent(
        subtitle = stringResource(
            R.string.add_subtitle,
            viewModel.meal.label(),
            dateLabel(viewModel.date, viewModel.today),
        ),
        query = viewModel.query,
        onQueryChange = viewModel::onQueryChange,
        state = state,
        onSelectFilter = viewModel::selectFilter,
        onBack = onBack,
        onQuickEntry = onQuickEntry,
        onCreateCustom = onCreateCustom,
        onLogFood = onLogFood,
        onEditFood = onEditFood,
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddFoodContent(
    subtitle: String,
    query: String,
    onQueryChange: (String) -> Unit,
    state: AddFoodState?,
    onSelectFilter: (AddFilter) -> Unit,
    onBack: () -> Unit,
    onQuickEntry: () -> Unit,
    onCreateCustom: () -> Unit,
    onLogFood: (CustomFood) -> Unit,
    onEditFood: (CustomFood) -> Unit,
    modifier: Modifier = Modifier,
) {
    FullScreenScaffold(
        title = stringResource(R.string.add_title),
        subtitle = subtitle,
        onBack = onBack,
        modifier = modifier,
    ) { innerPadding ->
        val searchFocus = remember { FocusRequester() }
        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = { Text(stringResource(R.string.add_search_label)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search_24), contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(painterResource(R.drawable.ic_close_24), stringResource(R.string.add_search_clear))
                            }
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .focusRequester(searchFocus),
                )
                // The search field takes the focus when the screen opens (PROMPT.md §7.3).
                LaunchedEffect(Unit) { searchFocus.requestFocus() }
            }
            item(key = "actions") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    FilledTonalButton(onClick = onQuickEntry) {
                        Icon(painterResource(R.drawable.ic_bolt_24), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.add_quick_entry), modifier = Modifier.padding(start = 8.dp))
                    }
                    OutlinedButton(onClick = onCreateCustom) {
                        Icon(painterResource(R.drawable.ic_add_24), contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.add_create_custom), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            item(key = "quick-hint") {
                HintText(
                    text = stringResource(R.string.add_quick_entry_hint),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            item(key = "chips") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    FilterChip(
                        selected = state?.selectedFilter == AddFilter.RECENT,
                        onClick = { onSelectFilter(AddFilter.RECENT) },
                        label = { Text(stringResource(R.string.add_chip_recent)) },
                    )
                    FilterChip(
                        selected = state?.selectedFilter == AddFilter.CUSTOM,
                        onClick = { onSelectFilter(AddFilter.CUSTOM) },
                        label = { Text(stringResource(R.string.add_chip_custom)) },
                    )
                }
            }
            if (state != null) {
                if (state.foods.isEmpty()) {
                    item(key = "empty") {
                        HintText(
                            text = stringResource(
                                when (state.mode) {
                                    AddMode.RECENT -> R.string.add_empty_recent
                                    AddMode.CUSTOM -> R.string.add_empty_custom
                                    AddMode.SEARCH -> R.string.add_no_results
                                },
                            ),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
                items(state.foods, key = { it.id }) { food ->
                    FoodRow(
                        food = food,
                        energyText = energyText(food.nutrients.kcal, state.energyUnit),
                        onClick = { onLogFood(food) },
                        onEdit = { onEditFood(food) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FoodRow(food: CustomFood, energyText: String, onClick: () -> Unit, onEdit: () -> Unit) {
    val per100 = stringResource(
        if (food.perUnit == PerUnit.MILLILITER) R.string.food_per_100ml else R.string.food_per_100g,
        energyText,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(food.name, style = MaterialTheme.typography.bodyLarge)
                SourceBadge(stringResource(R.string.food_source_custom))
            }
            val details = listOfNotNull(food.brand?.takeIf { it.isNotBlank() }, per100).joinToString(" · ")
            Text(
                text = details,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(
                painter = painterResource(R.drawable.ic_edit_24),
                contentDescription = stringResource(R.string.food_edit_description, food.name),
            )
        }
    }
}

@Composable
private fun SourceBadge(text: String) {
    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
