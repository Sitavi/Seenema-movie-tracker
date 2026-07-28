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

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel, nav: NavController) {
    val context = LocalContext.current
    var active by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.importCsv(uri)
    }

    val seenItems = vm.items(Status.SEEN)
    val watchItems = vm.items(Status.WATCHLIST)
    Scaffold(
        snackbarHost = { SnackbarHost(vm.snackbarHostState) },
        bottomBar = {
            if (!active) {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { Icon(Icons.Default.Star, contentDescription = null) },
                        label = { Text(stringResource(R.string.tab_seen, seenItems.size)) }
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = {
                            Icon(
                                painterResource(R.drawable.ic_watch_later),
                                contentDescription = null
                            )
                        },
                        label = { Text(stringResource(R.string.tab_watchlist, watchItems.size)) }
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!active) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, top = 8.dp, bottom = 4.dp, end = 4.dp)
                ) {
                    Icon(
                        painterResource(R.drawable.ic_logo),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.brand), style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.weight(1f))
                    // global actions live top right, next to the app name
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.menu))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share_csv)) },
                                onClick = { menuOpen = false; shareCsv(context, vm) }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.import_csv)) },
                                onClick = { menuOpen = false; importLauncher.launch("*/*") }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.about)) },
                                onClick = { menuOpen = false; nav.navigate("about") }
                            )
                        }
                    }
                }
            }
            SearchBar(
                query = vm.query,
                onQueryChange = vm::onQueryChange,
                onSearch = {},
                active = active,
                onActiveChange = { active = it },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (active) IconButton(onClick = {
                        if (vm.query.isBlank()) active = false else vm.onQueryChange("")
                    }) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear)) }
                },
                windowInsets = WindowInsets(0.dp),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .then(if (active) Modifier else Modifier.fillMaxWidth().padding(horizontal = 16.dp))
            ) {
                SearchResults(vm, nav)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 12.dp, end = 4.dp)
            ) {
                Spacer(Modifier.weight(1f))
                Box {
                    TextButton(onClick = { sortMenuOpen = true }) {
                        Text(stringResource(R.string.sort_by, stringResource(sortLabel(vm.sortMode))))
                    }
                    DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                        listOf(SortMode.DATE, SortMode.RATING, SortMode.YEAR).forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(stringResource(sortLabel(mode))) },
                                trailingIcon = if (mode == vm.sortMode) {
                                    { Icon(Icons.Default.Check, contentDescription = null) }
                                } else null,
                                onClick = { vm.setSort(mode); sortMenuOpen = false }
                            )
                        }
                    }
                }
                // direction toggle sits right of the selector, Files-app style
                IconButton(onClick = { vm.setSortDirection(!vm.sortAsc) }) {
                    Icon(
                        painterResource(R.drawable.ic_arrow_down),
                        contentDescription = stringResource(
                            if (vm.sortAsc) R.string.sort_asc else R.string.sort_desc
                        ),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(if (vm.sortAsc) 180f else 0f)
                    )
                }
            }

            val list = vm.sortedItems(if (tab == 0) Status.SEEN else Status.WATCHLIST)
            if (list.isEmpty()) {
                Centered {
                    Text(
                        stringResource(if (tab == 0) R.string.empty_seen else R.string.empty_watchlist),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val listState = rememberLazyListState()
                Box(Modifier.fillMaxSize()) {
                LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(list, key = { "${it.mediaType}-${it.id}" }) { item ->
                        MediaCard(
                            title = item.title,
                            subtitle = listOf(
                                item.year,
                                stringResource(if (item.mediaType == "tv") R.string.series else R.string.movie)
                            ).filter { it.isNotBlank() }.joinToString(" • "),
                            date = item.addedAt.take(10),
                            posterUrl = item.posterUrl.ifBlank { null },
                            onClick = { nav.navigate("title/${item.mediaType}/${item.id}") },
                            seen = item.rating > 0,
                            watchLater = item.status == Status.WATCHLIST,
                            onWatchLater = {
                                vm.toggleWatchlist(item.id, item.mediaType, item.title, item.year, item.posterUrl)
                            },
                        ) {
                            StarRating(item.rating) {
                                vm.rate(item.id, item.mediaType, item.title, item.year, item.posterUrl, it)
                            }
                        }
                    }
                }
                FastScroller(listState) { i ->
                    list.getOrNull(i)?.let { item ->
                        when (vm.sortMode) {
                            SortMode.RATING -> "★ ${item.rating}"
                            SortMode.YEAR -> item.year.ifBlank { "—" }
                            else -> item.addedAt.take(10)
                        }
                    }
                }
                }
            }
        }
    }
}

private fun sortLabel(mode: String): Int = when (mode) {
    SortMode.RATING -> R.string.sort_rating
    SortMode.YEAR -> R.string.sort_year
    else -> R.string.sort_date
}

@Composable
private fun SearchResults(vm: AppViewModel, nav: NavController) {
    if (vm.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        items(vm.results, key = { it.id }) { r ->
            if (r.isPerson) {
                MediaCard(
                    title = r.l,
                    subtitle = r.s ?: stringResource(R.string.person),
                    posterUrl = r.i?.imageUrl,
                    onClick = { nav.navigate("person/${r.id}") },
                )
            } else {
                val rating = vm.ratingOf(r.id, r.mediaType)
                MediaCard(
                    title = r.l,
                    subtitle = listOf(
                        r.year,
                        stringResource(if (r.mediaType == "tv") R.string.series else R.string.movie)
                    ).filter { it.isNotBlank() }.joinToString(" • "),
                    posterUrl = r.i?.imageUrl,
                    onClick = { nav.navigate("title/${r.mediaType}/${r.id}") },
                    seen = rating > 0,
                    watchLater = vm.statusOf(r.id, r.mediaType) == Status.WATCHLIST,
                    onWatchLater = { vm.toggleWatchlist(r.id, r.mediaType, r.l, r.year, r.i?.imageUrl) },
                ) {
                    StarRating(rating) {
                        vm.rate(r.id, r.mediaType, r.l, r.year, r.i?.imageUrl, it)
                    }
                }
            }
        }
        if (!vm.searching && vm.searchFailed) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(24.dp)
                ) {
                    Text(
                        stringResource(R.string.search_error),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { vm.retrySearch() }) { Text(stringResource(R.string.retry)) }
                }
            }
        } else if (!vm.searching && vm.query.isNotBlank() && vm.results.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.no_results),
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun shareCsv(context: Context, vm: AppViewModel) {
    val file = vm.csvFileForShare()
    val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}
