package tk.zwander.common.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tk.zwander.lockscreenwidgets.data.IDData
import java.util.TreeSet

object DebugIDsManager {
    private val oldItems = MutableStateFlow<List<String>>(listOf())

    private val sortedItems = TreeSet<IDData> { o1, o2 ->
        when {
            o1.type == IDData.IDType.ADDED && o2.type != IDData.IDType.ADDED -> -1
            o2.type == IDData.IDType.ADDED && o1.type != IDData.IDType.ADDED -> 1
            o1.type == IDData.IDType.REMOVED && o2.type == IDData.IDType.SAME -> -1
            o2.type == IDData.IDType.REMOVED && o1.type == IDData.IDType.SAME -> 1
            else -> o1.id.compareTo(o2.id)
        }
    }

    private val mutableItems = MutableStateFlow<List<IDData>>(listOf())
    val items: StateFlow<List<IDData>> = mutableItems.asStateFlow()

    fun setItems(newItems: Collection<String>) {
        val tempItems = newItems.toList()

        if (tempItems.containsAll(oldItems.value) && oldItems.value.containsAll(tempItems))
            return

        val removed = oldItems.value - tempItems.toSet()
        val added = tempItems - oldItems.value.toSet()
        val same = oldItems.value - removed.toSet() - added.toSet()

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

        oldItems.value = listOf()
        oldItems.value = tempItems

        sortedItems.clear()
        sortedItems.addAll(newList)

        mutableItems.value = sortedItems.toList()
    }
}