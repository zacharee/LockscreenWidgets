package androidx.recyclerview.widget

class FixedItemTouchHelper(callback: Callback) : ItemTouchHelper(callback) {
    override fun attachToRecyclerView(recyclerView: RecyclerView?) {
        if (recyclerView == null) {
            select(null, ACTION_STATE_IDLE)
        }

        super.attachToRecyclerView(recyclerView)
    }

    override fun scrollIfNecessary(): Boolean {
        if (mRecyclerView == null) {
            return false
        }

        return super.scrollIfNecessary()
    }
}
