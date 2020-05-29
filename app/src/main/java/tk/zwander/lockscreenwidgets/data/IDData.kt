package tk.zwander.lockscreenwidgets.data

data class IDData(
    val id: String,
    var type: IDType
) {
    enum class IDType {
        REMOVED,
        ADDED,
        SAME
    }

    override fun equals(other: Any?): Boolean {
        return other is IDData && other.id == id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}