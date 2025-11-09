package tk.zwander.common.util.migrations

import android.content.Context
import android.graphics.Point
import android.graphics.PointF
import tk.zwander.common.util.FrameSizeAndPosition
import tk.zwander.common.util.frameSizeAndPosition
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.data.Mode

@Suppress("DEPRECATION")
class FrameSizeAndPositionMigration : Migration {
    override val runOnOrBelowDatabaseVersion: Int
        get() = 4

    override fun run(context: Context) {
        val positions = mapOf(
            FrameSizeAndPosition.FrameType.LockNormal.Portrait to Point(
                context.prefManager.getCorrectFrameX(Mode.LOCK_NORMAL),
                context.prefManager.getCorrectFrameY(Mode.LOCK_NORMAL),
            ),
            FrameSizeAndPosition.FrameType.LockNotification.Portrait to Point(
                context.prefManager.getCorrectFrameX(Mode.LOCK_NOTIFICATION),
                context.prefManager.getCorrectFrameY(Mode.LOCK_NOTIFICATION),
            ),
            FrameSizeAndPosition.FrameType.NotificationNormal.Portrait to Point(
                context.prefManager.getCorrectFrameX(Mode.NOTIFICATION),
                context.prefManager.getCorrectFrameY(Mode.NOTIFICATION),
            ),
            FrameSizeAndPosition.FrameType.Preview.Portrait to Point(
                context.prefManager.getCorrectFrameX(Mode.PREVIEW),
                context.prefManager.getCorrectFrameY(Mode.PREVIEW),
            ),
        )

        val sizes = mapOf(
            FrameSizeAndPosition.FrameType.LockNormal.Portrait to PointF(
                context.prefManager.getCorrectFrameWidth(Mode.LOCK_NORMAL),
                context.prefManager.getCorrectFrameHeight(Mode.LOCK_NORMAL),
            ),
            FrameSizeAndPosition.FrameType.LockNotification.Portrait to PointF(
                context.prefManager.getCorrectFrameWidth(Mode.LOCK_NOTIFICATION),
                context.prefManager.getCorrectFrameHeight(Mode.LOCK_NOTIFICATION),
            ),
            FrameSizeAndPosition.FrameType.NotificationNormal.Portrait to PointF(
                context.prefManager.getCorrectFrameWidth(Mode.NOTIFICATION),
                context.prefManager.getCorrectFrameHeight(Mode.NOTIFICATION),
            ),
            FrameSizeAndPosition.FrameType.Preview.Portrait to PointF(
                context.prefManager.getCorrectFrameWidth(Mode.PREVIEW),
                context.prefManager.getCorrectFrameHeight(Mode.PREVIEW),
            ),
        )

        with (context.frameSizeAndPosition) {
            if (!hasPositions()) {
                positions.forEach { (k, v) ->
                    setPositionForType(k, v)
                }
            }

            if (!hasSizes()) {
                sizes.forEach { (k, v) ->
                    setSizeForType(k, v)
                }
            }
        }
    }
}