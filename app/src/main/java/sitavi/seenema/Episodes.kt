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

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

/**
 * Seasons and episodes of a series: a strip of season chips with their watched
 * count, then the episodes of the chosen season. Only one season is shown at a
 * time, so even a long run stays a short list.
 *
 * Episodes are IMDb titles, so each row opens its own page and carries its own
 * rating, kept apart from the rating of the series itself.
 */
@Composable
fun SeasonEpisodes(vm: AppViewModel, nav: NavController, series: SeriesRef) {
    val seriesId = series.id
    LaunchedEffect(seriesId) { vm.loadEpisodes(seriesId) }

    val known = vm.episodes[seriesId]
    val failed = vm.episodeKey(seriesId) in vm.episodeFailures
    // whatever was tracked before is in the library file, so a series that
    // cannot be fetched still shows the episodes the user has marked
    val stored = remember(vm.seen, seriesId) { vm.episodesOf(seriesId) }

    when {
        known == null && failed -> Column {
            LoadFailed { vm.loadEpisodes(seriesId, force = true) }
            stored.forEach { StoredEpisodeRow(vm, nav, it) }
        }

        known == null -> Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.Center,
        ) { CircularProgressIndicator() }

        known.seasons.isEmpty() -> Column {
            LoadFailed(R.string.no_results) { vm.loadEpisodes(seriesId, force = true) }
            stored.forEach { StoredEpisodeRow(vm, nav, it) }
        }

        else -> {
            val seasons = known.seasons
            var selected by rememberSaveable(seriesId) { mutableIntStateOf(seasons.first()) }
            val season = if (selected in seasons) selected else seasons.first()
            LaunchedEffect(seriesId, season) { vm.loadEpisodes(seriesId, season) }

            // one pass over the library instead of one per episode
            val watched = remember(known.episodes, vm.seen) {
                known.episodes.groupBy { it.season }.mapValues { (_, eps) ->
                    eps.count { vm.statusOf(it.id, EPISODE) == Status.SEEN }
                }
            }
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                seasons.forEach { s ->
                    val label = stringResource(R.string.season_short, s)
                    // a season nobody has opened yet has no count to show
                    val total = known.episodes.count { it.season == s }
                    FilterChip(
                        selected = s == season,
                        onClick = { selected = s },
                        label = {
                            Text(
                                if (total > 0) "$label  ${watched[s] ?: 0}/$total" else label
                            )
                        },
                    )
                }
            }

            val episodes = known.episodes.filter { it.season == season }
            Column(Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                when {
                    vm.episodeKey(seriesId, season) in vm.episodeFailures ->
                        LoadFailed { vm.loadEpisodes(seriesId, season, force = true) }

                    episodes.isEmpty() && vm.loadingEpisodes(seriesId, season) -> Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) { CircularProgressIndicator() }

                    else -> episodes.forEach { ep ->
                        EpisodeRow(vm, nav, series, ep)
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(vm: AppViewModel, nav: NavController, series: SeriesRef, ep: Episode) {
    val tracked = vm.itemOf(ep.id, EPISODE)
    val partOf = "${series.id}:S${ep.season}E${ep.number}"
    MediaCard(
        title = stringResource(R.string.episode_short, ep.number) + "  " + ep.title,
        subtitle = "",
        posterUrl = ep.imageUrl,
        onClick = { nav.navigate("title/$EPISODE/${ep.id}") },
        // the date an episode was watched, same as a library card
        date = tracked?.addedAt?.take(10).orEmpty(),
        seen = tracked?.status == Status.SEEN,
        watchLater = tracked?.status == Status.WATCHLIST,
        onWatchLater = {
            vm.toggleWatchlist(ep.id, EPISODE, ep.title, "", ep.imageUrl, partOf, series)
        },
        controlSize = EPISODE_STAR,
    ) {
        StarRating(tracked?.rating ?: 0, starSize = EPISODE_STAR) { stars ->
            vm.rate(ep.id, EPISODE, ep.title, "", ep.imageUrl, stars, partOf, series)
        }
    }
}

/** An episode known only from the library file, with no season list behind it. */
@Composable
private fun StoredEpisodeRow(vm: AppViewModel, nav: NavController, item: SeenItem) {
    MediaCard(
        title = item.title,
        subtitle = "",
        posterUrl = item.posterUrl.ifBlank { null },
        onClick = { nav.navigate("title/$EPISODE/${item.id}") },
        date = item.addedAt.take(10),
        seen = item.status == Status.SEEN,
        watchLater = item.status == Status.WATCHLIST,
        onWatchLater = {
            vm.toggleWatchlist(
                item.id, EPISODE, item.title, "", item.posterUrl, item.partOf
            )
        },
        controlSize = EPISODE_STAR,
    ) {
        StarRating(item.rating, starSize = EPISODE_STAR) { stars ->
            vm.rate(item.id, EPISODE, item.title, "", item.posterUrl, stars, item.partOf)
        }
    }
}

@Composable
private fun LoadFailed(message: Int = R.string.load_error, onRetry: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

/** Episode rows sit inside a card, so their controls are a size down. */
private val EPISODE_STAR = 26.dp

/** Media type stored for an episode row, kept apart from "movie" and "tv". */
const val EPISODE = "episode"
