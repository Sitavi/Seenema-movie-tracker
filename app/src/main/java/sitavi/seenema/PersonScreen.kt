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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonScreen(vm: AppViewModel, nav: NavController, id: String) {
    var person by remember { mutableStateOf<GqlPerson?>(null) }
    var error by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(id, reload) {
        error = false
        person = try {
            Imdb.person(id)
        } catch (e: Exception) {
            error = true
            null
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(vm.snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        val p = person
        when {
            error -> Centered(Modifier.padding(padding)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.load_error))
                    TextButton(onClick = { reload++ }) { Text(stringResource(R.string.retry)) }
                }
            }
            p == null -> Centered(Modifier.padding(padding)) { CircularProgressIndicator() }
            else -> PersonContent(vm, nav, p, Modifier.padding(padding))
        }
    }
}

@Composable
private fun PersonContent(vm: AppViewModel, nav: NavController, p: GqlPerson, modifier: Modifier) {
    var oldestFirst by rememberSaveable { mutableStateOf(false) }
    val filmography = remember(p, oldestFirst) {
        // titles without a year go last either way
        val knownYearFirst = compareByDescending<GqlTitle> { it.releaseYear?.year != null }
        p.credits?.edges.orEmpty()
            .mapNotNull { it.node?.title }
            .filter { it.titleType?.id in Imdb.listableTypes }
            .distinctBy { it.id }
            .sortedWith(
                if (oldestFirst) knownYearFirst.thenBy { it.releaseYear?.year ?: 0 }
                else knownYearFirst.thenByDescending { it.releaseYear?.year ?: 0 }
            )
    }
    val listState = rememberLazyListState()
    val bio = p.bio?.text?.plainText
    // list items sitting above the filmography rows: photo, optional bio, header
    val headerItems = if (bio.isNullOrBlank()) 2 else 3
    Box(modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            val photoUrl = p.primaryImage?.url
            var showPhoto by remember { mutableStateOf(false) }
            if (showPhoto && photoUrl != null) {
                Dialog(
                    onDismissRequest = { showPhoto = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    AsyncImage(
                        model = Imdb.thumb(photoUrl, 1280),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .clickable(interactionSource = null, indication = null) { showPhoto = false }
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp)
            ) {
                PersonImage(
                    p.primaryImage?.url,
                    Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(interactionSource = null, indication = null) { showPhoto = true }
                )
                Column(Modifier.padding(start = 16.dp)) {
                    Text(p.nameText?.text ?: "", style = MaterialTheme.typography.headlineSmall)
                    val professions = p.primaryProfessions.mapNotNull { it.category?.text }
                    if (professions.isNotEmpty()) {
                        Text(
                            professions.joinToString(", "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        if (!bio.isNullOrBlank()) {
            item {
                var expanded by remember { mutableStateOf(false) }
                Text(
                    bio,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (expanded) Int.MAX_VALUE else 5,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .clickable(interactionSource = null, indication = null) { expanded = !expanded }
                )
            }
        }
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp)
            ) {
                Text(
                    stringResource(R.string.filmography, filmography.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { oldestFirst = !oldestFirst }) {
                    Icon(
                        painterResource(R.drawable.ic_arrow_down),
                        contentDescription = stringResource(
                            if (oldestFirst) R.string.sort_asc else R.string.sort_desc
                        ),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(if (oldestFirst) 180f else 0f)
                    )
                }
            }
        }
        items(filmography, key = { it.id }) { t ->
            val type = if (t.isTv) "tv" else "movie"
            val rating = vm.ratingOf(t.id, type)
            MediaCard(
                title = t.displayTitle,
                subtitle = listOf(
                    t.yearText,
                    stringResource(if (type == "tv") R.string.series else R.string.movie)
                ).filter { it.isNotBlank() }.joinToString(" • "),
                posterUrl = t.primaryImage?.url,
                onClick = { nav.navigate("title/$type/${t.id}") },
                seen = rating > 0,
                watchLater = vm.statusOf(t.id, type) == Status.WATCHLIST,
                onWatchLater = {
                    vm.toggleWatchlist(t.id, type, t.displayTitle, t.yearText, t.primaryImage?.url)
                },
            ) {
                StarRating(rating) {
                    vm.rate(t.id, type, t.displayTitle, t.yearText, t.primaryImage?.url, it)
                }
            }
        }
    }
    FastScroller(listState) { i ->
        filmography.getOrNull(i - headerItems)?.yearText?.takeIf { it.isNotBlank() }
    }
    }
}
