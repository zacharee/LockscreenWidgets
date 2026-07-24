@file:Suppress("SuspendCoroutineLacksCancellationGuarantees")

package tk.zwander.common.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.Density
import androidx.core.view.LayoutInflaterCompat
import dev.zwander.lswinterconnect.safeApplicationContext
import tk.zwander.common.compose.AppTheme
import tk.zwander.common.util.compat.LayoutInflaterFactory2Compat
import tk.zwander.lockscreenwidgets.R
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

enum class DrawerOrFrame {
    DRAWER {
        override fun Context.duration(): Long {
            return if (prefManager.animateDrawerShowHide) prefManager.drawerAnimationDuration.toLong() else 0L
        }
    },
    FRAME {
        override fun Context.duration(): Long {
            return if (prefManager.animateShowHide) prefManager.animationDuration.toLong() else 0L
        }
    };

    abstract fun Context.duration(): Long
}

//Fade a View to 0% alpha and 95% scale. Used when hiding the widget frame.
suspend fun View.fadeAndScaleOut(drawerOrFrame: DrawerOrFrame) {
    clearAnimation()

    val animator = AnimatorSet().apply {
        playTogether(
            ObjectAnimator.ofFloat(this@fadeAndScaleOut, "scaleX", scaleX, 0.95f),
            ObjectAnimator.ofFloat(this@fadeAndScaleOut, "scaleY", scaleY, 0.95f),
            ObjectAnimator.ofFloat(this@fadeAndScaleOut, "alpha", alpha, 0f)
        )
        duration = with(drawerOrFrame) { context.duration() }
    }
    suspendCoroutine { continuation ->
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                scaleX = 0.95f
                scaleY = 0.95f
                alpha = 0f

                clearAnimation()
                continuation.resume(Unit)
            }
        })
        animator.start()
    }
}

suspend fun View.fadeOut(drawerOrFrame: DrawerOrFrame) {
    clearAnimation()

    val alphaAnimation = ObjectAnimator.ofFloat(this, "alpha", alpha, 0f)
    alphaAnimation.duration = with(drawerOrFrame) { context.duration() }
    suspendCoroutine { continuation ->
        alphaAnimation.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    alpha = 0f

                    clearAnimation()

                    continuation.resume(Unit)
                }
            },
        )
        alphaAnimation.start()
    }
}

//Fade a View to 100% alpha and 100% scale. Used when showing the widget frame.
suspend fun View.fadeAndScaleIn(drawerOrFrame: DrawerOrFrame) {
    clearAnimation()

    val animator = AnimatorSet().apply {
        playTogether(
            ObjectAnimator.ofFloat(this@fadeAndScaleIn, "scaleX", scaleX, 1.0f),
            ObjectAnimator.ofFloat(this@fadeAndScaleIn, "scaleY", scaleY, 1.0f),
            ObjectAnimator.ofFloat(this@fadeAndScaleIn, "alpha", alpha, 1.0f)
        )
        duration = with(drawerOrFrame) { context.duration() }
    }

    suspendCoroutine { continuation ->
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                scaleX = 1f
                scaleY = 1f
                alpha = 1f

                clearAnimation()
                continuation.resume(Unit)
            }
        })
        animator.start()
    }
}

suspend fun View.fadeIn(drawerOrFrame: DrawerOrFrame) {
    clearAnimation()

    val animator = AnimatorSet().apply {
        playTogether(
            ObjectAnimator.ofFloat(this@fadeIn, "alpha", alpha, 1f),
        )
        duration = with(drawerOrFrame) { context.duration() }
    }
    suspendCoroutine { continuation ->
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                alpha = 1f

                clearAnimation()
                continuation.resume(Unit)
            }
        })
        animator.start()
    }
}

fun View.hideNavBarsForGestureExclusion() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val controller = windowInsetsController
        controller?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsets.Type.navigationBars())
    }
}

val Context.statusBarHeight: Int
    @SuppressLint("InternalInsetResource", "DiscouragedApi")
    get() = resources.getDimensionPixelSize(
        resources.getIdentifier(
            "status_bar_height",
            "dimen",
            "android"
        )
    )

val Context.density: Density
    get() = Density(this)

val Context.themedContext: ContextWrapper
    get() = ContextThemeWrapper(this.safeApplicationContext, R.style.AppTheme)

val Context.themedLayoutInflater: LayoutInflater
    get() = LayoutInflater.from(themedContext).apply {
        LayoutInflaterCompat.setFactory2(
            this,
            LayoutInflaterFactory2Compat(),
        )
    }

fun AbstractComposeView.setThemedContent(content: @Composable () -> Unit) {
    if (this is ComposeView) {
        setContent {
            AppTheme {
                content()
            }
        }
    }
}

fun ComponentActivity.setThemedContent(content: @Composable () -> Unit) {
    setContent {
        AppTheme {
            content()
        }
    }
}
