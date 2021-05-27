package tk.zwander.lockscreenwidgets.data

/**
 * Persistent data for a widget added to the frame.
 * Currently only holds the ID, but is left open for
 * more potential persistent data.
 *
 * @property id the ID of the widget
 */
data class WidgetData(
    val id: Int,
    val type: WidgetType? = WidgetType.WIDGET
) {
    override fun equals(other: Any?): Boolean {
        return other is WidgetData && other.id == id
    }

    override fun hashCode(): Int {
        return id.toString().hashCode()
    }
}

enum class WidgetType {
    WIDGET,
    SHORTCUT
}