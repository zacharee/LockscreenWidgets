package tk.zwander.lockscreenwidgets.appwidget

import android.content.Context
import android.graphics.Color
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.recyclerview.widget.SortedList
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.data.IDData

/**
 * Essentially an adapter to host the current ID list in Lockscreen Widgets' widget
 */
class IDWidgetFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private val oldItems = ArrayList<String>()

    private val tempItems = ArrayList<String>()

    private val items = SortedList(IDData::class.java, object: SortedList.Callback<IDData>() {
        override fun areItemsTheSame(item1: IDData?, item2: IDData?): Boolean {
            return item1 == item2
        }

        override fun areContentsTheSame(oldItem: IDData?, newItem: IDData?): Boolean {
            return oldItem?.id == newItem?.id
        }

        override fun compare(o1: IDData, o2: IDData): Int {
            return when {
                o1.type == IDData.IDType.ADDED && o2.type != IDData.IDType.ADDED -> -1
                o2.type == IDData.IDType.ADDED && o1.type != IDData.IDType.ADDED -> 1
                o1.type == IDData.IDType.REMOVED && o2.type == IDData.IDType.SAME -> -1
                o2.type == IDData.IDType.REMOVED && o1.type == IDData.IDType.SAME -> 1
                else -> o1.id.compareTo(o2.id)
            }
        }

        override fun onInserted(position: Int, count: Int) {}
        override fun onRemoved(position: Int, count: Int) {}
        override fun onChanged(position: Int, count: Int) {}
        override fun onMoved(fromPosition: Int, toPosition: Int) {}
    })

    @Synchronized
    fun setItems(newItems: Collection<String>) {
        tempItems.clear()
        tempItems.addAll(newItems)
    }

    override fun onCreate() {}
    override fun onDestroy() {}
    override fun onDataSetChanged() {
        if (tempItems.containsAll(oldItems) && oldItems.containsAll(tempItems))
            return

        val removed = oldItems - tempItems.toSet()
        val added = tempItems - oldItems.toSet()
        val same = oldItems - removed.toSet() - added.toSet()

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

        oldItems.clear()
        oldItems.addAll(tempItems)

        items.replaceAll(newList)
    }

    @Synchronized
    override fun getCount(): Int {
        return items.size()
    }

    @Synchronized
    override fun getViewAt(position: Int): RemoteViews? {
        //The list might be replaced before onDataSetChanged() is called,
        //which can lead to indexing issues.
        if (position >= count) return null

        return RemoteViews(context.packageName, R.layout.id_list_widget_item).apply {
            val item = items[position]
            setTextViewText(R.id.id_list_item, item.id)
            setTextColor(R.id.id_list_item, when (item.type) {
                IDData.IDType.ADDED -> Color.GREEN
                IDData.IDType.REMOVED -> Color.RED
                IDData.IDType.SAME -> Color.WHITE
            })
        }
    }

    @Synchronized
    override fun getItemId(position: Int): Long {
        return try {
            items.get(position).hashCode().toLong()
        } catch (_: IndexOutOfBoundsException) {
            -1
        }
    }

    override fun getLoadingView(): RemoteViews? {
        return null
    }

    override fun getViewTypeCount(): Int {
        return 1
    }

    override fun hasStableIds(): Boolean {
        return false
    }
}