package sitavi.seenema

import android.content.Context
import java.io.File

object Status {
    const val SEEN = "seen"
    const val WATCHLIST = "watchlist"
}

data class SeenItem(
    val id: String, // IMDb id, e.g. "tt0133093"
    val mediaType: String, // "movie" or "tv"
    val title: String,
    val year: String,
    val posterUrl: String,
    val status: String, // Status.SEEN or Status.WATCHLIST
    val rating: Int, // 1..5 stars, 0 if unrated / watchlist
    val note: String,
    val addedAt: String, // ISO-8601 local date-time, sorts lexicographically
)

/**
 * Persists the list as a CSV in Android/media/sitavi.seenema/
 * (user-visible through any file manager, like WhatsApp's media folder).
 */
class SeenStore(private val context: Context) {

    @Suppress("DEPRECATION")
    val file: File by lazy {
        val dir = context.externalMediaDirs.firstOrNull()?.takeIf { it.exists() || it.mkdirs() }
            ?: context.filesDir
        File(dir, "seenema.csv")
    }

    fun load(): List<SeenItem> {
        if (!file.exists()) return emptyList()
        return try {
            parseItems(file.readText())
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Parses current and older CSV layouts; rows it can't understand are skipped. */
    fun parseItems(text: String): List<SeenItem> =
        parseCsv(text).drop(1).mapNotNull { row ->
            when {
                // id,type,title,year,poster_url,status,rating,note,added_at
                row.size >= 9 && row[0].isNotBlank() -> SeenItem(
                    row[0], row[1], row[2], row[3], row[4],
                    if (row[5] == Status.WATCHLIST) Status.WATCHLIST else Status.SEEN,
                    row[6].toIntOrNull() ?: 0, row[7], row[8],
                )
                // id,type,title,year,poster_url,rating,added_at
                row.size == 7 && row[0].isNotBlank() -> SeenItem(
                    row[0], row[1], row[2], row[3], row[4],
                    Status.SEEN, row[5].toIntOrNull() ?: 0, "", row[6],
                )
                // id,type,title,year,poster_url,added_at
                row.size == 6 && row[0].isNotBlank() -> SeenItem(
                    row[0], row[1], row[2], row[3], row[4], Status.SEEN, 0, "", row[5],
                )
                else -> null
            }
        }

    @Synchronized
    fun save(items: List<SeenItem>) {
        try {
            val sb = StringBuilder("id,type,title,year,poster_url,status,rating,note,added_at\r\n")
            for (item in items) {
                sb.append(
                    listOf(
                        item.id, item.mediaType, item.title, item.year, item.posterUrl,
                        item.status,
                        // empty (not "0") when unrated, so exported data can't be
                        // read as a zero-out-of-five rating
                        if (item.rating > 0) item.rating.toString() else "",
                        item.note, item.addedAt,
                    ).joinToString(",") { esc(it) }
                ).append("\r\n")
            }
            file.writeText(sb.toString())
        } catch (_: Exception) {
        }
    }

    private fun esc(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' })
            "\"" + value.replace("\"", "\"\"") + "\""
        else value

    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
            } else when (c) {
                '"' -> inQuotes = true
                ',' -> { row.add(field.toString()); field.setLength(0) }
                '\r' -> {}
                '\n' -> { row.add(field.toString()); field.setLength(0); rows.add(row); row = mutableListOf() }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
        return rows
    }
}
