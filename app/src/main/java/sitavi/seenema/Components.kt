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

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

@Composable
fun Poster(url: String?, modifier: Modifier = Modifier, width: Int = 280) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(R.drawable.ic_logo),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxSize(0.45f)
        )
        AsyncImage(
            model = Imdb.thumb(url, width),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )
    }
}

/** Person photo with an icon placeholder when there is none; clip via [modifier]. */
@Composable
fun PersonImage(url: String?, modifier: Modifier = Modifier) {
    Box(
        modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxSize(0.6f)
        )
        AsyncImage(
            model = Imdb.thumb(url),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )
    }
}

@Composable
fun MediaCard(
    title: String,
    subtitle: String,
    posterUrl: String?,
    onClick: () -> Unit,
    date: String = "", // always rendered as its own last line, below the subtitle
    seen: Boolean = false, // lighter card
    watchLater: Boolean? = null, // null hides the clock; true = in list (lighter card, filled icon)
    onWatchLater: () -> Unit = {},
    trailing: @Composable () -> Unit = {},
) {
    var showPoster by remember { mutableStateOf(false) }
    if (showPoster && posterUrl != null) {
        Dialog(
            onDismissRequest = { showPoster = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            AsyncImage(
                model = Imdb.thumb(posterUrl, 1280),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clickable(interactionSource = null, indication = null) { showPoster = false }
            )
        }
    }
    val base = CardDefaults.cardColors().containerColor
    val dark = isSystemInDarkTheme()
    // tracked titles (seen or watch later) lift lighter; the rest keep the default
    val container = if (seen || watchLater == true) {
        Color.White.copy(alpha = if (dark) 0.13f else 0.65f).compositeOver(base)
    } else base
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = container),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp)
        ) {
            Poster(
                posterUrl,
                Modifier
                    .width(58.dp)
                    .height(88.dp)
                    .clickable(interactionSource = null, indication = null) {
                        if (posterUrl != null) showPoster = true
                    }
            )
            // Title gets the full card width; below it the info sits on the
            // left with the stars and clock grouped together on the right.
            // The column height is fixed so every card is the same height
            // whether the texts wrap or not.
            Column(
                Modifier
                    .weight(1f)
                    .height(88.dp)
                    .padding(start = 10.dp, end = 2.dp)
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                    // info and date sit directly under the title; the stars and
                    // clock anchor to the card bottom so they sit at the same
                    // spot on every card
                    Column(
                        Modifier
                            .align(Alignment.Top)
                            .weight(1f)
                            .padding(end = 2.dp)
                    ) {
                        if (subtitle.isNotBlank()) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (date.isNotBlank()) {
                            Text(
                                date,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    trailing()
                    if (watchLater != null) {
                        // sixth element of the star row: same size, spacing and
                        // press ripple as the stars
                        Icon(
                            painterResource(
                                if (watchLater) R.drawable.ic_watch_later_filled else R.drawable.ic_watch_later
                            ),
                            contentDescription = stringResource(
                                if (watchLater) R.string.watchlist_remove else R.string.watchlist_add
                            ),
                            tint = if (watchLater) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            modifier = Modifier
                                .padding(start = 2.dp)
                                .clickable(onClick = onWatchLater)
                                .padding(2.dp)
                                .size(30.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Five tappable stars; tapping star N rates N (and marks the title seen).
 * Tapping the star matching the current rating again clears it.
 */
@Composable
fun StarRating(rating: Int, starSize: Dp = 30.dp, onRate: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 1..5) {
            Icon(
                Icons.Default.Star,
                contentDescription = stringResource(R.string.rate_star, i),
                tint = if (i <= rating) StarGold else MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier
                    .clickable { onRate(i) }
                    .padding(2.dp)
                    .size(starSize)
            )
        }
    }
}

private val StarGold = Color(0xFFF0B429)

@Composable
fun Centered(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/**
 * Draggable fast-scroll handle for a [androidx.compose.foundation.lazy.LazyColumn].
 * Place it inside the same Box, after the list; it pins itself to the right
 * edge, appears while the list moves and fades away when idle. While visible
 * the whole right-edge track reacts: tap to jump there, drag anywhere to
 * scrub, with the thumb centering under the finger. [label] maps the top
 * visible item's index to a short text shown in a bubble while dragging.
 */
@Composable
fun BoxScope.FastScroller(state: LazyListState, label: ((Int) -> String?)? = null) {
    // derived so scrolling doesn't recompose this every frame
    val scrollable by remember(state) {
        derivedStateOf {
            state.layoutInfo.totalItemsCount > state.layoutInfo.visibleItemsInfo.size
        }
    }
    if (!scrollable) return // everything already fits on screen

    var dragging by remember { mutableStateOf(false) }
    val active = dragging || state.isScrollInProgress
    val alpha by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(150, delayMillis = if (active) 0 else 900),
        label = "fastScroller"
    )
    val scope = rememberCoroutineScope()
    val thumbHeight = 64.dp
    BoxWithConstraints(
        Modifier
            .align(Alignment.CenterEnd)
            .fillMaxHeight()
            .width(30.dp)
    ) {
        val trackPx = constraints.maxHeight.toFloat()
        val thumbPx = with(LocalDensity.current) { thumbHeight.toPx() }
        val dragFrac = remember { mutableFloatStateOf(0f) }
        var scrollJob by remember { mutableStateOf<Job?>(null) }

        // scrollable range measured in items, with the viewport converted to a
        // fractional item count: the integer number of visible items flickers
        // between N and N+1 while scrolling, and using it directly makes the
        // thumb wobble
        fun maxItems(info: LazyListLayoutInfo): Float {
            val visible = info.visibleItemsInfo
            if (visible.isEmpty()) return 1f
            val avg = (visible.sumOf { it.size }.toFloat() / visible.size).coerceAtLeast(1f)
            val viewportItems = (info.viewportEndOffset - info.viewportStartOffset) / avg
            return (info.totalItemsCount - viewportItems).coerceAtLeast(1f)
        }

        fun listFrac(): Float {
            val info = state.layoutInfo
            val first = info.visibleItemsInfo.firstOrNull() ?: return 0f
            // include how far the first card has scrolled off screen, so the
            // thumb glides continuously instead of hopping one card at a time
            val scrolled = first.index + -first.offset.toFloat() / first.size.coerceAtLeast(1)
            return (scrolled / maxItems(info)).coerceIn(0f, 1f)
        }

        // thumb centered under the finger
        fun fracOf(y: Float) = ((y - thumbPx / 2) / (trackPx - thumbPx)).coerceIn(0f, 1f)

        // inverse of listFrac: item index plus a pixel offset inside it, so
        // the list follows the thumb fluidly instead of one card at a time;
        // cancel the pending jump so only the newest target actually scrolls
        fun scrollTo(frac: Float) {
            val info = state.layoutInfo
            val itemPx = info.visibleItemsInfo
                .let { v -> if (v.isEmpty()) 1 else v.sumOf { it.size } / v.size }
            val scrolled = frac * maxItems(info)
            val lastIndex = info.totalItemsCount - 1
            scrollJob?.cancel()
            scrollJob = scope.launch {
                if (frac >= 1f) {
                    // land on the absolute end, bottom padding included
                    state.scrollToItem(lastIndex)
                    state.dispatchRawDelta(100_000f)
                } else {
                    state.scrollToItem(scrolled.toInt(), ((scrolled % 1f) * itemPx).toInt())
                }
            }
        }

        // the whole track reacts, but only while visible, so the hidden
        // scroller never steals touches from the cards below it
        Box(
            Modifier
                .fillMaxSize()
                .then(
                    if (alpha > 0f) Modifier
                        .pointerInput(trackPx) {
                            detectTapGestures { offset ->
                                dragFrac.floatValue = fracOf(offset.y)
                                scrollTo(dragFrac.floatValue)
                            }
                        }
                        .pointerInput(trackPx) {
                            detectVerticalDragGestures(
                                onDragStart = { offset ->
                                    dragging = true
                                    dragFrac.floatValue = fracOf(offset.y)
                                    scrollTo(dragFrac.floatValue)
                                },
                                onDragEnd = { dragging = false },
                                onDragCancel = { dragging = false },
                            ) { change, _ ->
                                dragFrac.floatValue = fracOf(change.position.y)
                                scrollTo(dragFrac.floatValue)
                            }
                        }
                    else Modifier
                )
        )

        Box(
            Modifier
                .align(Alignment.TopEnd)
                // the position resolves during placement, so dragging or
                // scrolling moves the thumb without recomposing anything
                .offset {
                    val frac = if (dragging) dragFrac.floatValue else listFrac()
                    IntOffset(0, (frac * (trackPx - thumbPx)).roundToInt())
                }
                .alpha(alpha)
                .padding(end = 1.dp)
                .size(width = 10.dp, height = thumbHeight)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (dragging) 1f else 0.7f))
        )

        if (dragging && label != null) {
            val bubbleIndex by remember(state) {
                derivedStateOf {
                    val info = state.layoutInfo
                    (dragFrac.floatValue * maxItems(info)).roundToInt()
                        .coerceIn(0, (info.totalItemsCount - 1).coerceAtLeast(0))
                }
            }
            val text = label(bubbleIndex)
            if (!text.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(
                        topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp
                    ),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .graphicsLayer {
                            // floats above and to the left of the thumb
                            val thumbTop = dragFrac.floatValue * (trackPx - thumbPx)
                            translationY = maxOf(thumbTop - size.height - 12.dp.toPx(), 8.dp.toPx())
                            translationX = -20.dp.toPx()
                        }
                        .wrapContentWidth(unbounded = true, align = Alignment.End)
                ) {
                    Text(
                        text,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
