package tk.zwander.lockscreenwidgets.adapters

import android.annotation.SuppressLint
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.LayoutInflater
import android.view.LayoutInflater.Factory2
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatCallback
import androidx.core.view.LayoutInflaterCompat
import androidx.core.view.forEach
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.arasthel.spannedgridlayoutmanager.SpanSize
import com.arasthel.spannedgridlayoutmanager.SpannedGridLayoutManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tk.zwander.common.activities.PermissionIntentLaunchActivity
import tk.zwander.common.data.WidgetData
import tk.zwander.common.data.WidgetType
import tk.zwander.common.host.widgetHostCompat
import tk.zwander.common.listeners.WidgetResizeListener
import tk.zwander.common.util.Event
import tk.zwander.common.util.EventObserver
import tk.zwander.common.util.FrameSizeAndPosition
import tk.zwander.common.util.appWidgetManager
import tk.zwander.common.util.createWidgetErrorView
import tk.zwander.common.util.dpAsPx
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.frameSizeAndPosition
import tk.zwander.common.util.getAllInstalledWidgetProviders
import tk.zwander.common.util.hasConfiguration
import tk.zwander.common.util.logUtils
import tk.zwander.common.util.mainHandler
import tk.zwander.common.util.prefManager
import tk.zwander.common.util.pxAsDp
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.activities.add.ReconfigureFrameWidgetActivity
import tk.zwander.lockscreenwidgets.databinding.AddWidgetBinding
import tk.zwander.lockscreenwidgets.databinding.FrameShortcutViewBinding
import tk.zwander.lockscreenwidgets.databinding.WidgetPageHolderBinding
import java.util.Collections
import kotlin.math.min

/**
 * The adapter for the widget frame itself.
 */
