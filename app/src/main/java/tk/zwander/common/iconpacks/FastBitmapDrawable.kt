package tk.zwander.common.iconpacks

import android.animation.ObjectAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.FloatProperty
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import androidx.core.graphics.ColorUtils
import tk.zwander.common.iconpacks.BitmapInfo.DrawableCreationFlags
import androidx.core.graphics.withSave

@RequiresApi(Build.VERSION_CODES.N)
open class FastBitmapDrawable protected constructor(
    @JvmField protected val mBitmap: Bitmap,
    @JvmField protected val mIconColor: Int
) :
    Drawable(), Drawable.Callback {
    protected val mPaint: Paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    private var mColorFilter: ColorFilter? = null

    protected var mIsPressed: Boolean = false
    protected var mIsHovered: Boolean = false
    @JvmField
    protected var mIsDisabled: Boolean = false
    @JvmField
    var mDisabledAlpha: Float = 1f

    @DrawableCreationFlags
    var mCreationFlags: Int = 0

    protected var mScaleAnimation: ObjectAnimator? = null
    private var mScale: Float = 1f
    private var mAlpha: Int = 255

    private var mBadge: Drawable? = null

    @Suppress("unused")
    constructor(b: Bitmap) : this(b, Color.TRANSPARENT)

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        updateBadgeBounds(bounds)
    }

    private fun updateBadgeBounds(bounds: Rect) {
        if (mBadge != null) {
            setBadgeBounds(mBadge!!, bounds)
        }
    }

    override fun draw(canvas: Canvas) {
        if (mScale != 1f) {
            canvas.withSave {
                val bounds: Rect = bounds
                canvas.scale(mScale, mScale, bounds.exactCenterX(), bounds.exactCenterY())
                drawInternal(canvas, bounds)
                if (mBadge != null) {
                    mBadge!!.draw(canvas)
                }
            }
        } else {
            drawInternal(canvas, bounds)
            if (mBadge != null) {
                mBadge!!.draw(canvas)
            }
        }
    }

    protected open fun drawInternal(canvas: Canvas, bounds: Rect) {
        canvas.drawBitmap(mBitmap, null, bounds, mPaint)
    }

    open val iconColor: Int
        /**
         * Returns the primary icon color, slightly tinted white
         */
        get() {
            val whiteScrim: Int =
                setColorAlphaBound(
                    Color.WHITE,
                    WHITE_SCRIM_ALPHA
                )
            return ColorUtils.compositeColors(whiteScrim, mIconColor)
        }

    open val isThemed: Boolean
        /**
         * Returns if this represents a themed icon
         */
        get() = false

    override fun setColorFilter(cf: ColorFilter?) {
        mColorFilter = cf
        updateFilter()
    }

    @Deprecated("Deprecated in Java",
        ReplaceWith("PixelFormat.TRANSLUCENT", "android.graphics.PixelFormat")
    )
    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun setAlpha(alpha: Int) {
        if (mAlpha != alpha) {
            mAlpha = alpha
            mPaint.alpha = alpha
            invalidateSelf()
            if (mBadge != null) {
                mBadge!!.alpha = alpha
            }
        }
    }

    override fun setFilterBitmap(filterBitmap: Boolean) {
        mPaint.isFilterBitmap = filterBitmap
        mPaint.isAntiAlias = filterBitmap
    }

    override fun getAlpha(): Int {
        return mAlpha
    }

    override fun getIntrinsicWidth(): Int {
        return mBitmap.width
    }

    override fun getIntrinsicHeight(): Int {
        return mBitmap.height
    }

    override fun getMinimumWidth(): Int {
        return bounds.width()
    }

    override fun getMinimumHeight(): Int {
        return bounds.height()
    }

    override fun isStateful(): Boolean {
        return true
    }

    override fun getColorFilter(): ColorFilter? {
        return mPaint.colorFilter
    }

    override fun onStateChange(state: IntArray): Boolean {
        var isPressed = false
        var isHovered = false
        for (s: Int in state) {
            if (s == android.R.attr.state_pressed) {
                isPressed = true
                break
            } else if (sFlagHoverEnabled && s == android.R.attr.state_hovered) {
                isHovered = true
                // Do not break on hovered state, as pressed state should take precedence.
            }
        }
        if (mIsPressed != isPressed || mIsHovered != isHovered) {
            if (mScaleAnimation != null) {
                mScaleAnimation!!.cancel()
            }

            val endScale: Float =
                if (isPressed) PRESSED_SCALE else (if (isHovered) HOVERED_SCALE else 1f)
            if (mScale != endScale) {
                if (isVisible) {
                    val interpolator: Interpolator =
                        if (isPressed != mIsPressed)
                            (if (isPressed) ACCEL else DEACCEL)
                        else
                            HOVER_EMPHASIZED_DECELERATE_INTERPOLATOR
                    val duration: Int =
                        if (isPressed != mIsPressed)
                            CLICK_FEEDBACK_DURATION
                        else
                            HOVER_FEEDBACK_DURATION
                    mScaleAnimation = ObjectAnimator.ofFloat(this, SCALE, endScale)
                    mScaleAnimation?.setDuration(duration.toLong())
                    mScaleAnimation?.interpolator = interpolator
                    mScaleAnimation?.start()
                } else {
                    mScale = endScale
                    invalidateSelf()
                }
            }
            mIsPressed = isPressed
            mIsHovered = isHovered
            return true
        }
        return false
    }

    protected var isDisabled: Boolean
        get() = mIsDisabled
        set(isDisabled) {
            if (mIsDisabled != isDisabled) {
                mIsDisabled = isDisabled
                updateFilter()
            }
        }

    var badge: Drawable?
        get() = mBadge
        set(badge) {
            if (mBadge != null) {
                mBadge!!.callback = null
            }
            mBadge = badge
            if (mBadge != null) {
                mBadge!!.callback = this
            }
            updateBadgeBounds(bounds)
            updateFilter()
        }

    /**
     * Updates the paint to reflect the current brightness and saturation.
     */
    protected open fun updateFilter() {
        mPaint.setColorFilter(if (mIsDisabled) getDisabledColorFilter(mDisabledAlpha) else mColorFilter)
        if (mBadge != null) {
            mBadge!!.colorFilter = colorFilter
        }
        invalidateSelf()
    }

    protected open fun newConstantState(): FastBitmapConstantState {
        return FastBitmapConstantState(mBitmap, mIconColor)
    }

    override fun getConstantState(): ConstantState {
        val cs: FastBitmapConstantState = newConstantState()
        cs.mIsDisabled = mIsDisabled
        if (mBadge != null) {
            cs.mBadgeConstantState = mBadge!!.constantState
        }
        cs.mCreationFlags = mCreationFlags
        return cs
    }

    override fun invalidateDrawable(who: Drawable) {
        if (who === mBadge) {
            invalidateSelf()
        }
    }

    override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
        if (who === mBadge) {
            scheduleSelf(what, `when`)
        }
    }

    override fun unscheduleDrawable(who: Drawable, what: Runnable) {
        unscheduleSelf(what)
    }

    protected open class FastBitmapConstantState(@JvmField val mBitmap: Bitmap, @JvmField val mIconColor: Int) : ConstantState() {
        // These are initialized later so that subclasses don't need to
        // pass everything in constructor
        var mIsDisabled: Boolean = false
        var mBadgeConstantState: ConstantState? = null

        @DrawableCreationFlags
        var mCreationFlags: Int = 0

        protected open fun createDrawable(): FastBitmapDrawable {
            return FastBitmapDrawable(mBitmap, mIconColor)
        }

        override fun newDrawable(): FastBitmapDrawable {
            val drawable: FastBitmapDrawable = createDrawable()
            drawable.isDisabled = mIsDisabled
            drawable.badge = mBadgeConstantState?.newDrawable()
            drawable.mCreationFlags = mCreationFlags
            return drawable
        }

        override fun getChangingConfigurations(): Int {
            return 0
        }
    }

    init {
        isFilterBitmap = true
    }

    companion object {
        private val ACCEL: Interpolator = AccelerateInterpolator()
        private val DEACCEL: Interpolator = DecelerateInterpolator()
        private val HOVER_EMPHASIZED_DECELERATE_INTERPOLATOR: Interpolator =
            PathInterpolator(0.05f, 0.7f, 0.1f, 1.0f)

        protected const val PRESSED_SCALE: Float = 1.1f
        protected const val HOVERED_SCALE: Float = 1.1f
        const val WHITE_SCRIM_ALPHA: Int = 138

        private const val DISABLED_DESATURATION: Float = 1f
        private const val DISABLED_BRIGHTNESS: Float = 0.5f

        const val FULLY_OPAQUE: Int = 255

        const val CLICK_FEEDBACK_DURATION: Int = 200
        const val HOVER_FEEDBACK_DURATION: Int = 300

        private var sFlagHoverEnabled: Boolean = false

        // Animator and properties for the fast bitmap drawable's scale
        protected val SCALE
                : FloatProperty<FastBitmapDrawable> =
            object : FloatProperty<FastBitmapDrawable>("scale") {
                override fun get(fastBitmapDrawable: FastBitmapDrawable): Float {
                    return fastBitmapDrawable.mScale
                }

                override fun setValue(fastBitmapDrawable: FastBitmapDrawable, value: Float) {
                    fastBitmapDrawable.mScale = value
                    fastBitmapDrawable.invalidateSelf()
                }
            }
        @JvmStatic
        val disabledColorFilter: ColorFilter
            get() = getDisabledColorFilter(
                1f
            )

        private fun getDisabledColorFilter(disabledAlpha: Float): ColorFilter {
            val tempBrightnessMatrix = ColorMatrix()
            val tempFilterMatrix = ColorMatrix()

            tempFilterMatrix.setSaturation(1f - DISABLED_DESATURATION)
            val scale: Float = 1 - DISABLED_BRIGHTNESS
            val brightnessI: Int = (255 * DISABLED_BRIGHTNESS).toInt()
            val mat: FloatArray = tempBrightnessMatrix.array
            mat[0] = scale
            mat[6] = scale
            mat[12] = scale
            mat[4] = brightnessI.toFloat()
            mat[9] = brightnessI.toFloat()
            mat[14] = brightnessI.toFloat()
            mat[18] = disabledAlpha
            tempFilterMatrix.preConcat(tempBrightnessMatrix)
            return ColorMatrixColorFilter(tempFilterMatrix)
        }

        /**
         * Sets the bounds for the badge drawable based on the main icon bounds
         */
        fun setBadgeBounds(badge: Drawable, iconBounds: Rect) {
            val size: Int = getBadgeSizeForIconSize(iconBounds.width())
            badge.setBounds(
                iconBounds.right - size, iconBounds.bottom - size,
                iconBounds.right, iconBounds.bottom
            )
        }

        @ColorInt
        fun setColorAlphaBound(color: Int, alpha: Int): Int {
            var tAlpha: Int = alpha
            if (tAlpha < 0) {
                tAlpha = 0
            } else if (tAlpha > 255) {
                tAlpha = 255
            }
            return (color and 0x00ffffff) or (tAlpha shl 24)
        }

        private const val ICON_BADGE_SCALE: Float = 0.444f

        fun getBadgeSizeForIconSize(iconSize: Int): Int {
            return (ICON_BADGE_SCALE * iconSize).toInt()
        }
    }
}