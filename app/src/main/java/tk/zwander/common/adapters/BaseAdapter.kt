package tk.zwander.common.adapters

import android.annotation.SuppressLint
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.ListView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.forEach
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.arasthel.spannedgridlayoutmanager.SpanSize
import com.arasthel.spannedgridlayoutmanager.SpannedGridLayoutManager
import com.bugsnag.android.performance.compose.MeasuredComposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tk.zwander.common.activities.DismissOrUnlockActivity
import tk.zwander.common.activities.PermissionIntentLaunchActivity
import tk.zwander.common.compose.components.ShortcutItemLayout
import tk.zwander.common.compose.components.WidgetItemLayout
import tk.zwander.common.compose.util.rememberPreferenceState
import tk.zwander.common.compose.util.widgetViewCacheRegistry
import tk.zwander.common.data.WidgetData
import tk.zwander.common.data.WidgetType
import tk.zwander.common.host.widgetHostCompat
import tk.zwander.common.listeners.WidgetResizeListener
import tk.zwander.common.util.BaseDelegate
import tk.zwander.common.util.BrokenAppsRegistry
import tk.zwander.common.util.Event
import tk.zwander.common.util.EventObserver
import tk.zwander.common.util.LSDisplay
import tk.zwander.common.util.appWidgetManager
import tk.zwander.common.util.createWidgetErrorView
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.getAllInstalledWidgetProviders
import tk.zwander.common.util.logUtils
import tk.zwander.common.util.mitigations.SafeContextWrapper
import tk.zwander.common.util.prefManager
import tk.zwander.common.util.requireLsDisplayManager
import tk.zwander.common.util.setThemedContent
import tk.zwander.common.util.themedLayoutInflater
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.databinding.ComposeViewHolderBinding
import java.util.Collections
import kotlin.math.min

