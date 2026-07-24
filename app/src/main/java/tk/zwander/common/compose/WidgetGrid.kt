package tk.zwander.common.compose

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.util.SizeF
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.NestedScrollingChild
import androidx.core.view.forEach
import androidx.recyclerview.widget.RecyclerView
import com.bugsnag.android.performance.compose.MeasuredComposable
import dev.zwander.lswinterconnect.peekLogUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.reorderable
import tk.zwander.common.activities.DismissOrUnlockActivity
import tk.zwander.common.activities.PermissionIntentLaunchActivity
import tk.zwander.common.compose.components.ShortcutItemLayout
import tk.zwander.common.compose.components.WidgetItemLayout
import tk.zwander.common.compose.util.rememberPreferenceState
import tk.zwander.common.compose.util.widgetViewCacheRegistry
import tk.zwander.common.customgrid.*
import tk.zwander.common.data.WidgetData
import tk.zwander.common.data.WidgetType
import tk.zwander.common.host.widgetHostCompat
import tk.zwander.common.listeners.WidgetResizeListener
import tk.zwander.common.util.*
import tk.zwander.common.util.mitigations.SafeContextWrapper
import tk.zwander.common.views.remote.ComposeAdapterView
import tk.zwander.lockscreenwidgets.R
import kotlin.math.absoluteValue
import kotlin.math.min

