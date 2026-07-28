/*
 * Seenema: track and rate the films and series you have watched.
 * Copyright (C) 2026 Sitavi
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package sitavi.seenema

import android.app.Application
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

object SortMode {
    const val DATE = "date"
    const val RATING = "rating"
    const val YEAR = "year"
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SeenStore(app)
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // Removals remembered while the app is open, so re-rating an accidentally
    // removed title restores its original date and note instead of "today".
    private val removed = mutableMapOf<String, SeenItem>()

    // One shared host so every screen can show the undo banner in its Scaffold.
    val snackbarHostState = SnackbarHostState()

    var seen by mutableStateOf(listOf<SeenItem>())
        private set
    var query by mutableStateOf("")
        private set
    var results by mutableStateOf(listOf<SuggestItem>())
        private set
    var searching by mutableStateOf(false)
        private set
    var searchFailed by mutableStateOf(false)
        private set
    var sortMode by mutableStateOf(SortMode.DATE)
        private set
    var sortAsc by mutableStateOf(false)
        private set

    init {
        sortMode = prefs.getString("sort", SortMode.DATE) ?: SortMode.DATE
        sortAsc = prefs.getBoolean("sortAsc", false)
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = store.load().sortedByDescending { it.addedAt }
            withContext(Dispatchers.Main) { seen = loaded }
        }
    }

    fun setSort(mode: String) {
        sortMode = mode
        persistSort()
    }

    fun setSortDirection(asc: Boolean) {
        sortAsc = asc
        persistSort()
    }

    private fun persistSort() {
        prefs.edit().putString("sort", sortMode).putBoolean("sortAsc", sortAsc).apply()
    }

    fun items(status: String) = seen.filter { it.status == status }

    fun sortedItems(status: String): List<SeenItem> {
        val list = items(status)
        val sorted = when (sortMode) {
            SortMode.RATING -> list.sortedWith(
                compareByDescending<SeenItem> { it.rating }.thenByDescending { it.addedAt }
            )
            SortMode.YEAR -> list.sortedWith(
                compareByDescending<SeenItem> { it.year }.thenByDescending { it.addedAt }
            )
            else -> list.sortedByDescending { it.addedAt }
        }
        return if (sortAsc) sorted.reversed() else sorted
    }

    private var searchJob: Job? = null

    fun onQueryChange(newQuery: String) {
        query = newQuery
        searchJob?.cancel()
        if (newQuery.isBlank()) {
            results = emptyList()
            searching = false
            searchFailed = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            runSearch(newQuery)
        }
    }

    fun retrySearch() {
        if (query.isBlank()) return
        searchJob?.cancel()
        searchJob = viewModelScope.launch { runSearch(query) }
    }

    private suspend fun runSearch(q: String) {
        searching = true
        try {
            results = try {
                Imdb.search(q)
            } catch (e: CancellationException) {
                throw e // typing cancelled this search; not a network failure
            } catch (e: Exception) {
                delay(500)
                Imdb.search(q) // one silent retry for transient hiccups
            }
            searchFailed = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            searchFailed = true
            results = emptyList()
        }
        searching = false
    }

    private fun key(id: String, type: String) = "$type-$id"

    fun itemOf(id: String, type: String): SeenItem? =
        seen.firstOrNull { it.id == id && it.mediaType == type }

    fun ratingOf(id: String, type: String): Int = itemOf(id, type)?.rating ?: 0

    fun statusOf(id: String, type: String): String? = itemOf(id, type)?.status

    fun rate(id: String, type: String, title: String, year: String, posterUrl: String?, rating: Int) {
        val existing = itemOf(id, type)
        val before = seen
        seen = when {
            existing == null -> {
                val old = removed[key(id, type)]
                listOf(
                    SeenItem(
                        id, type, title, year, posterUrl.orEmpty(), Status.SEEN, rating,
                        note = old?.note ?: "",
                        addedAt = old?.addedAt ?: now(),
                    )
                ) + seen
            }
            existing.status == Status.SEEN && existing.rating == rating -> {
                removed[key(id, type)] = existing
                seen - existing
            }
            else -> seen.map {
                if (it.id == id && it.mediaType == type) it.copy(rating = rating, status = Status.SEEN) else it
            }
        }
        when {
            existing == null -> {} // plain add, nothing to undo
            existing.status == Status.SEEN && existing.rating == rating ->
                showUndo(R.string.undo_removed_seen, before)
            existing.status == Status.WATCHLIST -> showUndo(R.string.undo_moved_seen, before)
        }
        persist()
    }

    fun toggleWatchlist(id: String, type: String, title: String, year: String, posterUrl: String?) {
        val existing = itemOf(id, type)
        val before = seen
        seen = when {
            existing == null -> listOf(
                SeenItem(id, type, title, year, posterUrl.orEmpty(), Status.WATCHLIST, 0, "", now())
            ) + seen
            existing.status == Status.WATCHLIST -> seen - existing
            // already seen: clear the rating and move it back to Watch later
            else -> seen.map {
                if (it.id == id && it.mediaType == type) it.copy(status = Status.WATCHLIST, rating = 0) else it
            }
        }
        when {
            existing == null -> {} // plain add, nothing to undo
            existing.status == Status.WATCHLIST -> showUndo(R.string.undo_removed_watchlist, before)
            else -> showUndo(R.string.undo_moved_watchlist, before)
        }
        persist()
    }

    /** Banner saying what just happened, with a cancel action restoring the previous state. */
    private fun showUndo(messageRes: Int, before: List<SeenItem>) {
        val app = getApplication<Application>()
        snackbarHostState.currentSnackbarData?.dismiss()
        viewModelScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = app.getString(messageRes),
                actionLabel = app.getString(R.string.undo),
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                seen = before
                persist()
            }
        }
    }

    /** Stored poster links self-heal with the current one whenever a title page loads. */
    fun refreshPoster(id: String, type: String, posterUrl: String?) {
        if (posterUrl.isNullOrBlank()) return
        val existing = itemOf(id, type) ?: return
        if (existing.posterUrl != posterUrl) {
            seen = seen.map {
                if (it.id == id && it.mediaType == type) it.copy(posterUrl = posterUrl) else it
            }
            persist()
        }
    }

    fun updateDetails(id: String, type: String, note: String, date: String) {
        val validDate = runCatching { LocalDate.parse(date.trim()) }.getOrNull()
        seen = seen.map {
            if (it.id == id && it.mediaType == type) {
                it.copy(
                    note = note.trim(),
                    addedAt = validDate?.let { d -> "${d}T12:00:00" } ?: it.addedAt,
                )
            } else it
        }
        persist()
    }

    fun importCsv(uri: Uri) {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val imported = try {
                val text = app.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                store.parseItems(text)
            } catch (e: Exception) {
                null
            }
            withContext(Dispatchers.Main) {
                if (imported == null) {
                    Toast.makeText(app, app.getString(R.string.import_failed), Toast.LENGTH_SHORT).show()
                } else {
                    val current = seen
                    val fresh = imported.filter { i ->
                        current.none { it.id == i.id && it.mediaType == i.mediaType }
                    }
                    seen = (current + fresh).sortedByDescending { it.addedAt }
                    persist()
                    Toast.makeText(
                        app, app.getString(R.string.imported_toast, fresh.size), Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun now(): String = LocalDateTime.now().withNano(0).toString()

    private fun persist() {
        val snapshot = seen
        viewModelScope.launch(Dispatchers.IO) { store.save(snapshot) }
    }

    fun csvFileForShare(): File {
        if (!store.file.exists()) store.save(seen)
        return store.file
    }
}
