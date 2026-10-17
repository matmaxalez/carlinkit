package be.woogitsu.lekki

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.text.Collator
import java.util.concurrent.Executors

data class AppEntry(val label: String, val component: ComponentName)

/**
 * Lista aplikacji i ikon. Wszystko, co dotyka PackageManagera, idzie
 * na jeden wątek w tle, żeby UI nigdy nie czekało.
 */
class AppRepository(context: Context) {
    private val pm: PackageManager = context.packageManager
    private val ownPackage = context.packageName
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val icons = LruCache<ComponentName, Drawable>(160)

    @Volatile
    var apps: List<AppEntry> = emptyList()
        private set

    fun reload(onDone: (List<AppEntry>) -> Unit) {
        worker.execute {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val collator = Collator.getInstance()
            val list = pm.queryIntentActivities(intent, 0)
                .asSequence()
                .filter { it.activityInfo.packageName != ownPackage }
                .map {
                    AppEntry(
                        it.loadLabel(pm).toString().trim(),
                        ComponentName(it.activityInfo.packageName, it.activityInfo.name)
                    )
                }
                .distinctBy { it.component }
                .sortedWith { a, b -> collator.compare(a.label, b.label) }
                .toList()
            main.post {
                apps = list
                icons.evictAll()
                onDone(list)
            }
        }
    }

    /** Dopasowuje zapisany kafel do aktualnej listy (np. po aktualizacji aplikacji zmieniła się aktywność). */
    fun resolve(saved: ComponentName): AppEntry? =
        apps.firstOrNull { it.component == saved }
            ?: apps.firstOrNull { it.component.packageName == saved.packageName }

    fun findPackage(pkg: String): AppEntry? = apps.firstOrNull { it.component.packageName == pkg }

    fun bindIcon(view: ImageView, component: ComponentName) {
        view.tag = component
        val cached = icons.get(component)
        if (cached != null) {
            view.setImageDrawable(cached)
            return
        }
        view.setImageDrawable(null)
        worker.execute {
            val icon = try {
                pm.getActivityIcon(component)
            } catch (e: PackageManager.NameNotFoundException) {
                pm.defaultActivityIcon
            }
            main.post {
                icons.put(component, icon)
                if (view.tag == component) view.setImageDrawable(icon)
            }
        }
    }
}
