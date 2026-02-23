package tk.zwander.lockscreenwidgets.appwidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.ServiceManager
import android.util.SizeF
import android.util.SparseArray
import android.widget.RemoteViews
import androidx.core.app.PendingIntentCompat
import androidx.core.util.forEach
import androidx.core.util.plus
import com.android.internal.appwidget.IAppWidgetService
import tk.zwander.common.appwidget.RemoteViewsProxyService
import tk.zwander.common.host.widgetHostCompat
import tk.zwander.common.util.getRemoteViewsToApplyCompat
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.BuildConfig
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.activities.WidgetStackConfigure

class WidgetStackProvider : AppWidgetProvider() {
    private val appWidgetService by lazy {
        IAppWidgetService.Stub.asInterface(
            ServiceManager.getService(Context.APPWIDGET_SERVICE),
        )
    }

    private var fromHost = false

    override fun onReceive(context: Context, intent: Intent) {
        fromHost = intent.getBooleanExtra(EXTRA_FROM_HOST, false)

        if (intent.action == ACTION_SWAP_INDEX) {
            val backward = intent.getBooleanExtra(EXTRA_BACKWARD, false)
            val widgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)

            widgetIds?.forEach { widgetId ->
                val allStacks = context.prefManager.widgetStackWidgets
                val stackedWidgets = (allStacks[widgetId] ?: LinkedHashSet()).toList()

                val index = (context.prefManager.widgetStackIndices[widgetId] ?: 0)
                    .coerceAtMost(stackedWidgets.lastIndex)

                val newIndex = if (backward) {
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

                context.prefManager.widgetStackIndices = context.prefManager.widgetStackIndices.apply {
                    this[widgetId] = newIndex
                }
            }

            onReceive(context, intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE))
        }

