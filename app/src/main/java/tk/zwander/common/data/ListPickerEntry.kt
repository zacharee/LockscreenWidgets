package tk.zwander.common.data

sealed interface ListPickerEntry<V> {
    val label: String
    val value: V

    fun getKey(): String

    data class StringEntry(
        override val label: String,
        override val value: String?,
    ) : ListPickerEntry<String?> {
        override fun getKey(): String {
            return value ?: "NULL_VALUE_$label"
        }
    }

    data class WidgetCategoryEntry(
        override val label: String,
        override val value: WidgetListFilters.Category,
    ) : ListPickerEntry<WidgetListFilters.Category> {
        override fun getKey(): String {
            return value.toString()
        }
    }
}
