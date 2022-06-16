package tk.zwander.lockscreenwidgets.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Point
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import androidx.core.view.isVisible
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import tk.zwander.lockscreenwidgets.adapters.WidgetFrameAdapter
import tk.zwander.lockscreenwidgets.data.WidgetType
import tk.zwander.lockscreenwidgets.drawable.BackgroundBlurDrawableCompatDelegate
import kotlin.math.roundToInt
import kotlin.math.sign

fun createTouchHelperCallback(
    adapter: WidgetFrameAdapter,
    widgetMoved: (moved: Boolean) -> Unit,
    onItemSelected: (selected: Boolean) -> Unit,
    frameLocked: () -> Boolean
): ItemTouchHelper.SimpleCallback {
    return object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT or ItemTouchHelper.UP or ItemTouchHelper.DOWN,
        0
    ) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            return adapter.onMove(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                .also { moved ->
                    widgetMoved(moved)
                }
        }

        override fun getDragDirs(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int {
            return if (viewHolder is WidgetFrameAdapter.AddWidgetVH || frameLocked()) 0
            else super.getDragDirs(recyclerView, viewHolder)
        }

        override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
            return if (viewHolder.itemViewType != WidgetType.HEADER.ordinal) super.getMovementFlags(recyclerView, viewHolder)
            else 0
        }

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
                viewHolder?.itemView?.alpha = 0.5f
                onItemSelected(true)

                //The user has long-pressed a widget. Show the editing UI on that widget.
                //If the UI is already shown on it, hide it.
                val adapterPos = viewHolder?.bindingAdapterPosition ?: -1
                adapter.currentEditingInterfacePosition =
                    if (adapter.currentEditingInterfacePosition == adapterPos) -1 else adapterPos
            }

            super.onSelectedChanged(viewHolder, actionState)
        }

        override fun clearView(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ) {
            super.clearView(recyclerView, viewHolder)

            onItemSelected(false)

            viewHolder.itemView.alpha = 1.0f
        }

        override fun interpolateOutOfBoundsScroll(
            recyclerView: RecyclerView,
            viewSize: Int,
            viewSizeOutOfBounds: Int,
            totalSize: Int,
            msSinceStartScroll: Long
        ): Int {
            //The default scrolling speed is *way* too fast. Slow it down a bit.
            val direction = sign(viewSizeOutOfBounds.toFloat()).toInt()
            return (viewSize * 0.01f * direction).roundToInt()
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
    }
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

//Fade a View to 0% alpha and 95% scale. Used when hiding the widget frame.
fun View.fadeAndScaleOut(endListener: () -> Unit) {
    clearAnimation()

    val animator = AnimatorSet().apply {
        playTogether(
            ObjectAnimator.ofFloat(this@fadeAndScaleOut, "scaleX", scaleX, 0.95f),
            ObjectAnimator.ofFloat(this@fadeAndScaleOut, "scaleY", scaleY, 0.95f),
            ObjectAnimator.ofFloat(this@fadeAndScaleOut, "alpha", alpha, 0f)
        )
        duration = if (context.prefManager.animateShowHide) context.prefManager.animationDuration.toLong() else 0L
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
    clearAnimation()

    val animator = AnimatorSet().apply {
        playTogether(
            ObjectAnimator.ofFloat(this@fadeAndScaleIn, "scaleX", scaleX, 1.0f),
            ObjectAnimator.ofFloat(this@fadeAndScaleIn, "scaleY", scaleY, 1.0f),
            ObjectAnimator.ofFloat(this@fadeAndScaleIn, "alpha", alpha, 1.0f)
        )
        duration = if (context.prefManager.animateShowHide) context.prefManager.animationDuration.toLong() else 0L
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                clearAnimation()
                endListener()
            }
        })
    }
    animator.start()
}

val Context.screenSize: Point
    get() {
        @Suppress("DEPRECATION")
        return Point().apply {
            defaultDisplayCompat.getRealSize(this)
        }
    }

val Context.statusBarHeight: Int
    get() = resources.getDimensionPixelSize(resources.getIdentifier("status_bar_height", "dimen", "android"))

fun View.updateBlurLayer(wm: WindowManager, blurDrawable: BackgroundBlurDrawableCompatDelegate?, shouldBlur: Boolean, params: WindowManager.LayoutParams, blurAmount: Float) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val hasBlur = wm.isCrossWindowBlurEnabled && isHardwareAccelerated

        background = if (hasBlur) blurDrawable?.wrapped else null
        isVisible = hasBlur && shouldBlur
    } else {
        val f = try {
            params::class.java.getDeclaredField("samsungFlags")
        } catch (e: Exception) {
            null
        }

        if (shouldBlur) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND

            f?.set(params, f.get(params) as Int or 64)
            params.dimAmount = blurAmount
        } else {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv()

            f?.set(params, f.get(params) as Int and 64.inv())
            params.dimAmount = 0.0f
        }
    }
}
