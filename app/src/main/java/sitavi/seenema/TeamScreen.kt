package sitavi.seenema

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage

// Same section order as imdb.com's full-credits page; unknown categories go last.
private val webOrder = listOf(
    "director", "writer", "actor", "actress", "self", "producer", "composer",
    "cinematographer", "editor", "casting_director", "production_designer",
    "art_director", "set_decorator", "costume_designer", "make_up_department",
    "production_manager", "assistant_director", "art_department", "sound_department",
    "special_effects", "visual_effects", "stunts", "choreographer", "camera_department",
    "animation_department", "casting_department", "costume_department",
    "editorial_department", "location_management", "music_department",
    "script_department", "transportation_department", "additional_crew", "thanks",
)

private fun categoryRank(id: String?): Int {
    val index = webOrder.indexOf(id)
    return if (index >= 0) index else webOrder.size
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamScreen(nav: NavController, id: String) {
    var team by remember { mutableStateOf<List<GqlCastNode>?>(null) }
    var error by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(id, reload) {
        error = false
        team = try {
            Imdb.team(id)
        } catch (e: Exception) {
            error = true
            null
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        team?.size?.let { stringResource(R.string.full_team_count, it) }
                            ?: stringResource(R.string.full_team)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val members = team
        when {
            error -> Centered(Modifier.padding(padding)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.load_error))
                    TextButton(onClick = { reload++ }) { Text(stringResource(R.string.retry)) }
                }
            }
            members == null -> Centered(Modifier.padding(padding)) { CircularProgressIndicator() }
            else -> {
                val castLabel = stringResource(R.string.cast)
                val otherLabel = stringResource(R.string.other_category)
                val listState = rememberLazyListState()
                val sections = remember(members, castLabel, otherLabel) {
                    members.filter { it.name != null }
                        .groupBy {
                            when (it.category?.id) {
                                "actor", "actress", "self" -> castLabel
                                else -> it.category?.text ?: otherLabel
                            }
                        }
                        .entries.sortedBy { entry ->
                            entry.value.minOf { categoryRank(it.category?.id) }
                        }
                        .map { (category, people) -> category to people.distinctBy { it.name!!.id } }
                }
                // section name for every list row, headers included
                val rowLabels = remember(sections) {
                    buildList {
                        sections.forEach { (category, people) ->
                            add(category)
                            repeat(people.size) { add(category) }
                        }
                    }
                }
                Box(Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                sections.forEach { (category, people) ->
                    item(key = "header-$category") {
                        Text(
                            category,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
                        )
                    }
                    items(
                        people,
                        key = { "$category-${it.name!!.id}" }
                    ) { member ->
                        val name = member.name!!
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { nav.navigate("person/${name.id}") }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            PersonImage(
                                name.primaryImage?.url,
                                Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(name.nameText?.text ?: "", style = MaterialTheme.typography.bodyLarge)
                                val role = member.roleText
                                if (role.isNotBlank()) {
                                    Text(
                                        role,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                }
                FastScroller(listState) { i -> rowLabels.getOrNull(i) }
                }
            }
        }
    }
}
