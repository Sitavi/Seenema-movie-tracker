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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Locale

// ---------- Search (IMDb suggestion API) ----------

@Serializable
data class SuggestImage(@SerialName("imageUrl") val imageUrl: String? = null)

@Serializable
data class SuggestItem(
    val id: String = "",
    val l: String = "", // display title / name
    val q: String? = null, // human readable kind ("feature", "TV series", ...)
    val qid: String? = null, // machine kind ("movie", "tvSeries", "videoGame", ...)
    val s: String? = null, // headline actors, or a person's known-for line
    val y: Int? = null,
    val i: SuggestImage? = null,
) {
    val isPerson: Boolean get() = id.startsWith("nm")
    val isTitle: Boolean get() = id.startsWith("tt")
    val mediaType: String get() = if (qid?.startsWith("tv") == true && qid != "tvMovie") "tv" else "movie"
    val year: String get() = y?.toString() ?: ""
}

// ---------- Details (IMDb GraphQL API) ----------

@Serializable data class GqlText(val text: String = "")
@Serializable data class GqlPlainText(val plainText: String? = null)
@Serializable data class GqlImage(val url: String? = null)
@Serializable data class GqlYear(val year: Int? = null)
@Serializable data class GqlPlot(val plotText: GqlPlainText? = null)
@Serializable data class GqlRatings(val aggregateRating: Double? = null)
@Serializable data class GqlRuntime(val seconds: Int? = null)
@Serializable data class GqlGenre(val text: String = "")
@Serializable data class GqlGenres(val genres: List<GqlGenre> = emptyList())
@Serializable data class GqlTitleType(val id: String = "")
@Serializable data class GqlCharacter(val name: String = "")

@Serializable
data class GqlName(
    val id: String = "",
    val nameText: GqlText? = null,
    val primaryImage: GqlImage? = null,
)

@Serializable data class GqlCategory(val id: String = "", val text: String = "")

@Serializable
data class GqlCastNode(
    val name: GqlName? = null,
    val characters: List<GqlCharacter>? = null,
    val jobs: List<GqlText>? = null,
    val category: GqlCategory? = null,
) {
    /** Character(s) for cast, specific job(s) for crew, empty when IMDb has neither. */
    val roleText: String
        get() = characters?.mapNotNull { it.name.ifBlank { null } }?.joinToString(", ")
            ?.ifBlank { null }
            ?: jobs?.mapNotNull { it.text.ifBlank { null } }?.joinToString(", ")
            ?: ""
}

@Serializable
data class GqlPrincipalCredit(
    val category: GqlCategory? = null,
    val credits: List<GqlCastNode> = emptyList(),
)

@Serializable data class GqlCastEdge(val node: GqlCastNode? = null)
@Serializable data class GqlPageInfo(val hasNextPage: Boolean = false, val endCursor: String? = null)

@Serializable
data class GqlCastCredits(
    val edges: List<GqlCastEdge> = emptyList(),
    val pageInfo: GqlPageInfo? = null,
)

@Serializable data class GqlValue(val value: String? = null)

@Serializable
data class GqlPlaybackUrl(
    val url: String? = null,
    val videoMimeType: String? = null,
    val displayName: GqlValue? = null,
)

@Serializable
data class GqlVideoNode(
    val id: String = "",
    val name: GqlValue? = null,
    val thumbnail: GqlImage? = null,
    val playbackURLs: List<GqlPlaybackUrl> = emptyList(),
)

@Serializable data class GqlVideoEdge(val node: GqlVideoNode? = null)
@Serializable data class GqlVideos(val edges: List<GqlVideoEdge> = emptyList())

