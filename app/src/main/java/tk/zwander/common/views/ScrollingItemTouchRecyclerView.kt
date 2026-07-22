package tk.zwander.common.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.AbsListView
import androidx.core.view.NestedScrollingParent3
import androidx.core.view.NestedScrollingParentHelper
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import tk.zwander.common.util.verticalScrollOffset
import kotlin.math.absoluteValue

open class ScrollingItemTouchRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : RecyclerView(context, attrs, defStyleAttr), NestedScrollingParent3 {
    protected val parentHelper = NestedScrollingParentHelper(this)

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

    protected var nestedScrollTarget: View? = null
    protected var nestedScrollTargetWasUnableToScroll = false
    protected var touchSlop = 0

    init {
        isNestedScrollingEnabled = true
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            touchSlop = ViewConfiguration.get(context)
                .scaledTouchSlop
        }

        return super.dispatchTouchEvent(ev)
    }

    override fun onScrollStateChanged(state: Int) {
        super.onScrollStateChanged(state)
        nestedScrollingListener?.invoke(state != SCROLL_STATE_IDLE)
    }

    /* In ViewGroup for API 21+. */
    override fun onNestedFling(
        target: View,
        velocityX: Float,
        velocityY: Float,
        consumed: Boolean,
    ) = super.onNestedFling(target, velocityX, velocityY, consumed).also {
        // If the nested fling wasn't consumed, then the touch helper can act.
        // Otherwise, disable it.
        nestedScrollingListener?.invoke(it || consumed)
    }

    override fun getNestedScrollAxes(): Int {
        return parentHelper.nestedScrollAxes
    }

    override fun onStartNestedScroll(
        child: View,
        target: View,
        axes: Int,
        type: Int,
    ): Boolean {
        return onStartNestedScroll(child, target, axes)
    }

    override fun onStartNestedScroll(child: View, target: View, nestedScrollAxes: Int): Boolean {
        handleNestedTarget(target)
        return nestedScrollAxes and ViewCompat.SCROLL_AXIS_VERTICAL != 0 && !selectedItem
    }

    override fun onNestedScrollAccepted(
        child: View,
        target: View,
        axes: Int,
        type: Int,
    ) {
        if (axes and SCROLL_AXIS_VERTICAL != 0) {
            // A descendant started scrolling, so we'll observe it.
            setTarget(target)
        }
        parentHelper.onNestedScrollAccepted(child, target, axes, type)
    }

    override fun onNestedScrollAccepted(child: View, target: View, axes: Int) {
        if (axes and SCROLL_AXIS_VERTICAL != 0) {
            // A descendant started scrolling, so we'll observe it.
            setTarget(target)
        }
        parentHelper.onNestedScrollAccepted(child, target, axes)
    }

    fun cancelNestedScroll() {
        nestedScrollTarget?.let {
            onStopNestedScroll(it)
        }
        setTarget(null)
    }

    override fun onStopNestedScroll(target: View, type: Int) {
        // The descendant finished scrolling. Clean up!
        setTarget(null)
        parentHelper.onStopNestedScroll(target, type)
    }

    override fun onStopNestedScroll(child: View) {
        // The descendant finished scrolling. Clean up!
        setTarget(null)
        parentHelper.onStopNestedScroll(child)
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
    ) {
        onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed)
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
        consumed: IntArray,
    ) {
        onNestedScroll(target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, type)
    }

    override fun onNestedScroll(
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
    ) {
        if ((dyConsumed + dyUnconsumed).absoluteValue > touchSlop) {
            nestedScrollingListener?.invoke(true)
        }
    }

    override fun onNestedPreScroll(
        target: View,
        dx: Int,
        dy: Int,
        consumed: IntArray,
        type: Int,
    ) {
        onNestedPreScroll(target, dx, dy, consumed)
    }

    private fun setTarget(view: View?) {
        nestedScrollTarget = view
        nestedScrollTargetWasUnableToScroll = false
    }

    @SuppressLint("NewApi", "ClickableViewAccessibility")
    private fun handleNestedTarget(view: View) {
        when (view) {
            is AbsListView -> {
                var previousScrollOffset = view.verticalScrollOffset
                var prevY = 0f

                view.setOnScrollListener(object : AbsListView.OnScrollListener {
                    override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {
                        nestedScrollingListener?.invoke(scrollState != AbsListView.OnScrollListener.SCROLL_STATE_IDLE)
                    }

                    override fun onScroll(
                        view: AbsListView?,
                        firstVisibleItem: Int,
                        visibleItemCount: Int,
                        totalItemCount: Int,
                    ) {}
                })
                view.setOnTouchListener { _, event ->
                    when (event.action) {
                        MotionEvent.ACTION_MOVE -> {
                            val offset = view.verticalScrollOffset

                            if (previousScrollOffset != offset) {
                                nestedScrollingListener?.invoke(true)
                                previousScrollOffset = offset
                            }

                            if (prevY != event.rawY &&
                                (event.rawY - prevY).absoluteValue > touchSlop) {
                                nestedScrollingListener?.invoke(true)
                                prevY = event.rawY
                            }
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
                view.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                    if (scrollY != oldScrollY) {
                        nestedScrollingListener?.invoke(true)
                    }
                }
            }
        }
    }
}
