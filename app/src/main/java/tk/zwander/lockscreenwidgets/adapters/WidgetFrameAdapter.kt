package tk.zwander.lockscreenwidgets.adapters

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.*
import android.view.LayoutInflater.Factory2
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatCallback
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.LayoutInflaterCompat
import androidx.core.view.forEach
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.arasthel.spannedgridlayoutmanager.SpanSize
import com.arasthel.spannedgridlayoutmanager.SpannedGridLayoutManager
import kotlinx.coroutines.*
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.activities.DismissOrUnlockActivity
import tk.zwander.lockscreenwidgets.activities.add.ReconfigureWidgetActivity
import tk.zwander.lockscreenwidgets.data.WidgetData
import tk.zwander.lockscreenwidgets.data.WidgetType
import tk.zwander.lockscreenwidgets.databinding.FrameShortcutViewBinding
import tk.zwander.lockscreenwidgets.databinding.WidgetPageHolderBinding
import tk.zwander.lockscreenwidgets.host.WidgetHostCompat
import tk.zwander.lockscreenwidgets.listeners.WidgetResizeListener
import tk.zwander.lockscreenwidgets.observables.OnResizeObservable
import tk.zwander.lockscreenwidgets.observables.RemoveButtonObservable
import tk.zwander.lockscreenwidgets.util.*
import java.util.*
import kotlin.math.min

/**
 * The adapter for the widget frame itself.
 */