@Composable
fun <VM : BaseDelegate.BaseViewModel<*, *>> VM.WidgetGrid(
    currentWidgets: List<WidgetData>,
    onWidgetsChanged: (List<WidgetData>) -> Unit,
    orientation: Orientation,
    columnCount: Int,
    rowCount: Int,
    resizeThresholdPx: (which: WidgetResizeListener.Which) -> Int,
    launchAddActivity: () -> Unit,
    launchReconfigure: (id: Int, providerInfo: AppWidgetProviderInfo) -> Unit,
    launchShortcutIconOverride: (id: Int) -> Unit,
    modifier: Modifier = Modifier,
    rowSpanForAddButton: Int = 1,
    minColSpan: Int = 1,
    minRowSpan: Int = 1,
    enableSnapping: Boolean = false,
    contentPadding: PaddingValues = PaddingValues.Zero,
    lazyGridState: LazySpannedGridState = rememberLazySpannedGridState(),
) {
    val manager = remember { context.appWidgetManager }
    var currentEditingId by currentEditingInterfaceId.collectAsMutableState()

    val updatedCurrentWidgets by rememberUpdatedState(currentWidgets)
    val reorderableState = rememberReorderableLazySpannedGridState(
        onMove = { from, to ->
            onWidgetsChanged(
                updatedCurrentWidgets.toMutableList().apply {
                    add(to.index, removeAt(from.index))
                },
            )
        },
        gridState = lazyGridState,
    )

    val updatedMinColSpan by rememberUpdatedState(minColSpan)
    val updatedMinRowSpan by rememberUpdatedState(minRowSpan)
    val updatedRowCount by rememberUpdatedState(rowCount)
    val updatedColumnCount by rememberUpdatedState(columnCount)
    val spans by remember {
        derivedStateOf {
            updatedCurrentWidgets.map { widget ->
                val size = widget.safeSize

                IntSize(
                    size.safeWidgetWidthSpan
                        .coerceAtMost(updatedColumnCount)
                        .coerceAtLeast(updatedMinColSpan),
                    size.safeWidgetHeightSpan
                        .coerceAtMost(updatedRowCount)
                        .coerceAtLeast(updatedMinRowSpan),
                )
            }
        }
    }
    val updatedSpans by rememberUpdatedState(spans)
    val updatedRowSpanForAddButton by rememberUpdatedState(rowSpanForAddButton)

    val flingBehavior = if (enableSnapping) {
        rememberSpannedGridSnapFlingBehavior(lazyGridState)
    } else {
        null
    }

    val layoutDirection = LocalLayoutDirection.current
    val coroutineScope = rememberCoroutineScope()
    val rootView = LocalView.current

    LaunchedEffect(currentEditingId) {
        globalState.handlingClick.remove(holderId)
        globalState.itemIsActive.value = currentEditingId != RecyclerView.NO_POSITION
    }

    if (orientation == Orientation.Vertical) {
        LazyVerticalSpannedGrid(
            columnCount = columnCount,
            rowCount = rowCount,
            state = reorderableState.gridState,
            flingBehavior = flingBehavior,
            modifier = modifier
                .interceptUnclaimedDrags(lazyGridState, orientation, layoutDirection, coroutineScope, rootView, currentEditingId, flingBehavior)
                .reorderable(reorderableState)
                .detectReorderAfterLongPress(reorderableState),
            contentPadding = contentPadding,
        ) {
            widgetItems(
                currentWidgetsList = updatedCurrentWidgets,
                columnCount = updatedColumnCount,
                rowCount = updatedRowCount,
                rowSpanForAddButton = updatedRowSpanForAddButton,
                launchAddActivity = launchAddActivity,
                launchReconfigure = launchReconfigure,
                launchShortcutIconOverride = launchShortcutIconOverride,
                spans = updatedSpans,
                reorderableState = reorderableState,
                manager = manager,
                currentEditingId = currentEditingId,
                onCurrentEditingIdChanged = {
                    currentEditingId = it
                },
                onWidgetsChanged = onWidgetsChanged,
                resizeThresholdPx = resizeThresholdPx,
                viewModel = this@WidgetGrid,
            )
        }
    } else {
        LazyHorizontalSpannedGrid(
            columnCount = columnCount,
            rowCount = rowCount,
            state = reorderableState.gridState,
            flingBehavior = flingBehavior,
            modifier = modifier
                .interceptUnclaimedDrags(lazyGridState, orientation, layoutDirection, coroutineScope, rootView, currentEditingId, flingBehavior)
                .reorderable(reorderableState)
                .detectReorderAfterLongPress(reorderableState),
            contentPadding = contentPadding,
        ) {
            widgetItems(
                currentWidgetsList = updatedCurrentWidgets,
                columnCount = updatedColumnCount,
                rowCount = updatedRowCount,
                rowSpanForAddButton = updatedRowSpanForAddButton,
                launchAddActivity = launchAddActivity,
                launchReconfigure = launchReconfigure,
                launchShortcutIconOverride = launchShortcutIconOverride,
                spans = updatedSpans,
                reorderableState = reorderableState,
                manager = manager,
                currentEditingId = currentEditingId,
                onCurrentEditingIdChanged = {
                    currentEditingId = it
                },
                onWidgetsChanged = onWidgetsChanged,
                resizeThresholdPx = resizeThresholdPx,
                viewModel = this@WidgetGrid,
            )
        }
    }
}

