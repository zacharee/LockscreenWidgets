@file:Suppress("unused")

package tk.zwander.common.data

import android.graphics.Point
import android.graphics.PointF
import kotlinx.parcelize.Parcelize
import kotlin.math.min

private const val MAX_SIZE = 10_000f

@Parcelize
@ConsistentCopyVisibility
data class SafePointF private constructor(
    val x: Float,
    val y: Float,
) : PointF(x, y) {
    companion object {
        operator fun invoke(x: Float, y: Float): SafePointF {
            return SafePointF(min(x, MAX_SIZE), min(y, MAX_SIZE))
        }
    }

    constructor(point: Point) : this(PointF(point))
    constructor(point: PointF) : this(min(point.x, MAX_SIZE), min(point.y, MAX_SIZE))
}
