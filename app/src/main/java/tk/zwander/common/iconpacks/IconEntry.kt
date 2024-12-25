package tk.zwander.common.iconpacks

data class IconEntry(
    val packPackageName: String,
    val name: String,
    val type: IconType,
) {
    fun resolveDynamicCalendar(day: Int): IconEntry {
        check(type == IconType.Calendar) { "type is not calendar" }
        return IconEntry(packPackageName, "$name${day}", IconType.Normal)
    }
}

enum class IconType {
    Normal,
    Calendar,
}