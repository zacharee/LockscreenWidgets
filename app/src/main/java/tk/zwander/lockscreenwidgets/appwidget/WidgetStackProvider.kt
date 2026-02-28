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
    private var isSwap = false
    private var refresh = false
    private lateinit var intent: Intent

    override fun onReceive(context: Context, intent: Intent) {
        fromChild = intent.getBooleanExtra(FROM_CHILD, false)
        refresh = intent.getBooleanExtra(EXTRA_REFRESH, false)
        this.intent = intent

        if (intent.action == ACTION_SWAP_INDEX) {
            isSwap = true
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
            val stackedWidgets = (context.prefManager.widgetStackWidgets[appWidgetId] ?: LinkedHashSet()).toList()
            val index = (context.prefManager.widgetStackIndices[appWidgetId] ?: 0)
                .coerceAtMost(stackedWidgets.lastIndex)
                .coerceAtLeast(0)
            val widgetData = stackedWidgets.getOrNull(index)
            val widgetView =
                widgetData?.let {
                    context.widgetHostCompat.cachedRemoteViews[it.id]?.let { cached ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            RemoteViews(cached)
                        } else {
                            cached
                        }
                    } ?: appWidgetService.getAppWidgetViews(context.packageName, it.id)
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
                    innerWidgetId = widgetData.id,
                    index = index,
                    rootWidgetViews = widgetView,
                    root = root,
                    stackSize = stackedWidgets.size,
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
        innerWidgetId: Int,
        index: Int,
        rootWidgetViews: RemoteViews,
        root: RemoteViews,
        stackSize: Int,
    ) {
        val options = appWidgetManager.getAppWidgetOptions(stackId)

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

        repeat(stackSize) {
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

        if (options?.matches(appWidgetManager.getAppWidgetOptions(innerWidgetId)) != true) {
            appWidgetManager.updateAppWidgetOptions(innerWidgetId, options)
        }

        val viewsToApply = rootWidgetViews.getRemoteViewsToApplyCompat(
            context = context,
            size = realSize?.let { SizeF(it.first, it.second) },
        )

        processActions(
            context = context,
            innerView = viewsToApply,
            innerWidgetId = innerWidgetId,
        )

        hoistCollections(
            innerView = viewsToApply,
            rootWidgetViews = rootWidgetViews,
            outerView = root,
        )

        val rem = index % 3
        val prevIndex = if (index > 0) {
            index - 1
        } else {
            stackSize - 1
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

        if (!isSwap || refresh) {
            root.removeAllViews(R.id.widget_content_even)
            root.removeAllViews(R.id.widget_content_odd)
            root.removeAllViews(R.id.widget_content_third)
        } else {
            App.instance.scheduleWidgetRefresh(stackId)
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

        if (!fromChild && !refresh) {
            root.setDisplayedChild(R.id.widget_content, realRem)
        }

        hoistWidgetData(
            innerView = viewsToApply,
            outerView = root,
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
        const val EXTRA_REFRESH = "refresh"

        fun update(context: Context, ids: IntArray, fromChild: Boolean = false, refresh: Boolean = false) {
            context.sendBroadcast(
                createBaseIntent(context, ids)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(FROM_CHILD, fromChild)
                    .putExtra(EXTRA_REFRESH, refresh),
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
                .setData("widgets://${ids.contentToString()}".toUri())
        }

        @Suppress("UNCHECKED_CAST")
        private fun hoistCollections(
            innerView: RemoteViews,
            rootWidgetViews: RemoteViews,
            outerView: RemoteViews,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
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
        }

        @Suppress("UNCHECKED_CAST")
        private fun processActions(
            context: Context,
            innerView: RemoteViews,
            innerWidgetId: Int,
        ) {
            val sourceActions = innerView::class.java.getDeclaredField("mActions")
                .apply { isAccessible = true }
                .get(innerView) as? MutableList<Any>

            sourceActions?.forEach { action ->
                if (action::class.java.name.contains("SetRemoteViewsAdapterIntent")) {
                    val wrappedIntentField = try {
                        action::class.java.getDeclaredField("mIntent")
                    } catch (_: NoSuchFieldException) {
                        action::class.java.getDeclaredField("intent")
                    }.apply { isAccessible = true }
                    val wrappedIntent = wrappedIntentField.get(action) as? Intent

                    if (wrappedIntent?.data == null || wrappedIntent.data?.scheme != "widgetproxy") {
                        val newIntent = RemoteViewsProxyService.createProxyIntent(
                            context = context,
                            widgetId = innerWidgetId,
                            widgetIntent = wrappedIntent,
                        )
                        wrappedIntentField.set(action, newIntent)
                    }
                }
            }

//            val collectionActions = (sourceActions?.filter { action ->
//                action::class.java.name.contains("SetRemoteCollectionItemListAdapterAction")
//                        || action::class.java.name.contains("SetRemoteViewsAdapterIntent")
//            } ?: listOf()).flatMap { action ->
//                if (action::class.java.name.contains("SetRemoteCollectionItemListAdapterAction")) {
//                    val wrappedIntentField = action::class.java
//                        .getDeclaredField("mServiceIntent")
//                        .apply { isAccessible = true }
//                    val mIntentId = action::class.java
//                        .getDeclaredField("mIntentId")
//                        .apply { isAccessible = true }
//                        .get(action) as Int
//                    val wrappedIntent = wrappedIntentField.get(action) as? Intent
//
//                    action::class.java.getDeclaredField("mIsReplacedIntoAction")
//                        .apply { isAccessible = true }
//                        .set(action, true)
//
//                    val newIntent = RemoteViewsProxyService.createProxyIntent(
//                        context = context,
//                        widgetId = innerWidgetId,
//                        widgetIntent = wrappedIntent,
//                        intentId = mIntentId,
//                    )
//                    newIntent.data = newIntent.toUri(Intent.URI_INTENT_SCHEME).toUri()
//
//                    wrappedIntentField.set(action, newIntent)
//
//                    val collectionCacheDest = outerView::class.java.getDeclaredField("mCollectionCache")
//                        .apply { isAccessible = true }
//                        .get(outerView)
//
//                    val sourceUriToCollectionMapping = collectionCacheDest::class.java.getDeclaredField("mUriToCollectionMapping")
//                        .apply { isAccessible = true }
//                        .get(collectionCacheDest) as MutableMap<String, Any?>
//                    val rootSourceIdToUriMapping = collectionCacheDest::class.java.getDeclaredField("mIdToUriMapping")
//                        .apply { isAccessible = true }
//                        .get(collectionCacheDest) as SparseArray<String>
//
//                    val oldUri = rootSourceIdToUriMapping[mIntentId]
//                    val newUri = newIntent.toUri(0)
//
//                    rootSourceIdToUriMapping.set(mIntentId, newUri)
//                    sourceUriToCollectionMapping[newUri.toString()] = sourceUriToCollectionMapping[oldUri.toString()]
//
////                    val legacyAction = Class.forName($$"android.widget.RemoteViews$SetRemoteViewsAdapterIntent")
////                        .declaredConstructors
////                        .also {
////                            it.forEach {
////                                Log.e("LSW", "${it.parameters.contentToString()}")
////                            }
////                        }
////                        .find { it.parameterTypes.run { contains(Int::class.java) && contains(Intent::class.java) } }
////                        ?.apply { isAccessible = true }
////                        ?.newInstance(
////                            null,
////                            Class.forName($$"android.widget.RemoteViews$Action")
////                                .getDeclaredField("mViewId")
////                                .apply { isAccessible = true }
////                                .get(action),
////                            newIntent,
////                        )
//
//                    return@flatMap listOf(action)
////
////                    return@flatMap listOf(legacyAction)
//                }

//                listOf(action)
//            }
        }

        @SuppressLint("PrivateApi")
        @Suppress("UNCHECKED_CAST")
        private fun hoistWidgetData(
            innerView: RemoteViews,
            outerView: RemoteViews,
        ): MutableList<Any> {
            val sourceActions = innerView::class.java.getDeclaredField("mActions")
                .apply { isAccessible = true }
                .get(innerView) as? MutableList<Any>

            val collectionActions = (sourceActions?.filter { action ->
                action::class.java.name.contains("SetRemoteCollectionItemListAdapterAction")
                        || action::class.java.name.contains("SetRemoteViewsAdapterIntent")
            } ?: listOf())

//            sourceActions?.forEach { action ->
//                action::class.java.getMethod(
//                    "setHierarchyRootData",
//                    Class.forName($$"android.widget.RemoteViews$HierarchyRootData"),
//                ).apply { isAccessible = true }
//                    .invoke(
//                        action,
//                        outerView::class.java.getDeclaredMethod("getHierarchyRootData")
//                            .apply { isAccessible = true }
//                            .invoke(outerView),
//                    )
//            }

            val destActionsField = outerView::class.java.getDeclaredField("mActions")
                .apply { isAccessible = true }

            if (destActionsField.get(outerView) == null) {
                destActionsField.set(outerView, collectionActions)
            } else {
                (destActionsField.get(outerView) as? MutableList<Any>)?.addAll(collectionActions)
            }

//            rootWidgetViews::class.java.getDeclaredMethod(
//                "configureAsChild",
//                Class.forName($$"android.widget.RemoteViews$HierarchyRootData"),
//            ).apply { isAccessible = true }
//                .invoke(
//                rootWidgetViews,
//                outerView::class.java.getDeclaredMethod("getHierarchyRootData")
//                    .apply { isAccessible = true }
//                    .invoke(outerView),
//            )
//
//            innerView::class.java.getDeclaredMethod(
//                "configureAsChild",
//                Class.forName($$"android.widget.RemoteViews$HierarchyRootData"),
//            ).apply { isAccessible = true }
//                .invoke(
//                innerView,
//                outerView::class.java.getDeclaredMethod("getHierarchyRootData")
//                    .apply { isAccessible = true }
//                    .invoke(outerView),
//            )

//            sourceActions?.removeAll { collectionActions.contains(it) }

            return sourceActions!!
        }
    }
}
