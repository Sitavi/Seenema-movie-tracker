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

    /** Episode rows of a series, optionally only those in one list. */
    fun episodesOf(seriesId: String, status: String? = null): List<SeenItem> =
        seen.filter {
            it.seriesId == seriesId && seriesId.isNotBlank() &&
                (status == null || it.status == status)
        }

    /**
     * The date a card shows. A series answers with the most recent date among
     * its own row and its tracked episodes, so the card says when the series
     * was last touched rather than when it was first added. On a library tab
     * only that tab's list counts; a search result counts everything.
     */
    fun cardDate(id: String, type: String, status: String? = null): String {
        val own = itemOf(id, type)
            ?.takeIf { status == null || it.status == status }
            ?.addedAt
        if (type != "tv") return own.orEmpty()
        val episode = episodesOf(id, status).maxOfOrNull { it.addedAt }
        return listOfNotNull(own, episode).maxOrNull().orEmpty()
    }

    /**
     * What one tab lists: titles in it outright, plus the series holding an
     * episode in it. A series with episodes seen and others still to watch is
     * therefore in both tabs, each showing its own date.
     */
    fun items(status: String): List<SeenItem> {
        val own = seen.filter { it.partOf.isBlank() }
        val direct = own.filter { it.status == status }
        val episodes = seen.filter { it.partOf.isNotBlank() && it.status == status }
        val viaEpisodes = episodes
            .map { it.seriesId }
            .distinct()
            .filter { id -> id.isNotBlank() && direct.none { it.id == id } }
            .mapNotNull { id -> own.firstOrNull { it.id == id && it.mediaType == "tv" } }
        // an episode with no series row behind it, from a hand-edited or
        // partial file, is listed on its own rather than kept out of sight
        val loose = episodes.filter { ep ->
            ep.seriesId.isBlank() || own.none { it.id == ep.seriesId && it.mediaType == "tv" }
        }
        return direct + viaEpisodes + loose
    }

    fun sortedItems(status: String): List<SeenItem> {
        val list = items(status)
        // the date the card shows is the date it sorts by
        val dateOf = { item: SeenItem -> cardDate(item.id, item.mediaType, status) }
        val sorted = when (sortMode) {
            SortMode.RATING -> list.sortedWith(
                compareByDescending<SeenItem> { it.rating }.thenByDescending(dateOf)
            )
            SortMode.YEAR -> list.sortedWith(
                compareByDescending<SeenItem> { it.year }.thenByDescending(dateOf)
            )
            else -> list.sortedByDescending(dateOf)
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

    // Season and episode structure per series, kept for the life of the app so
    // collapsing and reopening a card costs nothing.
    var episodes by mutableStateOf(mapOf<String, SeriesEpisodes>())
        private set
    var episodeFailures by mutableStateOf(setOf<String>())
        private set
    private val loadingEpisodes = mutableSetOf<String>()

    /** Key of one load: the whole series, or one season of it. */
    fun episodeKey(seriesId: String, season: Int? = null): String =
        if (season == null) seriesId else "$seriesId:$season"

    fun loadingEpisodes(seriesId: String, season: Int? = null): Boolean =
        episodeKey(seriesId, season) in loadingEpisodes

    /**
     * Loads the episodes of a series, or of one of its seasons. The first call
     * takes the whole run in one request; only a series too long for that comes
     * back incomplete, and then each season is fetched as it is opened.
     */
    fun loadEpisodes(seriesId: String, season: Int? = null, force: Boolean = false) {
        if (seriesId.isBlank()) return
        val key = episodeKey(seriesId, season)
        // a second request for the same thing would only duplicate the work,
        // force or not, and the one in flight already clears the failure
        if (key in loadingEpisodes) return
        if (!force && has(seriesId, season)) return
        loadingEpisodes += key
        episodeFailures = episodeFailures - key
        viewModelScope.launch {
            try {
                val page = Imdb.episodes(seriesId, season)
                if (page.episodes.isEmpty() && page.seasons.isEmpty()) {
                    // an empty answer is as likely a hiccup as an empty series,
                    // and caching it would take the retry away for good
                    episodeFailures = episodeFailures + key
                } else {
                    episodes = episodes + (seriesId to merge(episodes[seriesId], page, season))
                    episodeFailures = episodeFailures - key
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                episodeFailures = episodeFailures + key
            } finally {
                loadingEpisodes -= key
            }
        }
    }

    private fun has(seriesId: String, season: Int?): Boolean {
        val known = episodes[seriesId] ?: return false
        return season == null || known.complete || season in known.loadedSeasons
    }

    private fun merge(known: SeriesEpisodes?, page: EpisodePage, season: Int?): SeriesEpisodes {
        val old = known ?: SeriesEpisodes()
        val kept = old.episodes.filter { e -> page.episodes.none { it.id == e.id } }
        val whole = season == null && !page.truncated
        return SeriesEpisodes(
            seasons = page.seasons.ifEmpty { old.seasons },
            episodes = (kept + page.episodes).sortedWith(compareBy({ it.season }, { it.number })),
            loadedSeasons = old.loadedSeasons + page.episodes.map { it.season } + listOfNotNull(season),
            complete = whole || old.complete,
        )
    }

    private fun key(id: String, type: String) = "$type-$id"

    fun itemOf(id: String, type: String): SeenItem? =
        seen.firstOrNull { it.id == id && it.mediaType == type }

    fun ratingOf(id: String, type: String): Int = itemOf(id, type)?.rating ?: 0

    fun statusOf(id: String, type: String): String? = itemOf(id, type)?.status

    fun rate(
        id: String, type: String, title: String, year: String, posterUrl: String?, rating: Int,
        partOf: String = "", series: SeriesRef? = null,
    ) {
        val existing = itemOf(id, type)
        val before = seen
        // an older banner still on screen would restore a list from before
        // this change, so it goes as soon as anything else happens
        snackbarHostState.currentSnackbarData?.dismiss()
        seen = withSeries(seen, series)
        seen = when {
            existing == null -> {
                val old = removed[key(id, type)]
                listOf(
                    SeenItem(
                        id, type, title, year, posterUrl.orEmpty(), Status.SEEN, rating,
                        note = old?.note ?: "",
                        addedAt = old?.addedAt ?: now(),
                        partOf = partOf,
                    )
                ) + seen
            }
            existing.status == Status.SEEN && existing.rating == rating -> {
                removed[key(id, type)] = existing
                untrack(seen, existing)
            }
            else -> {
                // Coming in from Watch later, the stored date stops meaning "added"
                // and starts meaning "watched", so it moves to today. A title that
                // was already seen keeps its date: re-rating is not re-watching.
                val watchedAt = when (existing.status) {
                    Status.SEEN -> existing.addedAt
                    else -> removed[key(id, type)]?.addedAt ?: now()
                }
                seen.map {
                    if (it.id == id && it.mediaType == type)
                        it.copy(
                            rating = rating,
                            status = Status.SEEN,
                            addedAt = watchedAt,
                            partOf = partOf.ifBlank { it.partOf },
                        )
                    else it
                }
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

    fun toggleWatchlist(
        id: String, type: String, title: String, year: String, posterUrl: String?,
        partOf: String = "", series: SeriesRef? = null,
    ) {
        val existing = itemOf(id, type)
        val before = seen
        snackbarHostState.currentSnackbarData?.dismiss()
        seen = withSeries(seen, series)
        seen = when {
            // a series kept only for its episodes is in no list, so the clock
            // adds it to Watch later rather than moving it out of Seen
            existing == null -> listOf(
                SeenItem(
                    id, type, title, year, posterUrl.orEmpty(), Status.WATCHLIST, 0, "", now(),
                    partOf = partOf,
                )
            ) + seen
            // only its episodes kept this row: it joins the list as if new,
            // but the note the user wrote on it stays
            existing.status == Status.TRACKED -> seen.map {
                if (it.id == id && it.mediaType == type)
                    it.copy(status = Status.WATCHLIST, rating = 0, addedAt = now())
                else it
            }
            existing.status == Status.WATCHLIST -> untrack(seen, existing)
            // already seen: clear the rating and move it back to Watch later,
            // remembering when it was watched in case it is rated again
            else -> {
                removed[key(id, type)] = existing
                seen.map {
                    if (it.id == id && it.mediaType == type) it.copy(status = Status.WATCHLIST, rating = 0) else it
                }
            }
        }
        when {
            existing == null || existing.status == Status.TRACKED -> {} // plain add
            existing.status == Status.WATCHLIST -> showUndo(R.string.undo_removed_watchlist, before)
            else -> showUndo(R.string.undo_moved_watchlist, before)
        }
        persist()
    }

    /**
     * Makes sure the series of a just-tracked episode has a row, since the
     * lists read the title and poster from it. The series itself joins no
     * list: that is what [Status.TRACKED] says.
     */
    private fun withSeries(list: List<SeenItem>, series: SeriesRef?): List<SeenItem> {
        if (series == null || series.id.isBlank() || series.title.isBlank()) return list
        if (list.any { it.id == series.id && it.mediaType == "tv" }) return list
        return list + SeenItem(
            series.id, "tv", series.title, series.year, series.posterUrl.orEmpty(),
            Status.TRACKED, 0, "", now(),
        )
    }

    /**
     * Takes a title out of the lists. A series with tracked episodes keeps its
     * row so those episodes stay reachable, it just stops being a list entry
     * of its own.
     */
    private fun untrack(list: List<SeenItem>, item: SeenItem): List<SeenItem> =
        if (item.mediaType == "tv" && episodesOf(item.id).isNotEmpty())
            list.map { if (it.id == item.id && it.mediaType == item.mediaType) it.copy(status = Status.TRACKED, rating = 0) else it }
        else list - item

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
        // forget the date this title used to carry, or rating it again would
        // bring that one back over the one just entered
        if (validDate != null) removed.remove(key(id, type))
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
                        app,
                        app.resources.getQuantityString(
                            R.plurals.imported_toast, fresh.size, fresh.size
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    private fun now(): String = LocalDateTime.now().withNano(0).toString()

    private fun persist() {
        // a series kept only for its episodes has nothing left to show once
        // the last of them is untracked
        val orphans = seen.filter {
            it.status == Status.TRACKED && it.partOf.isBlank() && it.mediaType == "tv" &&
                episodesOf(it.id).isEmpty()
        }
        if (orphans.isNotEmpty()) seen = seen - orphans.toSet()
        val snapshot = seen
        viewModelScope.launch(Dispatchers.IO) { store.save(snapshot) }
    }

    fun csvFileForShare(): File {
        if (!store.file.exists()) store.save(seen)
        return store.file
    }
}
