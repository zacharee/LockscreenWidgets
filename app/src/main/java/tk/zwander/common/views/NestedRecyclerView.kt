package tk.zwander.common.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.AbsListView
import androidx.core.view.NestedScrollingParent3
import androidx.core.view.NestedScrollingParentHelper
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import tk.zwander.common.util.verticalScrollOffset

//https://stackoverflow.com/a/68318211/5496177
open class NestedRecyclerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr), NestedScrollingParent3 {
    private var nestedScrollTarget: View? = null
    private var nestedScrollTargetWasUnableToScroll = false
    private val parentHelper by lazy { NestedScrollingParentHelper(this) }

    /**
     * Set this wherever you have access to your item touch helper instance.
     * Using `attachToRecyclerView(null)` resets any long-press timers.
     *
     * Example:
     *
     * nestedRecyclerView.nestedScrollingListener = {
     *      itemTouchHelper.attachToRecyclerView(if (!it) nestedRecyclerView else null)
     * }
     */
    var nestedScrollingListener: ((Boolean) -> Unit)? = null

    /**
     * Set this from your item touch helper callback to let the RecyclerView
     * know when an item is selected (prevents an inverse nested scrolling issue
     * where the nested view scrolls and the item touch helper doesn't receive
     * further callbacks).
     *
     * Example:
     * override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
     *      if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) nestedRecyclerView.selectedItem = true
     *      ...
     * }
     * ...
     * override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
     *      nestedRecyclerView.selectedItem = false
     *      ...
     * }
     */
    var selectedItem: Boolean = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_UP) {
            // This is to prevent an issue where the item touch helper receives
            // an ACTION_DOWN but then doesn't later get the ACTION_UP event,
            // causing it to run any long-press events.
            nestedScrollingListener?.invoke(true)
            nestedScrollingListener?.invoke(false)
        }

        // Nothing special if no child scrolling target.
        if (nestedScrollTarget == null || selectedItem) return super.dispatchTouchEvent(ev)

        // Inhibit the execution of our onInterceptTouchEvent for now...
        requestDisallowInterceptTouchEvent(true)
        // ... but do all other processing.
        var handled = super.dispatchTouchEvent(ev)

        // If the first dispatch yielded an unhandled event or the descendant view is unable to
        // scroll in the direction the user is scrolling, we dispatch once more but without skipping
        // our onInterceptTouchEvent. Note that RecyclerView automatically cancels active touches of
        // all its descendants once it starts scrolling so we don't have to do that.
        requestDisallowInterceptTouchEvent(false)
        if (!handled || nestedScrollTargetWasUnableToScroll) {
            handled = super.dispatchTouchEvent(ev)
        }

        return handled
    }

    override fun getNestedScrollAxes(): Int {
        return parentHelper.nestedScrollAxes
    }

    // We only support vertical scrolling.
    override fun onStartNestedScroll(child: View, target: View, nestedScrollAxes: Int) =
        nestedScrollAxes and ViewCompat.SCROLL_AXIS_VERTICAL != 0

    /*  Introduced with NestedScrollingParent2. */
    override fun onStartNestedScroll(child: View, target: View, axes: Int, type: Int) =
        onStartNestedScroll(child, target, axes)

    override fun onNestedScrollAccepted(child: View, target: View, axes: Int) {
        if (axes and View.SCROLL_AXIS_VERTICAL != 0) {
            // A descendant started scrolling, so we'll observe it.
            setTarget(target)
        }
        parentHelper.onNestedScrollAccepted(child, target, axes)
    }

    /*  Introduced with NestedScrollingParent2. */
    override fun onNestedScrollAccepted(child: View, target: View, axes: Int, type: Int) {
        if (axes and View.SCROLL_AXIS_VERTICAL != 0) {
            // A descendant started scrolling, so we'll observe it.
            setTarget(target)
        }
        parentHelper.onNestedScrollAccepted(child, target, axes, type)
    }

    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray) {
        super.onNestedPreScroll(target, dx, dy, consumed)
    }

    /*  Introduced with NestedScrollingParent2. */
    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray, type: Int) {
        onNestedPreScroll(target, dx, dy, consumed)
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int
    ) {
        if (target === nestedScrollTarget && dyUnconsumed != 0) {
            // The descendant could not fully consume the scroll. We remember that in order
            // to allow the RecyclerView to take over scrolling.
            nestedScrollTargetWasUnableToScroll = true
            // Let the parent start to consume scroll events.
            target.parent?.requestDisallowInterceptTouchEvent(false)
        }
    }

    /*  Introduced with NestedScrollingParent2. */
    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int
    ) {
        onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed)
    }

    /*  Introduced with NestedScrollingParent3. */
    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
        consumed: IntArray
    ) {
        onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, type)
    }

    /* From ViewGroup */
    override fun onStopNestedScroll(child: View) {
        // The descendant finished scrolling. Clean up!
        setTarget(null)
        parentHelper.onStopNestedScroll(child)
    }

    /*  Introduced with NestedScrollingParent2. */
    override fun onStopNestedScroll(target: View, type: Int) {
        // The descendant finished scrolling. Clean up!
        setTarget(null)
        parentHelper.onStopNestedScroll(target, type)
    }

    /*  Introduced with NestedScrollingParent2. */
    override fun onNestedPreFling(target: View, velocityX: Float, velocityY: Float): Boolean {
        return super.onNestedPreFling(target, velocityX, velocityY)
    }

    /* In ViewGroup for API 21+. */
    override fun onNestedFling(
        target: View,
        velocityX: Float,
        velocityY: Float,
        consumed: Boolean
    ) = super.onNestedFling(target, velocityX, velocityY, consumed).also {
        // If the nested fling wasn't consumed, then the touch helper can act.
        // Otherwise, disable it.
        nestedScrollingListener?.invoke(!it)
    }

    @SuppressLint("NewApi", "ClickableViewAccessibility")
    private fun setTarget(view: View?) {
        nestedScrollTarget = view
        nestedScrollTargetWasUnableToScroll = false

        view?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                nestedScrollingListener?.invoke(true)
                nestedScrollingListener?.invoke(false)
            }
            false
        }

        when (view) {
            is AbsListView -> {
                var previousScrollOffset = view.verticalScrollOffset

                view.setOnScrollListener(object : AbsListView.OnScrollListener {
                    override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {
                        nestedScrollingListener?.invoke(scrollState != AbsListView.OnScrollListener.SCROLL_STATE_IDLE)
                    }

                    override fun onScroll(
                        view: AbsListView?,
                        firstVisibleItem: Int,
                        visibleItemCount: Int,
                        totalItemCount: Int
                    ) {}
                })
                view.setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_MOVE) {
                        val offset = view.verticalScrollOffset

                        if (previousScrollOffset != offset) {
                            nestedScrollingListener?.invoke(true)
                            previousScrollOffset = offset
                        }
                    }
                    false
                }
            }
            is RecyclerView -> {
                view.addOnScrollListener(object : OnScrollListener() {
                    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                        nestedScrollingListener?.invoke(newState != SCROLL_STATE_IDLE)
                    }
                })
            }
            else -> {
                view?.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                    if (scrollY != oldScrollY) {
                        nestedScrollingListener?.invoke(true)
                    }
                }
            }
        }
    }
}