@Serializable
data class GqlTitle(
    val id: String = "",
    val titleText: GqlText? = null,
    val titleType: GqlTitleType? = null,
    val releaseYear: GqlYear? = null,
    val plot: GqlPlot? = null,
    val ratingsSummary: GqlRatings? = null,
    val runtime: GqlRuntime? = null,
    val genres: GqlGenres? = null,
    val primaryImage: GqlImage? = null,
    val credits: GqlCastCredits? = null,
    val primaryVideos: GqlVideos? = null,
    val principalCredits: List<GqlPrincipalCredit> = emptyList(),
) {
    val displayTitle: String get() = titleText?.text ?: ""
    val yearText: String get() = releaseYear?.year?.toString() ?: ""
    val isTv: Boolean get() = titleType?.id?.startsWith("tv") == true && titleType?.id != "tvMovie"
    val trailer: GqlVideoNode? get() = primaryVideos?.edges?.firstOrNull()?.node
    val trailerMp4: String?
        get() = trailer?.playbackURLs?.let { urls ->
            (urls.firstOrNull { it.videoMimeType == "MP4" && it.displayName?.value == "480p" }
                ?: urls.firstOrNull { it.videoMimeType == "MP4" })?.url
        }
}

@Serializable data class GqlTitleData(val title: GqlTitle? = null)
@Serializable data class GqlTitleResponse(val data: GqlTitleData? = null)

@Serializable data class GqlProfession(val category: GqlText? = null)
@Serializable data class GqlBio(val text: GqlPlainText? = null)
@Serializable data class GqlFilmNode(val title: GqlTitle? = null)
@Serializable data class GqlFilmEdge(val node: GqlFilmNode? = null)

@Serializable
data class GqlFilmCredits(
    val edges: List<GqlFilmEdge> = emptyList(),
    val pageInfo: GqlPageInfo? = null,
)

@Serializable
data class GqlPerson(
    val id: String = "",
    val nameText: GqlText? = null,
    val primaryImage: GqlImage? = null,
    val primaryProfessions: List<GqlProfession> = emptyList(),
    val bio: GqlBio? = null,
    val credits: GqlFilmCredits? = null,
)

@Serializable data class GqlNameData(val name: GqlPerson? = null)
@Serializable data class GqlNameResponse(val data: GqlNameData? = null)

@Serializable
data class GqlSearchEntity(
    val id: String = "",
    val titleText: GqlText? = null,
    val titleType: GqlTitleType? = null,
    val releaseYear: GqlYear? = null,
    val nameText: GqlText? = null,
    val primaryImage: GqlImage? = null,
    val primaryProfessions: List<GqlProfession> = emptyList(),
)

@Serializable data class GqlSearchNode(val entity: GqlSearchEntity? = null)
@Serializable data class GqlSearchEdge(val node: GqlSearchNode? = null)
@Serializable data class GqlMainSearch(val edges: List<GqlSearchEdge> = emptyList())
@Serializable data class GqlSearchData(val mainSearch: GqlMainSearch? = null)
@Serializable data class GqlSearchResponse(val data: GqlSearchData? = null)

// ---------- Client ----------

object Imdb {
    /** Title kinds worth listing in a filmography or search results. */
    val listableTypes = setOf("movie", "tvSeries", "tvMiniSeries", "tvMovie", "tvSpecial", "short", "video")

