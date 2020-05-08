package tk.zwander.lockscreenwidgets.util

import android.content.Context
import android.util.DisplayMetrics
import android.util.TypedValue
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