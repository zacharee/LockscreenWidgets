package tk.zwander.common.util

import android.os.SystemClock
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import tk.zwander.lockscreenwidgets.data.IDData

object DebugIDsManager {
    private const val ID_LIST_INTERACTION_GRACE_MS = 2_000L

    private val oldItems = hashMapOf<Int, Set<String>>()

    private val sortedItems: HashMap<Int, List<IDData>> = hashMapOf()

    private val mutableItems = MutableStateFlow<Map<Int, List<IDData>>>(mapOf())
    val items: StateFlow<Map<Int, List<IDData>>> = mutableItems.asStateFlow()

    private val idListWidgetIds = atomic<Set<Int>>(emptySet())
    private val idListLastInteraction = atomic(0L)

    /**
     * Whether anything is currently consuming the full visible-ID list. If nothing is,
     * accessibility passes can skip collecting every visible view ID.
     */
    val shouldCollectIds: Boolean
        get() = mutableItems.subscriptionCount.value > 0 || idListWidgetIds.value.isNotEmpty()

    /**
     * Replaces the known ID list widget IDs. [android.appwidget.AppWidgetManager.getAppWidgetIds]
     * returns every instance for this provider across all hosts, so this is authoritative.
     */
    fun setExistingIdListWidgetIds(ids: Collection<Int>) {
        idListWidgetIds.value = ids.toSet()
    }

    /**
     * [android.appwidget.AppWidgetProvider.onUpdate] only reports the IDs belonging to the
     * host that sent the broadcast, so merge instead of replacing.
     */
    fun addIdListWidgetIds(ids: Collection<Int>) {
        idListWidgetIds.value += ids.toSet()
    }

    fun removeIdListWidgetIds(ids: Collection<Int>) {
        idListWidgetIds.value -= ids.toSet()
    }

    fun clearIdListWidgetIds() {
        idListWidgetIds.value = emptySet()
    }

    /**
     * True while the user is touching a hosted widget, plus a short grace period after the
     * last touch so an in-progress fling is covered too. Replacing a widget's
     * [android.widget.RemoteViews] collection in the middle of a scroll can trip the
     * platform's `RemoteCollectionItemsAdapter` (it queries item positions without a
     * bounds check), so the ID list widget defers updates until this is false.
     */
    val isIdListInteracting: Boolean
        get() = SystemClock.elapsedRealtime() - idListLastInteraction.value <
                ID_LIST_INTERACTION_GRACE_MS

    fun notifyIdListInteraction() {
        idListLastInteraction.value = SystemClock.elapsedRealtime()
    }

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
