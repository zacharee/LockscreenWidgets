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
import android.view.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.Density
import androidx.core.view.LayoutInflaterCompat
import dev.zwander.lswinterconnect.safeApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import tk.zwander.common.compose.AppTheme
import tk.zwander.common.util.compat.LayoutInflaterFactory2Compat
import tk.zwander.lockscreenwidgets.R
import java.util.*
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

// Tracks the in-flight fade/scale animator per View. A new fade call cancels whatever's
// still running instead of letting two AnimatorSets fight over the same properties, which
// is how a queued draw can end up racing a WindowManager.removeView() teardown of the
// view's Surface (native "getFrame() called on a context with no surface!" abort).
private val runningFadeAnimators = WeakHashMap<View, AnimatorSet>()

private fun View.cancelRunningFadeAnimator() {
    runningFadeAnimators.remove(this)?.cancel()
}

/**
 * Suspend until the next real draw pass of this View completes. Called after fading a view
 * out and before removing its window, so the window isn't torn down while the fade's final
 * frame is still queued on RenderThread.
 */
suspend fun View.awaitNextDraw() {
    val vto = viewTreeObserver
    if (!isAttachedToWindow || !vto.isAlive) return

    suspendCancellableCoroutine { cont ->
        lateinit var listener: ViewTreeObserver.OnDrawListener
        listener = ViewTreeObserver.OnDrawListener {
            post {
                if (vto.isAlive) {
                    vto.removeOnDrawListener(listener)
                }
            }
            if (cont.isActive) {
                cont.resume(Unit)
            }
        }

        vto.addOnDrawListener(listener)

        cont.invokeOnCancellation {
            if (vto.isAlive) {
                vto.removeOnDrawListener(listener)
            }
        }
    }
}

//Fade a View to 0% alpha and 95% scale. Used when hiding the widget frame.
suspend fun View.fadeAndScaleOut(drawerOrFrame: DrawerOrFrame) {
    cancelRunningFadeAnimator()

    val animator = AnimatorSet().apply {
        playTogether(
            ObjectAnimator.ofFloat(this@fadeAndScaleOut, "scaleX", scaleX, 0.95f),
            ObjectAnimator.ofFloat(this@fadeAndScaleOut, "scaleY", scaleY, 0.95f),
            ObjectAnimator.ofFloat(this@fadeAndScaleOut, "alpha", alpha, 0f)
        )
        duration = with(drawerOrFrame) { context.duration() }
    }
    runningFadeAnimators[this] = animator

    suspendCoroutine { continuation ->
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (runningFadeAnimators[this@fadeAndScaleOut] === animator) {
                    runningFadeAnimators.remove(this@fadeAndScaleOut)
                }

                scaleX = 0.95f
                scaleY = 0.95f
                alpha = 0f

                continuation.resume(Unit)
            }
        })
        animator.start()
    }

    awaitNextDraw()
}

suspend fun View.fadeOut(drawerOrFrame: DrawerOrFrame) {
    cancelRunningFadeAnimator()

    val animator = AnimatorSet().apply {
        playTogether(ObjectAnimator.ofFloat(this@fadeOut, "alpha", alpha, 0f))
        duration = with(drawerOrFrame) { context.duration() }
    }
    runningFadeAnimators[this] = animator

    suspendCoroutine { continuation ->
        animator.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (runningFadeAnimators[this@fadeOut] === animator) {
                        runningFadeAnimators.remove(this@fadeOut)
                    }

                    alpha = 0f

                    continuation.resume(Unit)
                }
            },
        )
        animator.start()
    }

    awaitNextDraw()
}

//Fade a View to 100% alpha and 100% scale. Used when showing the widget frame.
suspend fun View.fadeAndScaleIn(drawerOrFrame: DrawerOrFrame) {
    cancelRunningFadeAnimator()

    val animator = AnimatorSet().apply {
        playTogether(
            ObjectAnimator.ofFloat(this@fadeAndScaleIn, "scaleX", scaleX, 1.0f),
            ObjectAnimator.ofFloat(this@fadeAndScaleIn, "scaleY", scaleY, 1.0f),
            ObjectAnimator.ofFloat(this@fadeAndScaleIn, "alpha", alpha, 1.0f)
        )
        duration = with(drawerOrFrame) { context.duration() }
    }
    runningFadeAnimators[this] = animator

    suspendCoroutine { continuation ->
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (runningFadeAnimators[this@fadeAndScaleIn] === animator) {
                    runningFadeAnimators.remove(this@fadeAndScaleIn)
                }

                scaleX = 1f
                scaleY = 1f
                alpha = 1f

                continuation.resume(Unit)
            }
        })
        animator.start()
    }
}

suspend fun View.fadeIn(drawerOrFrame: DrawerOrFrame) {
    cancelRunningFadeAnimator()

    val animator = AnimatorSet().apply {
        playTogether(
            ObjectAnimator.ofFloat(this@fadeIn, "alpha", alpha, 1f),
        )
        duration = with(drawerOrFrame) { context.duration() }
    }
    runningFadeAnimators[this] = animator

    suspendCoroutine { continuation ->
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (runningFadeAnimators[this@fadeIn] === animator) {
                    runningFadeAnimators.remove(this@fadeIn)
                }

                alpha = 1f

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
