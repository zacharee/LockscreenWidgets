package tk.zwander.lockscreenwidgets.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.TypedValue
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SnapHelper
import tk.zwander.lockscreenwidgets.services.Accessibility
import tk.zwander.lockscreenwidgets.services.NotificationListener
import kotlin.math.roundToInt

val Context.prefManager: PrefManager
    get() = PrefManager.getInstance(this)

val Context.isAccessibilityEnabled: Boolean
    get() = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        ?.contains(ComponentName(this, Accessibility::class.java).flattenToString()) ?: false

val Context.isNotificationListenerActive: Boolean
    get() = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        ?.run {
            val cmp = ComponentName(this@isNotificationListenerActive, NotificationListener::class.java)
            contains(cmp.flattenToString()) || contains(cmp.flattenToShortString())
        } ?: false

fun Context.dpAsPx(dpVal: Number) =
    TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dpVal.toFloat(),
        resources.displayMetrics
    ).roundToInt()

fun Context.pxAsDp(pxVal: Number) =
    pxVal.toFloat() / resources.displayMetrics.density

fun SnapHelper.getSnapPosition(recyclerView: RecyclerView): Int {
    val layoutManager = recyclerView.layoutManager ?: return RecyclerView.NO_POSITION
    val snapView = findSnapView(layoutManager) ?: return RecyclerView.NO_POSITION
    return layoutManager.getPosition(snapView)
}

fun Context.launchUrl(url: String) {
    try {
        val browserIntent =
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(browserIntent)
    } catch (e: Exception) {}
}

fun Context.launchEmail(to: String, subject: String) {
    try {
        val intent = Intent(Intent.ACTION_SENDTO)
        intent.type = "text/plain"
        intent.data = Uri.parse("mailto:${Uri.encode(to)}?subject=${Uri.encode(subject)}")

        startActivity(intent)
    } catch (e: Exception) {}
}

fun View.fadeAndScaleOut(endListener: () -> Unit) {
    animate().cancel()
    animate()
        .scaleX(0.95f)
        .scaleY(0.95f)
        .alpha(0f)
        .setInterpolator(AccelerateInterpolator())
        .withEndAction {
            endListener()
        }
}

fun View.fadeAndScaleIn(endListener: () -> Unit) {
    animate().cancel()
    animate()
        .scaleX(1.0f)
        .scaleY(1.0f)
        .alpha(1f)
        .setInterpolator(DecelerateInterpolator())
        .withEndAction {
            endListener()
        }
}