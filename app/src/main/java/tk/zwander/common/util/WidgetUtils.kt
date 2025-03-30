package tk.zwander.common.util

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.view.View
import android.widget.TextView
import com.android.internal.R

val Context.appWidgetManager: AppWidgetManager
    get() = AppWidgetManager.getInstance(safeApplicationContext)

fun AppWidgetProviderInfo.getSamsungConfigureComponent(context: Context): ComponentName? {
    return try {
        context.packageManager.getReceiverInfoCompat(
            provider,
            PackageManager.GET_META_DATA
        ).metaData?.getString("android.appwidget.provider.semConfigureActivity")
            ?.let { ComponentName.unflattenFromString("${provider.packageName}/$it") }
    } catch (_: PackageManager.NameNotFoundException) {
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
    tv.setText(R.string.gadget_host_error_inflating)

    tv.setBackgroundColor(Color.argb(127, 0, 0, 0))
    return tv
}
