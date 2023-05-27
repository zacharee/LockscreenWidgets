package tk.zwander.common.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Point
import android.graphics.PointF
import com.google.gson.reflect.TypeToken
import tk.zwander.lockscreenwidgets.R

val Context.frameSizeAndPosition: FrameSizeAndPosition
    get() = FrameSizeAndPosition.getInstance(this)

class FrameSizeAndPosition private constructor(context: Context) : ContextWrapper(context) {
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

    private val prefManager = baseContext.prefManager

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

    fun getSizeForType(type: FrameType): PointF {
        return sizesMap[type.key] ?: getDefaultSizeForType(type)
    }

    fun setSizeForType(type: FrameType, size: PointF) {
        sizesMap = sizesMap.toMutableMap().apply {
            this[type.key] = size
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
            FrameType.Preview.Portrait -> Point(0, 0)

            FrameType.LockNotification.Portrait,
            FrameType.NotificationNormal.Portrait -> Point(
                calculateNCPosXFromRightDefault(),
                calculateNCPosYFromTopDefault()
            )

            FrameType.LockNormal.Landscape -> getPositionForType(FrameType.LockNormal.Portrait)
            FrameType.LockNotification.Landscape -> getPositionForType(FrameType.LockNotification.Portrait)
            FrameType.NotificationNormal.Landscape -> getPositionForType(FrameType.NotificationNormal.Portrait)
            FrameType.Preview.Landscape -> getPositionForType(FrameType.Preview.Portrait)
        }
    }

    private fun getDefaultSizeForType(type: FrameType): PointF {
        return when (type) {
            FrameType.LockNormal.Portrait,
            FrameType.Preview.Portrait -> PointF(
                prefManager.getResourceFloat(R.integer.def_frame_width),
                prefManager.getResourceFloat(R.integer.def_frame_height)
            )

            FrameType.LockNotification.Portrait,
            FrameType.NotificationNormal.Portrait -> PointF(
                prefManager.getResourceFloat(R.integer.def_notification_frame_width),
                prefManager.getResourceFloat(R.integer.def_notification_frame_height)
            )

            FrameType.LockNormal.Landscape -> getSizeForType(FrameType.LockNormal.Portrait)
            FrameType.LockNotification.Landscape -> getSizeForType(FrameType.LockNotification.Portrait)
            FrameType.NotificationNormal.Landscape -> getSizeForType(FrameType.NotificationNormal.Portrait)
            FrameType.Preview.Landscape -> getSizeForType(FrameType.Preview.Portrait)
        }
    }

    sealed class FrameType(val key: String) {
        sealed class LockNormal(key: String) : FrameType("lock_normal_$key") {
            companion object {
                fun select(portrait: Boolean): FrameType {
                    return if (portrait) Portrait else Landscape
                }
            }

            object Portrait : LockNormal("portrait")
            object Landscape : LockNormal("landscape")
        }

        sealed class LockNotification(key: String) : FrameType("lock_notification_$key") {
            companion object {
                fun select(portrait: Boolean): FrameType {
                    return if (portrait) Portrait else Landscape
                }
            }

            object Portrait : LockNotification("portrait")
            object Landscape : LockNotification("landscape")
        }

        sealed class NotificationNormal(key: String) : FrameType("notification_normal_$key") {
            companion object {
                fun select(portrait: Boolean): FrameType {
                    return if (portrait) Portrait else Landscape
                }
            }

            object Portrait : NotificationNormal("portrait")
            object Landscape : NotificationNormal("landscape")
        }

        sealed class Preview(key: String) : FrameType("preview_$key") {
            companion object {
                fun select(portrait: Boolean): FrameType {
                    return if (portrait) Portrait else Landscape
                }
            }

            object Portrait : Preview("portrait")
            object Landscape : Preview("landscape")
        }
    }
}