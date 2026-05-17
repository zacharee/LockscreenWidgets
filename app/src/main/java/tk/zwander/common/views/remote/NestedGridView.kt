package tk.zwander.common.views.remote

import android.content.Context
import android.util.AttributeSet
import android.widget.GridView
import androidx.core.view.NestedScrollingChild3
import androidx.core.view.NestedScrollingChildHelper

class NestedGridView(context: Context, attrs: AttributeSet) : GridView(context, attrs), NestedScrollingChild3 {
    private val helper = NestedScrollingChildHelper(this)

    init {
        helper.isNestedScrollingEnabled = true
    }

    override fun startNestedScroll(p0: Int, p1: Int): Boolean {
        return helper.startNestedScroll(p0, p1)
    }

    override fun stopNestedScroll(p0: Int) {
        helper.stopNestedScroll(p0)
    }

    override fun hasNestedScrollingParent(p0: Int): Boolean {
        return helper.hasNestedScrollingParent(p0)
    }

    override fun dispatchNestedScroll(
        p0: Int,
        p1: Int,
        p2: Int,
        p3: Int,
        p4: IntArray?,
        p5: Int,
    ): Boolean {
        return helper.dispatchNestedScroll(p0, p1, p2, p3, p4, p5)
    }

    override fun dispatchNestedPreScroll(
        p0: Int,
        p1: Int,
        p2: IntArray?,
        p3: IntArray?,
        p4: Int,
    ): Boolean {
        return helper.dispatchNestedPreScroll(p0, p1, p2, p3, p4)
    }

    override fun dispatchNestedScroll(
        p0: Int,
        p1: Int,
        p2: Int,
        p3: Int,
        p4: IntArray?,
        p5: Int,
        p6: IntArray,
    ) {
        helper.dispatchNestedScroll(p0, p1, p2, p3, p4, p5, p6)
    }

    override fun dispatchNestedFling(
        velocityX: Float,
        velocityY: Float,
        consumed: Boolean,
    ): Boolean {
        return helper.dispatchNestedFling(velocityX, velocityY, consumed)
    }

    override fun dispatchNestedPreFling(velocityX: Float, velocityY: Float): Boolean {
        return helper.dispatchNestedPreFling(velocityX, velocityY)
    }

    override fun dispatchNestedPreScroll(
        dx: Int,
        dy: Int,
        consumed: IntArray?,
        offsetInWindow: IntArray?,
    ): Boolean {
        return helper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow)
    }

    override fun dispatchNestedScroll(
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        offsetInWindow: IntArray?,
    ): Boolean {
        return helper.dispatchNestedScroll(
            dxConsumed,
            dyConsumed,
            dxUnconsumed,
            dyUnconsumed,
            offsetInWindow,
        )
    }

    override fun hasNestedScrollingParent(): Boolean {
        return helper.hasNestedScrollingParent()
    }

    override fun isNestedScrollingEnabled(): Boolean {
        return helper.isNestedScrollingEnabled
    }
}