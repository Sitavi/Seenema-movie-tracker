package sitavi.seenema

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import coil.compose.AsyncImage
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitleScreen(vm: AppViewModel, nav: NavController, type: String, id: String) {
    var detail by remember { mutableStateOf<GqlTitle?>(null) }
    var error by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(type, id, reload) {
        error = false
        detail = try {
            Imdb.title(id).also { vm.refreshPoster(id, type, it.primaryImage?.url) }
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val d = detail
        when {
            error -> Centered(Modifier.padding(padding)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.load_error))
                    TextButton(onClick = { reload++ }) { Text(stringResource(R.string.retry)) }
                }
            }
            d == null -> Centered(Modifier.padding(padding)) { CircularProgressIndicator() }
            else -> TitleContent(vm, nav, type, d, Modifier.padding(padding))
        }
    }
}

@Composable
private fun TitleContent(
    vm: AppViewModel,
    nav: NavController,
    type: String,
    d: GqlTitle,
    modifier: Modifier,
) {
    val context = LocalContext.current
    LazyColumn(modifier.fillMaxSize()) {
        item {
            var showPoster by remember { mutableStateOf(false) }
            if (showPoster) {
                Dialog(
                    onDismissRequest = { showPoster = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    AsyncImage(
                        model = Imdb.thumb(d.primaryImage?.url, 1280),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .clickable(interactionSource = null, indication = null) { showPoster = false }
                    )
                }
            }
            Row(Modifier.padding(16.dp)) {
                Poster(
                    d.primaryImage?.url,
                    Modifier
                        .width(110.dp)
                        .height(165.dp)
                        .clickable(interactionSource = null, indication = null) { showPoster = true },
                    width = 500
                )
                Column(Modifier.padding(start = 16.dp)) {
                    Text(d.displayTitle, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(4.dp))
                    val info = listOfNotNull(
                        d.yearText.ifBlank { null },
                        if (type == "tv") stringResource(R.string.series)
                        else d.runtime?.seconds?.takeIf { it > 0 }?.let { "${it / 60} min" },
                        d.ratingsSummary?.aggregateRating?.takeIf { it > 0 }?.let { "IMDb " + "%.1f".format(it) + "/10" },
                    ).joinToString("  •  ")
                    if (info.isNotBlank()) {
                        Text(
                            info,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val genres = d.genres?.genres.orEmpty()
                    if (genres.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            genres.joinToString { it.text },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // Directors for movies, Creator for series, Writers, ... (cast has its own row)
                    d.principalCredits.filter { it.category?.id != "cast" }.forEach { group ->
                        val names = group.credits.mapNotNull { it.name?.nameText?.text }
                        if (names.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                (group.category?.text ?: "") + ": " + names.joinToString(", "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                val overview = d.plot?.plotText?.plainText
                if (!overview.isNullOrBlank()) {
                    Text(overview, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                }
                val myRating = vm.ratingOf(d.id, type)
                Box(Modifier.align(Alignment.CenterHorizontally)) {
                    StarRating(myRating, starSize = 38.dp) {
                        vm.rate(d.id, type, d.displayTitle, d.yearText, d.primaryImage?.url, it)
                    }
                }
                Text(
                    stringResource(if (myRating > 0) R.string.rate_caption_rated else R.string.rate_caption_unrated),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                if (myRating > 0) {
                    val item = vm.itemOf(d.id, type)
                    var showEdit by remember { mutableStateOf(false) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(
                            stringResource(R.string.seen_on, item?.addedAt?.take(10).orEmpty()) +
                                (item?.note?.takeIf { it.isNotBlank() }?.let { " • $it" } ?: ""),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        IconButton(onClick = { showEdit = true }) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = stringResource(R.string.edit),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (showEdit && item != null) {
                        EditSeenDialog(
                            item = item,
                            onSave = { note, date ->
                                vm.updateDetails(d.id, type, note, date)
                                showEdit = false
                            },
                            onDismiss = { showEdit = false }
                        )
                    }
                }
                val inWatchlist = vm.statusOf(d.id, type) == Status.WATCHLIST
                TextButton(
                    onClick = {
                        vm.toggleWatchlist(d.id, type, d.displayTitle, d.yearText, d.primaryImage?.url)
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Icon(
                        painterResource(
                            if (inWatchlist) R.drawable.ic_watch_later_filled else R.drawable.ic_watch_later
                        ),
                        contentDescription = null
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (inWatchlist) R.string.watchlist_remove else R.string.watchlist_add))
                }
                val trailerUrl = d.trailerMp4
                Spacer(Modifier.height(16.dp))
                if (trailerUrl != null) {
                    TrailerPlayer(
                        url = trailerUrl,
                        thumbUrl = d.trailer?.thumbnail?.url ?: d.primaryImage?.url,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    OutlinedButton(
                        onClick = {
                            val q = URLEncoder.encode("${d.displayTitle} ${d.yearText} trailer", "UTF-8")
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$q"))
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.trailer))
                    }
                }
            }
        }
        val cast = d.credits?.edges.orEmpty().mapNotNull { it.node }.filter { it.name != null }
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp)
            ) {
                Text(
                    stringResource(if (cast.isNotEmpty()) R.string.cast else R.string.team),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { nav.navigate("team/${d.id}") }) {
                    Text(stringResource(R.string.full_team))
                }
            }
        }
        if (cast.isNotEmpty()) {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                    itemsIndexed(cast) { _, node ->
                        CastCard(node) { nav.navigate("person/${node.name!!.id}") }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun EditSeenDialog(item: SeenItem, onSave: (String, String) -> Unit, onDismiss: () -> Unit) {
    var note by rememberSaveable { mutableStateOf(item.note) }
    var date by rememberSaveable { mutableStateOf(item.addedAt.take(10)) }
    val dateValid = runCatching { java.time.LocalDate.parse(date.trim()) }.isSuccess
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text(stringResource(R.string.date_label)) },
                    singleLine = true,
                    isError = !dateValid
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(stringResource(R.string.note_label)) },
                    minLines = 2
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(note, date) }, enabled = dateValid) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun TrailerPlayer(url: String, thumbUrl: String?, modifier: Modifier) {
    var playing by remember { mutableStateOf(false) }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (!playing) {
            AsyncImage(
                model = Imdb.thumb(thumbUrl, 780),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            FilledIconButton(onClick = { playing = true }, modifier = Modifier.size(64.dp)) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.play_trailer),
                    modifier = Modifier.size(36.dp)
                )
            }
        } else {
            val context = LocalContext.current
            val player = remember {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(url))
                    playWhenReady = true
                    prepare()
                }
            }
            DisposableEffect(Unit) {
                onDispose { player.release() }
            }
            AndroidView(
                factory = { ctx ->
                    val view = android.view.LayoutInflater.from(ctx)
                        .inflate(R.layout.trailer_player, null) as PlayerView
                    view.player = player
                    view
                },
                modifier = Modifier.matchParentSize()
            )
        }
    }
}

@Composable
private fun CastCard(node: GqlCastNode, onClick: () -> Unit) {
    val name = node.name ?: return
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(92.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        PersonImage(
            name.primaryImage?.url,
            Modifier
                .size(76.dp)
                .clip(CircleShape)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            name.nameText?.text ?: "",
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        val character = node.characters?.firstOrNull()?.name
        if (!character.isNullOrBlank()) {
            Text(
                character,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}
