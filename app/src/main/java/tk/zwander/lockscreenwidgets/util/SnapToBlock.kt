package tk.zwander.lockscreenwidgets.util

import android.annotation.TargetApi
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.animation.Interpolator
import android.widget.Scroller
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.*
import kotlin.math.abs

//https://stackoverflow.com/questions/47514072/how-to-snap-recyclerview-items-so-that-every-x-items-would-be-considered-like-a
class SnapToBlock internal constructor(
    // Maximum blocks to move during most vigorous fling.
    var maxFlingBlocks: Int
) : SnapHelper() {
    private var recyclerView: RecyclerView? = null

    // Total number of items in a block of view in the RecyclerView
    private var blockSize = 0

    // Maximum number of positions to move on a fling.
    private var maxPositionsToMove = 0

    // Width of a RecyclerView item if orientation is horizontal; height of the item if vertical
    private var itemDimension = 0

    // Callback interface when blocks are snapped.
    private var snapBlockCallback: SnapBlockCallback? = null

    // When snapping, used to determine direction of snap.
    private var priorFirstPosition = RecyclerView.NO_POSITION

    // Our private scroller
    private var scroller: Scroller? = null

    // Horizontal/vertical layout helper
    private var orientationHelper: OrientationHelper? = null

    // LTR/RTL helper
    private var layoutDirectionHelper: LayoutDirectionHelper? = null

    @Throws(IllegalStateException::class)
    override fun attachToRecyclerView(@Nullable recyclerView: RecyclerView?) {
        if (recyclerView != null) {
            this.recyclerView = recyclerView
            val layoutManager: LinearLayoutManager = recyclerView.layoutManager as LinearLayoutManager
            when {
                layoutManager.canScrollHorizontally() -> {
                    orientationHelper = OrientationHelper.createHorizontalHelper(layoutManager)
                    layoutDirectionHelper =
                        LayoutDirectionHelper(ViewCompat.getLayoutDirection(this.recyclerView!!))
                }
                layoutManager.canScrollVertically() -> {
                    orientationHelper = OrientationHelper.createVerticalHelper(layoutManager)
                    // RTL doesn't matter for vertical scrolling for this class.
                    layoutDirectionHelper =
                        LayoutDirectionHelper(RecyclerView.LAYOUT_DIRECTION_LTR)
                }
                else -> {
                    throw IllegalStateException("RecyclerView must be scrollable")
                }
            }
            scroller = Scroller(this.recyclerView!!.context, sInterpolator)
            initItemDimensionIfNeeded(layoutManager)
        }
        super.attachToRecyclerView(recyclerView)
    }

    // Called when the target view is available and we need to know how much more
    // to scroll to get it lined up with the side of the RecyclerView.
    @NonNull
    override fun calculateDistanceToFinalSnap(
        @NonNull layoutManager: RecyclerView.LayoutManager,
        @NonNull targetView: View
    ): IntArray? {
        val out = IntArray(2)
        if (layoutManager.canScrollHorizontally()) {
            out[0] = layoutDirectionHelper!!.getScrollToAlignView(targetView)
        }
        if (layoutManager.canScrollVertically()) {
            out[1] = layoutDirectionHelper!!.getScrollToAlignView(targetView)
        }
        if (snapBlockCallback != null) {
            if (out[0] == 0 && out[1] == 0) {
                snapBlockCallback!!.onBlockSnapped(layoutManager.getPosition(targetView))
            } else {
                snapBlockCallback!!.onBlockSnap(layoutManager.getPosition(targetView))
            }
        }
        return out
    }

    // We are flinging and need to know where we are heading.
    override fun findTargetSnapPosition(
        layoutManager: RecyclerView.LayoutManager,
        velocityX: Int, velocityY: Int
    ): Int {
        val lm: LinearLayoutManager = layoutManager as LinearLayoutManager
        initItemDimensionIfNeeded(layoutManager)
        scroller!!.fling(
            0,
            0,
            velocityX,
            velocityY,
            Int.MIN_VALUE,
            Int.MAX_VALUE,
            Int.MIN_VALUE,
            Int.MAX_VALUE
        )
        if (velocityX != 0) {
            return layoutDirectionHelper!!.getPositionsToMove(lm, scroller!!.finalX, itemDimension)
        }
        return if (velocityY != 0) {
            layoutDirectionHelper!!.getPositionsToMove(lm, scroller!!.finalY, itemDimension)
        } else RecyclerView.NO_POSITION
    }

    // We have scrolled to the neighborhood where we will snap. Determine the snap position.
    override fun findSnapView(layoutManager: RecyclerView.LayoutManager): View? {
        // Snap to a view that is either 1) toward the bottom of the data and therefore on screen,
        // or, 2) toward the top of the data and may be off-screen.
        val snapPos = calcTargetPosition(layoutManager as LinearLayoutManager)
        val snapView =
            if (snapPos == RecyclerView.NO_POSITION) null else layoutManager.findViewByPosition(
                snapPos
            )
        if (snapView == null) {
            Log.d(TAG, "<<<<findSnapView is returning null!")
        }
        Log.d(TAG, "<<<<findSnapView snapos=$snapPos")
        return snapView
    }

    // Does the heavy lifting for findSnapView.
    private fun calcTargetPosition(layoutManager: LinearLayoutManager): Int {
        val snapPos: Int
        val firstVisiblePos: Int = layoutManager.findFirstVisibleItemPosition()
        if (firstVisiblePos == RecyclerView.NO_POSITION) {
            return RecyclerView.NO_POSITION
        }
        initItemDimensionIfNeeded(layoutManager)
        if (firstVisiblePos >= priorFirstPosition) {
            // Scrolling toward bottom of data
            val firstCompletePosition: Int = layoutManager.findFirstCompletelyVisibleItemPosition()
            snapPos = if (firstCompletePosition != RecyclerView.NO_POSITION
                && firstCompletePosition % blockSize == 0
            ) {
                firstCompletePosition
            } else {
                roundDownToBlockSize(firstVisiblePos + blockSize)
            }
        } else {
            // Scrolling toward top of data
//            snapPos = roundDownToBlockSize(firstVisiblePos)
            val firstCompletePosition: Int = layoutManager.findFirstCompletelyVisibleItemPosition()
            snapPos = if (firstCompletePosition != RecyclerView.NO_POSITION
                && firstCompletePosition % blockSize == 0
            ) {
                firstCompletePosition
            } else {
                roundDownToBlockSize(firstVisiblePos + blockSize)
            }
            // Check to see if target view exists. If it doesn't, force a smooth scroll.
            // SnapHelper only snaps to existing views and will not scroll to a non-existant one.
            // If limiting fling to single block, then the following is not needed since the
            // views are likely to be in the RecyclerView pool.
            if (layoutManager.findViewByPosition(snapPos) == null) {
                val toScroll =
                    layoutDirectionHelper!!.calculateDistanceToScroll(layoutManager, snapPos)
                recyclerView!!.smoothScrollBy(
                    toScroll[0],
                    toScroll[1],
                    sInterpolator
                )
            }
        }
        priorFirstPosition = firstVisiblePos
        return snapPos
    }

    private fun initItemDimensionIfNeeded(layoutManager: RecyclerView.LayoutManager) {
//        if (mItemDimension != 0) {
//            return
//        }
        val child: View?
        if (layoutManager.getChildAt(0).also { child = it } == null) {
            return
        }
        if (child == null) return
        if (layoutManager.canScrollHorizontally()) {
            itemDimension = child.width
            blockSize = getSpanCount(layoutManager) * (recyclerView!!.width / itemDimension)
        } else if (layoutManager.canScrollVertically()) {
            itemDimension = child.height
            blockSize = getSpanCount(layoutManager) * (recyclerView!!.height / itemDimension)
        }
        maxPositionsToMove = blockSize * maxFlingBlocks
    }

    private fun getSpanCount(layoutManager: RecyclerView.LayoutManager): Int {
        return if (layoutManager is GridLayoutManager) layoutManager.spanCount else 1
    }

    private fun roundDownToBlockSize(trialPosition: Int): Int {
        return trialPosition - trialPosition % blockSize
    }

    private fun roundUpToBlockSize(trialPosition: Int): Int {
        return roundDownToBlockSize(trialPosition + blockSize - 1)
    }

    @Nullable
    override fun createScroller(layoutManager: RecyclerView.LayoutManager): LinearSmoothScroller? {
        return if (layoutManager !is RecyclerView.SmoothScroller.ScrollVectorProvider) {
            null
        } else object : LinearSmoothScroller(recyclerView!!.context) {
            override fun onTargetFound(
                targetView: View,
                state: RecyclerView.State,
                action: Action
            ) {
                val snapDistances = calculateDistanceToFinalSnap(
                    recyclerView!!.layoutManager!!,
                    targetView
                )
                val dx = snapDistances!![0]
                val dy = snapDistances[1]
                val time: Int = calculateTimeForDeceleration(
                    abs(dx).coerceAtLeast(abs(dy))
                )
                if (time > 0) {
                    action.update(dx, dy, time, sInterpolator)
                }
            }

            override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float {
                return MILLISECONDS_PER_INCH / displayMetrics.densityDpi
            }
        }
    }

    fun setSnapBlockCallback(@Nullable callback: SnapBlockCallback?) {
        snapBlockCallback = callback
    }

    /*
        Helper class that handles calculations for LTR and RTL layouts.
     */
    private inner class LayoutDirectionHelper @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1) internal constructor(
        direction: Int
    ) {
        // Is the layout an RTL one?
        private val mIsRTL: Boolean = direction == View.LAYOUT_DIRECTION_RTL

        /*
            Calculate the amount of scroll needed to align the target view with the layout edge.
         */
        fun getScrollToAlignView(targetView: View?): Int {
            return if (mIsRTL) orientationHelper!!.getDecoratedEnd(targetView) - recyclerView!!.width else orientationHelper!!.getDecoratedStart(
                targetView
            )
        }

        /**
         * Calculate the distance to final snap position when the view corresponding to the snap
         * position is not currently available.
         *
         * @param layoutManager LinearLayoutManager or descendent class
         * @param targetPos     - Adapter position to snap to
         * @return int[2] {x-distance in pixels, y-distance in pixels}
         */
        fun calculateDistanceToScroll(
            layoutManager: LinearLayoutManager,
            targetPos: Int
        ): IntArray {
            val out = IntArray(2)
            val firstVisiblePos: Int = layoutManager.findFirstVisibleItemPosition()
            if (layoutManager.canScrollHorizontally()) {
                if (targetPos <= firstVisiblePos) { // scrolling toward top of data
                    if (mIsRTL) {
                        val lastView =
                            layoutManager.findViewByPosition(layoutManager.findLastVisibleItemPosition())
                        out[0] = (orientationHelper!!.getDecoratedEnd(lastView)
                                + (firstVisiblePos - targetPos) * itemDimension)
                    } else {
                        val firstView =
                            layoutManager.findViewByPosition(firstVisiblePos)
                        out[0] = (orientationHelper!!.getDecoratedStart(firstView)
                                - (firstVisiblePos - targetPos) * itemDimension)
                    }
                }
            }
            if (layoutManager.canScrollVertically()) {
                if (targetPos <= firstVisiblePos) { // scrolling toward top of data
                    val firstView =
                        layoutManager.findViewByPosition(firstVisiblePos)
                    out[1] = firstView!!.top - (firstVisiblePos - targetPos) * itemDimension
                }
            }
            return out
        }

        /*
            Calculate the number of positions to move in the RecyclerView given a scroll amount
            and the size of the items to be scrolled. Return integral multiple of mBlockSize not
            equal to zero.
         */
        fun getPositionsToMove(llm: LinearLayoutManager, scroll: Int, itemSize: Int): Int {
            var positionsToMove: Int
            positionsToMove = roundUpToBlockSize(Math.abs(scroll) / itemSize)
            if (positionsToMove < blockSize) {
                // Must move at least one block
                positionsToMove = blockSize
            } else if (positionsToMove > maxPositionsToMove) {
                // Clamp number of positions to move so we don't get wild flinging.
                positionsToMove = maxPositionsToMove
            }
            if (scroll < 0) {
                positionsToMove *= -1
            }
            if (mIsRTL) {
                positionsToMove *= -1
            }
            return if (layoutDirectionHelper!!.isDirectionToBottom(scroll < 0)) {
                // Scrolling toward the bottom of data.
                roundDownToBlockSize(llm.findFirstVisibleItemPosition()) + positionsToMove
            } else roundDownToBlockSize(llm.findLastVisibleItemPosition()) + positionsToMove
            // Scrolling toward the top of the data.
        }

        fun isDirectionToBottom(velocityNegative: Boolean): Boolean {
            return if (mIsRTL) velocityNegative else !velocityNegative
        }

    }

    interface SnapBlockCallback {
        fun onBlockSnap(snapPosition: Int)
        fun onBlockSnapped(snapPosition: Int)
    }

    companion object {
        // Borrowed from ViewPager.java
        private val sInterpolator: Interpolator = Interpolator { t ->
            // _o(t) = t * t * ((tension + 1) * t + tension)
            // o(t) = _o(t - 1) + 1
            var newT = t
            newT -= 1.0f
            newT * newT * newT + 1.0f
        }
        private const val MILLISECONDS_PER_INCH = 100f
        private const val TAG = "SnapToBlock"
    }

}