package tk.zwander.common.util

import android.annotation.SuppressLint
import android.content.Context
import dev.zwander.lswinterconnect.safeApplicationContext
import tk.zwander.common.host.widgetHostCompat
import tk.zwander.lockscreenwidgets.util.FramePrefs

val Context.idManager: IDManager
    get() = IDManager.getInstance(this)

class IDManager private constructor(private val context: Context) {
    companion object {
        @SuppressLint("StaticFieldLeak")
        private var instance: IDManager? = null

        @Synchronized
        fun getInstance(context: Context): IDManager {
            return instance ?: IDManager(context.safeApplicationContext).apply {
                instance = this
            }
        }
    }

    private val host by lazy { context.widgetHostCompat }

    @SuppressLint("NewApi")
    fun allocateAndSaveShortcutId(): Int {
        val currentShortcutIds = context.prefManager.shortcutIds

        val id = allocateId()

        context.prefManager.shortcutIds = currentShortcutIds.apply { add(id.toString()) }

        return id
    }

    fun allocateId(): Int {
        val current = collectAllIds()

        var id = host.allocateAppWidgetId()

        while (current.contains(id)) {
            id = host.allocateAppWidgetId()
        }

        return id
    }

    fun removeShortcutId(id: Int) {
        context.prefManager.shortcutIds = context.prefManager.shortcutIds.apply { remove(id.toString()) }
        context.prefManager.shortcutOverrideIcons = context.prefManager.shortcutOverrideIcons.apply { remove(id) }
        host.deleteAppWidgetId(id)
    }

    fun collectAllIds(): List<Int> {
        return context.prefManager.shortcutIds.map { it.toInt() } +
                context.prefManager.currentWidgets.map { it.id } +
                context.prefManager.drawerWidgets.map { it.id } +
                context.prefManager.widgetStackWidgets.flatMap { entry -> [entry.key] + entry.value.map { it.id } } +
                context.prefManager.currentSecondaryFramesWithStringDisplay.flatMap { [frameId] ->
                    FramePrefs.getWidgetsForFrame(context, frameId).map { it.id }
                }
    }
}