    private val client = OkHttpClient()
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private suspend fun graphql(query: String): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject { put("query", query) }.toString()
            .toRequestBody("application/json".toMediaType())
        // Titles and plots come back in the device language when IMDb has a
        // translation; anything unsupported silently falls back to English.
        val locale = Locale.getDefault()
        // IMDb's endpoint only answers requests that carry these site headers.
        val request = Request.Builder()
            .url("https://api.graphql.imdb.com/")
            .post(body)
            .header("Origin", "https://www.imdb.com")
            .header("Referer", "https://www.imdb.com/")
            .header("x-imdb-user-language", locale.toLanguageTag())
            .apply { if (locale.country.isNotBlank()) header("x-imdb-user-country", locale.country) }
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            resp.body?.string() ?: throw IOException("empty body")
        }
    }

    /** Search titles and people; results come back in the device language. */
    suspend fun search(query: String): List<SuggestItem> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val term = q.replace("\\", "\\\\").replace("\"", "\\\"")
        val text = graphql(
            """query { mainSearch(first: 20, options: { searchTerm: "$term", type: [TITLE, NAME] }) { edges { node { entity { ... on Title { id titleText { text } titleType { id } releaseYear { year } primaryImage { url } } ... on Name { id nameText { text } primaryImage { url } primaryProfessions { category { text } } } } } } } }"""
        )
        return json.decodeFromString<GqlSearchResponse>(text).data?.mainSearch?.edges.orEmpty()
            .mapNotNull { it.node?.entity }
            .mapNotNull { e ->
                when {
                    e.id.startsWith("tt") && e.titleType?.id in listableTypes -> SuggestItem(
                        id = e.id,
                        l = e.titleText?.text ?: "",
                        qid = e.titleType?.id,
                        y = e.releaseYear?.year,
                        i = e.primaryImage?.url?.let { SuggestImage(it) },
                    )
                    e.id.startsWith("nm") -> SuggestItem(
                        id = e.id,
                        l = e.nameText?.text ?: "",
                        s = e.primaryProfessions.mapNotNull { p -> p.category?.text }
                            .joinToString(", ").ifBlank { null },
                        i = e.primaryImage?.url?.let { SuggestImage(it) },
                    )
                    else -> null
                }
            }
    }

    suspend fun title(id: String): GqlTitle {
        val text = graphql(
            """query { title(id: "$id") { id titleText { text } titleType { id } releaseYear { year } plot { plotText { plainText } } ratingsSummary { aggregateRating } runtime { seconds } genres { genres { text } } primaryImage { url } principalCredits { category { id text } credits { name { id nameText { text } } } } primaryVideos(first: 1) { edges { node { id name { value } thumbnail { url } playbackURLs { url videoMimeType displayName { value } } } } } credits(first: 25, filter: { categories: ["actor", "actress"] }) { edges { node { name { id nameText { text } primaryImage { url } } ... on Cast { characters { name } } } } } } }"""
        )
        return json.decodeFromString<GqlTitleResponse>(text).data?.title
            ?: throw IOException("title not found")
    }

    /** Person with their complete list of credited titles (any role), page by page. */
    suspend fun person(id: String): GqlPerson {
        var person: GqlPerson? = null
        val edges = mutableListOf<GqlFilmEdge>()
        var cursor: String? = null
        repeat(4) { // safety cap: 4 pages x 250 = 1000 credits
            val after = cursor?.let { """, after: "$it"""" } ?: ""
            val text = graphql(
                """query { name(id: "$id") { id nameText { text } primaryImage { url } primaryProfessions { category { text } } bio { text { plainText } } credits(first: 250$after) { pageInfo { hasNextPage endCursor } edges { node { title { id titleText { text } titleType { id } releaseYear { year } primaryImage { url } } } } } } }"""
            )
            val page = json.decodeFromString<GqlNameResponse>(text).data?.name
                ?: throw IOException("person not found")
            if (person == null) person = page
            edges += page.credits?.edges.orEmpty()
            val info = page.credits?.pageInfo
            if (info?.hasNextPage != true || info.endCursor == null) {
                return person!!.copy(credits = GqlFilmCredits(edges))
            }
            cursor = info.endCursor
        }
        return person!!.copy(credits = GqlFilmCredits(edges))
    }

    /** Every credited person on the title, fetched page by page. */
    suspend fun team(id: String): List<GqlCastNode> {
        val all = mutableListOf<GqlCastNode>()
        var cursor: String? = null
        repeat(12) { // safety cap: 12 pages x 250 = 3000 people
            val after = cursor?.let { """, after: "$it"""" } ?: ""
            val text = graphql(
                """query { title(id: "$id") { credits(first: 250$after) { pageInfo { hasNextPage endCursor } edges { node { category { id text } name { id nameText { text } primaryImage { url } } ... on Cast { characters { name } } ... on Crew { jobs { text } } } } } } }"""
            )
            val credits = json.decodeFromString<GqlTitleResponse>(text).data?.title?.credits
                ?: throw IOException("team not found")
            all += credits.edges.mapNotNull { it.node }
            val page = credits.pageInfo
            if (page?.hasNextPage != true || page.endCursor == null) return all
            cursor = page.endCursor
        }
        return all
    }

    /** IMDb images accept size hints in the URL; keeps list thumbnails small. */
    fun thumb(url: String?, width: Int = 280): String? =
        url?.replace("._V1_", "._V1_QL75_UX${width}_")
}
