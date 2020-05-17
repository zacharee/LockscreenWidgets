package tk.zwander.lockscreenwidgets.data

data class WidgetData(
    val id: Int
) {
    override fun equals(other: Any?): Boolean {
        return other is WidgetData && other.id == id
    }

    override fun hashCode(): Int {
        return id.toString().hashCode()
    }
}