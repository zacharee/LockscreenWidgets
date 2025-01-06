package tk.zwander.common.iconpacks

import android.annotation.TargetApi
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.SystemClock
import androidx.core.util.Supplier
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Wrapper over [AdaptiveIconDrawable] to intercept icon flattening logic for dynamic
 * clock icons
 */
@TargetApi(Build.VERSION_CODES.O)
class ClockDrawableWrapper private constructor(base: AdaptiveIconDrawable) :
    AdaptiveIconDrawable(base.background, base.foreground) {
    private val mAnimationInfo = AnimationInfo()

    private class AnimationInfo {
        var baseDrawableState: ConstantState? = null

        var hourLayerIndex: Int = 0
        var minuteLayerIndex: Int = 0
        var secondLayerIndex: Int = 0
        var defaultHour: Int = 0
        var defaultMinute: Int = 0
        var defaultSecond: Int = 0

        fun copyForIcon(icon: Drawable): AnimationInfo {
            val result = AnimationInfo()
            result.baseDrawableState = icon.constantState
            result.defaultHour = defaultHour
            result.defaultMinute = defaultMinute
            result.defaultSecond = defaultSecond
            result.hourLayerIndex = hourLayerIndex
            result.minuteLayerIndex = minuteLayerIndex
            result.secondLayerIndex = secondLayerIndex
            return result
        }

        fun applyTime(time: Calendar, foregroundDrawable: LayerDrawable): Boolean {
            time.timeInMillis = System.currentTimeMillis()

            // We need to rotate by the difference from the default time if one is specified.
            val convertedHour = (time[Calendar.HOUR] + (12 - defaultHour)) % 12
            val convertedMinute = (time[Calendar.MINUTE] + (60 - defaultMinute)) % 60
            val convertedSecond = (time[Calendar.SECOND] + (60 - defaultSecond)) % 60

            var invalidate = false
            if (hourLayerIndex != INVALID_VALUE) {
                val hour = foregroundDrawable.getDrawable(hourLayerIndex)
                if (hour.setLevel(convertedHour * 60 + time[Calendar.MINUTE])) {
                    invalidate = true
                }
            }

            if (minuteLayerIndex != INVALID_VALUE) {
                val minute = foregroundDrawable.getDrawable(minuteLayerIndex)
                if (minute.setLevel(time[Calendar.HOUR] * 60 + convertedMinute)) {
                    invalidate = true
                }
            }

            if (secondLayerIndex != INVALID_VALUE) {
                val second = foregroundDrawable.getDrawable(secondLayerIndex)
                if (second.setLevel(convertedSecond * LEVELS_PER_SECOND)) {
                    invalidate = true
                }
            }

            return invalidate
        }
    }

    private class ClockIconDrawable(cs: ClockConstantState) :
        FastBitmapDrawable(cs.mBitmap, cs.mIconColor), Runnable {
        private val mTime: Calendar = Calendar.getInstance()

        private val mBoundsOffset = cs.mBoundsOffset
        private val mAnimInfo =
            cs.mAnimInfo

        private val mBG = cs.mBG
        private val mBgPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        private val mBgFilter = cs.mBgFilter
        private val mThemedFgColor: Int

        private val mFullDrawable: AdaptiveIconDrawable
        private val mFG: LayerDrawable
        private val mCanvasScale: Float

        init {
            mBgPaint.setColorFilter(cs.mBgFilter)
            mThemedFgColor = cs.mThemedFgColor

            mFullDrawable =
                mAnimInfo!!.baseDrawableState!!.newDrawable().mutate() as AdaptiveIconDrawable
            mFG = mFullDrawable.foreground as LayerDrawable

            // Time needs to be applied here since drawInternal is NOT guaranteed to be called
            // before this foreground drawable is shown on the screen.
            mAnimInfo.applyTime(mTime, mFG)
            mCanvasScale = 1 - 2 * mBoundsOffset
        }

        override fun setAlpha(alpha: Int) {
            super.setAlpha(alpha)
            mBgPaint.alpha = alpha
            mFG.alpha = alpha
        }

        override fun onBoundsChange(bounds: Rect) {
            super.onBoundsChange(bounds)

            // b/211896569 AdaptiveIcon does not work properly when bounds
            // are not aligned to top/left corner
            mFullDrawable.setBounds(0, 0, bounds.width(), bounds.height())
        }

        public override fun drawInternal(canvas: Canvas, bounds: Rect) {
            if (mAnimInfo == null) {
                super.drawInternal(canvas, bounds)
                return
            }
            canvas.drawBitmap(mBG, null, bounds, mBgPaint)

            // prepare and draw the foreground
            mAnimInfo.applyTime(mTime, mFG)
            val saveCount = canvas.save()
            canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
            canvas.scale(mCanvasScale, mCanvasScale, bounds.width() / 2f, bounds.height() / 2f)
            canvas.clipPath(mFullDrawable.iconMask)
            mFG.draw(canvas)
            canvas.restoreToCount(saveCount)

            reschedule()
        }

        override val isThemed: Boolean
            get() = mBgPaint.colorFilter != null

        override fun updateFilter() {
            super.updateFilter()
            val alpha = if (mIsDisabled) (mDisabledAlpha * FULLY_OPAQUE).toInt() else FULLY_OPAQUE
            setAlpha(alpha)
            mBgPaint.setColorFilter(if (mIsDisabled) disabledColorFilter else mBgFilter)
            mFG.colorFilter =
                if (mIsDisabled) disabledColorFilter else null
        }

        override val iconColor: Int
            get() = if (isThemed) mThemedFgColor else super.iconColor

        override fun run() {
            if (mAnimInfo!!.applyTime(mTime, mFG)) {
                invalidateSelf()
            } else {
                reschedule()
            }
        }

        override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
            val result = super.setVisible(visible, restart)
            if (visible) {
                reschedule()
            } else {
                unscheduleSelf(this)
            }
            return result
        }

        fun reschedule() {
            if (!isVisible) {
                return
            }
            unscheduleSelf(this)
            val upTime = SystemClock.uptimeMillis()
            val step = TICK_MS /* tick every 200 ms */
            scheduleSelf(this, upTime - ((upTime % step)) + step)
        }

        public override fun newConstantState(): FastBitmapConstantState {
            return ClockConstantState(
                mBitmap, mIconColor, mThemedFgColor, mBoundsOffset,
                mAnimInfo, mBG, mBgPaint.colorFilter
            )
        }

        private class ClockConstantState(
            bitmap: Bitmap,
            color: Int,
            val mThemedFgColor: Int,
            val mBoundsOffset: Float,
            val mAnimInfo: AnimationInfo?,
            val mBG: Bitmap,
            val mBgFilter: ColorFilter
        ) : FastBitmapConstantState(bitmap, color) {
            public override fun createDrawable(): FastBitmapDrawable {
                return ClockIconDrawable(this)
            }
        }
    }

    companion object {
        private const val DISABLE_SECONDS = true

        // Time after which the clock icon should check for an update. The actual invalidate
        // will only happen in case of any change.
        val TICK_MS: Long = if (DISABLE_SECONDS) TimeUnit.MINUTES.toMillis(1) else 200L

        /* Number of levels to jump per second for the second hand */
        private const val LEVELS_PER_SECOND = 10

        const val INVALID_VALUE: Int = -1

        fun forMeta(
            metadata: ClockMetadata,
            drawableProvider: Supplier<Drawable?>
        ): ClockDrawableWrapper? {
            val drawable = drawableProvider.get()?.mutate() as? AdaptiveIconDrawable ?: return null

            val wrapper =
                ClockDrawableWrapper(drawable)
            val info = wrapper.mAnimationInfo

            info.baseDrawableState = drawable.constantState

            info.hourLayerIndex = metadata.hourLayerIndex
            info.minuteLayerIndex = metadata.minuteLayerIndex
            info.secondLayerIndex = metadata.secondLayerIndex

            info.defaultHour = metadata.defaultHour
            info.defaultMinute = metadata.defaultMinute
            info.defaultSecond = metadata.defaultSecond

            val foreground = wrapper.foreground as LayerDrawable
            val layerCount = foreground.numberOfLayers
            if (info.hourLayerIndex < 0 || info.hourLayerIndex >= layerCount) {
                info.hourLayerIndex = INVALID_VALUE
            }
            if (info.minuteLayerIndex < 0 || info.minuteLayerIndex >= layerCount) {
                info.minuteLayerIndex = INVALID_VALUE
            }
            if (info.secondLayerIndex < 0 || info.secondLayerIndex >= layerCount) {
                info.secondLayerIndex = INVALID_VALUE
            } else if (DISABLE_SECONDS) {
                foreground.setDrawable(info.secondLayerIndex, null)
                info.secondLayerIndex = INVALID_VALUE
            }
            info.applyTime(Calendar.getInstance(), foreground)
            return wrapper
        }
    }
}