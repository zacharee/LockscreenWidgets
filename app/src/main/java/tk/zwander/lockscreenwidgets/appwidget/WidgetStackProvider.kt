package tk.zwander.lockscreenwidgets.appwidget

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.ServiceManager
import android.util.SizeF
import android.util.SparseArray
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.PendingIntentCompat
import androidx.core.net.toUri
import androidx.core.os.BundleCompat
import androidx.core.util.forEach
import androidx.core.util.plus
import com.android.internal.appwidget.IAppWidgetService
import tk.zwander.common.appwidget.RemoteViewsProxyService
import tk.zwander.common.data.WidgetData
import tk.zwander.common.host.widgetHostCompat
import tk.zwander.common.util.Event
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.getRemoteViewsToApplyCompat
import tk.zwander.common.util.matches
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.App
import tk.zwander.lockscreenwidgets.BuildConfig
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.activities.WidgetStackConfigure
import kotlin.math.roundToInt

class WidgetStackProvider : AppWidgetProvider() {
    private val appWidgetService by lazy {
        IAppWidgetService.Stub.asInterface(
            ServiceManager.getService(Context.APPWIDGET_SERVICE),
        )
    }

    private var fromChild = false
    private lateinit var intent: Intent

    override fun onReceive(context: Context, intent: Intent) {
        fromChild = intent.getBooleanExtra(FROM_CHILD, false)
        this.intent = intent

        if (intent.action == ACTION_SWAP_INDEX) {
            val autoSwap = intent.getBooleanExtra(EXTRA_AUTO_SWAP, false)
            val targetIndex = intent.getIntExtra(EXTRA_SWAP_INDEX, -1)

            val backward = intent.getBooleanExtra(EXTRA_BACKWARD, false)
            val widgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)

            widgetIds?.forEach { widgetId ->
                if (autoSwap && context.prefManager.widgetStackAutoChange[widgetId]?.first != true) {
                    return@forEach
                }

                val allStacks = context.prefManager.widgetStackWidgets
                val stackedWidgets = (allStacks[widgetId] ?: LinkedHashSet()).toList()

                val index = (context.prefManager.widgetStackIndices[widgetId] ?: 0)
                    .coerceAtMost(stackedWidgets.lastIndex)

                val newIndex = if (targetIndex != -1) {
                    targetIndex
                } else {
                    if (backward) {
                        if (index - 1 >= 0) {
                            index - 1
                        } else {
                            stackedWidgets.lastIndex
                        }
                    } else {
                        if (index + 1 <= stackedWidgets.lastIndex) {
                            index + 1
                        } else {
                            0
                        }
                    }
                }

                intent.putExtra(EXTRA_SWAP_INDEX, newIndex)
                intent.putExtra(EXTRA_PREVIOUS_INDEX, index)

                context.prefManager.widgetStackIndices = context.prefManager.widgetStackIndices.apply {
                    this[widgetId] = newIndex
                }
            }

            onReceive(context, intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE))
        }

        super.onReceive(context, intent)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { appWidgetId ->
            val root = RemoteViews(context.packageName, R.layout.widget_stack)
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val stackedWidgets = (context.prefManager.widgetStackWidgets[appWidgetId] ?: LinkedHashSet()).toList()
            val index = (context.prefManager.widgetStackIndices[appWidgetId] ?: 0)
                .coerceAtMost(stackedWidgets.lastIndex)
                .coerceAtLeast(0)
            val widgetData = stackedWidgets.getOrNull(index)
            val widgetView =
                widgetData?.let {
                    appWidgetService.getAppWidgetViews(context.packageName, it.id)
                }

            root.setViewVisibility(
                R.id.add_widget,
                if (widgetView == null) View.VISIBLE else View.GONE,
            )
            root.setViewVisibility(
                R.id.widget_content_wrapper,
                if (widgetView == null) View.GONE else View.VISIBLE,
            )

            // Dummy click listener to prevent default click behavior opening the main app.
            root.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntentCompat.getBroadcast(
                    context,
                    Int.MAX_VALUE,
                    Intent(context, WidgetStackProvider::class.java),
                    0,
                    false,
                ),
            )

            if (widgetView != null) {
                fillInWidgetViews(
                    context = context,
                    appWidgetManager = appWidgetManager,
                    stackId = appWidgetId,
                    options = options,
                    widgetData = widgetData,
                    index = index,
                    rootWidgetViews = widgetView,
                    root = root,
                    stackedWidgets = stackedWidgets,
                )
            } else {
                root.setOnClickPendingIntent(
                    R.id.add_widget,
                    PendingIntentCompat.getActivity(
                        context,
                        appWidgetId,
                        Intent(context, WidgetStackConfigure::class.java)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                        0,
                        false,
                    ),
                )
            }

            App.instance.updateAutoChangeForStack(appWidgetId)
            App.instance.updateWidgetStackMonitor()
            appWidgetManager.updateAppWidget(appWidgetId, root)
            context.eventManager.sendEvent(Event.StackUpdateComplete(appWidgetId))
        }
    }

    @SuppressLint("RestrictedApi")
    private fun fillInWidgetViews(
        context: Context,
        appWidgetManager: AppWidgetManager,
        stackId: Int,
        options: Bundle?,
        widgetData: WidgetData,
        index: Int,
        rootWidgetViews: RemoteViews,
        root: RemoteViews,
        stackedWidgets: List<WidgetData>,
    ) {
        root.setOnClickPendingIntent(
            R.id.stack_forward,
            PendingIntentCompat.getBroadcast(
                context,
                stackId,
                createSwapIntent(context, intArrayOf(stackId), false),
                0,
                false,
            )
        )
        root.setOnClickPendingIntent(
            R.id.stack_backward,
            PendingIntentCompat.getBroadcast(
                context,
                stackId + 100000,
                createSwapIntent(context, intArrayOf(stackId), true),
                0,
                false,
            ),
        )
        root.setOnClickPendingIntent(
            R.id.stack_configure,
            PendingIntentCompat.getActivity(
                context,
                stackId + 10000,
                Intent(context, WidgetStackConfigure::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, stackId),
                0,
                false,
            ),
        )

        val previousIndex = intent.getIntExtra(EXTRA_PREVIOUS_INDEX, -1)

        root.removeAllViews(R.id.stack_dot_row)

        repeat(stackedWidgets.size) {
            val dot = RemoteViews(context.packageName, R.layout.widget_stack_page_dot)
            dot.setViewVisibility(
                R.id.page_dot_active,
                if (index == it && previousIndex != -1) {
                    View.VISIBLE
                } else {
                    View.INVISIBLE
                },
            )
            dot.setViewVisibility(
                R.id.page_dot_inactive,
                if (it == previousIndex) {
                    View.VISIBLE
                } else {
                    View.INVISIBLE
                },
            )
            dot.setViewVisibility(
                R.id.page_dot_static,
                if (previousIndex == -1 || (index != previousIndex && index != it)) {
                    View.VISIBLE
                } else {
                    View.INVISIBLE
                },
            )
            dot.setImageViewResource(
                R.id.page_dot_static,
                if (index == it) {
                    R.drawable.circle_5
                } else {
                    R.drawable.circle_0
                },
            )
            dot.setOnClickPendingIntent(
                R.id.page_dot_root,
                PendingIntentCompat.getBroadcast(
                    context,
                    80000 + stackId + it,
                    createSwapIntent(
                        context = context,
                        ids = intArrayOf(stackId),
                        backward = false,
                        autoSwap = false,
                        swapIndex = it,
                    ),
                    0,
                    false,
                ),
            )
            root.addView(R.id.stack_dot_row, dot)
        }

        val realSize = options?.let {
            extractSizeFromOptions(it)
        }

        realSize?.apply {
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, realSize.first.roundToInt())
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, realSize.first.roundToInt())
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, realSize.second.roundToInt())
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, realSize.second.roundToInt())
        }

        if (options?.matches(appWidgetManager.getAppWidgetOptions(widgetData.id)) != true) {
            appWidgetManager.updateAppWidgetOptions(widgetData.id, options)
        }

        val viewsToApply = rootWidgetViews.getRemoteViewsToApplyCompat(context, realSize?.let { SizeF(it.first, it.second) })

        val rem = index % 3
        val prevIndex = if (index > 0) {
            index - 1
        } else {
            stackedWidgets.lastIndex
        }
        val prevRem = prevIndex % 3
        val realRem = if (rem == prevRem) {
            if (rem > 0) {
                rem - 1
            } else {
                2
            }
        } else {
            rem
        }

        when (realRem) {
            0 -> {
                root.removeAllViews(R.id.widget_content_even)
                root.addView(R.id.widget_content_even, viewsToApply)
            }

            1 -> {
                root.removeAllViews(R.id.widget_content_odd)
                root.addView(R.id.widget_content_odd, viewsToApply)
            }

            2 -> {
                root.removeAllViews(R.id.widget_content_third)
                root.addView(R.id.widget_content_third, viewsToApply)
            }
        }

        if (!fromChild) {
            root.setDisplayedChild(R.id.widget_content, realRem)
        }

        hoistWidgetData(
            context = context,
            innerView = viewsToApply,
            outerView = root,
            rootWidgetViews = rootWidgetViews,
            innerWidgetId = widgetData.id,
        )
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?,
    ) {
        onUpdate(
            context,
            appWidgetManager,
            intArrayOf(appWidgetId),
        )
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)

        appWidgetIds.forEach { appWidgetId ->
            context.widgetHostCompat.deleteAppWidgetId(appWidgetId)
        }
    }

    companion object {
        const val ACTION_SWAP_INDEX = "${BuildConfig.APPLICATION_ID}.intent.action.SWAP_INDEX"
        const val FROM_CHILD = "from_child"
        const val EXTRA_BACKWARD = "backward"
        const val EXTRA_AUTO_SWAP = "auto_swap"
        const val EXTRA_SWAP_INDEX = "swap_index"
        const val EXTRA_PREVIOUS_INDEX = "previous_index"

        fun update(context: Context, ids: IntArray, fromChild: Boolean = false) {
            context.sendBroadcast(
                createBaseIntent(context, ids)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(FROM_CHILD, fromChild),
            )
        }

        fun updateOptions(context: Context, ids: IntArray, options: Bundle?) {
            context.sendBroadcast(
                createBaseIntent(context, ids)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options),
            )
        }

        fun createSwapIntent(
            context: Context,
            ids: IntArray,
            backward: Boolean,
            autoSwap: Boolean = false,
            swapIndex: Int? = null,
        ): Intent {
            return createBaseIntent(context, ids)
                .setAction(ACTION_SWAP_INDEX)
                .putExtra(EXTRA_BACKWARD, backward)
                .putExtra(EXTRA_AUTO_SWAP, autoSwap)
                .apply {
                    swapIndex?.let {
                        putExtra(EXTRA_SWAP_INDEX, it)
                    }
                }
                .setData("widget://${ids.joinToString(",")}".toUri())
        }

        fun extractSizeFromOptions(options: Bundle): Pair<Float, Float> {
            val optionsWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH).toFloat()
            val optionsHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).toFloat()

            val baseSize = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val allSizes = BundleCompat.getParcelableArrayList(
                    options,
                    AppWidgetManager.OPTION_APPWIDGET_SIZES,
                    SizeF::class.java,
                )

                (allSizes?.minOf { it.width } ?: optionsWidth) to (allSizes?.maxOf { it.height } ?: optionsHeight)
            } else {
                optionsWidth to optionsHeight
            }

            // Subtract bottom bar dp height.
            return baseSize.first to (baseSize.second - 36)
        }

        private fun createBaseIntent(context: Context, ids: IntArray): Intent {
            return Intent(context, WidgetStackProvider::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }

        @Suppress("UNCHECKED_CAST")
        private fun hoistWidgetData(
            context: Context,
            innerView: RemoteViews,
            outerView: RemoteViews,
            rootWidgetViews: RemoteViews,
            innerWidgetId: Int,
        ) {
            val sourceActions = innerView::class.java.getDeclaredField("mActions")
                .apply { isAccessible = true }
                .get(innerView) as? MutableList<Any>

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && innerView.hasLegacyLists()) {
                val collectionCacheSource = innerView::class.java.getDeclaredField("mCollectionCache")
                    .apply { isAccessible = true }
                    .get(innerView)
                val rootCollectionCacheSource = rootWidgetViews::class.java.getDeclaredField("mCollectionCache")
                    .apply { isAccessible = true }
                    .get(rootWidgetViews)

                val collectionCacheDest = outerView::class.java.getDeclaredField("mCollectionCache")
                    .apply { isAccessible = true }
                    .get(outerView)

                val sourceIdToUriMapping = collectionCacheSource::class.java.getDeclaredField("mIdToUriMapping")
                    .apply { isAccessible = true }
                    .get(collectionCacheSource) as SparseArray<String>
                val sourceUriToCollectionMapping = collectionCacheSource::class.java.getDeclaredField("mUriToCollectionMapping")
                    .apply { isAccessible = true }
                    .get(collectionCacheSource) as Map<String, *>

                val rootSourceIdToUriMapping = rootCollectionCacheSource::class.java.getDeclaredField("mIdToUriMapping")
                    .apply { isAccessible = true }
                    .get(rootCollectionCacheSource) as SparseArray<String>
                val rootSourceUriToCollectionMapping = rootCollectionCacheSource::class.java.getDeclaredField("mUriToCollectionMapping")
                    .apply { isAccessible = true }
                    .get(rootCollectionCacheSource) as Map<String, *>

                (sourceIdToUriMapping + rootSourceIdToUriMapping).forEach { intentId, uri ->
                    val items = sourceUriToCollectionMapping[uri] ?: rootSourceUriToCollectionMapping[uri]!!

                    collectionCacheDest::class.java.getDeclaredMethod(
                        "addMapping",
                        Int::class.java,
                        String::class.java,
                        items::class.java,
                    ).apply {
                        isAccessible = true
                    }.invoke(
                        collectionCacheDest,
                        intentId,
                        uri,
                        items,
                    )
                }
            }

            val collectionActions = (sourceActions?.filter { action ->
                action::class.java.name.contains("SetRemoteCollectionItemListAdapterAction")
                        || action::class.java.name.contains("SetRemoteViewsAdapterIntent")
            } ?: listOf()).map { action ->
                if (action::class.java.name.contains("SetRemoteViewsAdapterIntent")) {
                    val wrappedIntentField = try {
                        action::class.java.getDeclaredField("mIntent")
                    } catch (_: NoSuchFieldException) {
                        action::class.java.getDeclaredField("intent")
                    }.apply { isAccessible = true }
                    val wrappedIntent = wrappedIntentField.get(action) as? Intent
                    val newIntent = RemoteViewsProxyService.createProxyIntent(
                        context = context,
                        widgetId = innerWidgetId,
                        widgetIntent = wrappedIntent,
                    )
                    wrappedIntentField.set(action, newIntent)
                }

                action
            }

            val destActionsField = outerView::class.java.getDeclaredField("mActions")
                .apply { isAccessible = true }

            if (destActionsField.get(outerView) == null) {
                destActionsField.set(outerView, collectionActions)
            } else {
                (destActionsField.get(outerView) as? MutableList<Any>)?.addAll(collectionActions)
            }

            sourceActions?.removeAll { collectionActions.contains(it) }
        }
    }
}
