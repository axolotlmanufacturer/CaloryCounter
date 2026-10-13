package de.privat.kalorienzaehler.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.privat.kalorienzaehler.R
import de.privat.kalorienzaehler.domain.diary.DiaryEntry
import de.privat.kalorienzaehler.domain.diary.Meal
import de.privat.kalorienzaehler.ui.LoadingScreen
import de.privat.kalorienzaehler.ui.components.TabScaffold
import de.privat.kalorienzaehler.ui.diary.dateLabel
import de.privat.kalorienzaehler.ui.diary.label
import de.privat.kalorienzaehler.ui.profile.HintText
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** The page of the pager that shows "today"; one page per day, 100 years either way. */
internal const val TODAY_PAGE = 36_500
internal const val PAGE_COUNT = 2 * TODAY_PAGE + 1

internal fun pageToDate(page: Int, today: LocalDate): LocalDate = today.plusDays((page - TODAY_PAGE).toLong())

internal fun dateToPage(date: LocalDate, today: LocalDate): Int =
    (TODAY_PAGE + (date.toEpochDay() - today.toEpochDay())).toInt().coerceIn(0, PAGE_COUNT - 1)

/** Heute: the diary of one day, swipe or arrows to change the day. */
@Composable
fun TodayScreen(
    onAddFood: (LocalDate, Meal) -> Unit,
    onEditEntry: (DiaryEntry) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val undoLabel = stringResource(R.string.action_undo)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            // Each message replaces the one before; the host queues nothing behind a stale one.
            snackbarHostState.currentSnackbarData?.dismiss()
            scope.launch {
                when (event) {
                    is TodayEvent.EntryDeleted -> {
                        val result = snackbarHostState.showSnackbar(
                            message = resources.getString(R.string.today_entry_deleted),
                            actionLabel = undoLabel,
                            duration = SnackbarDuration.Long,
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.restore(event.entry)
                    }
                    is TodayEvent.MealCopied -> {
                        val result = snackbarHostState.showSnackbar(
                            message = resources.getQuantityString(R.plurals.today_entries_copied, event.ids.size, event.ids.size),
                            actionLabel = undoLabel,
                            duration = SnackbarDuration.Long,
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.undoCopy(event.ids)
                    }
                    TodayEvent.Failed ->
                        snackbarHostState.showSnackbar(resources.getString(R.string.save_failed_message))
                }
            }
        }
    }

    val header by viewModel.header.collectAsStateWithLifecycle()
    val today = remember(viewModel) { viewModel.today }
    val currentHeader = header
    if (currentHeader == null) {
        LoadingScreen(modifier)
    } else {
        TodayContent(
            header = currentHeader,
            today = today,
            dayFlow = viewModel::day,
            snackbarHostState = snackbarHostState,
            onAddFood = onAddFood,
            onEditEntry = onEditEntry,
            onDeleteEntry = viewModel::delete,
            onCopyFromPreviousDay = viewModel::copyFromPreviousDay,
            modifier = modifier,
        )
    }
}

@Composable
fun TodayContent(
    header: TodayHeader,
    today: LocalDate,
    dayFlow: (LocalDate) -> Flow<DayData>,
    snackbarHostState: SnackbarHostState,
    onAddFood: (LocalDate, Meal) -> Unit,
    onEditEntry: (DiaryEntry) -> Unit,
    onDeleteEntry: (DiaryEntry) -> Unit,
    onCopyFromPreviousDay: (LocalDate, Meal) -> Unit,
    modifier: Modifier = Modifier,
    pagerState: PagerState = rememberPagerState(initialPage = TODAY_PAGE) { PAGE_COUNT },
) {
    val scope = rememberCoroutineScope()
    val selectedDate = pageToDate(pagerState.currentPage, today)
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    // True while an entry is being swiped away: the day must not change under the finger.
    var rowSwiping by remember { mutableStateOf(false) }

    TabScaffold(
        topBar = {
            DateBar(
                date = selectedDate,
                today = today,
                onPrevious = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                onNext = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                onPick = { showDatePicker = true },
                onJumpToToday = { scope.launch { pagerState.scrollToPage(TODAY_PAGE) } },
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data -> Snackbar(data) }
        },
        modifier = modifier,
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 0,
            userScrollEnabled = !rowSwiping,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) { page ->
            val date = pageToDate(page, today)
            DayPage(
                date = date,
                today = today,
                header = header,
                dayFlow = dayFlow,
                onAddFood = onAddFood,
                onEditEntry = onEditEntry,
                onDeleteEntry = onDeleteEntry,
                onCopyFromPreviousDay = onCopyFromPreviousDay,
                onRowSwipingChange = { rowSwiping = it },
            )
        }
    }

    if (showDatePicker) {
        DatePickerSheet(
            initial = selectedDate,
            onDismiss = { showDatePicker = false },
            onPicked = { picked ->
                showDatePicker = false
                val target = dateToPage(picked, today)
                scope.launch {
                    if (abs(target - pagerState.currentPage) <= 1) {
                        pagerState.animateScrollToPage(target)
                    } else {
                        pagerState.scrollToPage(target)
                    }
                }
            },
        )
    }
}

