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
import com.arasthel.spannedgridlayoutmanager.SpanSize
import com.arasthel.spannedgridlayoutmanager.SpannedGridLayoutManager
import kotlinx.android.synthetic.main.widget_page_holder.view.*
import kotlinx.coroutines.*
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.activities.AddWidgetActivity
import tk.zwander.lockscreenwidgets.data.WidgetData
import tk.zwander.lockscreenwidgets.data.WidgetSizeData
import tk.zwander.lockscreenwidgets.host.WidgetHostCompat
import tk.zwander.lockscreenwidgets.listeners.WidgetResizeListener
import tk.zwander.lockscreenwidgets.observables.OnResizeObservable
import tk.zwander.lockscreenwidgets.observables.RemoveButtonObservable
import tk.zwander.lockscreenwidgets.util.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.min

/**
 * The adapter for the widget frame itself.
 */
class WidgetFrameAdapter(
    private val manager: AppWidgetManager,
    private val host: WidgetHostCompat,
    private val params: WindowManager.LayoutParams,
    private val onRemoveCallback: (WidgetFrameAdapter, WidgetData) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), CoroutineScope by MainScope() {
    companion object {
        const val VIEW_TYPE_WIDGET = 0
        const val VIEW_TYPE_ADD = 1
    }

    val widgets = ArrayList<WidgetData>()
    val onResizeObservable = OnResizeObservable()

    val spanSizeLookup = WidgetSpanSizeLookup()

    var currentEditingInterfacePosition = -1
        set(value) {
            field = value
            mainHandler.post {
                editingInterfaceObservable.notifyObservers(value)
            }
        }

    private val editingInterfaceObservable =
        RemoveButtonObservable()

    /**
     * Push a new set of widgets to the frame.
     * If there are currently no widgets added,
     * add the new ones and notify the entire set.
     * Otherwise, calculate the diffeence and notify
     * accordingly.
     */
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

    /**
     * Move a widget to a new position in the adapter.
     * Used for when user is reordering widgets.
     */
    fun onMove(from: Int, to: Int): Boolean {
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

    /**
     * Represents an individual widget.
     * The item will be properly sized based on the number of columns the user
     * has specified for the frame.
     */
    @SuppressLint("ClickableViewAccessibility")
    inner class WidgetVH(view: View) : RecyclerView.ViewHolder(view) {
        var editingInterfaceShown: Boolean
            get() = itemView.widget_edit_wrapper.isVisible
            set(value) {
                itemView.widget_edit_wrapper.isVisible = value
                showHorizontalSizers = value && itemView.context.prefManager.frameColCount > 1
                showVerticalSizers = value && itemView.context.prefManager.frameRowCount > 1
            }
        var showHorizontalSizers: Boolean
            get() = itemView.run { widget_left_dragger.isVisible && widget_right_dragger.isVisible }
            set(value) {
                itemView.apply {
                    widget_left_dragger.isVisible = value
                    widget_right_dragger.isVisible = value
                }
            }
        var showVerticalSizers: Boolean
            get() = itemView.run { widget_top_dragger.isVisible && widget_bottom_dragger.isVisible }
            set(value) {
                itemView.apply {
                    widget_top_dragger.isVisible = value
                    widget_bottom_dragger.isVisible = value
                }
            }

        val currentData: WidgetData
            get() = widgets[adapterPosition]

        val currentSizeInfo: WidgetSizeData
            get() = itemView.context.prefManager.widgetSizes[currentData.id]
                ?: WidgetSizeData(currentData.id, 1, 1)

        init {
            itemView.remove_widget.setOnClickListener {
                val newPos = adapterPosition
                if (newPos != -1) {
                    onRemoveCallback(this@WidgetFrameAdapter, widgets[newPos])
                }
            }

            itemView.apply {
                widget_left_dragger.setOnTouchListener(WidgetResizeListener(context, WidgetResizeListener.Which.LEFT) {
                    val sizeInfo = currentSizeInfo
                    sizeInfo.safeWidgetWidthSpan = min(sizeInfo.safeWidgetWidthSpan - it, context.prefManager.frameColCount)

                    persistNewSizeInfo(sizeInfo)
                    onResize(currentData)
                })

                widget_top_dragger.setOnTouchListener(WidgetResizeListener(context, WidgetResizeListener.Which.TOP) {
                    val sizeInfo = currentSizeInfo
                    sizeInfo.safeWidgetHeightSpan = min(sizeInfo.safeWidgetHeightSpan - it, context.prefManager.frameRowCount)

                    persistNewSizeInfo(sizeInfo)
                    onResize(currentData)
                })

                widget_right_dragger.setOnTouchListener(WidgetResizeListener(context, WidgetResizeListener.Which.RIGHT) {
                    val sizeInfo = currentSizeInfo
                    sizeInfo.safeWidgetWidthSpan = min(sizeInfo.safeWidgetWidthSpan + it, context.prefManager.frameColCount)

                    persistNewSizeInfo(sizeInfo)
                    onResize(currentData)
                })

                widget_bottom_dragger.setOnTouchListener(WidgetResizeListener(context, WidgetResizeListener.Which.BOTTOM) {
                    val sizeInfo = currentSizeInfo
                    sizeInfo.safeWidgetHeightSpan = min(sizeInfo.safeWidgetHeightSpan + it, context.prefManager.frameRowCount)

                    persistNewSizeInfo(sizeInfo)
                    onResize(currentData)
                })
            }

            editingInterfaceObservable.addObserver { _, arg ->
                editingInterfaceShown = currentEditingInterfacePosition != -1 && (currentEditingInterfacePosition == adapterPosition)
            }

            onResizeObservable.addObserver { _, _ ->
                val pos = adapterPosition

                if (pos != -1 && pos < widgets.size) {
                    onResize(widgets[pos])
                }
            }
        }

        fun onBind(data: WidgetData) {
            itemView.apply {
                launch {
                    onResize(data)
                    editingInterfaceShown = currentEditingInterfacePosition != -1 && currentEditingInterfacePosition == adapterPosition

                    val widgetInfo = withContext(Dispatchers.Main) {
                        manager.getAppWidgetInfo(data.id)
                    }

                    widget_holder.apply {
                        removeAllViews()
                        try {
                            //We're recreating the AppWidgetHostView here each time, which probably isn't the most efficient
                            //way to do things. However, it's not trivial to just set a new source on an AppWidgetHostView,
                            //so this makes the most sense right now.
                            addView(withContext(Dispatchers.Main) {
                                host.createView(itemView.context, data.id, widgetInfo).apply {
                                    val width = context.pxAsDp(itemView.width).toInt()
                                    val height = context.pxAsDp(itemView.height).toInt()
                                    updateAppWidgetSize(null, width, height, width, height)
                                }
                            })
                        } catch (e: SecurityException) {
                            //There was an error adding the widget. Some OEMs (OPPO...) like to add permissions requirements to their
                            //widgets, which can make it impossible for third-party non-launcher apps to bind to them.
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

        //Make sure the item's width is properly updated on a frame resize, or on initial bind
        fun onResize(data: WidgetData) {
            itemView.apply {
                layoutParams = (layoutParams as ViewGroup.LayoutParams).apply {
                    width = calculateWidgetWidth(params.width, data.id)
//                    height = calculateWidgetHeight(params.height, data.id)
                }
            }
        }

        fun persistNewSizeInfo(info: WidgetSizeData) {
            itemView.context.prefManager.apply {
                widgetSizes = widgetSizes.apply {
                    this[info.widgetId] = info
                }
            }
            notifyItemChanged(adapterPosition)
        }
    }

    /**
     * Represents the "add button" page when no widgets are currently
     * added to the frame.
     */
    inner class AddWidgetVH(view: View) : RecyclerView.ViewHolder(view) {
        init {
            itemView.setOnClickListener {
                val intent = Intent(view.context, AddWidgetActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                view.context.startActivity(intent)
            }
        }
    }

    inner class WidgetSpanSizeLookup : SpannedGridLayoutManager.SpanSizeLookup({ position ->
        if (widgets.isEmpty()) SpanSize(1, 1)
        else {
            val widget = if (position >= widgets.size) null else widgets[position]
            val id = widget?.id ?: -1
            val size = host.context.prefManager.widgetSizes[id]

            SpanSize(
                size?.safeWidgetWidthSpan?.coerceAtMost(host.context.prefManager.frameColCount) ?: 1,
                size?.safeWidgetHeightSpan?.coerceAtMost(host.context.prefManager.frameRowCount) ?: 1
            )
        }
    })
}