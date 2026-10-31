package tk.zwander.common.util

import android.appwidget.AppWidgetProviderInfo
import android.content.IntentSender
import android.os.Bundle
import android.widget.RemoteViews
import com.android.internal.appwidget.IAppWidgetService
import dev.zwander.lswinterconnect.peekLogUtils

fun IAppWidgetService.getAppWidgetViewsCompat(
    packageName: String,
    appWidgetId: Int,
): RemoteViews? {
    return try {
        getAppWidgetViews(packageName, appWidgetId)
    } catch (e: NoSuchMethodError) {
        peekLogUtils?.debugLog("Unable to get app widget views for $appWidgetId", e)
        this::class.java.getMethod(
            "getAppWidgetViews",
            String::class.java,
            Int::class.java,
            Int::class.java,
        ).invoke(
            this,
            packageName,
            appWidgetId,
            UserHandleCompat.myUserId,
        ) as RemoteViews?
    }
}

fun IAppWidgetService.createAppWidgetConfigIntentSenderCompat(
    packageName: String,
    widgetId: Int,
    intentFlags: Int,
): IntentSender? {
    return try {
        createAppWidgetConfigIntentSender(packageName, widgetId, intentFlags)
    } catch (e: NoSuchMethodError) {
        peekLogUtils?.debugLog("Unable to get app widget config intent sender for $widgetId", e)
        this::class.java.getMethod(
            "createAppWidgetConfigIntentSender",
            String::class.java,
            Int::class.java,
            Int::class.java,
            Int::class.java,
        ).invoke(
            this,
            packageName,
            widgetId,
            intentFlags,
            UserHandleCompat.myUserId,
        ) as IntentSender?
    }
}

fun IAppWidgetService.getAppWidgetOptionsCompat(
    packageName: String,
    widgetId: Int,
): Bundle? {
    return try {
        getAppWidgetOptions(packageName, widgetId)
    } catch (e: NoSuchMethodError) {
        peekLogUtils?.debugLog("Unable to get app widget options for $widgetId", e)
        this::class.java.getMethod(
            "getAppWidgetOptions",
            String::class.java,
            Int::class.java,
            Int::class.java,
        ).invoke(
            this,
            packageName,
            widgetId,
            UserHandleCompat.myUserId,
        ) as Bundle?
    }
}

fun IAppWidgetService.getAppWidgetInfoCompat(
    packageName: String,
    widgetId: Int,
): AppWidgetProviderInfo? {
    return try {
        getAppWidgetInfo(packageName, widgetId)
    } catch (e: NoSuchMethodError) {
        peekLogUtils?.debugLog("Unable to get app widget info for $widgetId", e)
        this::class.java.getMethod(
            "getAppWidgetInfo",
            String::class.java,
            Int::class.java,
            Int::class.java,
        ).invoke(
            this,
            packageName,
            widgetId,
            UserHandleCompat.myUserId,
        ) as AppWidgetProviderInfo?
    }
}
