package be.woogitsu.lekki

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.GridView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast

class HomeActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var repo: AppRepository

    private lateinit var homeView: View
    private lateinit var homeGrid: GridView
    private lateinit var homeEmpty: TextView
    private lateinit var drawerView: View
    private lateinit var drawerGrid: GridView

    private val homeAdapter = TileAdapter()
    private val drawerAdapter = DrawerAdapter()
    private var tiles: List<AppEntry> = emptyList()

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = reload()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        prefs = Prefs(this)
        repo = AppRepository(this)

        homeView = findViewById(R.id.home)
        homeGrid = findViewById(R.id.home_grid)
        homeEmpty = findViewById(R.id.home_empty)
        drawerView = findViewById(R.id.drawer)
        drawerGrid = findViewById(R.id.drawer_grid)

        homeGrid.adapter = homeAdapter
        drawerGrid.adapter = drawerAdapter

        homeGrid.setOnItemClickListener { _, _, pos, _ ->
            if (pos < tiles.size) launch(tiles[pos].component) else showDrawer(true)
        }
        homeGrid.setOnItemLongClickListener { _, _, pos, _ ->
            if (pos < tiles.size) tileMenu(pos)
            true
        }
        drawerGrid.setOnItemClickListener { _, _, pos, _ -> launch(repo.apps[pos].component) }
        drawerGrid.setOnItemLongClickListener { _, _, pos, _ ->
            drawerMenu(repo.apps[pos])
            true
        }
        findViewById<Button>(R.id.settings).setOnClickListener { settingsMenu() }
        findViewById<Button>(R.id.drawer_back).setOnClickListener { showDrawer(false) }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, filter)

        applyLayout()
        reload()
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Przycisk HOME przy otwartej szufladzie wraca na ekran główny.
        showDrawer(false)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyLayout()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Launcher nie ma dokąd wracać; zamykamy tylko szufladę.
        if (drawerView.visibility == View.VISIBLE) showDrawer(false)
    }

    private fun reload() {
        repo.reload {
            if (!prefs.hasPinnedConfig) prefs.pinned = defaultPins()
            refreshTiles()
            drawerAdapter.notifyDataSetChanged()
            maybeLaunchStartupApp()
        }
    }

    private fun defaultPins(): List<ComponentName> =
        DEFAULT_PACKAGES.mapNotNull { repo.findPackage(it)?.component }

    private fun refreshTiles() {
        tiles = prefs.pinned.mapNotNull { repo.resolve(it) }.distinctBy { it.component }
        homeEmpty.visibility = if (tiles.isEmpty()) View.VISIBLE else View.GONE
        homeAdapter.notifyDataSetChanged()
    }

    private fun savePins(list: List<AppEntry>) {
        prefs.pinned = list.map { it.component }
        refreshTiles()
    }

    /** Liczba kolumn wynika z realnej szerokości okna, nie ze sztywnej rozdzielczości. */
    private fun applyLayout() {
        val widthDp = resources.configuration.screenWidthDp
        val tileDp = when (prefs.tileSize) { 0 -> 130; 2 -> 230; else -> 170 }
        homeGrid.numColumns = (widthDp / tileDp).coerceIn(2, 6)
        drawerGrid.numColumns = (widthDp / 96).coerceIn(3, 10)
        homeAdapter.notifyDataSetChanged()
    }

    private fun showDrawer(show: Boolean) {
        drawerView.visibility = if (show) View.VISIBLE else View.GONE
        homeView.visibility = if (show) View.GONE else View.VISIBLE
        if (show) drawerGrid.setSelection(0)
    }

    private fun launch(component: ComponentName) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            val fallback = packageManager.getLaunchIntentForPackage(component.packageName)
            if (fallback != null) startActivity(fallback)
            else toast(getString(R.string.cannot_open, component.packageName))
        } catch (e: SecurityException) {
            toast(getString(R.string.cannot_open, component.packageName))
        }
    }

    private fun maybeLaunchStartupApp() {
        val boot = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)
        if (boot == prefs.lastBootCount) return
        prefs.lastBootCount = boot
        prefs.startupApp?.let { saved -> repo.resolve(saved)?.let { launch(it.component) } }
    }

    private fun tileMenu(pos: Int) {
        val entry = tiles[pos]
        val options = arrayOf(
            getString(R.string.move_left),
            getString(R.string.move_right),
            getString(R.string.remove_from_home),
            getString(R.string.app_info),
        )
        AlertDialog.Builder(this)
            .setTitle(entry.label)
            .setItems(options) { _, which ->
                val list = tiles.toMutableList()
                when (which) {
                    0 -> if (pos > 0) { list.add(pos - 1, list.removeAt(pos)); savePins(list) }
                    1 -> if (pos < list.size - 1) { list.add(pos + 1, list.removeAt(pos)); savePins(list) }
                    2 -> { list.removeAt(pos); savePins(list) }
                    3 -> openAppInfo(entry.component.packageName)
                }
            }
            .show()
    }

    private fun drawerMenu(entry: AppEntry) {
        val pinned = tiles.any { it.component == entry.component }
        val options = arrayOf(
            getString(if (pinned) R.string.remove_from_home else R.string.add_to_home),
            getString(R.string.app_info),
            getString(R.string.uninstall),
        )
        AlertDialog.Builder(this)
            .setTitle(entry.label)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> savePins(if (pinned) tiles.filter { it.component != entry.component } else tiles + entry)
                    1 -> openAppInfo(entry.component.packageName)
                    2 -> safeStart(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + entry.component.packageName)))
                }
            }
            .show()
    }

    private fun settingsMenu() {
        val sizes = arrayOf(R.string.size_small, R.string.size_medium, R.string.size_large)
        val startup = prefs.startupApp?.let { repo.resolve(it)?.label } ?: getString(R.string.none)
        val options = arrayOf(
            getString(R.string.tile_size, getString(sizes[prefs.tileSize])),
            getString(if (prefs.showLabels) R.string.labels_on else R.string.labels_off),
            getString(R.string.startup_app, startup),
            getString(R.string.wifi_settings),
            getString(R.string.bluetooth_settings),
            getString(R.string.android_settings),
            getString(R.string.home_settings),
            getString(R.string.dev_settings),
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.settings)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { prefs.tileSize = (prefs.tileSize + 1) % 3; applyLayout() }
                    1 -> { prefs.showLabels = !prefs.showLabels; homeAdapter.notifyDataSetChanged() }
                    2 -> chooseStartupApp()
                    3 -> safeStart(Intent(Settings.ACTION_WIFI_SETTINGS))
                    4 -> safeStart(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                    5 -> safeStart(Intent(Settings.ACTION_SETTINGS))
                    6 -> safeStart(Intent(Settings.ACTION_HOME_SETTINGS))
                    7 -> safeStart(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
                }
            }
            .show()
    }

    private fun chooseStartupApp() {
        val apps = repo.apps
        val labels = arrayOf(getString(R.string.none)) + apps.map { it.label }
        AlertDialog.Builder(this)
            .setTitle(R.string.choose_startup)
            .setItems(labels) { _, which ->
                prefs.startupApp = if (which == 0) null else apps[which - 1].component
            }
            .show()
    }

    private fun openAppInfo(pkg: String) =
        safeStart(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))

    private fun safeStart(intent: Intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            // Firmware Carlinkita bywa okrojony; zwykły ekran ustawień jako zapas.
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (ignored: ActivityNotFoundException) {
                toast(getString(R.string.cannot_open, intent.action ?: ""))
            }
        }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    /** Kafle ekranu głównego + stały kafel „Aplikacje” na końcu. */
    private inner class TileAdapter : BaseAdapter() {
        override fun getCount() = tiles.size + 1
        override fun getItem(position: Int): Any? = tiles.getOrNull(position)
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(parent.context).inflate(R.layout.item_tile, parent, false)
            val grid = parent as GridView
            val colWidth = grid.columnWidth.takeIf { it > 0 } ?: (grid.width / grid.numColumns.coerceAtLeast(1))
            val height = (colWidth * 0.8f).toInt().coerceAtLeast(1)
            view.layoutParams = (view.layoutParams as? AbsListView.LayoutParams
                ?: AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)).also { it.height = height }

            val icon = view.findViewById<ImageView>(R.id.icon)
            val label = view.findViewById<TextView>(R.id.label)
            val iconSize = (height * 0.45f).toInt()
            icon.layoutParams.width = iconSize
            icon.layoutParams.height = iconSize
            label.visibility = if (prefs.showLabels) View.VISIBLE else View.GONE

            val entry = tiles.getOrNull(position)
            if (entry != null) {
                label.text = entry.label
                repo.bindIcon(icon, entry.component)
            } else {
                label.setText(R.string.all_apps)
                icon.tag = null
                icon.setImageResource(R.drawable.ic_apps)
            }
            return view
        }
    }

    private inner class DrawerAdapter : BaseAdapter() {
        override fun getCount() = repo.apps.size
        override fun getItem(position: Int): Any = repo.apps[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
            val entry = repo.apps[position]
            view.findViewById<TextView>(R.id.label).text = entry.label
            repo.bindIcon(view.findViewById(R.id.icon), entry.component)
            return view
        }
    }

    private companion object {
        /** Pierwsze uruchomienie: przypinamy tylko te, które są zainstalowane. */
        val DEFAULT_PACKAGES = listOf(
            "com.waze",
            "com.google.android.apps.maps",
            "com.spotify.music",
            "com.google.android.youtube",
            "com.google.android.apps.youtube.music",
            "com.wbd.stream",
            "com.hbo.hbonow",
            "com.netflix.mediaclient",
            "com.android.vending",
        )
    }
}
