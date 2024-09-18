package tk.zwander.common.util

import android.appwidget.AppWidgetProviderInfo

object BrokenAppsRegistry {
    val brokenApps = arrayOf(
        "com.hihonor.calendar",
        "com.huawei.android.totemweather",
        "com.hihonor.android.totemweather",
    )
    private val brokenProviders = arrayOf(
        "com.android.calendar.mycalendar",
    )

    fun isBroken(provider: AppWidgetProviderInfo): Boolean {
        return brokenApps.contains(provider.provider.packageName) || brokenProviders.any {
            it.contains(provider.provider.flattenToString()) || provider.provider.flattenToString().contains(it)
        }
    }
}
