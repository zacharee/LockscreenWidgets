package tk.zwander.common.util

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.view.View
import android.widget.TextView
import tk.zwander.lockscreenwidgets.R
import kotlin.math.floor

val Context.appWidgetManager: AppWidgetManager
    get() = AppWidgetManager.getInstance(safeApplicationContext)

fun AppWidgetProviderInfo.getSamsungConfigureComponent(context: Context): ComponentName? {
    return try {
        context.packageManager.getReceiverInfoCompat(
            provider,
            PackageManager.GET_META_DATA
        ).metaData?.getString("android.appwidget.provider.semConfigureActivity")
            ?.let { ComponentName.unflattenFromString("${provider.packageName}/$it") }
    } catch (e: PackageManager.NameNotFoundException) {
        context.logUtils.debugLog("Error getting Samsung configure component", e)
        null
    }
}

fun AppWidgetProviderInfo?.hasConfiguration(context: Context): Boolean {
    return this != null &&
            (configure != null || getSamsungConfigureComponent(context) != null)
}

fun Context.getAllInstalledWidgetProviders(pkg: String? = null): List<AppWidgetProviderInfo> {
    val manager = appWidgetManager

    return try {
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
        logUtils.debugLog("Unable to use getInstalledProvidersForProfile", e)

        (manager.getInstalledProviders(AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) +
                manager.getInstalledProviders(AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD) +
                manager.getInstalledProviders(AppWidgetProviderInfo.WIDGET_CATEGORY_SEARCHBOX))
            .run {
                if (pkg != null) {
                    filter { it.providerInfo.packageName == pkg }
                } else {
                    this
                }
            }
    }
}

fun Context.createWidgetErrorView(): View {
    val tv = TextView(this)
    tv.setText(R.string.error_showing_widget)
    tv.setTextColor(Color.WHITE)

    tv.setBackgroundColor(Color.argb(127, 0, 0, 0))
    return tv
}

fun AppWidgetProviderInfo.getCellWidthCompat(totalWidthPx: Int, colCount: Int): Int {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val targetCells = this.targetCellWidth

        if (targetCells > 0) {
            return targetCells
        }
    }

    val actualMinWidth = this.minWidth

    if (actualMinWidth <= 0) return 1

    return (floor(actualMinWidth.toFloat() / totalWidthPx.toFloat()) * colCount)
        .toInt()
        .coerceAtLeast(1)
}

fun AppWidgetProviderInfo.getCellHeightCompat(totalHeightPx: Int, rowCount: Int): Int {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val targetCells = this.targetCellHeight

        if (targetCells > 0) {
            return targetCells
        }
    }

    val actualMinHeight = this.minHeight

    if (actualMinHeight <= 0) return 1

    return (floor(actualMinHeight.toFloat() / totalHeightPx.toFloat()) * rowCount)
        .toInt()
        .coerceAtLeast(1)
}
