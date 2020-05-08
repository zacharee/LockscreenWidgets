package tk.zwander.lockscreenwidgets.util

import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SnapHelper
import tk.zwander.lockscreenwidgets.interfaces.OnSnapPositionChangeListener

class SnapScrollListener(private val snapHelper: SnapHelper, private val onSnapPositionChangeListener: OnSnapPositionChangeListener) : RecyclerView.OnScrollListener() {
    private var snapPosition = RecyclerView.NO_POSITION

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        maybeNotifySnapPositionChange(recyclerView)
    }

    private fun maybeNotifySnapPositionChange(recyclerView: RecyclerView) {
        val snapPosition = snapHelper.getSnapPosition(recyclerView)
        val snapPositionChanged = this.snapPosition != snapPosition
        if (snapPositionChanged) {
            onSnapPositionChangeListener
                .onSnapPositionChange(snapPosition)
            this.snapPosition = snapPosition
        }
    }
}