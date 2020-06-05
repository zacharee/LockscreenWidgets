package tk.zwander.lockscreenwidgets.adapters

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.widget_page_holder.view.*
import kotlinx.coroutines.*
import tk.zwander.lockscreenwidgets.IRemoveConfirmCallback
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.activities.AddWidgetActivity
import tk.zwander.lockscreenwidgets.activities.RemoveWidgetDialogActivity
import tk.zwander.lockscreenwidgets.data.WidgetData
import tk.zwander.lockscreenwidgets.host.WidgetHostCompat
import tk.zwander.lockscreenwidgets.interfaces.ItemTouchHelperAdapter
import tk.zwander.lockscreenwidgets.observables.OnResizeObservable
import tk.zwander.lockscreenwidgets.observables.RemoveButtonObservable
import tk.zwander.lockscreenwidgets.util.calculateWidgetWidth
import tk.zwander.lockscreenwidgets.util.prefManager
import tk.zwander.lockscreenwidgets.util.pxAsDp
import java.util.*
import kotlin.collections.ArrayList

class WidgetFrameAdapter(
    private val manager: AppWidgetManager,
    private val host: WidgetHostCompat,
    private val params: WindowManager.LayoutParams,
    private val onRemoveCallback: (WidgetFrameAdapter, WidgetData) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), ItemTouchHelperAdapter, CoroutineScope by MainScope() {
    companion object {
        const val VIEW_TYPE_WIDGET = 0
        const val VIEW_TYPE_ADD = 1
    }

    val widgets = ArrayList<WidgetData>()
    val onResizeObservable = OnResizeObservable()

    var currentRemoveButtonPosition = -1
        set(value) {
            field = value
            removeButtonObservable.notifyObservers(value)
        }

    private val removeButtonObservable =
        RemoveButtonObservable()

    fun updateWidgets(newWidgets: List<WidgetData>) {
        if (widgets.isEmpty()) {
            widgets.addAll(newWidgets)
            notifyDataSetChanged()
        } else {
            val oldWidgets = widgets.toList()
            this.widgets.clear()
            this.widgets.addAll(newWidgets)

            val result = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    return oldWidgets[oldItemPosition].id == newWidgets[newItemPosition].id
                }

                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    return oldWidgets[oldItemPosition] == newWidgets[newItemPosition]
                }

                override fun getNewListSize(): Int {
                    return newWidgets.size
                }

                override fun getOldListSize(): Int {
                    return oldWidgets.size
                }
            }, true)

            result.dispatchUpdatesTo(this)
        }
    }

    override fun onMove(from: Int, to: Int): Boolean {
        return if (to < widgets.size && from < widgets.size) {
            if (from < to) {
                for (i in from until to) {
                    Collections.swap(widgets, i, i + 1)
                }
            } else {
                for (i in from downTo to + 1) {
                    Collections.swap(widgets, i, i - 1)
                }
            }
            notifyItemMoved(from, to)
            true
        } else false
    }

    override fun getItemCount(): Int {
        return if (widgets.size == 0) 1 else widgets.size
    }

    override fun getItemViewType(position: Int): Int {
        return if (widgets.size == 0) VIEW_TYPE_ADD
        else VIEW_TYPE_WIDGET
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_ADD) AddWidgetVH(inflater.inflate(R.layout.add_widget, parent, false))
        else WidgetVH(inflater.inflate(R.layout.widget_page_holder, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (position < widgets.size) {
            (holder as WidgetVH).onBind(widgets[position])
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    inner class WidgetVH(view: View) : RecyclerView.ViewHolder(view) {
        var removeButtonShown: Boolean
            get() = itemView.remove_widget.isVisible
            set(value) {
                itemView.remove_widget.isVisible = value
            }

        init {
            itemView.remove_widget.setOnClickListener {
                val newPos = adapterPosition
                if (newPos != -1) {
                    onRemoveCallback(this@WidgetFrameAdapter, widgets[newPos])
                }
            }

            removeButtonObservable.addObserver { _, arg ->
                removeButtonShown = (arg.toString().toInt() == adapterPosition)
            }

            onResizeObservable.addObserver { _, _ ->
                onResize()
            }
        }

        fun onBind(data: WidgetData) {
            itemView.apply {
                launch {
                    onResize()

                    removeButtonShown = currentRemoveButtonPosition == adapterPosition

                    val widgetInfo = withContext(Dispatchers.Main) {
                        manager.getAppWidgetInfo(data.id)
                    }

                    widget_holder.apply {
                        removeAllViews()
                        try {
                            addView(withContext(Dispatchers.Main) {
                                host.createView(itemView.context, data.id, widgetInfo).apply {
                                    val width = context.pxAsDp(itemView.width).toInt()
                                    val height = context.pxAsDp(itemView.height).toInt()
                                    updateAppWidgetSize(null, width, height, width, height)
                                }
                            })
                        } catch (e: SecurityException) {
                            Toast.makeText(context, resources.getString(R.string.bind_widget_error, widgetInfo.provider), Toast.LENGTH_LONG).show()
                            context.prefManager.currentWidgets = context.prefManager.currentWidgets.apply {
                                remove(data)
                                host.deleteAppWidgetId(data.id)
                            }
                        }
                    }
                }
            }
        }

        fun onResize() {
            itemView.apply {
                layoutParams = (layoutParams as ViewGroup.LayoutParams).apply {
                    width = calculateWidgetWidth(params.width)
                }
            }
        }
    }

    inner class AddWidgetVH(view: View) : RecyclerView.ViewHolder(view) {
        init {
            itemView.setOnClickListener {
                val intent = Intent(view.context, AddWidgetActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                view.context.startActivity(intent)
            }
        }
    }
}