        super.onReceive(context, intent)
    }

    @Suppress("UNCHECKED_CAST")
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { appWidgetId ->
            val view = RemoteViews(context.packageName, R.layout.stack_widget)
            val stackedWidgets = (context.prefManager.widgetStackWidgets[appWidgetId] ?: LinkedHashSet()).toList()
            val stackForward = RemoteViews(context.packageName, R.layout.stack_forward)
            val stackBackward = RemoteViews(context.packageName, R.layout.stack_backward)
            val stackConfigure = RemoteViews(context.packageName, R.layout.stack_configure)

            stackForward.setOnClickPendingIntent(
                R.id.stack_forward,
                PendingIntentCompat.getBroadcast(
                    context,
                    appWidgetId,
                    createSwapIntent(context, intArrayOf(appWidgetId), false),
                    0,
                    false,
                )
            )
            stackBackward.setOnClickPendingIntent(
                R.id.stack_backward,
                PendingIntentCompat.getBroadcast(
                    context,
                    appWidgetId + 100000,
                    createSwapIntent(context, intArrayOf(appWidgetId), true),
                    0,
                    false,
                ),
            )

            stackConfigure.setOnClickPendingIntent(
                R.id.stack_configure,
                PendingIntentCompat.getActivity(
                    context,
                    appWidgetId + 10000,
                    Intent(context, WidgetStackConfigure::class.java)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
                    0,
                    false,
                ),
            )

            val index = (context.prefManager.widgetStackIndices[appWidgetId] ?: 0)
                .coerceAtMost(stackedWidgets.lastIndex)
                .coerceAtLeast(0)

            view.removeAllViews(R.id.stack_dot_row)

            if (stackedWidgets.isNotEmpty()) {
                repeat(stackedWidgets.size) {
                    val dot = RemoteViews(context.packageName, R.layout.widget_stack_page_dot)
                    dot.setImageViewResource(
                        R.id.page_dot,
                        if (index == it) {
                            R.drawable.circle
                        } else {
                            R.drawable.circle_inactive
                        },
                    )
                    view.addView(R.id.stack_dot_row, dot)
                }
            }

            stackedWidgets.getOrNull(index)?.let { widget ->
                val widgetView = appWidgetService.getAppWidgetViews(context.packageName, widget.id) ?: return@forEach

                val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                val maxWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH).toFloat()
                val maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).toFloat()

                val viewsToApply = widgetView.getRemoteViewsToApplyCompat(context, SizeF(maxWidth, maxHeight))
                val sourceActions = viewsToApply::class.java.getDeclaredField("mActions")
                    .apply { isAccessible = true }
                    .get(viewsToApply) as? MutableList<Any>

                val rem = index % 3

                when (rem) {
                    0 -> {
                        view.removeAllViews(R.id.widget_content_even)
                        view.addView(R.id.widget_content_even, viewsToApply)
                    }

                    1 -> {
                        view.removeAllViews(R.id.widget_content_odd)
                        view.addView(R.id.widget_content_odd, viewsToApply)
                    }

                    2 -> {
                        view.removeAllViews(R.id.widget_content_third)
                        view.addView(R.id.widget_content_third, viewsToApply)
                    }
                }

                view.setDisplayedChild(R.id.widget_content, rem)

                view.addView(R.id.widget_root, stackForward)
                view.addView(R.id.widget_root, stackBackward)
                view.addView(R.id.widget_root, stackConfigure)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && widgetView.hasLegacyLists()) {
                    val collectionCacheSource = viewsToApply::class.java.getDeclaredField("mCollectionCache")
                        .apply { isAccessible = true }
                        .get(viewsToApply)
                    val rootCollectionCacheSource = widgetView::class.java.getDeclaredField("mCollectionCache")
                        .apply { isAccessible = true }
                        .get(widgetView)
                    val collectionCacheDest = view::class.java.getDeclaredField("mCollectionCache")
                        .apply { isAccessible = true }
                        .get(view)

                    val sourceIdToUriMapping = collectionCacheSource::class.java.getDeclaredField("mIdToUriMapping")
                        .apply { isAccessible = true }
                        .get(collectionCacheSource) as SparseArray<String>
                    val rootSourceIdToUriMapping = rootCollectionCacheSource::class.java.getDeclaredField("mIdToUriMapping")
                        .apply { isAccessible = true }
                        .get(rootCollectionCacheSource) as SparseArray<String>
                    val sourceUriToCollectionMapping = collectionCacheSource::class.java.getDeclaredField("mUriToCollectionMapping")
                        .apply { isAccessible = true }
                        .get(collectionCacheSource) as Map<String, *>
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
                            widgetId = widget.id,
                            widgetIntent = wrappedIntent,
                        )
                        wrappedIntentField.set(action, newIntent)
                    }

                    action
                }

                val destActionsField =  view::class.java.getDeclaredField("mActions")
                    .apply { isAccessible = true }

                if (destActionsField.get(view) == null) {
                    destActionsField.set(view, collectionActions)
                } else {
                    (destActionsField.get(view) as? MutableList<Any>)?.addAll(collectionActions)
                }

                sourceActions?.removeAll { collectionActions.contains(it) }
            } ?: run {
                val addWidgetView = RemoteViews(context.packageName, R.layout.add_widget)
                addWidgetView.setOnClickPendingIntent(
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
                view.removeAllViews(R.id.widget_content_even)
                view.removeAllViews(R.id.widget_content_odd)
                view.addView(R.id.widget_content_even, addWidgetView)

                view.removeFromParent(R.id.stack_backward)
                view.removeFromParent(R.id.stack_forward)
                view.removeFromParent(R.id.stack_configure)
            }

            appWidgetManager.updateAppWidget(appWidgetId, view)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?,
    ) {
        if (newOptions != appWidgetManager.getAppWidgetOptions(appWidgetId)) {
            context.prefManager.widgetStackWidgets[appWidgetId]?.forEach { stackWidget ->
                appWidgetManager.updateAppWidgetOptions(
                    stackWidget.id,
                    newOptions?.apply {
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH))
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT))
                    },
                )
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)

        appWidgetIds.forEach { appWidgetId ->
            context.widgetHostCompat.deleteAppWidgetId(appWidgetId)
        }
    }

    companion object {
        const val ACTION_SWAP_INDEX = "${BuildConfig.APPLICATION_ID}.intent.action.SWAP_INDEX"
        const val EXTRA_FROM_HOST = "from_host"
        const val EXTRA_BACKWARD = "backward"

        fun update(context: Context, ids: IntArray, fromHost: Boolean = false) {
            context.sendBroadcast(
                createBaseIntent(context, ids)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(EXTRA_FROM_HOST, fromHost),
            )
        }

        fun updateOptions(context: Context, ids: IntArray, options: Bundle?) {
            context.sendBroadcast(
                createBaseIntent(context, ids)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_OPTIONS_CHANGED)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options),
            )
        }

        private fun createSwapIntent(context: Context, ids: IntArray, backward: Boolean): Intent {
            return createBaseIntent(context, ids)
                .setAction(ACTION_SWAP_INDEX)
                .putExtra(EXTRA_BACKWARD, backward)
        }

        private fun createBaseIntent(context: Context, ids: IntArray): Intent {
            return Intent(context, WidgetStackProvider::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        }
    }
}
