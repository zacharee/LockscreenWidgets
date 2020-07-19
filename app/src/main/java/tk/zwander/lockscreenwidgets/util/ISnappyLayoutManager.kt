package tk.zwander.lockscreenwidgets.util

//Based on https://stackoverflow.com/a/26445064/5496177
interface ISnappyLayoutManager {
    fun getPositionForVelocity(velocityX: Int, velocityY: Int): Int
    fun getFixScrollPos(velocityX: Int, velocityY: Int): Int

    fun canSnap(): Boolean
}