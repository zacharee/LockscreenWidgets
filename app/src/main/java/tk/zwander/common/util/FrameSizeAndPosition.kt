package tk.zwander.common.util

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Point
import android.graphics.PointF
import com.google.gson.reflect.TypeToken
import tk.zwander.common.data.SafePointF
import tk.zwander.lockscreenwidgets.R

val Context.frameSizeAndPosition: FrameSizeAndPosition
    get() = FrameSizeAndPosition.getInstance(this)

fun Context.calculateNCPosXFromRightDefault(type: FrameSizeAndPosition.FrameType): Int {
    val fromRight = dpAsPx(resources.getInteger(R.integer.def_notification_pos_x_from_right_dp))
    val screenWidth = screenSize.x
    val frameWidthPx = dpAsPx(frameSizeAndPosition.getSizeForType(type).x)

    val frameRight = (frameWidthPx / 2f)
    val coord = (screenWidth / 2f) - fromRight - frameRight

    return coord.toInt()
}

fun Context.calculateNCPosYFromTopDefault(type: FrameSizeAndPosition.FrameType): Int {
    val fromTop = dpAsPx(resources.getInteger(R.integer.def_notification_pos_y_from_top_dp))
    val screenHeight = screenSize.y
    val frameHeightPx = dpAsPx(frameSizeAndPosition.getSizeForType(type).y)

    val frameTop = (frameHeightPx / 2f)
    val coord = -(screenHeight / 2f) + frameTop + fromTop

    return coord.toInt()
}

class FrameSizeAndPosition private constructor(private val context: Context) {
    companion object {
        @SuppressLint("StaticFieldLeak")
        private var instance: FrameSizeAndPosition? = null

        @Synchronized
        fun getInstance(context: Context): FrameSizeAndPosition {
            return instance ?: FrameSizeAndPosition(context.safeApplicationContext).apply {
                instance = this
            }
        }

        const val KEY_POSITIONS_MAP = "frame_positions_map"
        const val KEY_SIZES_MAP = "frame_sizes_map"
    }

    private val prefManager = context.prefManager

    private var positionsMap: Map<String, Point>
        get() = prefManager.gson.fromJson(
            prefManager.getString(KEY_POSITIONS_MAP),
            object : TypeToken<HashMap<String, Point>>() {}.type
        ) ?: mapOf()
        set(value) {
            prefManager.putString(
                KEY_POSITIONS_MAP,
                prefManager.gson.toJson(value)
            )
        }

    private var sizesMap: Map<String, PointF>
        get() = prefManager.gson.fromJson(
            prefManager.getString(KEY_SIZES_MAP),
            object : TypeToken<HashMap<String, PointF>>() {}.type
        ) ?: mapOf()
        set(value) {
            prefManager.putString(
                KEY_SIZES_MAP,
                prefManager.gson.toJson(value)
            )
        }

    fun getPositionForType(type: FrameType): Point {
        return positionsMap[type.key] ?: getDefaultPositionForType(type)
    }

    fun setPositionForType(type: FrameType, position: Point) {
        positionsMap = positionsMap.toMutableMap().apply {
            this[type.key] = position
        }
    }

    fun removePositionForType(type: FrameType) {
        positionsMap = positionsMap.toMutableMap().apply {
            remove(type.key)
        }
    }

    fun getSizeForType(type: FrameType): PointF {
        return sizesMap[type.key] ?: getDefaultSizeForType(type)
    }

    fun setSizeForType(type: FrameType, size: PointF) {
        sizesMap = sizesMap.toMutableMap().apply {
            this[type.key] = SafePointF(size)
        }
    }

    fun removeSizeForType(type: FrameType) {
        sizesMap = sizesMap.toMutableMap().apply {
            remove(type.key)
        }
    }

    fun setDefaultSizeForType(type: FrameType) {
        setSizeForType(type, getDefaultSizeForType(type))
    }

    fun setDefaultPositionForType(type: FrameType) {
        setPositionForType(type, getDefaultPositionForType(type))
    }

