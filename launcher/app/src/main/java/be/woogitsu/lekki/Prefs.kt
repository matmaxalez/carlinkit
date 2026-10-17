package be.woogitsu.lekki

import android.content.ComponentName
import android.content.Context

/** Cała konfiguracja launchera w jednym pliku SharedPreferences. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("lekki", Context.MODE_PRIVATE)

    /** Kafle ekranu głównego w kolejności, jako spłaszczone ComponentName. */
    var pinned: List<ComponentName>
        get() = sp.getString(KEY_PINNED, "").orEmpty()
            .split('\n')
            .mapNotNull { ComponentName.unflattenFromString(it) }
        set(value) = sp.edit()
            .putString(KEY_PINNED, value.joinToString("\n") { it.flattenToString() })
            .apply()

    val hasPinnedConfig: Boolean get() = sp.contains(KEY_PINNED)

    /** 0 = mały, 1 = średni, 2 = duży. */
    var tileSize: Int
        get() = sp.getInt(KEY_TILE_SIZE, 1)
        set(value) = sp.edit().putInt(KEY_TILE_SIZE, value).apply()

    var showLabels: Boolean
        get() = sp.getBoolean(KEY_LABELS, true)
        set(value) = sp.edit().putBoolean(KEY_LABELS, value).apply()

    var startupApp: ComponentName?
        get() = sp.getString(KEY_STARTUP, null)?.let { ComponentName.unflattenFromString(it) }
        set(value) = sp.edit().putString(KEY_STARTUP, value?.flattenToString()).apply()

    var lastBootCount: Int
        get() = sp.getInt(KEY_BOOT, -1)
        set(value) = sp.edit().putInt(KEY_BOOT, value).apply()

    private companion object {
        const val KEY_PINNED = "pinned"
        const val KEY_TILE_SIZE = "tile_size"
        const val KEY_LABELS = "labels"
        const val KEY_STARTUP = "startup"
        const val KEY_BOOT = "boot_count"
    }
}
