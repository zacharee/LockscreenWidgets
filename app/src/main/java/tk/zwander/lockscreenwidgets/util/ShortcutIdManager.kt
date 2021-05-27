package tk.zwander.lockscreenwidgets.util

import android.annotation.SuppressLint
import android.appwidget.AppWidgetHost
import android.content.Context
import kotlin.random.Random

class ShortcutIdManager private constructor(private val context: Context, private val host: AppWidgetHost) {
    companion object {
        @SuppressLint("StaticFieldLeak")
        private var instance: ShortcutIdManager? = null

        fun getInstance(context: Context, host: AppWidgetHost): ShortcutIdManager {
            if (instance == null) instance = ShortcutIdManager(context.applicationContext, host)
            return instance!!
        }
    }

    private val prefs by lazy { PrefManager.getInstance(context) }

    @SuppressLint("NewApi")
    fun allocateShortcutId(): Int {
        val current = prefs.shortcutIds

        val random = Random(System.currentTimeMillis())
        var id = random.nextInt()

        //AppWidgetHost.appWidgetIds has existed since at least 5.1.1, just hidden
        while (current.contains(id.toString()) && host.appWidgetIds.contains(id))
            id = random.nextInt()

        prefs.shortcutIds = current.apply { add(id.toString()) }

        return id
    }

    fun removeShortcutId(id: Int) {
        prefs.shortcutIds = prefs.shortcutIds.apply { remove(id.toString()) }
        prefs.widgetSizes = prefs.widgetSizes.apply { remove(id) }
    }
}