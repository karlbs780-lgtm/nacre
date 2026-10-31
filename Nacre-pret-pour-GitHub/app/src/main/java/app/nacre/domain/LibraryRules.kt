package app.nacre.domain

import app.nacre.data.Track
import java.text.Normalizer

fun normalized(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace("\\p{M}+".toRegex(), "").lowercase(java.util.Locale.ROOT)

fun matches(track: Track, query: String): Boolean {
    val words = normalized(query).trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
    val haystack = normalized("${track.title} ${track.artist} ${track.album} ${track.genre}")
    return words.all { it in haystack }
}
fun excluded(folder: String, rules: Set<String>): Boolean = rules.any { rule ->
    val path = folder.trim('/').lowercase(java.util.Locale.ROOT)
    val prefix = rule.trim().trim('/').lowercase(java.util.Locale.ROOT)
    prefix.isNotEmpty() && (path == prefix || path.startsWith("$prefix/"))
}
fun timeLabel(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return "%d:%02d".format(java.util.Locale.ROOT, seconds / 60, seconds % 60)
}
fun <T> moved(items: List<T>, from: Int, to: Int): List<T> = items.toMutableList().apply {
    if (from in indices && to in indices && from != to) add(to, removeAt(from))
}
