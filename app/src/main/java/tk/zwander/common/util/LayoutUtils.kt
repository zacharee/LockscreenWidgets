package tk.zwander.common.util

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.Density
import androidx.core.view.LayoutInflaterCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import tk.zwander.common.adapters.BaseAdapter
import tk.zwander.common.compose.AppTheme
import tk.zwander.common.data.WidgetType
import tk.zwander.common.util.compat.LayoutInflaterFactory2Compat
import tk.zwander.lockscreenwidgets.R
import kotlin.math.roundToInt
import kotlin.math.sign


fun createTouchHelperCallback(
    adapter: BaseAdapter,
    widgetMoved: (moved: Boolean) -> Unit,
    onItemSelected: (selected: Boolean, highlighted: Boolean) -> Unit,
    frameLocked: () -> Boolean,
    viewModel: BaseDelegate.BaseViewModel<*, *>,
): ItemTouchHelper.SimpleCallback {
    return object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT or ItemTouchHelper.UP or ItemTouchHelper.DOWN,
        0,
    ) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder,
        ): Boolean {
            return adapter.onMove(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                .also { moved ->
                    widgetMoved(moved)
                }
        }

        override fun getDragDirs(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
        ): Int {
            return if (viewHolder is BaseAdapter.AddWidgetVH || frameLocked() || viewModel.isResizingItem.value) 0
            else super.getDragDirs(recyclerView, viewHolder)
        }

        override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
            return if (viewHolder.itemViewType != WidgetType.HEADER.ordinal) super.getMovementFlags(recyclerView, viewHolder)
            else 0
        }

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            if (!viewModel.isResizingItem.value) {
                if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
                    viewHolder?.itemView?.alpha = 0.5f

                    //The user has long-pressed a widget. Show the editing UI on that widget.
                    //If the UI is already shown on it, hide it.
                    val adapterPos = viewHolder?.bindingAdapterPosition ?: -1
                    viewModel.currentEditingInterfacePosition.value =
                        if (viewModel.currentEditingInterfacePosition.value == adapterPos) -1 else adapterPos
                }

                onItemSelected(
                    actionState == ItemTouchHelper.ACTION_STATE_DRAG,
                    viewModel.currentEditingInterfacePosition.value != -1,
                )
            }

            super.onSelectedChanged(viewHolder, actionState)
        }

        override fun clearView(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
        ) {
            super.clearView(recyclerView, viewHolder)

            onItemSelected(false, false)

            viewHolder.itemView.alpha = 1.0f
        }

        override fun interpolateOutOfBoundsScroll(
            recyclerView: RecyclerView,
            viewSize: Int,
            viewSizeOutOfBounds: Int,
            totalSize: Int,
            msSinceStartScroll: Long,
        ): Int {
            //The default scrolling speed is *way* too fast. Slow it down a bit.
            val direction = sign(viewSizeOutOfBounds.toFloat()).toInt()
            return (viewSize * 0.01f * direction).roundToInt()
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
    }
}

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
            override fun onAnimationEnd(animation: Animator) {
                scaleX = 0.95f
                scaleY = 0.95f
                alpha = 0f

                clearAnimation()

                handler?.postDelayed({
                    endListener()
                }, 1)
            }
        })
    }
    animator.start()
}

fun View.fadeOut(endListener: () -> Unit) {
    clearAnimation()

    val alphaAnimation = ObjectAnimator.ofFloat(this, "alpha", alpha, 0f)
    alphaAnimation.duration = if (context.prefManager.animateShowHide) context.prefManager.animationDuration.toLong() else 0L
    alphaAnimation.addListener(
        object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                alpha = 0f

                clearAnimation()

                handler?.postDelayed({
                    endListener()
                }, 1)
            }
        },
    )
    alphaAnimation.start()
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
            override fun onAnimationEnd(animation: Animator) {
                scaleX = 1f
                scaleY = 1f
                alpha = 1f

                clearAnimation()
                endListener()
            }
        })
    }
    animator.start()
}

fun View.fadeIn(endListener: () -> Unit) {
    clearAnimation()

    val animator = AnimatorSet().apply {
        playTogether(
            ObjectAnimator.ofFloat(this@fadeIn, "alpha", alpha, 1f),
        )
        duration = if (context.prefManager.animateShowHide) context.prefManager.animationDuration.toLong() else 0L
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                alpha = 1f

                clearAnimation()
                endListener()
            }
        })
    }
    animator.start()
}

val Context.statusBarHeight: Int
    @SuppressLint("InternalInsetResource", "DiscouragedApi")
    get() = resources.getDimensionPixelSize(resources.getIdentifier("status_bar_height", "dimen", "android"))

val Context.density: Density
    get() = Density(this)

val Context.themedContext: ContextWrapper
    get() = ContextThemeWrapper(this, R.style.AppTheme)

val Context.themedLayoutInflater: LayoutInflater
    get() = LayoutInflater.from(themedContext).apply {
        LayoutInflaterCompat.setFactory2(
            this,
            LayoutInflaterFactory2Compat(),
        )
    }

fun ComposeView.setThemedContent(content: @Composable () -> Unit) {
    setContent {
        AppTheme {
            content()
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