@Suppress("LeakingThis")
abstract class BaseAdapter(
    protected val holderId: Int,
    protected val context: Context,
    protected val rootView: View,
    protected val onRemoveCallback: (WidgetData, Int) -> Unit,
    protected val displayId: Int,
    protected val viewModel: BaseDelegate.BaseViewModel<*, *>,
) : RecyclerView.Adapter<BaseAdapter.BaseVH<*>>(), CoroutineScope by MainScope() {
    companion object {
        const val VIEW_TYPE_WIDGET = 0
        const val VIEW_TYPE_ADD = 1
    }

    val widgets = ArrayList<WidgetData>()
    val spanSizeLookup = WidgetSpanSizeLookup()

    private var didResize = false

    protected val host = context.widgetHostCompat
    protected val manager = context.appWidgetManager
    protected val viewCacheRegistry = context.widgetViewCacheRegistry

    protected val display: LSDisplay
        get() = context.requireLsDisplayManager.requireDisplay(displayId)

    private val baseLayoutInflater = context.themedLayoutInflater

    protected abstract val colCount: Int
    protected abstract val rowCount: Int
    protected open val minColSpan: Int = 1
    protected abstract val minRowSpan: Int
    protected abstract val rowSpanForAddButton: Int
    protected abstract var currentWidgets: Collection<WidgetData>

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

                val result = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                    override fun areContentsTheSame(
                        oldItemPosition: Int,
                        newItemPosition: Int,
                    ): Boolean {
                        return oldWidgets[oldItemPosition].id == uniqueNewWidgets[newItemPosition].id
                    }

                    override fun areItemsTheSame(
                        oldItemPosition: Int,
                        newItemPosition: Int,
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

                widgets.clear()
                widgets.addAll(uniqueNewWidgets)

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
        return if (widgets.isEmpty()) 1 else widgets.size
    }

    override fun getItemViewType(position: Int): Int {
        return if (widgets.isEmpty()) VIEW_TYPE_ADD
        else VIEW_TYPE_WIDGET
    }

    override fun getItemId(position: Int): Long {
        return if (widgets.isEmpty()) {
            VIEW_TYPE_ADD.toLong()
        } else {
            widgets.getOrNull(position)?.id?.toLong() ?: -1
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseVH<*> {
        val inflater = baseLayoutInflater

        val view = ComposeViewHolderBinding.inflate(inflater, parent, false)

        return if (viewType == VIEW_TYPE_ADD) {
            AddWidgetVH(view)
        } else {
            WidgetVH(view)
        }
    }

    override fun onBindViewHolder(holder: BaseVH<*>, position: Int) {
        holder.setCompositionContext()

        if (position < widgets.size) {
            (holder as WidgetVH).performBind(widgets[position])
        }

        (holder as? AddWidgetVH)?.performBind(Unit)
    }

    override fun onViewRecycled(holder: BaseVH<*>) {
        holder.onDestroy()
    }

    fun updateViews() {
        notifyItemRangeChanged(0, itemCount, Any())
    }

    abstract fun launchAddActivity()
    abstract fun launchReconfigure(id: Int, providerInfo: AppWidgetProviderInfo)
    abstract fun View.onWidgetResize(
        data: WidgetData,
        params: ViewGroup.LayoutParams,
        amount: Int,
        direction: Int,
    )

    abstract fun launchShortcutIconOverride(id: Int)

    abstract fun getThresholdPx(which: WidgetResizeListener.Which): Int

    /**
     * Represents an individual widget.
     * The item will be properly sized based on the number of columns the user
     * has specified for the frame.
     */
    @SuppressLint("ClickableViewAccessibility")
    inner class WidgetVH(binding: ComposeViewHolderBinding) : BaseVH<WidgetData>(binding),
        EventObserver {
        private fun openWidgetConfig(currentData: WidgetData) {
            val provider = currentData.widgetProviderComponent

            if (provider == null) {
                Toast.makeText(context, R.string.error_reconfiguring_widget, Toast.LENGTH_SHORT)
                    .show()
                context.logUtils.normalLog("Unable to reconfigure widget: provider is null.")
            } else {
                val pkg = provider.packageName
                val providerInfo = manager.getAppWidgetInfo(currentData.id)
                    ?: (context.getAllInstalledWidgetProviders(pkg)
                        .find { info -> info.provider == provider })

                if (providerInfo == null) {
                    Toast.makeText(context, R.string.error_reconfiguring_widget, Toast.LENGTH_SHORT)
                        .show()
                    context.logUtils.normalLog("Unable to reconfigure widget $provider: provider info is null.")
                } else {
                    launchReconfigure(
                        id = currentData.id,
                        providerInfo = providerInfo,
                    )
                }
            }
        }

        override fun performBind(data: WidgetData) {
            launch {
                context.logUtils.debugLog("Binding ${data.copy(icon = null, iconRes = null)}", null)
                context.eventManager.addObserver(this@WidgetVH)

                onResize(data, 0, 1)

                val widgetInfo = withContext(Dispatchers.Main) {
                    if (data.type == WidgetType.WIDGET) {
                        try {
                            manager.getAppWidgetInfo(data.id)
                        } catch (_: PackageManager.NameNotFoundException) {
                            null
                        }
                    } else {
                        null
                    }
                }

                binding.root.setThemedContent {
                    val currentEditingPosition by viewModel.currentEditingInterfacePosition.collectAsState()

                    viewModel.WidgetItemLayout(
                        needsReconfigure = data.type == WidgetType.WIDGET && widgetInfo == null,
                        widgetData = data,
                        widgetContents = { modifier ->
                            when (data.safeType) {
                                WidgetType.WIDGET -> WidgetContents(data, widgetInfo!!, modifier)
                                WidgetType.SHORTCUT, WidgetType.LAUNCHER_SHORTCUT -> {
                                    ShortcutContent(data, modifier)
                                }

                                WidgetType.LAUNCHER_ITEM -> LauncherIconContent(data, modifier)
                                WidgetType.HEADER -> {}
                            }
                        },
                        cornerRadiusKey = viewModel.widgetCornerRadiusKey,
                        launchIconOverride = {
                            launchShortcutIconOverride(data.id)
                        },
                        launchReconfigure = {
                            openWidgetConfig(data)
                        },
                        remove = {
                            onRemoveCallback(data, data.id)
                        },
                        getResizeThresholdPx = {
                            getThresholdPx(it)
                        },
                        onResize = { overThreshold, step, amount, direction, vertical ->
                            handleResize(
                                currentData = data,
                                overThreshold = overThreshold,
                                step = step,
                                amount = amount,
                                direction = direction,
                                vertical = vertical,
                            )
                        },
                        liftCallback = {
                            notifyItemChanged(bindingAdapterPosition)
                        },
                        rowCount = rowCount,
                        colCount = colCount,
                        isEditing = currentEditingPosition == bindingAdapterPosition,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        override fun onDestroy() {
            context.eventManager.removeObserver(this)
        }

        override fun onEvent(event: Event) {
            when (event) {
                is Event.FrameMoveFinished -> {
                    if (event.frameId == holderId) {
                        val pos = bindingAdapterPosition

                        if (pos != -1 && pos < widgets.size) {
                            onResize(widgets[pos], 0, 1)
                        }
                    }
                }

                else -> {}
            }
        }

        @Composable
        private fun WidgetContents(
            data: WidgetData,
            widgetInfo: AppWidgetProviderInfo,
            modifier: Modifier = Modifier,
        ) {
            val resources = LocalResources.current
            var widgetView by remember {
                mutableStateOf<View?>(null)
            }

            LaunchedEffect(null) {
                if (!BrokenAppsRegistry.isBroken(widgetInfo)) {
                    try {
                        withContext(Dispatchers.Main) {
                            widgetView = viewCacheRegistry.getOrCreateView(
                                SafeContextWrapper(itemView.context),
                                data.id,
                                widgetInfo,
                            ).apply hostView@{
                                findScrollableViewsInHierarchy(this).forEach { list ->
                                    list.isNestedScrollingEnabled = true
                                }

                                this.viewTreeObserver.addOnGlobalLayoutListener {
                                    findScrollableViewsInHierarchy(this).forEach { list ->
                                        list.isNestedScrollingEnabled = true
                                    }
                                }

                                val width = this@BaseAdapter.display.pxToDp(itemView.width)
                                val height = this@BaseAdapter.display.pxToDp(itemView.height)

                                val paddingValue = this@BaseAdapter.display.pxToDp(
                                    resources.getDimensionPixelSize(R.dimen.app_widget_padding),
                                )

                                // Workaround to fix the One UI 5.1 battery grid widget on some devices.
                                if (widgetInfo.provider.packageName == "com.android.settings.intelligence") {
                                    updateAppWidgetOptions(
                                        manager.getAppWidgetOptions(appWidgetId).apply {
                                            putBoolean("hsIsHorizontalIcon", false)
                                            putInt("semAppWidgetRowSpan", 1)
                                        })
                                }

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    updateAppWidgetSize(
                                        manager.getAppWidgetOptions(appWidgetId),
                                        listOf(
                                            SizeF(
                                                width + 2 * paddingValue,
                                                height + 2 * paddingValue,
                                            ),
                                        ),
                                    )
                                } else {
                                    val adjustedWidth = width + 2 * paddingValue
                                    val adjustedHeight = height + 2 * paddingValue

                                    @Suppress("DEPRECATION")
                                    updateAppWidgetSize(
                                        manager.getAppWidgetOptions(appWidgetId),
                                        adjustedWidth.toInt(),
                                        adjustedHeight.toInt(),
                                        adjustedWidth.toInt(),
                                        adjustedHeight.toInt(),
                                    )
                                }
                            }
                        }
                    } catch (e: Throwable) {
                        context.logUtils.normalLog(
                            "Unable to bind widget view ${widgetInfo.provider}",
                            e
                        )

                        if (e is SecurityException) {
                            Toast.makeText(
                                context,
                                resources.getString(
                                    R.string.bind_widget_error,
                                    widgetInfo.provider
                                ),
                                Toast.LENGTH_LONG,
                            ).show()
                            currentWidgets = currentWidgets.toMutableList().apply {
                                remove(data)
                                host.deleteAppWidgetId(data.id)
                            }
                        } else {
                            widgetView = context.createWidgetErrorView()
                        }
                    }
                } else {
                    context.logUtils.normalLog(
                        "Broken app widget detected: ${widgetInfo.provider}. Removing from adapter list.",
                        null,
                    )
                    currentWidgets = currentWidgets.toMutableList().apply {
                        remove(data)
                        host.deleteAppWidgetId(data.id)
                    }
                }
            }

            widgetView?.let { widgetView ->
                AndroidView(
                    factory = {
                        widgetView.apply {
                            (parent as? ViewGroup)?.removeView(this)
                        }
                    },
                    modifier = modifier,
                )
            }
        }

        @Composable
        private fun LauncherIconContent(data: WidgetData, modifier: Modifier) {
            ShortcutItemLayout(
                icon = data.getIconBitmap(context),
                name = null,
                onClick = {
                    val launchIntent = Intent(Intent.ACTION_MAIN)
                    launchIntent.addCategory(Intent.CATEGORY_LAUNCHER)
                    launchIntent.`package` = data.widgetProviderComponent?.packageName
                    launchIntent.component = data.widgetProviderComponent
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                    DismissOrUnlockActivity.launch(
                        context = context,
                        activityIntent = launchIntent,
                    )
                },
                cornerRadiusKey = viewModel.widgetCornerRadiusKey,
                modifier = modifier,
            )
        }

        @SuppressLint("DiscouragedApi")
        @Composable
        private fun ShortcutContent(data: WidgetData, modifier: Modifier) {
            ShortcutItemLayout(
                icon = data.getIconBitmap(context),
                name = data.label,
                onClick = {
                    data.shortcutIntent?.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                        PermissionIntentLaunchActivity.start(
                            context = context,
                            intent = this,
                            launchType = PermissionIntentLaunchActivity.LaunchType.ACTIVITY,
                        )
                    }
                },
                cornerRadiusKey = viewModel.widgetCornerRadiusKey,
                modifier = modifier,
            )
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
            currentData: WidgetData,
            overThreshold: Boolean,
            step: Int,
            amount: Int,
            direction: Int,
            vertical: Boolean
        ) {
            context.logUtils.debugLog(
                "handleResize($overThreshold, $step, $amount, $direction, $vertical)",
                null
            )

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

            context.logUtils.debugLog("New size $newSizeInfo, old size $sizeInfo")

            val newData = currentData.copy(size = newSizeInfo)

            onResize(newData, amount, step)
            didResize = true
            bindingAdapterPosition.takeIf { it != -1 }?.let { pos ->
                currentWidgets = widgets.apply {
                    this[pos] = newData
                }
            }
        }

        //Make sure the item's size is properly updated on a frame resize, or on initial bind
        private fun onResize(data: WidgetData, amount: Int, direction: Int) {
            itemView.apply {
                layoutParams = layoutParams.apply {
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
    inner class AddWidgetVH(binding: ComposeViewHolderBinding) : BaseVH<Unit>(binding) {
        override fun performBind(data: Unit) {
            binding.root.setThemedContent {
                MeasuredComposable(name = "AddWidgetLayout") {
                    val resources = LocalResources.current
                    val widgetCornerRadius by rememberPreferenceState(
                        key = viewModel.widgetCornerRadiusKey,
                        value = {
                            (context.prefManager.getInt(
                                it,
                                resources.getInteger(R.integer.def_corner_radius_dp_scaled_10x),
                            ) / 10f).dp
                        },
                    )

                    Card(
                        modifier = Modifier.fillMaxSize(),
                        colors = CardColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White,
                            disabledContentColor = Color.White,
                            disabledContainerColor = Color.Transparent,
                        ),
                        shape = RoundedCornerShape(widgetCornerRadius),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    enabled = true,
                                    interactionSource = remember { MutableInteractionSource() },
                                    onClick = { launchAddActivity() },
                                    indication = ripple(
                                        color = Color.Black,
                                    ),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_baseline_add_24),
                                    contentDescription = stringResource(R.string.add_widget),
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(
                                            brush = Brush.radialGradient(
                                                0f to Color.Black.copy(alpha = 0.5f),
                                                1f to Color.Transparent,
                                            ),
                                        ),
                                )

                                Text(
                                    text = stringResource(R.string.add_widget),
                                    fontWeight = FontWeight.Bold,
                                    style = LocalTextStyle.current.copy(
                                        shadow = Shadow(
                                            color = Color.Black,
                                            offset = Offset(3f, 3f),
                                            blurRadius = 5f,
                                        ),
                                    ),
                                    fontSize = 20.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    abstract inner class BaseVH<Data : Any>(protected val binding: ComposeViewHolderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun setCompositionContext() {
            binding.root.setParentCompositionContext(rootView.createLifecycleAwareWindowRecomposer())
        }

        abstract fun performBind(data: Data)

        open fun onDestroy() {}
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
