package de.privat.kalorienzaehler.data.repository

import de.privat.kalorienzaehler.domain.diary.DiaryEntry
import de.privat.kalorienzaehler.domain.diary.Meal
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/** The food diary. Entries carry snapshots of their nutrition values and never change by themselves. */
interface DiaryRepository {

    /** All entries of [date], oldest first. */
    fun observeDay(date: LocalDate): Flow<List<DiaryEntry>>

    suspend fun get(id: Long): DiaryEntry?

    /** Stores a new entry and returns its id. */
    suspend fun add(entry: DiaryEntry): Long

    suspend fun update(entry: DiaryEntry)

    suspend fun delete(id: Long)

    /** Puts a deleted entry back with its old id (undo). */
    suspend fun restore(entry: DiaryEntry)

    /**
     * Copies every entry of [meal] on [from] to the same meal on [to], stamped with [createdAtMillis].
     * Returns the ids of the new entries (so the copy can be undone with [deleteAll]).
     */
    suspend fun copyMeal(from: LocalDate, to: LocalDate, meal: Meal, createdAtMillis: Long): List<Long>

    suspend fun deleteAll(ids: List<Long>)

    /** Removes the whole diary ("Alle Daten löschen"). */
    suspend fun deleteEverything()
}
