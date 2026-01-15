package tk.zwander.common.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tk.zwander.lockscreenwidgets.data.IDData
import java.util.TreeSet

object DebugIDsManager {
    private val oldItems = MutableStateFlow<Map<Int, List<String>>>(mapOf())

    private val sortedItems: HashMap<Int, TreeSet<IDData>> = hashMapOf()

    private val mutableItems = MutableStateFlow<Map<Int, List<IDData>>>(mapOf())
    val items: StateFlow<Map<Int, List<IDData>>> = mutableItems.asStateFlow()

    fun setItems(displayId: Int, newItems: Collection<String>) {
        val tempItems = newItems.toList()

        if (tempItems.containsAll(oldItems.value[displayId] ?: listOf())
            && oldItems.value[displayId]?.containsAll(tempItems) == true)
            return

        val removed = (oldItems.value[displayId] ?: listOf()) - tempItems.toSet()
        val added = tempItems - (oldItems.value[displayId]?.toSet() ?: setOf())
        val same = (oldItems.value[displayId] ?: listOf()) - removed.toSet() - added.toSet()

        val newList = ArrayList<IDData>()

        removed.forEach {
            newList.add(IDData(it, IDData.IDType.REMOVED))
        }

        added.forEach {
            newList.add(IDData(it, IDData.IDType.ADDED))
        }

        same.forEach {
            newList.add(IDData(it, IDData.IDType.SAME))
        }

        oldItems.value = oldItems.value.toMutableMap().apply {
            this[displayId] = listOf()
        }
        oldItems.value = oldItems.value.toMutableMap().apply {
            this[displayId] = tempItems
        }

        sortedItems[displayId] = createTreeSet().also { it.addAll(newList) }

        mutableItems.value = sortedItems.map { it.key to it.value.toList() }.toMap()
    }

    private fun createTreeSet() = TreeSet<IDData> { o1, o2 ->
        when {
            o1.type == IDData.IDType.ADDED && o2.type != IDData.IDType.ADDED -> -1
            o2.type == IDData.IDType.ADDED && o1.type != IDData.IDType.ADDED -> 1
            o1.type == IDData.IDType.REMOVED && o2.type == IDData.IDType.SAME -> -1
            o2.type == IDData.IDType.REMOVED && o1.type == IDData.IDType.SAME -> 1
            else -> o1.id.compareTo(o2.id)
        }
    }
}