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

val mainHandler = Handler(Looper.getMainLooper())

val Context.prefManager: PrefManager
    get() = PrefManager.getInstance(this)

val Context.isAccessibilityEnabled: Boolean
    get() = Settings.Secure.getString(
        contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    )?.contains(ComponentName(this, Accessibility::class.java).flattenToString()) ?: false

val Context.isNotificationListenerActive: Boolean
    get() = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        ?.run {
            val cmp =
                ComponentName(this@isNotificationListenerActive, NotificationListener::class.java)
            contains(cmp.flattenToString()) || contains(cmp.flattenToShortString())
        } ?: false

val Context.isDebug: Boolean
    get() = prefManager.debugLog

val AccessibilityWindowInfo.safeRoot: AccessibilityNodeInfo?
    get() = try {
        root
    } catch (e: NullPointerException) {
        null
    }

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

fun View.calculateWidgetWidth(paramWidth: Int): Int {
    return paramWidth / context.prefManager.frameColCount
}

fun Int.makeEven(): Int {
    return when {
        this == 0 -> this
        this == 1 -> 2
        this == -1 -> -2
        this % 2 == 0 -> this
        else -> this + if (this < 0) -1 else 1
    }
}