package tk.zwander.lockscreenwidgets.interfaces

interface ItemTouchHelperAdapter {
    fun onMove(from: Int, to: Int): Boolean
}