private fun <VM: BaseDelegate.BaseViewModel<*, *>> LazySpannedGridScope.widgetItems(
    currentWidgetsList: List<WidgetData>,
    columnCount: Int,
    rowCount: Int,
    rowSpanForAddButton: Int,
    launchAddActivity: () -> Unit,
    launchReconfigure: (id: Int, providerInfo: AppWidgetProviderInfo) -> Unit,
    launchShortcutIconOverride: (id: Int) -> Unit,
    spans: List<IntSize>,
    reorderableState: ReorderableLazySpannedGridState,
    manager: AppWidgetManager,
    currentEditingId: Int,
    onCurrentEditingIdChanged: (Int) -> Unit,
    onWidgetsChanged: (List<WidgetData>) -> Unit,
    resizeThresholdPx: (which: WidgetResizeListener.Which) -> Int,
    viewModel: VM,
) {
    with(viewModel) {
        if (currentWidgetsList.isEmpty()) {
            item(key = "ADD", span = SpannedGridItemSpan(columnCount, rowSpanForAddButton)) {
                MeasuredComposable(name = "AddWidgetLayout") {
                    val resources = LocalResources.current
                    val widgetCornerRadius by rememberPreferenceState(
                        key = widgetCornerRadiusKey,
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

        itemsIndexed(
            items = currentWidgetsList,
            span = { index, _ -> SpannedGridItemSpan(spans[index]) },
            key = { _, data -> data.id },
        ) { index, data ->
            val updatedData by rememberUpdatedState(data)

            ReorderableItem(
                state = reorderableState,
                key = updatedData.id,
                orientationLocked = false,
                modifier = Modifier.animateItem(),
            ) { isDragging ->
                val elevation by animateDpAsState(if (isDragging) 16.dp else 0.dp)
                val scale by animateFloatAsState(if (isDragging) 1.08f else 1f)
                val alpha by animateFloatAsState(if (isDragging) 0.7f else 1f)

                val widgetInfo = if (updatedData.type == WidgetType.WIDGET) {
                    try {
                        manager.getAppWidgetInfo(updatedData.id)
                    } catch (e: PackageManager.NameNotFoundException) {
                        context.logUtils.debugLog(
                            "Unable to retrieve widget info for ${updatedData.id} ${data.widgetProviderComponent}",
                            e,
                        )
                        null
                    }
                } else {
                    null
                }

                LaunchedEffect(isDragging) {
                    if (isDragging) {
                        onCurrentEditingIdChanged(
                            if (currentEditingId == updatedData.id) {
                                RecyclerView.NO_POSITION
                            } else {
                                updatedData.id
                            },
                        )
                    }
                }

                WidgetItemLayout(
                    needsReconfigure = widgetInfo == null,
                    widgetData = updatedData,
                    widgetContents = { modifier ->
                        when (updatedData.safeType) {
                            WidgetType.WIDGET -> widgetInfo?.let {
                                WidgetContents(
                                    data = updatedData,
                                    widgetInfo = widgetInfo,
                                    modifier = modifier,
                                    currentWidgets = currentWidgetsList,
                                    onWidgetsChanged = onWidgetsChanged,
                                )
                            }
                            WidgetType.SHORTCUT, WidgetType.LAUNCHER_SHORTCUT -> {
                                ShortcutContent(data = updatedData, modifier = modifier)
                            }
                            WidgetType.LAUNCHER_ITEM -> LauncherIconContent(
                                data = updatedData,
                                modifier = modifier,
                            )
                            WidgetType.HEADER -> {}
                        }
                    },
                    cornerRadiusKey = widgetCornerRadiusKey,
                    launchIconOverride = {
                        launchShortcutIconOverride(updatedData.id)
                    },
                    launchReconfigure = {
                        launchReconfigure(updatedData.id, widgetInfo!!)
                    },
                    remove = {
                        itemToRemove.value = updatedData
                    },
                    getResizeThresholdPx = resizeThresholdPx,
                    onResize = { overThreshold, step, amount, direction, vertical ->
                        handleResize(
                            currentData = updatedData,
                            overThreshold = overThreshold,
                            step = step,
                            amount = amount,
                            direction = direction,
                            vertical = vertical,
                            index = index,
                            currentWidgets = currentWidgetsList,
                            onWidgetsChanged = onWidgetsChanged,
                        )
                    },
                    liftCallback = {},
                    rowCount = rowCount,
                    colCount = columnCount,
                    isEditing = currentEditingId == updatedData.id,
                    modifier = Modifier.fillMaxSize()
                        .scale(scale)
                        .alpha(alpha)
                        .shadow(elevation)
                        .systemGestureExclusion(),
                    ignoreTouchesKey = ignoreWidgetTouchesKey,
                    doubleTapTurnOffKey = doubleTapTurnOffDisplayKey,
                )
            }
        }
    }
}

@Composable
private fun <VM : BaseDelegate.BaseViewModel<*, *>> VM.WidgetContents(
    data: WidgetData,
    widgetInfo: AppWidgetProviderInfo,
    currentWidgets: List<WidgetData>,
    onWidgetsChanged: (List<WidgetData>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val host = remember { context.widgetHostCompat }
    val manager = remember { context.appWidgetManager }
    val viewCacheRegistry = remember { context.widgetViewCacheRegistry }
    val resources = LocalResources.current
    val density = LocalDensity.current
    val currentEditingId by currentEditingInterfaceId.collectAsState()
    val updatedData by rememberUpdatedState(data)

    var widgetView by remember {
        mutableStateOf<View?>(null, neverEqualPolicy())
    }

    LaunchedEffect(currentEditingId) {
        if (currentEditingId == updatedData.id) {
            widgetView?.dispatchTouchEvent(
                MotionEvent.obtain(
                    System.currentTimeMillis(),
                    System.currentTimeMillis(),
                    MotionEvent.ACTION_CANCEL,
                    0f, 0f, 0,
                ),
            )
        }
    }

    BoxWithConstraints(
        modifier = modifier,
    ) {
        val width = with(density) {
            constraints.maxWidth.toDp()
        }
        val height = with(density) {
            constraints.maxHeight.toDp()
        }
        val paddingValue = dimensionResource(R.dimen.app_widget_padding)

        LaunchedEffect(width, height, data.id, data.safeSize) {
            if (!BrokenAppsRegistry.isBroken(widgetInfo)) {
                try {
                    withContext(Dispatchers.Main) {
                        widgetView = viewCacheRegistry.getOrCreateView(
                            SafeContextWrapper(context),
                            data.id,
                            widgetInfo,
                        ).apply hostView@{
                            findScrollableViewsInHierarchy(this).forEach { list ->
                                list.isNestedScrollingEnabled = true
                            }

                            display?.let { display ->
                                // Workaround to fix the One UI 5.1 battery grid widget on some devices.
                                if (widgetInfo.provider.packageName == "com.android.settings.intelligence") {
                                    updateAppWidgetOptions(
                                        manager.getAppWidgetOptions(appWidgetId).apply {
                                            putBoolean("hsIsHorizontalIcon", false)
                                            putInt("semAppWidgetRowSpan", 1)
                                            putInt("displayId", display.displayId)
                                        })
                                }

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    updateAppWidgetSize(
                                        manager.getAppWidgetOptions(appWidgetId),
                                        [
                                            SizeF(
                                                width.value + 2 * paddingValue.value,
                                                height.value + 2 * paddingValue.value,
                                            ),
                                        ],
                                    )
                                } else {
                                    val adjustedWidth = width.value + 2 * paddingValue.value
                                    val adjustedHeight = height.value + 2 * paddingValue.value

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
                        onWidgetsChanged(
                            currentWidgets.toMutableList().apply {
                                remove(data)
                                host.deleteAppWidgetId(data.id)
                            },
                        )
                    } else {
                        widgetView = context.createWidgetErrorView()
                    }
                }
            } else {
                context.logUtils.normalLog(
                    "Broken app widget detected: ${widgetInfo.provider}. Removing from adapter list.",
                    null,
                )
                onWidgetsChanged(
                    currentWidgets.toMutableList().apply {
                        remove(data)
                        host.deleteAppWidgetId(data.id)
                    },
                )
            }
        }

        AndroidView(
            factory = { FrameLayout(it) },
            modifier = Modifier.fillMaxSize(),
            update = {
                it.removeAllViews()
                widgetView?.let { v ->
                    it.addView(v.andRemoveFromParent())
                }
            }
        )
    }
}

@Composable
private fun <VM : BaseDelegate.BaseViewModel<*, *>> VM.LauncherIconContent(data: WidgetData, modifier: Modifier) {
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
        cornerRadiusKey = widgetCornerRadiusKey,
        modifier = modifier,
    )
}

@SuppressLint("DiscouragedApi")
@Composable
private fun <VM : BaseDelegate.BaseViewModel<*, *>> VM.ShortcutContent(data: WidgetData, modifier: Modifier) {
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
        cornerRadiusKey = widgetCornerRadiusKey,
        modifier = modifier,
    )
}

private fun findScrollableViewsInHierarchy(root: View): List<View> {
    val ret = arrayListOf<View>()

    if (root is ViewGroup) {
        if (root !is ComposeAdapterView) {
            ret.add(root)
        }

        root.forEach { child ->
            if (child.isNestedScrollCapable()) {
                ret.add(child)
            } else if (child is ViewGroup) {
                ret.addAll(findScrollableViewsInHierarchy(child))
            }
        }
    }

    return ret
}

/**
 * Whether this view is a type that can participate in nested scrolling, regardless of whether it
 * *currently* has enough content to actually scroll. This runs right after a widget's view is
 * created, but RemoteViews' collection widgets (ListView/GridView, both [AbsListView] subclasses)
 * load their rows asynchronously over IPC via `RemoteViewsAdapter` — so at that point,
 * `canScrollVertically`/`canScrollHorizontally` can't be trusted to reflect whether the view *will*
 * need to scroll once its content actually loads; checking the view's type instead is immediate
 * and doesn't depend on that timing.
 */
private fun View.isNestedScrollCapable(): Boolean {
    return this is AbsListView ||
        this is ScrollView ||
        this is HorizontalScrollView ||
        this is RecyclerView ||
        this is NestedScrollingChild
}

/**
 * Whether a view at [screenX]/[screenY] (screen coordinates, matching [MotionEvent.getRawX]/
 * [MotionEvent.getRawY]) — or one of its ancestors up to (and including) this view — both
 * [isNestedScrollCapable] and can currently still scroll further in [direction] (see
 * [View.canScrollVertically]/[View.canScrollHorizontally] for what [direction]'s sign means).
 *
 * Used by [interceptUnclaimedDrags] to tell a widget's own, still-scrollable inner list apart from
 * everything else (its plain background, or a list that's already scrolled to its own limit in
 * this direction) — both of which should fall through to the outer grid instead.
 */
private fun View.hasScrollableDescendantAt(screenX: Float, screenY: Float, isVertical: Boolean, direction: Int): Boolean {
    if (this is ViewGroup) {
        val rect = Rect()
        // Reverse order to match touch dispatch/draw order: the topmost (last-drawn) child wins.
        for (i in childCount - 1 downTo 0) {
            val child = getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            if (!child.getGlobalVisibleRect(rect) || !rect.contains(screenX.toInt(), screenY.toInt())) continue

            val canScroll = if (isVertical) child.canScrollVertically(direction) else child.canScrollHorizontally(direction)
            if (child.isNestedScrollCapable() && canScroll) return true
            if (child.hasScrollableDescendantAt(screenX, screenY, isVertical, direction)) return true
        }
    }
    return false
}

/**
 * Steals a drag for [gridState] once it's clearly moving along the scroll axis — specifically,
 * dragging over a widget's plain, non-scrolling background (most widgets set a root-level click
 * listener, e.g. RemoteViews' default "open app" behavior).
 *
 * This mirrors, in Compose terms, what the legacy (pre-Compose) widget grid did explicitly via
 * `NestedRecyclerView`/`ScrollingItemTouchRecyclerView.dispatchTouchEvent`
 * (`app/src/main/java/tk/zwander/common/views/`): track raw motion, and once it exceeds touch
 * slop along the scrollable axis, take over.
 *
 * This *used* to decide at [PointerEventPass.Final] — after every descendant, Compose or native,
 * had already had a chance to consume — and only take over if still unconsumed by then, so it
 * wouldn't also steal drags a widget's own inner scrollable content (a [isNestedScrollCapable]
 * view) legitimately wanted. That was correct in theory, but logging confirmed it never actually
 * won: a widget's native content typically calls `requestDisallowInterceptTouchEvent(true)` as
 * soon as *it* recognizes the drag (completely standard Android practice — protecting its own
 * gesture, e.g. click/ripple feedback, from an ancestor scrollable stealing it), and per
 * `PointerInteropFilter`'s own documented behavior, once that flag is set it dispatches to the
 * native view *during the Initial pass itself* for every subsequent move — before Main, before
 * Final, before this or the grid's own `Modifier.scrollable` ever see it. The scroll position
 * never moved at all with the Final-pass version, confirming this.
 *
 * The fix is to decide at Initial too, immediately, from raw touch-slop distance, since Initial
 * dispatches ancestor-first — this modifier, wrapping the whole grid, is dispatched to before any
 * descendant (including a widget's `AndroidView` content) regardless of what that descendant later
 * does. Deciding this early means it can no longer rely on nested scroll or consumption to tell
 * "empty background" apart from "a widget's own inner scrollable list that hasn't hit its own
 * limit yet" — both trigger `requestDisallowInterceptTouchEvent` at essentially the same point
 * (past *their* own touch slop) — so instead it hit-tests [rootView] directly at the current touch
 * point via [hasScrollableDescendantAt], re-checked on every move (so a list that gets exhausted
 * mid-gesture correctly falls through to this afterward, same as the list itself would via nested
 * scroll).
 */
private fun Modifier.interceptUnclaimedDrags(
    gridState: LazySpannedGridState,
    orientation: Orientation,
    layoutDirection: LayoutDirection,
    scope: CoroutineScope,
    rootView: View,
    currentEditingId: Int,
    flingBehavior: FlingBehavior?,
): Modifier = composed {
    val updatedEditingId by rememberUpdatedState(currentEditingId)
    // Falls back to the same default Modifier.scrollable itself would use when its own
    // flingBehavior is null (a plain decay, no snapping) — see below for why this modifier needs
    // to run its own fling at all instead of just deferring to that one.
    val defaultFlingBehavior = ScrollableDefaults.flingBehavior()
    val updatedFlingBehavior by rememberUpdatedState(flingBehavior ?: defaultFlingBehavior)

    this.pointerInput(gridState, orientation, layoutDirection, rootView) {
        val isVertical = orientation == Orientation.Vertical
        val negateForRtl = !isVertical && layoutDirection == LayoutDirection.Rtl

        awaitPointerEventScope {
            while (true) {
                val downEvent = awaitPointerEvent(pass = PointerEventPass.Initial)
                val down = downEvent.changes.firstOrNull { it.pressed } ?: continue
                val downMotionEvent = downEvent.motionEvent
                val pointerId = down.id
                val downPosition = down.position
                var committed = false
                var liftedNaturally = false

                // Tracked for the whole gesture (not just once committed) so a fling at the end
                // reflects the finger's actual recent motion regardless of exactly when touch
                // slop was crossed.
                val velocityTracker = VelocityTracker()
                velocityTracker.addPosition(down.uptimeMillis, down.position)
                var flingVelocity = 0f

                // Deltas while committed are applied through this single, long-lived scroll{}
                // transaction (opened below, once, right when committing) rather than a separate
                // gridState.scrollBy call per move: scrollBy's scroll(priority) { ... } acquires
                // ScrollableState's MutatorMutex on every call, and launching a whole new
                // coroutine per pointer-move event on top of that is real per-frame overhead —
                // and, worse, overlapping launches can queue up and fall behind the finger, which
                // is exactly the "laggy, doesn't track 1:1" symptom this was causing. Modifier.
                // scrollable's own drag handling avoids this the same way: one scroll{} for the
                // whole gesture, plain scrollBy calls inside it.
                //
                // That same transaction is also where the fling below runs, once the raw drag
                // portion (the channel) drains: since this modifier consumes every move once
                // committed, Modifier.scrollable's own drag+fling handling on the grid never sees
                // those events at all (they're gone by the time its Main-pass handler would run),
                // so without this the gesture would just stop dead on lift — no momentum, no
                // snapping — which is exactly what was observed.
                var scrollChannel: Channel<Float>? = null

                try {
                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        if (!change.pressed) {
                            liftedNaturally = true
                            break
                        }
                        if (change.isConsumed) break

                        velocityTracker.addPosition(change.uptimeMillis, change.position)

                        if (committed) {
                            val delta = change.position - change.previousPosition
                            val axisDelta = if (isVertical) delta.y else delta.x
                            change.consume()

                            // LazySpannedGridState.scrollBy's delta is subtracted from the scroll
                            // offset (see its own doc comment), so a positive delta moves it
                            // backward — exactly what a finger moving down/right (revealing
                            // earlier content) should do, with no sign flip needed outside of
                            // RTL, which reverses which physical direction is logically
                            // "backward" (mirroring LazyHorizontalSpannedGrid's own
                            // reverseDirection handling).
                            val signedDelta = if (negateForRtl) -axisDelta else axisDelta
                            scrollChannel?.trySend(signedDelta)
                        } else {
                            val totalDelta = change.position - downPosition
                            val axisDistance = if (isVertical) totalDelta.y else totalDelta.x
                            val crossDistance = if (isVertical) totalDelta.x else totalDelta.y

                            if (axisDistance.absoluteValue > viewConfiguration.touchSlop &&
                                axisDistance.absoluteValue >= crossDistance.absoluteValue
                            ) {
                                // canScrollVertically/Horizontally's direction: positive means "can
                                // still scroll towards the bottom/right" (finger moving up/left
                                // reveals it), negative the opposite — so it's the sign of the raw
                                // axis movement, flipped.
                                val direction = if (axisDistance < 0) 1 else -1
                                val rawEvent = event.motionEvent ?: downMotionEvent
                                val hasScrollableDescendant = rawEvent != null &&
                                    rootView.hasScrollableDescendantAt(rawEvent.rawX, rawEvent.rawY, isVertical, direction)

                                if (!hasScrollableDescendant && updatedEditingId == RecyclerView.NO_POSITION) {
                                    committed = true
                                    change.consume()

                                    val channel = Channel<Float>(Channel.UNLIMITED)
                                    scrollChannel = channel
                                    val fb = updatedFlingBehavior
                                    scope.launch {
                                        gridState.scroll {
                                            for (d in channel) scrollBy(d)
                                            // flingVelocity is only finalized in the finally block
                                            // below, right before the channel is closed — by the
                                            // time this line runs (the for loop only completes once
                                            // the channel is closed), it's already set.
                                            if (liftedNaturally) {
                                                with(fb) { performFling(flingVelocity) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    if (committed && liftedNaturally) {
                        val rawVelocity = velocityTracker.calculateVelocity()
                        val axisVelocity = if (isVertical) rawVelocity.y else rawVelocity.x
                        // Same sign convention as the per-move scrollBy delta above.
                        flingVelocity = if (negateForRtl) -axisVelocity else axisVelocity
                    }
                    // Closing (rather than cancelling) lets the scroll{} transaction above drain
                    // any already-buffered deltas — and then run the fling above — instead of
                    // dropping them.
                    scrollChannel?.close()
                }
            }
        }
    }
}

private fun <VM : BaseDelegate.BaseViewModel<*, *>> VM.handleResize(
    currentWidgets: List<WidgetData>,
    currentData: WidgetData,
    overThreshold: Boolean,
    step: Int,
    amount: Int,
    direction: Int,
    vertical: Boolean,
    index: Int,
    onWidgetsChanged: (List<WidgetData>) -> Unit,
) {
    peekLogUtils?.debugLog(
        "handleResize($overThreshold, $step, $amount, $direction, $vertical)",
        null,
    )

    val sizeInfo = currentData.safeSize

    val newSizeInfo = if (overThreshold) {
        if (vertical) {
            sizeInfo.safeCopy(
                widgetHeightSpan = min(
                    sizeInfo.safeWidgetHeightSpan + step * direction,
                    rowCount,
                ),
            )
        } else {
            sizeInfo.safeCopy(
                widgetWidthSpan = min(
                    sizeInfo.safeWidgetWidthSpan + step * direction,
                    colCount,
                ),
            )
        }
    } else {
        sizeInfo
    }

    peekLogUtils?.debugLog("New size $newSizeInfo, old size $sizeInfo")

    val newData = currentData.copy(size = newSizeInfo)

    onWidgetsChanged(
        currentWidgets.toMutableList().apply {
            this[index] = newData
        },
    )
}
