package tk.zwander.common.views

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.AbsListView
import androidx.annotation.CallSuper
import androidx.recyclerview.widget.RecyclerView
import tk.zwander.common.util.verticalScrollOffset
import kotlin.math.absoluteValue

open class ScrollingItemTouchRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : RecyclerView(context, attrs, defStyleAttr) {
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

    init {
        isNestedScrollingEnabled = true
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_UP) {
//            // This is to prevent an issue where the item touch helper receives
//            // an ACTION_DOWN but then doesn't later get the ACTION_UP event,
//            // causing it to run any long-press events.
//            nestedScrollingListener?.invoke(true)
            nestedScrollingListener?.invoke(false)
        }

        return super.dispatchTouchEvent(ev)
    }

    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray) {
        nestedScrollingListener?.invoke(true)
        super.onNestedPreScroll(target, dx, dy, consumed)
    }

    @CallSuper
    override fun onStartNestedScroll(child: View, target: View, nestedScrollAxes: Int): Boolean {
        handleNestedTarget(child)
        return !selectedItem
    }

    override fun onScrollStateChanged(state: Int) {
        super.onScrollStateChanged(state)
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
        nestedScrollingListener?.invoke(!it)
    }

    @SuppressLint("NewApi", "ClickableViewAccessibility")
    private fun handleNestedTarget(view: View) {
        val slop = ViewConfiguration.get(context).scaledTouchSlop

        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
//                nestedScrollingListener?.invoke(true)
                nestedScrollingListener?.invoke(false)
            }
            false
        }

        when (view) {
            is AbsListView -> {
                var previousScrollOffset = view.verticalScrollOffset
                var prevY = 0f
                var downY = 0f

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
                        MotionEvent.ACTION_DOWN -> {
                            downY = event.rawY
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val offset = view.verticalScrollOffset

                            if (previousScrollOffset != offset) {
                                nestedScrollingListener?.invoke(true)
                                previousScrollOffset = offset
                            }

                            if (prevY != event.rawY &&
                                (event.rawY - downY).absoluteValue > slop) {
                                nestedScrollingListener?.invoke(true)
                                prevY = event.rawY
                                downY = Int.MIN_VALUE.toFloat()
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