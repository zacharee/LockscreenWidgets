package tk.zwander.common.util

import android.annotation.SuppressLint
import android.content.Context
import tk.zwander.common.host.widgetHostCompat

val Context.shortcutIdManager: ShortcutIdManager
    get() = ShortcutIdManager.getInstance(this)

class ShortcutIdManager private constructor(private val context: Context) {
    companion object {
        @SuppressLint("StaticFieldLeak")
        private var instance: ShortcutIdManager? = null

        @Synchronized
        fun getInstance(context: Context): ShortcutIdManager {
            return instance ?: ShortcutIdManager(context.safeApplicationContext).apply {
                instance = this
            }
        }
    }

    private val host by lazy { context.widgetHostCompat }

    @SuppressLint("NewApi")
    fun allocateShortcutId(): Int {
        val current = context.prefManager.shortcutIds

        var id = host.allocateAppWidgetId()

        while (current.contains(id.toString())) {
            id = host.allocateAppWidgetId()
        }

        context.prefManager.shortcutIds = current.apply { add(id.toString()) }

        return id
    }

    fun removeShortcutId(id: Int) {
        context.prefManager.shortcutIds = context.prefManager.shortcutIds.apply { remove(id.toString()) }
        context.prefManager.shortcutOverrideIcons = context.prefManager.shortcutOverrideIcons.apply { remove(id) }
        host.deleteAppWidgetId(id)
    }
}