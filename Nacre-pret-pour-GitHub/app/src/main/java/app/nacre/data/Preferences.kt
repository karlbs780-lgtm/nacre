package app.nacre.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    val palette: String = "lilac", val motion: Boolean = true,
    val visualizer: Boolean = true, val playerStyle: String = "square",
    val exclusions: Set<String> = emptySet()
)
class Preferences(context: Context) {
    val storage = context.getSharedPreferences("nacre", Context.MODE_PRIVATE)
    private fun read() = Settings(
        storage.getString("palette", "lilac") ?: "lilac",
        storage.getBoolean("motion", true), storage.getBoolean("visualizer", true),
        storage.getString("playerStyle", "square") ?: "square",
        storage.getStringSet("exclusions", emptySet())?.toSet() ?: emptySet()
    )
    private val mutable = MutableStateFlow(read())
    val state = mutable.asStateFlow()
    fun update(value: Settings) {
        storage.edit().putString("palette", value.palette).putBoolean("motion", value.motion)
            .putBoolean("visualizer", value.visualizer).putString("playerStyle", value.playerStyle)
            .putStringSet("exclusions", value.exclusions.toSet()).apply()
        mutable.value = value
    }
}