/** `‹  Heute  ›` with the date picker behind the middle and a way back to today. */
@Composable
private fun DateBar(
    date: LocalDate,
    today: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPick: () -> Unit,
    onJumpToToday: () -> Unit,
) {
    val label = dateLabel(date, today)
    val pickDescription = stringResource(R.string.today_pick_date, label)
    val jumpDescription = stringResource(R.string.today_jump_to_today)
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 4.dp),
        ) {
            IconButton(onClick = onPrevious) {
                Icon(painterResource(R.drawable.ic_chevron_left_24), stringResource(R.string.today_previous_day))
            }
            TextButton(
                onClick = onPick,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { contentDescription = pickDescription },
                )
            }
            if (date != today) {
                TextButton(onClick = onJumpToToday) {
                    Text(
                        text = stringResource(R.string.date_today),
                        modifier = Modifier.semantics { contentDescription = jumpDescription },
                    )
                }
            }
            IconButton(onClick = onNext) {
                Icon(painterResource(R.drawable.ic_chevron_right_24), stringResource(R.string.today_next_day))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerSheet(initial: LocalDate, onDismiss: () -> Unit, onPicked: (LocalDate) -> Unit) {
    // The Material date picker works with UTC midnight millis.
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onPicked(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    } ?: onDismiss()
                },
            ) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

/** One day: energy ring, macro bars and the four meals. */
@Composable
private fun DayPage(
    date: LocalDate,
    today: LocalDate,
    header: TodayHeader,
    dayFlow: (LocalDate) -> Flow<DayData>,
    onAddFood: (LocalDate, Meal) -> Unit,
    onEditEntry: (DiaryEntry) -> Unit,
    onDeleteEntry: (DiaryEntry) -> Unit,
    onCopyFromPreviousDay: (LocalDate, Meal) -> Unit,
    onRowSwipingChange: (Boolean) -> Unit,
) {
    val flow = remember(date, dayFlow) { dayFlow(date) }
    val day by flow.collectAsStateWithLifecycle<DayData?>(initialValue = null)
    val data = day ?: run {
        Box(Modifier.fillMaxSize())
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "energy") {
            EnergyCard(
                eatenKcal = data.totals.kcal,
                targetKcal = header.plan?.targetKcal,
                unit = header.energyUnit,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        item(key = "macros") {
            MacroCard(
                totals = data.totals,
                targets = header.plan?.macros,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        if (data.isEmpty) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.today_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                )
                HintText(
                    text = stringResource(R.string.today_empty_message),
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
                )
            }
        }
        data.sections.forEach { section ->
            item(key = "header-${section.meal}") {
                MealHeader(
                    meal = section.meal,
                    kcal = section.totals.kcal,
                    unit = header.energyUnit,
                    onAdd = { onAddFood(date, section.meal) },
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            items(section.entries, key = { it.id }) { entry ->
                EntryRow(
                    entry = entry,
                    unit = header.energyUnit,
                    onClick = { onEditEntry(entry) },
                    onDelete = { onDeleteEntry(entry) },
                    onSwipingChange = onRowSwipingChange,
                )
            }
            val copyable = data.previousDayCounts[section.meal] ?: 0
            if (section.entries.isEmpty() && copyable > 0) {
                item(key = "copy-${section.meal}") {
                    CopyFromPreviousDayButton(
                        count = copyable,
                        isToday = date == today,
                        onClick = { onCopyFromPreviousDay(date, section.meal) },
                    )
                }
            }
        }
    }
}
