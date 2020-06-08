package tk.zwander.lockscreenwidgets.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SnapHelper
import tk.zwander.lockscreenwidgets.BuildConfig
import tk.zwander.lockscreenwidgets.services.Accessibility
import tk.zwander.lockscreenwidgets.services.NotificationListener
import kotlin.math.roundToInt

/**
 * Various utility functions
 */

//A global reference to the main-thread Handler
val mainHandler = Handler(Looper.getMainLooper())

//Convenience method for getting the preference store instance
val Context.prefManager: PrefManager
    get() = PrefManager.getInstance(this)

//Check if the Accessibility service is enabled
val Context.isAccessibilityEnabled: Boolean
    get() = Settings.Secure.getString(
        contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    )?.contains(ComponentName(this, Accessibility::class.java).flattenToString()) ?: false

//Check if the notification listener service is enabled
val Context.isNotificationListenerActive: Boolean
    get() = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        ?.run {
            val cmp =
                ComponentName(this@isNotificationListenerActive, NotificationListener::class.java)
            contains(cmp.flattenToString()) || contains(cmp.flattenToShortString())
        } ?: false

//Convenience method to check if debug logging is enabled
val Context.isDebug: Boolean
    get() = prefManager.debugLog

//Sometimes retrieving the root of a window causes an NPE
//in the framework. Catch that here and return null if it happens.
val AccessibilityWindowInfo.safeRoot: AccessibilityNodeInfo?
    get() = try {
        root
    } catch (e: NullPointerException) {
        null
    }

//Take a DP value and return its representation in pixels.
fun Context.dpAsPx(dpVal: Number) =
    TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dpVal.toFloat(),
        resources.displayMetrics
    ).roundToInt()

//Take a pixel value and return its representation in DP.
fun Context.pxAsDp(pxVal: Number) =
    pxVal.toFloat() / resources.displayMetrics.density

//Safely launch a URL.
//If no matching Activity is found, silently fail.
fun Context.launchUrl(url: String) {
    try {
        val browserIntent =
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(browserIntent)
    } catch (e: Exception) {}
}

//Safely start an email draft.
//If no matching email client is found, silently fail.
fun Context.launchEmail(to: String, subject: String) {
    try {
        val intent = Intent(Intent.ACTION_SENDTO)
        intent.type = "text/plain"
        intent.data = Uri.parse("mailto:${Uri.encode(to)}?subject=${Uri.encode(subject)}")

        startActivity(intent)
    } catch (e: Exception) {}
}

//Fade a View to 0% alpha and 95% scale. Used when hiding the widget frame.
fun View.fadeAndScaleOut(endListener: () -> Unit) {
    val animator = AnimatorSet().apply {
        playTogether(
            ObjectAnimator.ofFloat(this@fadeAndScaleOut, "scaleX", 1.0f, 0.95f),
            ObjectAnimator.ofFloat(this@fadeAndScaleOut, "scaleY", 1.0f, 0.95f),
            ObjectAnimator.ofFloat(this@fadeAndScaleOut, "alpha", 1.0f, 0f)
        )
        duration = if (context.prefManager.animateShowHide) 100L else 0L
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                clearAnimation()
                endListener()
            }
        })
    }
    animator.start()
}

//Fade a View to 100% alpha and 100% scale. Used when showing the widget frame.
fun View.fadeAndScaleIn(endListener: () -> Unit) {
    val animator = AnimatorSet().apply {
        playTogether(
            ObjectAnimator.ofFloat(this@fadeAndScaleIn, "scaleX", 0.95f, 1.0f),
            ObjectAnimator.ofFloat(this@fadeAndScaleIn, "scaleY", 0.95f, 1.0f),
            ObjectAnimator.ofFloat(this@fadeAndScaleIn, "alpha", 0f, 1.0f)
        )
        duration = if (context.prefManager.animateShowHide) 100L else 0L
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                clearAnimation()
                endListener()
            }
        })
    }
    animator.start()
}

//Convenience method to calculate the proper widget width
//based on the frame column count.
fun View.calculateWidgetWidth(paramWidth: Int, widgetId: Int): Int {
    return paramWidth / context.prefManager.frameColCount *
            (context.prefManager.widgetSizes[widgetId]?.safeWidgetWidthSpan ?: 1)
}

fun View.calculateWidgetHeight(paramHeight: Int, widgetId: Int): Int {
    return paramHeight / context.prefManager.frameRowCount *
            (context.prefManager.widgetSizes[widgetId]?.safeWidgetHeightSpan ?: 1)
}

val Context.widgetBlockWidth: Int
    get() = (pxAsDp(prefManager.frameWidthDp) / prefManager.frameColCount).roundToInt()

val Context.widgetBlockHeight: Int
    get() = (pxAsDp(prefManager.frameHeightDp) / prefManager.frameRowCount).roundToInt()

//Take an integer and make it even.
//If the integer == 0, return itself (0).
//If the integer is 1, return 2.
//If the integer is -1, return -2.
//If the integer is even, return itself.
//If the integer is odd and negative, return itself - 1
//If the integer is odd and positive, return itself + 1
fun Int.makeEven(): Int {
    return when {
        this == 0 -> this
        this == 1 -> 2
        this == -1 -> -2
        this % 2 == 0 -> this
        else -> this + if (this < 0) -1 else 1
    }
}