@Suppress("LeakingThis")
open class WidgetFrameAdapter(
    protected val context: Context,
    protected val onRemoveCallback: (WidgetData, Int) -> Unit,
    protected val saveTypeGetter: () -> FrameSizeAndPosition.FrameType,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), CoroutineScope by MainScope() {
    companion object {
        const val VIEW_TYPE_WIDGET = 0
        const val VIEW_TYPE_ADD = 1
    }

    val widgets = ArrayList<WidgetData>()
    val spanSizeLookup = WidgetSpanSizeLookup()

    var currentEditingInterfacePosition = -1
        set(value) {
            val changed = field != value

            field = value

            if (changed) {
                mainHandler.post {
                    context.eventManager.sendEvent(Event.EditingIndexUpdated(value))
                }
            }
        }

    private var didResize = false

    protected open val colCount: Int
        get() = context.prefManager.frameColCount
    protected open val rowCount: Int
        get() = context.prefManager.frameRowCount
    protected open val minColSpan: Int
        get() = 1
    protected open val minRowSpan: Int
        get() = 1
    protected open val rowSpanForAddButton: Int
        get() = rowCount
    protected open var currentWidgets: Collection<WidgetData>
        get() = context.prefManager.currentWidgets
        set(value) {
            context.prefManager.currentWidgets = LinkedHashSet(value)
        }
    protected open val widgetCornerRadius: Float
        get() = context.prefManager.frameWidgetCornerRadiusDp

    protected val host = context.widgetHostCompat
    protected val manager = context.appWidgetManager

    init {
        setHasStableIds(true)
    }

    /**
     * Push a new set of widgets to the frame.
     * If there are currently no widgets added,
     * add the new ones and notify the entire set.
     * Otherwise, calculate the difference and notify
     * accordingly.
     */
    fun updateWidgets(newWidgets: List<WidgetData>) {
        val uniqueNewWidgets = newWidgets.distinctBy { it.id }

        if (!didResize) {
            if (widgets.isEmpty()) {
                widgets.addAll(uniqueNewWidgets)
                notifyItemRangeInserted(0, itemCount)
            } else {
                val oldWidgets = widgets.toList()
                this.widgets.clear()
                this.widgets.addAll(uniqueNewWidgets)

                val result = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                    override fun areContentsTheSame(
                        oldItemPosition: Int,
                        newItemPosition: Int
                    ): Boolean {
                        return oldWidgets[oldItemPosition].id == uniqueNewWidgets[newItemPosition].id
                    }

                    override fun areItemsTheSame(
                        oldItemPosition: Int,
                        newItemPosition: Int
                    ): Boolean {
                        return oldWidgets[oldItemPosition].id == uniqueNewWidgets[newItemPosition].id
                    }

                    override fun getNewListSize(): Int {
                        return uniqueNewWidgets.size
                    }

                    override fun getOldListSize(): Int {
                        return oldWidgets.size
                    }
                }, true)

                result.dispatchUpdatesTo(this)
            }
        } else {
            didResize = false
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

    override fun getItemId(position: Int): Long {
        return if (widgets.size == 0) {
            VIEW_TYPE_ADD.toLong()
        } else {
            widgets.getOrNull(position)?.id?.toLong() ?: -1
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context).cloneInContext(parent.context)
        LayoutInflaterCompat.setFactory2(
            inflater,
            Class.forName("androidx.appcompat.app.AppCompatDelegateImpl")
                .getDeclaredConstructor(
                    Context::class.java,
                    Window::class.java,
                    AppCompatCallback::class.java
                )
                .apply { isAccessible = true }
                .newInstance(parent.context, null, null) as Factory2
        )
        return if (viewType == VIEW_TYPE_ADD) AddWidgetVH(
            inflater.inflate(
                R.layout.add_widget,
                parent,
                false
            )
        )
        else WidgetVH(inflater.inflate(R.layout.widget_page_holder, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (position < widgets.size) {
            (holder as WidgetVH).onBind(widgets[position])
        }

        (holder as? AddWidgetVH)?.onBind()
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        (holder as? WidgetVH)?.onDestroy()
    }

    fun updateViews() {
        notifyItemRangeChanged(0, itemCount, Any())
    }

    protected open fun launchAddActivity() {
        context.eventManager.sendEvent(Event.LaunchAddWidget)
    }

    protected open fun launchReconfigure(id: Int, providerInfo: AppWidgetProviderInfo) {
        ReconfigureFrameWidgetActivity.launch(context, id, providerInfo)
    }

    protected open fun View.onWidgetResize(
        data: WidgetData,
        params: ViewGroup.LayoutParams,
        amount: Int,
        direction: Int
    ) {
        params.width =
            params.width / context.prefManager.frameColCount * (data.size?.safeWidgetWidthSpan ?: 1)
        params.height =
            params.height / context.prefManager.frameRowCount * (data.size?.safeWidgetHeightSpan
                ?: 1)
    }

    protected open fun getThresholdPx(which: WidgetResizeListener.Which): Int {
        return context.run {
            val frameSize = frameSizeAndPosition.getSizeForType(saveTypeGetter())
            if (which == WidgetResizeListener.Which.LEFT || which == WidgetResizeListener.Which.RIGHT) {
                frameSize.x.toInt() / prefManager.frameColCount
            } else {
                frameSize.y.toInt() / prefManager.frameRowCount
            }
        }
    }

    /**
     * Represents an individual widget.
     * The item will be properly sized based on the number of columns the user
     * has specified for the frame.
     */
    @SuppressLint("ClickableViewAccessibility")
    inner class WidgetVH(view: View) : RecyclerView.ViewHolder(view), EventObserver {
        private val binding = WidgetPageHolderBinding.bind(view)

        private var editingInterfaceShown: Boolean
            get() = binding.widgetEditWrapper.isVisible
            set(value) {
                binding.widgetEditWrapper.isVisible = value
                showHorizontalSizers = value && colCount > 1
                showVerticalSizers = value && rowCount > 1
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

        private var currentData: WidgetData?
            get() = bindingAdapterPosition.let { if (it != -1) widgets.getOrNull(it) else null }
            set(value) {
                if (value != null) {
                    bindingAdapterPosition.takeIf { it != -1 }?.let { pos ->
                        currentWidgets = widgets.apply {
                            this[pos] = value
                        }
                    }
                }
            }

        init {
            binding.removeWidget.setOnClickListener {
                currentData?.let {
                    onRemoveCallback(it, bindingAdapterPosition)
                }
            }

            itemView.apply {
                binding.widgetLeftDragger.setOnTouchListener(WidgetResizeListener(
                    ::getThresholdPx,
                    WidgetResizeListener.Which.LEFT,
                    { overThreshold, step, amount ->
                        handleResize(
                            overThreshold,
                            step,
                            amount,
                            -1,
                            false
                        )
                    }
                ) { notifyItemChanged(bindingAdapterPosition) })

                binding.widgetTopDragger.setOnTouchListener(WidgetResizeListener(
                    ::getThresholdPx,
                    WidgetResizeListener.Which.TOP,
                    { overThreshold, step, amount ->
                        handleResize(
                            overThreshold,
                            step,
                            amount,
                            -1,
                            true
                        )
                    }
                ) { notifyItemChanged(bindingAdapterPosition) })

                binding.widgetRightDragger.setOnTouchListener(WidgetResizeListener(
                    ::getThresholdPx,
                    WidgetResizeListener.Which.RIGHT,
                    { overThreshold, step, amount ->
                        handleResize(
                            overThreshold,
                            step,
                            amount,
                            1,
                            false
                        )
                    }
                ) { notifyItemChanged(bindingAdapterPosition) })

                binding.widgetBottomDragger.setOnTouchListener(WidgetResizeListener(
                    ::getThresholdPx,
                    WidgetResizeListener.Which.BOTTOM,
                    { overThreshold, step, amount ->
                        handleResize(
                            overThreshold,
                            step,
                            amount,
                            1,
                            true
                        )
                    }
                ) { notifyItemChanged(bindingAdapterPosition) })
            }

            binding.widgetReconfigure.setOnClickListener {
                openWidgetConfig()
            }
            binding.openWidgetConfig.setOnClickListener {
                openWidgetConfig()
            }
        }

        private fun openWidgetConfig() {
            val data = currentData ?: return
            val provider = data.widgetProviderComponent

            if (provider == null) {
                //TODO: Notify + debug log
                itemView.context.logUtils.normalLog("Unable to reconfigure widget: provider is null.")
            } else {
                val pkg = provider.packageName
                val providerInfo = manager.getAppWidgetInfo(data.id)
                    ?: (itemView.context.getAllInstalledWidgetProviders(pkg)
                        .find { info -> info.provider == provider })

                if (providerInfo == null) {
                    //TODO: Notify + debug log
                    itemView.context.logUtils.normalLog("Unable to reconfigure widget $provider: provider info is null.")
                } else {
                    launchReconfigure(data.id, providerInfo)
                }
            }
        }

        fun onBind(data: WidgetData) {
            itemView.apply {
                launch {
                    context.eventManager.addObserver(this@WidgetVH)

                    onResize(data, 0, 1)
                    updateEditingUI(currentEditingInterfacePosition)

                    binding.widgetHolder.removeAllViews()

                    when (data.safeType) {
                        WidgetType.WIDGET -> bindWidget(data)
                        WidgetType.SHORTCUT, WidgetType.LAUNCHER_SHORTCUT -> bindShortcut(data)
                        WidgetType.HEADER -> {}
                    }

                    binding.card.radius = context.dpAsPx(widgetCornerRadius).toFloat()
                    binding.widgetEditOutline.background =
                        (binding.widgetEditOutline.background.mutate() as GradientDrawable).apply {
                            this.cornerRadius = binding.card.radius
                        }
                }
            }
        }

        fun onDestroy() {
            itemView.context.eventManager.removeObserver(this)
        }

        override fun onEvent(event: Event) {
            when (event) {
                Event.FrameMoveFinished -> {
                    val pos = bindingAdapterPosition

                    if (pos != -1 && pos < widgets.size) {
                        onResize(widgets[pos], 0, 1)
                    }
                }

                is Event.EditingIndexUpdated -> {
                    updateEditingUI(event.index)
                }

                else -> {}
            }
        }

        private fun updateEditingUI(index: Int) {
            val oldState = editingInterfaceShown
            val newState = index != -1 && (index == bindingAdapterPosition)
            editingInterfaceShown = newState
            if (oldState != newState && index != -1 && index == bindingAdapterPosition) {
                notifyItemChanged(bindingAdapterPosition)
            }
        }

        private suspend fun bindWidget(data: WidgetData) {
            val widgetInfo = withContext(Dispatchers.Main) {
                try {
                    manager.getAppWidgetInfo(data.id)
                } catch (_: PackageManager.NameNotFoundException) {
                    null
                }
            }

            binding.openWidgetConfig.isVisible =
                widgetInfo.hasConfiguration(itemView.context) == true

            if (widgetInfo != null) {
                binding.widgetReconfigure.isVisible = false
                binding.widgetHolder.apply {
                    isVisible = true

                    try {
                        // We're recreating the AppWidgetHostView here each time, which probably isn't the most efficient
                        // way to do things. However, it's not trivial to just set a new source on an AppWidgetHostView,
                        // so this makes the most sense right now.
                        addView(withContext(Dispatchers.Main) {
                            host.createView(itemView.context, data.id, widgetInfo).apply hostView@{
                                findScrollableViewsInHierarchy(this).forEach { list ->
                                    list.isNestedScrollingEnabled = true
                                }

                                this.viewTreeObserver.addOnGlobalLayoutListener {
                                    findScrollableViewsInHierarchy(this).forEach { list ->
                                        list.isNestedScrollingEnabled = true
                                    }
                                }

                                val width = context.pxAsDp(itemView.width)
                                val height = context.pxAsDp(itemView.height)

                                val paddingRect = Rect(
                                    R.dimen.app_widget_padding,
                                    R.dimen.app_widget_padding,
                                    R.dimen.app_widget_padding,
                                    R.dimen.app_widget_padding
                                ).run {
                                    RectF(
                                        context.pxAsDp(context.resources.getDimensionPixelSize(left)),
                                        context.pxAsDp(context.resources.getDimensionPixelSize(top)),
                                        context.pxAsDp(context.resources.getDimensionPixelSize(right)),
                                        context.pxAsDp(
                                            context.resources.getDimensionPixelSize(
                                                bottom
                                            )
                                        )
                                    )
                                }

                                // Workaround to fix the One UI 5.1 battery grid widget on some devices.
                                if (widgetInfo.provider.packageName == "com.android.settings.intelligence") {
                                    updateAppWidgetOptions(Bundle().apply {
                                        putBoolean("hsIsHorizontalIcon", false)
                                        putInt("semAppWidgetRowSpan", 1)
                                    })
                                }

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    updateAppWidgetSize(
                                        Bundle(),
                                        listOf(
                                            SizeF(
                                                width + paddingRect.left + paddingRect.right,
                                                height + paddingRect.top + paddingRect.bottom
                                            )
                                        )
                                    )
                                } else {
                                    val adjustedWidth = width + paddingRect.left + paddingRect.right
                                    val adjustedHeight =
                                        height + paddingRect.top + paddingRect.bottom

                                    @Suppress("DEPRECATION")
                                    updateAppWidgetSize(
                                        null,
                                        adjustedWidth.toInt(),
                                        adjustedHeight.toInt(),
                                        adjustedWidth.toInt(),
                                        adjustedHeight.toInt()
                                    )
                                }
                            }
                        })
                    } catch (e: Throwable) {
                        context.logUtils.normalLog("Unable to bind widget view ${widgetInfo.provider}", e)

                        if (e is SecurityException) {
                            Toast.makeText(
                                context,
                                resources.getString(R.string.bind_widget_error, widgetInfo.provider),
                                Toast.LENGTH_LONG,
                            ).show()
                            currentWidgets = currentWidgets.toMutableList().apply {
                                remove(data)
                                host.deleteAppWidgetId(data.id)
                            }
                        } else {
                            addView(context.createWidgetErrorView())
                        }
                    }
                }
            } else {
                binding.widgetReconfigure.isVisible = true
                with(itemView.context) { binding.widgetPreview.setImageBitmap(data.iconBitmap) }
                binding.widgetLabel.text = data.label
            }
        }

        @SuppressLint("DiscouragedApi")
        private fun bindShortcut(data: WidgetData) {
            binding.widgetReconfigure.isVisible = false
            binding.widgetHolder.isVisible = true
            binding.openWidgetConfig.isVisible = false

            val shortcutView = FrameShortcutViewBinding.inflate(
                LayoutInflater.from(binding.widgetHolder.context)
            )
            val icon = with(itemView.context) { data.iconBitmap }

            shortcutView.shortcutRoot.setOnClickListener {
                data.shortcutIntent?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                    PermissionIntentLaunchActivity.start(
                        context = itemView.context,
                        intent = this,
                        launchType = PermissionIntentLaunchActivity.LaunchType.ACTIVITY
                    )
                }
            }
            shortcutView.shortcutIcon.setImageBitmap(icon)
            shortcutView.shortcutName.text = data.label

            binding.widgetHolder.addView(shortcutView.root)
        }

        private fun findScrollableViewsInHierarchy(root: View): List<View> {
            val ret = arrayListOf<View>()

            if (root is ViewGroup) {
                if (root is ListView) {
                    ret.add(root)
                }

                root.forEach { child ->
                    if (child.canScrollVertically(1) || child.canScrollVertically(-1)) {
                        ret.add(child)
                    } else if (child is ViewGroup) {
                        ret.addAll(findScrollableViewsInHierarchy(child))
                    }
                }
            }

            return ret
        }

        private fun handleResize(
            overThreshold: Boolean,
            step: Int,
            amount: Int,
            direction: Int,
            vertical: Boolean
        ) {
            val currentData = currentData ?: return
            val sizeInfo = currentData.safeSize

            val newSizeInfo = if (overThreshold) {
                if (vertical) {
                    sizeInfo.safeCopy(
                        widgetHeightSpan = min(
                            sizeInfo.safeWidgetHeightSpan + step * direction,
                            rowCount
                        ),
                    )
                } else {
                    sizeInfo.safeCopy(
                        widgetWidthSpan = min(
                            sizeInfo.safeWidgetWidthSpan + step * direction,
                            colCount
                        ),
                    )
                }
            } else {
                sizeInfo
            }

            val newData = currentData.copy(size = newSizeInfo)

            onResize(newData, amount, step)
            didResize = true
            this.currentData = newData
        }

        //Make sure the item's size is properly updated on a frame resize, or on initial bind
        private fun onResize(data: WidgetData, amount: Int, direction: Int) {
            itemView.apply {
                layoutParams = (layoutParams as ViewGroup.LayoutParams).apply {
                    onWidgetResize(data, this, amount, direction)
                }

                forceLayout()
                invalidate()
            }
        }
    }

    /**
     * Represents the "add button" page when no widgets are currently
     * added to the frame.
     */
    inner class AddWidgetVH(view: View) : RecyclerView.ViewHolder(view) {
        private val binding = AddWidgetBinding.bind(view)

        init {
            binding.clickTarget.setOnClickListener {
                launchAddActivity()
            }
        }

        fun onBind() {
            binding.root.radius = itemView.context.dpAsPx(widgetCornerRadius).toFloat()
        }
    }

    inner class WidgetSpanSizeLookup : SpannedGridLayoutManager.SpanSizeLookup({ position ->
        if (widgets.isEmpty()) SpanSize(colCount, rowSpanForAddButton)
        else {
            val widget = if (position >= widgets.size) null else widgets[position]
            val size = widget?.safeSize

            SpanSize(
                size?.safeWidgetWidthSpan?.coerceAtMost(colCount)?.coerceAtLeast(minColSpan)
                    ?: minColSpan,
                size?.safeWidgetHeightSpan?.coerceAtMost(rowCount)?.coerceAtLeast(minRowSpan)
                    ?: minRowSpan
            )
        }
    })
}