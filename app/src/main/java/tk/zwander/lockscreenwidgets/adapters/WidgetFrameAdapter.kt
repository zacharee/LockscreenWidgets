package tk.zwander.lockscreenwidgets.adapters

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.os.UserHandle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.arasthel.spannedgridlayoutmanager.SpanSize
import com.arasthel.spannedgridlayoutmanager.SpannedGridLayoutManager
import kotlinx.coroutines.*
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.activities.DismissOrUnlockActivity
import tk.zwander.lockscreenwidgets.activities.add.AddWidgetActivity
import tk.zwander.lockscreenwidgets.activities.add.ReconfigureWidgetActivity
import tk.zwander.lockscreenwidgets.data.WidgetData
import tk.zwander.lockscreenwidgets.data.WidgetSizeData
import tk.zwander.lockscreenwidgets.data.WidgetType
import tk.zwander.lockscreenwidgets.databinding.FrameShortcutViewBinding
import tk.zwander.lockscreenwidgets.databinding.WidgetPageHolderBinding
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
            notifyItemRangeInserted(0, itemCount)
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

    fun updateViews() {
        notifyItemRangeChanged(0, itemCount)
    }

    /**
     * Represents an individual widget.
     * The item will be properly sized based on the number of columns the user
     * has specified for the frame.
     */
    @SuppressLint("ClickableViewAccessibility")
    inner class WidgetVH(view: View) : RecyclerView.ViewHolder(view) {
        private val binding = WidgetPageHolderBinding.bind(view)

        private var editingInterfaceShown: Boolean
            get() = binding.widgetEditWrapper.isVisible
            set(value) {
                binding.widgetEditWrapper.isVisible = value
                showHorizontalSizers = value && itemView.context.prefManager.frameColCount > 1
                showVerticalSizers = value && itemView.context.prefManager.frameRowCount > 1
            }
        private var showHorizontalSizers: Boolean
            get() = itemView.run { binding.widgetLeftDragger.isVisible && binding.widgetRightDragger.isVisible }
            set(value) {
                itemView.apply {
                    binding.widgetLeftDragger.isVisible = value
                    binding.widgetRightDragger.isVisible = value
                }
            }
        private var showVerticalSizers: Boolean
            get() = itemView.run { binding.widgetTopDragger.isVisible && binding.widgetBottomDragger.isVisible }
            set(value) {
                itemView.apply {
                    binding.widgetTopDragger.isVisible = value
                    binding.widgetBottomDragger.isVisible = value
                }
            }

        private val currentData: WidgetData
            get() = widgets[bindingAdapterPosition]

        private val currentSizeInfo: WidgetSizeData
            get() = itemView.context.prefManager.widgetSizes[currentData.id]
                ?: WidgetSizeData(currentData.id, 1, 1)

        init {
            binding.removeWidget.setOnClickListener {
                val newPos = bindingAdapterPosition
                if (newPos != -1) {
                    onRemoveCallback(this@WidgetFrameAdapter, widgets[newPos])
                }
            }

            itemView.apply {
                binding.widgetLeftDragger.setOnTouchListener(WidgetResizeListener(context, WidgetResizeListener.Which.LEFT,
                    { handleResize(it, -1) },
                    { notifyItemChanged(bindingAdapterPosition) }
                ))

                binding.widgetTopDragger.setOnTouchListener(WidgetResizeListener(context, WidgetResizeListener.Which.TOP,
                    { handleResize(it, -1) },
                    { notifyItemChanged(bindingAdapterPosition) }
                ))

                binding.widgetRightDragger.setOnTouchListener(WidgetResizeListener(context, WidgetResizeListener.Which.RIGHT,
                    { handleResize(it, 1) },
                    { notifyItemChanged(bindingAdapterPosition) }
                ))

                binding.widgetBottomDragger.setOnTouchListener(WidgetResizeListener(context, WidgetResizeListener.Which.BOTTOM,
                    { handleResize(it, 1) },
                    { notifyItemChanged(bindingAdapterPosition) }
                ))
            }

            binding.widgetReconfigure.setOnClickListener {
                val data = currentData
                val provider = data.widgetProviderComponent

                if (provider == null) {
                    //TODO: Notify + debug log
                    it.context.logUtils.debugLog("Unable to reconfigure widget: provider is null.")
                } else {
                    val manager = AppWidgetManager.getInstance(itemView.context)
                    val pkg = provider.packageName
                    val providerInfo = (manager.getInstalledProvidersForProfile(
                        AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, UserHandle.CURRENT, pkg) +
                            manager.getInstalledProvidersForProfile(
                                AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD, UserHandle.CURRENT, pkg))
                        .find { info -> info.provider == provider }

                    if (providerInfo == null) {
                        //TODO: Notify + debug log
                        it.context.logUtils.debugLog("Unable to reconfigure widget $provider: provider info is null.")
                    } else {
                        ReconfigureWidgetActivity.launch(it.context, data.id, providerInfo)
                    }
                }
            }

            editingInterfaceObservable.addObserver { _, arg ->
                editingInterfaceShown = currentEditingInterfacePosition != -1 && (currentEditingInterfacePosition == bindingAdapterPosition)
            }

            onResizeObservable.addObserver { _, _ ->
                val pos = bindingAdapterPosition

                if (pos != -1 && pos < widgets.size) {
                    onResize(widgets[pos])
                }
            }
        }

        fun onBind(data: WidgetData) {
            itemView.apply {
                launch {
                    onResize(data)
                    editingInterfaceShown = currentEditingInterfacePosition != -1 && currentEditingInterfacePosition == bindingAdapterPosition

                    binding.widgetHolder.removeAllViews()

                    when (data.safeType) {
                        WidgetType.WIDGET -> bindWidget(data)
                        WidgetType.SHORTCUT -> bindShortcut(data)
                    }
                }
            }
        }

        private suspend fun bindWidget(data: WidgetData) {
            val widgetInfo = withContext(Dispatchers.Main) {
                manager.getAppWidgetInfo(data.id)
            }

            if (widgetInfo != null) {
                binding.widgetHolder.apply {
                    isVisible = true

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
            } else {
                binding.widgetReconfigure.isVisible = true
            }
        }

        private suspend fun bindShortcut(data: WidgetData) {
            val shortcutView = FrameShortcutViewBinding.inflate(
                LayoutInflater.from(binding.widgetHolder.context))
            val icon = data.icon.base64ToBitmap() ?: data.iconRes?.run {
                val res = try {
                    itemView.context.packageManager.getResourcesForApplication(this.packageName)
                } catch (e: PackageManager.NameNotFoundException) {
                    onRemoveCallback(this@WidgetFrameAdapter, data)
                    return@bindShortcut
                }
                ResourcesCompat.getDrawable(
                    res,
                    res.getIdentifier(this.resourceName, "drawable", this.packageName),
                    res.newTheme()
                )?.toBitmap()
            }

            shortcutView.shortcutRoot.setOnClickListener {
                data.shortcutIntent?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                    try {
                        itemView.context.startActivity(this)
                        DismissOrUnlockActivity.launch(itemView.context)
                    } catch (e: Exception) {
                        Toast.makeText(itemView.context, R.string.launch_shortcut_error, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            shortcutView.shortcutIcon.setImageBitmap(icon)
            shortcutView.shortcutName.text = data.label

            binding.widgetHolder.addView(shortcutView.root)
        }

        private fun handleResize(step: Int, direction: Int) {
            val sizeInfo = currentSizeInfo
            sizeInfo.safeWidgetWidthSpan = min(sizeInfo.safeWidgetWidthSpan + step * direction,
                itemView.context.prefManager.frameColCount)

            persistNewSizeInfo(sizeInfo)
            onResize(currentData)
        }

        //Make sure the item's width is properly updated on a frame resize, or on initial bind
        private fun onResize(data: WidgetData) {
            itemView.apply {
                layoutParams = (layoutParams as ViewGroup.LayoutParams).apply {
                    width = calculateWidgetWidth(params.width, data.id)
//                    height = calculateWidgetHeight(params.height, data.id)
                }
            }
        }

        private fun persistNewSizeInfo(info: WidgetSizeData) {
            itemView.context.prefManager.apply {
                widgetSizes = widgetSizes.apply {
                    this[info.widgetId] = info
                }
            }
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