open class WidgetFrameAdapter(
    protected val manager: AppWidgetManager,
    protected val host: WidgetHostCompat,
    protected val params: WindowManager.LayoutParams,
    protected val onRemoveCallback: (WidgetFrameAdapter, WidgetData, Int) -> Unit
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

    private var didResize = false

    protected open val colCount: Int
        get() = host.context.prefManager.frameColCount
    protected open val rowCount: Int
        get() = host.context.prefManager.frameRowCount
    protected open val minColSpan: Int
        get() = 1
    protected open val minRowSpan: Int
        get() = 1
    protected open var currentWidgets: MutableCollection<WidgetData>
        get() = host.context.prefManager.currentWidgets
        set(value) {
            host.context.prefManager.currentWidgets = LinkedHashSet(value)
        }

    /**
     * Push a new set of widgets to the frame.
     * If there are currently no widgets added,
     * add the new ones and notify the entire set.
     * Otherwise, calculate the difference and notify
     * accordingly.
     */
    fun updateWidgets(newWidgets: List<WidgetData>) {
        if (!didResize) {
            if (widgets.isEmpty()) {
                widgets.addAll(newWidgets)
                notifyItemRangeInserted(0, itemCount)
            } else {
                val oldWidgets = widgets.toList()
                this.widgets.clear()
                this.widgets.addAll(newWidgets)

                val result = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                    override fun areContentsTheSame(
                        oldItemPosition: Int,
                        newItemPosition: Int
                    ): Boolean {
                        return oldWidgets[oldItemPosition].id == newWidgets[newItemPosition].id
                    }

                    override fun areItemsTheSame(
                        oldItemPosition: Int,
                        newItemPosition: Int
                    ): Boolean {
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context).cloneInContext(parent.context)
        LayoutInflaterCompat.setFactory2(
            inflater,
            Class.forName("androidx.appcompat.app.AppCompatDelegateImpl")
                .getDeclaredConstructor(Context::class.java, Window::class.java, AppCompatCallback::class.java)
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
    }

    fun updateViews() {
        notifyItemRangeChanged(0, itemCount)
    }

    protected fun persistResize() {
        currentWidgets = LinkedHashSet(widgets)
    }

    protected open fun launchAddActivity() {
        host.context.eventManager.sendEvent(Event.LaunchAddWidget)
    }

    protected open fun launchReconfigure(id: Int, providerInfo: AppWidgetProviderInfo) {
        ReconfigureWidgetActivity.launch(host.context, id, providerInfo)
    }

    protected open fun View.onWidgetResize(data: WidgetData, params: ViewGroup.LayoutParams, amount: Int, direction: Int) {
        params.width = params.width / context.prefManager.frameColCount * (data.size?.safeWidgetWidthSpan ?: 1)
        params.height = params.height / context.prefManager.frameRowCount * (data.size?.safeWidgetHeightSpan ?: 1)
    }

    protected open fun getThresholdPx(which: WidgetResizeListener.Which): Int {
        return host.context.run {
            if (which == WidgetResizeListener.Which.LEFT || which == WidgetResizeListener.Which.RIGHT) {
                dpAsPx(prefManager.frameWidthDp) / prefManager.frameColCount
            } else {
                dpAsPx(prefManager.frameHeightDp) / prefManager.frameRowCount
            }
        }
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

        private val currentData: WidgetData?
            get() = bindingAdapterPosition.let { if (it != -1) widgets.getOrNull(it) else null }

        init {
            binding.removeWidget.setOnClickListener {
                currentData?.let {
                    onRemoveCallback(this@WidgetFrameAdapter, it, bindingAdapterPosition)
                }
            }

            itemView.apply {
                binding.widgetLeftDragger.setOnTouchListener(WidgetResizeListener(
                    ::getThresholdPx,
                    WidgetResizeListener.Which.LEFT,
                    { overThreshold, step, amount -> handleResize(overThreshold, step, amount, -1, false) }
                ) { notifyItemChanged(bindingAdapterPosition) })

                binding.widgetTopDragger.setOnTouchListener(WidgetResizeListener(
                    ::getThresholdPx,
                    WidgetResizeListener.Which.TOP,
                    { overThreshold, step, amount -> handleResize(overThreshold, step, amount, -1, true) }
                ) { notifyItemChanged(bindingAdapterPosition) })

                binding.widgetRightDragger.setOnTouchListener(WidgetResizeListener(
                    ::getThresholdPx,
                    WidgetResizeListener.Which.RIGHT,
                    { overThreshold, step, amount -> handleResize(overThreshold, step, amount, 1, false) }
                ) { notifyItemChanged(bindingAdapterPosition) })

                binding.widgetBottomDragger.setOnTouchListener(WidgetResizeListener(
                    ::getThresholdPx,
                    WidgetResizeListener.Which.BOTTOM,
                    { overThreshold, step, amount -> handleResize(overThreshold, step, amount, 1, true) }
                ) { notifyItemChanged(bindingAdapterPosition) })
            }

            binding.widgetReconfigure.setOnClickListener {
                val data = currentData ?: return@setOnClickListener
                val provider = data.widgetProviderComponent

                if (provider == null) {
                    //TODO: Notify + debug log
                    it.context.logUtils.normalLog("Unable to reconfigure widget: provider is null.")
                } else {
                    val manager = AppWidgetManager.getInstance(itemView.context)
                    val pkg = provider.packageName
                    val providerInfo = run {
                        val providers = try {
                            manager.getInstalledProvidersForProfile(
                                AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                                null,
                                pkg
                            ) + manager.getInstalledProvidersForProfile(
                                AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD,
                                null,
                                pkg
                            ) + manager.getInstalledProvidersForProfile(
                                AppWidgetProviderInfo.WIDGET_CATEGORY_SEARCHBOX,
                                null,
                                pkg
                            )
                        } catch (e: NoSuchMethodError) {
                            itemView.context.logUtils.debugLog("Unable to use getInstalledProvidersForProfile", e)

                            manager.getInstalledProviders(AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) +
                                    manager.getInstalledProviders(AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD) +
                                    manager.getInstalledProviders(AppWidgetProviderInfo.WIDGET_CATEGORY_SEARCHBOX)
                        }
                        providers.find { info -> info.provider == provider }
                    }

                    if (providerInfo == null) {
                        //TODO: Notify + debug log
                        it.context.logUtils.normalLog("Unable to reconfigure widget $provider: provider info is null.")
                    } else {
                        launchReconfigure(data.id, providerInfo)
                    }
                }
            }

            editingInterfaceObservable.addObserver { _, _ ->
                editingInterfaceShown =
                    currentEditingInterfacePosition != -1 && (currentEditingInterfacePosition == bindingAdapterPosition)
            }

            onResizeObservable.addObserver { _, _ ->
                val pos = bindingAdapterPosition

                if (pos != -1 && pos < widgets.size) {
                    onResize(widgets[pos], 0, 1)
                }
            }
        }

        fun onBind(data: WidgetData) {
            itemView.apply {
                launch {
                    onResize(data, 0, 1)
                    editingInterfaceShown =
                        currentEditingInterfacePosition != -1 && currentEditingInterfacePosition == bindingAdapterPosition

                    binding.widgetHolder.removeAllViews()

                    when (data.safeType) {
                        WidgetType.WIDGET -> bindWidget(data)
                        WidgetType.SHORTCUT -> bindShortcut(data)
                        WidgetType.HEADER -> {}
                    }
                }
            }
        }

        private suspend fun bindWidget(data: WidgetData) {
            val widgetInfo = withContext(Dispatchers.Main) {
                manager.getAppWidgetInfo(data.id)
            }

            if (widgetInfo != null) {
                binding.widgetReconfigure.isVisible = false
                binding.widgetHolder.apply {
                    isVisible = true

                    try {
                        //We're recreating the AppWidgetHostView here each time, which probably isn't the most efficient
                        //way to do things. However, it's not trivial to just set a new source on an AppWidgetHostView,
                        //so this makes the most sense right now.
                        addView(withContext(Dispatchers.Main) {
                            host.createView(itemView.context, data.id, widgetInfo).apply {
                                findListViewsInHierarchy(this).forEach { list ->
                                    list.isNestedScrollingEnabled = true
                                }

                                this.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
                                    override fun onChildViewAdded(parent: View, child: View?) {
                                        findListViewsInHierarchy(parent).forEach { list ->
                                            list.isNestedScrollingEnabled = true
                                        }
                                    }

                                    override fun onChildViewRemoved(parent: View?, child: View?) {}
                                })

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
                    } catch (e: SecurityException) {
                        context.logUtils.debugLog("Unable to bind widget view", e)

                        //There was an error adding the widget. Some OEMs (OPPO...) like to add permissions requirements to their
                        //widgets, which can make it impossible for third-party non-launcher apps to bind to them.
                        Toast.makeText(
                            context,
                            resources.getString(R.string.bind_widget_error, widgetInfo.provider),
                            Toast.LENGTH_LONG
                        ).show()
                        currentWidgets = currentWidgets.apply {
                            remove(data)
                            host.deleteAppWidgetId(data.id)
                        }
                    }
                }
            } else {
                binding.widgetReconfigure.isVisible = true
                binding.widgetPreview.setImageBitmap(data.icon?.base64ToBitmap())
                binding.widgetLabel.text = data.label
            }
        }

        private suspend fun bindShortcut(data: WidgetData) {
            binding.widgetReconfigure.isVisible = false
            binding.widgetHolder.isVisible = true

            val shortcutView = FrameShortcutViewBinding.inflate(
                LayoutInflater.from(binding.widgetHolder.context)
            )
            val icon = data.icon.base64ToBitmap() ?: data.iconRes?.run {
                val res = try {
                    itemView.context.packageManager.getResourcesForApplication(this.packageName)
                } catch (e: PackageManager.NameNotFoundException) {
                    host.context.logUtils.debugLog("Unable to bind shortcut", e)
                    onRemoveCallback(this@WidgetFrameAdapter, data, bindingAdapterPosition)
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

                    mainHandler.post {
                        try {
                            itemView.context.startActivity(this)
                            DismissOrUnlockActivity.launch(itemView.context, false)
                        } catch (e: Exception) {
                            it.context.logUtils.normalLog("Unable to launch shortcut", e)
                            Toast.makeText(
                                itemView.context,
                                R.string.launch_shortcut_error,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
            shortcutView.shortcutIcon.setImageBitmap(icon)
            shortcutView.shortcutName.text = data.label

            binding.widgetHolder.addView(shortcutView.root)
        }

        private fun findListViewsInHierarchy(root: View): List<ListView> {
            val ret = arrayListOf<ListView>()

            if (root is ViewGroup) {
                root.forEach { child ->
                    if (child is ListView) {
                        ret.add(child)
                    } else if (child is ViewGroup) {
                        ret.addAll(findListViewsInHierarchy(child))
                    }
                }
            }

            return ret
        }

        private fun handleResize(overThreshold: Boolean, step: Int, amount: Int, direction: Int, vertical: Boolean) {
            val sizeInfo = currentData?.safeSize ?: return

            if (overThreshold) {
                if (vertical) {
                    sizeInfo.safeWidgetHeightSpan = min(
                        sizeInfo.safeWidgetHeightSpan + step * direction,
                        rowCount
                    )
                } else {
                    sizeInfo.safeWidgetWidthSpan = min(
                        sizeInfo.safeWidgetWidthSpan + step * direction,
                        colCount
                    )
                }
            }

            onResize(currentData ?: return, amount, step)
            persistResize()
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

        private fun persistResize() {
            didResize = true

            this@WidgetFrameAdapter.persistResize()
        }
    }

    /**
     * Represents the "add button" page when no widgets are currently
     * added to the frame.
     */
    inner class AddWidgetVH(view: View) : RecyclerView.ViewHolder(view) {
        init {
            itemView.setOnClickListener {
                launchAddActivity()
            }
        }
    }

    inner class WidgetSpanSizeLookup : SpannedGridLayoutManager.SpanSizeLookup({ position ->
        if (widgets.isEmpty()) SpanSize(colCount, minRowSpan)
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