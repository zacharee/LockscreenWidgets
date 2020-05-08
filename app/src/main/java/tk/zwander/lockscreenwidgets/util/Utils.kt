package tk.zwander.lockscreenwidgets.util

import android.content.Context
import android.util.DisplayMetrics
import android.util.TypedValue
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SnapHelper
import kotlin.math.roundToInt

val Context.prefManager: PrefManager
    get() = PrefManager.getInstance(this)

fun Context.dpAsPx(dpVal: Number) =
    TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dpVal.toFloat(),
        resources.displayMetrics
    ).roundToInt()

fun Context.pxAsDp(pxVal: Number) =
    pxVal.toFloat() / resources.displayMetrics.density

fun SnapHelper.getSnapPosition(recyclerView: RecyclerView): Int {
    val layoutManager = recyclerView.layoutManager ?: return RecyclerView.NO_POSITION
    val snapView = findSnapView(layoutManager) ?: return RecyclerView.NO_POSITION
    return layoutManager.getPosition(snapView)
}