    private fun getDefaultPositionForType(type: FrameType): Point {
        return when (type) {
            FrameType.LockNormal.Portrait,
            FrameType.Preview.Portrait,
            is FrameType.SecondaryLockscreen.Portrait
                 -> Point(0, 0)

            FrameType.LockNotification.Portrait,
            FrameType.NotificationNormal.Portrait -> Point(
                context.calculateNCPosXFromRightDefault(type),
                context.calculateNCPosYFromTopDefault(type),
            )

            // These are getting the *current* position for portrait, which is to keep things somewhat
            // consistent on squarer displays.
            FrameType.LockNormal.Landscape -> getPositionForType(FrameType.LockNormal.Portrait)
            FrameType.LockNotification.Landscape -> getPositionForType(FrameType.LockNotification.Portrait)
            FrameType.NotificationNormal.Landscape -> getPositionForType(FrameType.NotificationNormal.Portrait)
            FrameType.Preview.Landscape -> getPositionForType(FrameType.Preview.Portrait)
            is FrameType.SecondaryLockscreen.Landscape -> getPositionForType(FrameType.SecondaryLockscreen.Portrait(type.id))
        }
    }

    private fun getDefaultSizeForType(type: FrameType): PointF {
        return when (type) {
            FrameType.LockNormal.Portrait,
            FrameType.Preview.Portrait,
            is FrameType.SecondaryLockscreen.Portrait -> PointF(
                prefManager.getResourceFloat(R.integer.def_frame_width),
                prefManager.getResourceFloat(R.integer.def_frame_height),
            )

            FrameType.LockNotification.Portrait,
            FrameType.NotificationNormal.Portrait -> PointF(
                prefManager.getResourceFloat(R.integer.def_notification_frame_width),
                prefManager.getResourceFloat(R.integer.def_notification_frame_height),
            )

            // These are getting the *current* size for portrait, which is to keep things somewhat
            // consistent on squarer displays.
            FrameType.LockNormal.Landscape -> getSizeForType(FrameType.LockNormal.Portrait)
            FrameType.LockNotification.Landscape -> getSizeForType(FrameType.LockNotification.Portrait)
            FrameType.NotificationNormal.Landscape -> getSizeForType(FrameType.NotificationNormal.Portrait)
            FrameType.Preview.Landscape -> getSizeForType(FrameType.Preview.Portrait)
            is FrameType.SecondaryLockscreen.Landscape -> getSizeForType(FrameType.SecondaryLockscreen.Portrait(type.id))
        }
    }

    sealed class FrameType(val key: String) {
        sealed class LockNormal(key: String) : FrameType("lock_normal_$key") {
            companion object {
                fun select(portrait: Boolean): FrameType {
                    return if (portrait) Portrait else Landscape
                }
            }

            data object Portrait : LockNormal("portrait")
            data object Landscape : LockNormal("landscape")
        }

        sealed class LockNotification(key: String) : FrameType("lock_notification_$key") {
            companion object {
                fun select(portrait: Boolean): FrameType {
                    return if (portrait) Portrait else Landscape
                }
            }

            data object Portrait : LockNotification("portrait")
            data object Landscape : LockNotification("landscape")
        }

        sealed class NotificationNormal(key: String) : FrameType("notification_normal_$key") {
            companion object {
                fun select(portrait: Boolean): FrameType {
                    return if (portrait) Portrait else Landscape
                }
            }

            data object Portrait : NotificationNormal("portrait")
            data object Landscape : NotificationNormal("landscape")
        }

        sealed class Preview(key: String) : FrameType("preview_$key") {
            companion object {
                fun select(portrait: Boolean): FrameType {
                    return if (portrait) Portrait else Landscape
                }
            }

            data object Portrait : Preview("portrait")
            data object Landscape : Preview("landscape")
        }

        sealed class SecondaryLockscreen(key: String): FrameType("secondary_lockscreen_$key") {
            companion object {
                fun select(portrait: Boolean, id: Int): FrameType {
                    return if (portrait) Portrait(id) else Landscape(id)
                }
            }

            data class Portrait(val id: Int) : SecondaryLockscreen("Portrait_$id")
            data class Landscape(val id: Int) : SecondaryLockscreen("Landscape_$id")
        }
    }
}