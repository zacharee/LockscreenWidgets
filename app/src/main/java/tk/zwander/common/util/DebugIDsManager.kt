package tk.zwander.common.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tk.zwander.lockscreenwidgets.data.IDData

object DebugIDsManager {
    private val oldItems = hashMapOf<Int, Set<String>>()

    private val sortedItems: HashMap<Int, List<IDData>> = hashMapOf()

    private val mutableItems = MutableStateFlow<Map<Int, List<IDData>>>(mapOf())
    val items: StateFlow<Map<Int, List<IDData>>> = mutableItems.asStateFlow()

    private val comparator = Comparator<IDData> { o1, o2 ->
        when {
            o1.type == IDData.IDType.ADDED && o2.type != IDData.IDType.ADDED -> -1
            o2.type == IDData.IDType.ADDED && o1.type != IDData.IDType.ADDED -> 1
            o1.type == IDData.IDType.REMOVED && o2.type == IDData.IDType.SAME -> -1
            o2.type == IDData.IDType.REMOVED && o1.type == IDData.IDType.SAME -> 1
            else -> o1.id.compareTo(o2.id)
        }
    }

    /**
     * @return true if items changed
     */
    fun setItems(displayId: Int, newItems: Set<String>): Boolean {
        val old = oldItems[displayId].orEmpty()
        if (old == newItems) return false

        val removed = old - newItems
        val added = newItems - old
        val same = old - removed

        val newList = ArrayList<IDData>(removed.size + added.size + same.size)

        removed.mapTo(newList) { IDData(it, IDData.IDType.REMOVED) }
        added.mapTo(newList) { IDData(it, IDData.IDType.ADDED) }
        same.mapTo(newList) { IDData(it, IDData.IDType.SAME) }

        newList.sortWith(comparator)
        sortedItems[displayId] = newList
        mutableItems.value = sortedItems.toMap()

        return true
    }
}
