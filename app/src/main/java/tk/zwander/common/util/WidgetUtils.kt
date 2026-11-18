package tk.zwander.common.util

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.UserHandle
import android.os.UserManager
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import android.widget.TextView
import dev.zwander.lswinterconnect.safeApplicationContext
import tk.zwander.lockscreenwidgets.R
import java.lang.reflect.Field
import kotlin.math.floor

val Context.appWidgetManager: AppWidgetManager
    get() = AppWidgetManager.getInstance(safeApplicationContext)

fun AppWidgetProviderInfo.getSamsungConfigureComponent(context: Context): ComponentName? {
    return try {
        context.packageManager.getReceiverInfoCompat(
            provider,
            PackageManager.GET_META_DATA,
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

fun Context.getAllInstalledWidgetProviders(pkg: String? = null): Map<UserHandle, List<AppWidgetProviderInfo>> {
    val userManager = getSystemService(Context.USER_SERVICE) as UserManager
    val profiles = userManager.userProfiles

    return profiles.associateWith { profile ->
        getAllInstalledWidgetProvidersForProfile(profile, pkg)
    }
}

private fun Context.getAllInstalledWidgetProvidersForProfile(
    profile: UserHandle,
    pkg: String?,
): List<AppWidgetProviderInfo> {
    val manager = appWidgetManager

    return try {
        manager.getInstalledProvidersForProfile(
            AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
            profile,
            pkg,
        ) + manager.getInstalledProvidersForProfile(
            AppWidgetProviderInfo.WIDGET_CATEGORY_KEYGUARD,
            profile,
            pkg,
        ) + manager.getInstalledProvidersForProfile(
            AppWidgetProviderInfo.WIDGET_CATEGORY_SEARCHBOX,
            profile,
            pkg,
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

@SuppressLint("PrivateApi")
private fun remoteViewsField(name: String): Field? {
    return try {
        RemoteViews::class.java.getDeclaredField(name).apply { isAccessible = true }
    } catch (_: Throwable) {
        null
    }
}

/**
 * The size-dependent layouts of this [RemoteViews] (if it has any) paired with the size each
 * one was designed for.
 */
private val sizedRemoteViewsField by lazy { remoteViewsField("mSizedRemoteViews") }

private val remoteViewsIdealSizeField by lazy { remoteViewsField("mIdealSize") }

@SuppressLint("PrivateApi")
private fun RemoteViews.getSizedVariants(): List<Pair<RemoteViews, SizeF>>? {
    val variantsField = sizedRemoteViewsField ?: return null
    val idealSizeField = remoteViewsIdealSizeField ?: return null

    val variants = try {
        variantsField.get(this) as? List<*>
    } catch (_: Throwable) {
        null
    } ?: return null

    val result = variants.mapNotNull { variant ->
        val remoteViews = variant as? RemoteViews ?: return@mapNotNull null
        val idealSize = try {
            idealSizeField.get(remoteViews) as? SizeF
        } catch (_: Throwable) {
            null
        } ?: return@mapNotNull null

        remoteViews to idealSize
    }

    return result.takeIf { it.isNotEmpty() }
}

/**
 * Picks the size-dependent layout that fills [size] the best.
 *
 * The framework's own selection falls back to the *smallest* layout when none of the layouts
 * fit inside the given size. A widget stack reserves space for its bottom bar (and optionally
 * for padding), so a widget can end up a few dp short of the layout it was designed for and
 * then be drawn at its smallest size, leaving most of the stack empty. Prefer the largest
 * layout that fits, and otherwise the one closest to the space that's actually available.
 */
@SuppressLint("PrivateApi")
private fun RemoteViews.pickSizedVariant(size: SizeF): RemoteViews? {
    val variants = getSizedVariants() ?: return null

    // The framework allows a 1dp margin to account for rounding.
    val fits = variants.filter {
        it.second.width <= size.width + 1f && it.second.height <= size.height + 1f
    }

    return if (fits.isNotEmpty()) {
        fits.maxByOrNull { it.second.width * it.second.height }?.first
    } else {
        variants.minByOrNull {
            val widthDelta = it.second.width - size.width
            val heightDelta = it.second.height - size.height
            widthDelta * widthDelta + heightDelta * heightDelta
        }?.first
    }
}

@SuppressLint("DiscouragedPrivateApi", "PrivateApi")
fun RemoteViews.getRemoteViewsToApplyCompat(context: Context, size: SizeF? = null): RemoteViews {
    if (size != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        pickSizedVariant(size)?.let { return it }
    }

    return try {
        getRemoteViewsToApply(context, size)
    } catch (_: NoSuchMethodError) {
        RemoteViews::class.java
            .getDeclaredMethod("getRemoteViewsToApply", Context::class.java)
            .apply { isAccessible = true }
            .invoke(this, context) as RemoteViews
    }
}
