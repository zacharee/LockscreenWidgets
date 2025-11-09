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

fun Context.calculateNCPosXFromRightDefault(type: FrameSizeAndPosition.FrameType, display: LSDisplay): Int {
    val fromRight = display.dpToPx(resources.getInteger(R.integer.def_notification_pos_x_from_right_dp))
    val screenWidth = display.realSize.x
    val frameWidthPx = display.dpToPx(frameSizeAndPosition.getSizeForType(type, display).x)

    val frameRight = (frameWidthPx / 2f)
    val coord = (screenWidth / 2f) - fromRight - frameRight

    return coord.toInt()
}

fun Context.calculateNCPosYFromTopDefault(type: FrameSizeAndPosition.FrameType, display: LSDisplay): Int {
    val fromTop = display.dpToPx(resources.getInteger(R.integer.def_notification_pos_y_from_top_dp))
    val screenHeight = display.realSize.y
    val frameHeightPx = display.dpToPx(frameSizeAndPosition.getSizeForType(type, display).y)

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

    fun hasPositions() = positionsMap.isNotEmpty()
    fun hasSizes() = sizesMap.isNotEmpty()

    fun getPositionForType(type: FrameType, display: LSDisplay): Point {
        return positionsMap[type.key] ?: getDefaultPositionForType(type, display)
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

    fun getSizeForType(type: FrameType, display: LSDisplay): PointF {
        return sizesMap[type.key] ?: getDefaultSizeForType(type, display)
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

    private fun getDefaultPositionForType(type: FrameType, display: LSDisplay): Point {
        return when (type) {
            FrameType.LockNormal.Portrait,
            FrameType.Preview.Portrait,
            is FrameType.SecondaryLockscreen.Portrait,
            is FrameType.SecondaryPreview.Portrait,
                 -> Point(0, 0)

            FrameType.LockNotification.Portrait,
            FrameType.NotificationNormal.Portrait,
            is FrameType.SecondaryNotification.Portrait,
            is FrameType.SecondaryLockNotification.Portrait,
                -> Point(
                context.calculateNCPosXFromRightDefault(type, display),
                context.calculateNCPosYFromTopDefault(type, display),
            )

            // These are getting the *current* position for portrait, which is to keep things somewhat
            // consistent on squarer displays.
            FrameType.LockNormal.Landscape -> getPositionForType(FrameType.LockNormal.Portrait, display)
            FrameType.LockNotification.Landscape -> getPositionForType(FrameType.LockNotification.Portrait, display)
            FrameType.NotificationNormal.Landscape -> getPositionForType(FrameType.NotificationNormal.Portrait, display)
            FrameType.Preview.Landscape -> getPositionForType(FrameType.Preview.Portrait, display)
            is FrameType.SecondaryLockscreen.Landscape -> getPositionForType(FrameType.SecondaryLockscreen.Portrait(type.id), display)
            is FrameType.SecondaryNotification.Landscape -> getPositionForType(FrameType.SecondaryNotification.Portrait(type.id), display)
            is FrameType.SecondaryLockNotification.Landscape -> getPositionForType(FrameType.SecondaryLockNotification.Portrait(type.id), display)
            is FrameType.SecondaryPreview.Landscape -> getPositionForType(FrameType.SecondaryPreview.Portrait(type.id), display)
        }
    }

    private fun getDefaultSizeForType(type: FrameType, display: LSDisplay): PointF {
        return when (type) {
            FrameType.LockNormal.Portrait,
            FrameType.Preview.Portrait,
            is FrameType.SecondaryLockscreen.Portrait,
            is FrameType.SecondaryPreview.Portrait -> PointF(
                prefManager.getResourceFloat(R.integer.def_frame_width),
                prefManager.getResourceFloat(R.integer.def_frame_height),
            )

            FrameType.LockNotification.Portrait,
            FrameType.NotificationNormal.Portrait,
            is FrameType.SecondaryNotification.Portrait,
            is FrameType.SecondaryLockNotification.Portrait -> PointF(
                prefManager.getResourceFloat(R.integer.def_notification_frame_width),
                prefManager.getResourceFloat(R.integer.def_notification_frame_height),
            )

            // These are getting the *current* size for portrait, which is to keep things somewhat
            // consistent on squarer displays.
            FrameType.LockNormal.Landscape -> getSizeForType(FrameType.LockNormal.Portrait, display)
            FrameType.LockNotification.Landscape -> getSizeForType(FrameType.LockNotification.Portrait, display)
            FrameType.NotificationNormal.Landscape -> getSizeForType(FrameType.NotificationNormal.Portrait, display)
            FrameType.Preview.Landscape -> getSizeForType(FrameType.Preview.Portrait, display)
            is FrameType.SecondaryLockscreen.Landscape -> getSizeForType(FrameType.SecondaryLockscreen.Portrait(type.id), display)
            is FrameType.SecondaryLockNotification.Landscape -> getSizeForType(FrameType.SecondaryLockNotification.Portrait(type.id), display)
            is FrameType.SecondaryNotification.Landscape -> getSizeForType(FrameType.SecondaryNotification.Portrait(type.id), display)
            is FrameType.SecondaryPreview.Landscape -> getSizeForType(FrameType.SecondaryPreview.Portrait(type.id), display)
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

        sealed class SecondaryLockscreen(key: String) : FrameType("secondary_lockscreen_$key") {
            companion object {
                fun select(portrait: Boolean, id: Int): FrameType {
                    return if (portrait) Portrait(id) else Landscape(id)
                }
            }

            data class Portrait(val id: Int) : SecondaryLockscreen("Portrait_$id")
            data class Landscape(val id: Int) : SecondaryLockscreen("Landscape_$id")
        }

        sealed class SecondaryLockNotification(key: String) : FrameType("secondary_lock_notification_$key") {
            companion object {
                fun select(portrait: Boolean, id: Int): FrameType {
                    return if (portrait) Portrait(id) else Landscape(id)
                }
            }

            data class Portrait(val id: Int) : SecondaryLockNotification("Portrait_$id")
            data class Landscape(val id: Int) : SecondaryLockNotification("Landscape_$id")
        }

        sealed class SecondaryNotification(key: String) : FrameType("secondary_notification_$key") {
            companion object {
                fun select(portrait: Boolean, id: Int): FrameType {
                    return if (portrait) Portrait(id) else Landscape(id)
                }
            }

            data class Portrait(val id: Int) : SecondaryLockNotification("Portrait_$id")
            data class Landscape(val id: Int) : SecondaryLockNotification("Landscape_$id")
        }

        sealed class SecondaryPreview(key: String) : FrameType("secondary_Preview_$key") {
            companion object {
                fun select(portrait: Boolean, id: Int): FrameType {
                    return if (portrait) Portrait(id) else Landscape(id)
                }
            }

            data class Portrait(val id: Int) : SecondaryLockNotification("Portrait_$id")
            data class Landscape(val id: Int) : SecondaryLockNotification("Landscape_$id")
        }
    }
}