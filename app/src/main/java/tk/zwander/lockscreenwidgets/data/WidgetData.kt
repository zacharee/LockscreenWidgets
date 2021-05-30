package tk.zwander.lockscreenwidgets.data

import android.content.Intent
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.*

/**
 * Persistent data for a widget added to the frame.
 * Currently only holds the ID, but is left open for
 * more potential persistent data.
 *
 * @property id the ID of the widget
 */
@Parcelize
open class WidgetData(
    val id: Int,
    val type: WidgetType? = WidgetType.WIDGET,
    var label: String?,
    var icon: String?,
    var iconRes: Intent.ShortcutIconResource?,
    var shortcutIntent: Intent?
) : Parcelable {
    companion object {
        fun shortcut(
            id: Int,
            label: String,
            icon: String?,
            iconRes: Intent.ShortcutIconResource?,
            shortcutIntent: Intent
        ): WidgetData {
            return WidgetData(
                id, WidgetType.SHORTCUT,
                label, icon, iconRes, shortcutIntent
            )
        }

        fun widget(
            id: Int
        ): WidgetData {
            return WidgetData(id, WidgetType.WIDGET, null, null,
                null, null)
        }
    }

    val safeType: WidgetType
        get() = type ?: WidgetType.WIDGET

    override fun equals(other: Any?): Boolean {
        return other is WidgetData && other.id == id
                && other.safeType == safeType
    }

    override fun hashCode(): Int {
        return Objects.hash(id, safeType)
    }
}

enum class WidgetType {
    WIDGET,
    SHORTCUT
}