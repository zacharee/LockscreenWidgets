package tk.zwander.common.iconpacks;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.core.util.Supplier;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/**
 * Wrapper over {@link AdaptiveIconDrawable} to intercept icon flattening logic for dynamic
 * clock icons
 */
@TargetApi(Build.VERSION_CODES.O)
public class ClockDrawableWrapper extends AdaptiveIconDrawable {

    public static boolean sRunningInTest = false;

    private static final String TAG = "ClockDrawableWrapper";

    private static final boolean DISABLE_SECONDS = true;
    private static final int NO_COLOR = -1;

    // Time after which the clock icon should check for an update. The actual invalidate
    // will only happen in case of any change.
    public static final long TICK_MS = DISABLE_SECONDS ? TimeUnit.MINUTES.toMillis(1) : 200L;

    private static final String LAUNCHER_PACKAGE = "com.android.launcher3";
    private static final String ROUND_ICON_METADATA_KEY = LAUNCHER_PACKAGE
            + ".LEVEL_PER_TICK_ICON_ROUND";
    private static final String HOUR_INDEX_METADATA_KEY = LAUNCHER_PACKAGE + ".HOUR_LAYER_INDEX";
    private static final String MINUTE_INDEX_METADATA_KEY = LAUNCHER_PACKAGE
            + ".MINUTE_LAYER_INDEX";
    private static final String SECOND_INDEX_METADATA_KEY = LAUNCHER_PACKAGE
            + ".SECOND_LAYER_INDEX";
    private static final String DEFAULT_HOUR_METADATA_KEY = LAUNCHER_PACKAGE
            + ".DEFAULT_HOUR";
    private static final String DEFAULT_MINUTE_METADATA_KEY = LAUNCHER_PACKAGE
            + ".DEFAULT_MINUTE";
    private static final String DEFAULT_SECOND_METADATA_KEY = LAUNCHER_PACKAGE
            + ".DEFAULT_SECOND";

    /* Number of levels to jump per second for the second hand */
    private static final int LEVELS_PER_SECOND = 10;

    public static final int INVALID_VALUE = -1;

    private int mTargetSdkVersion;

    private final AnimationInfo mAnimationInfo = new AnimationInfo();
    private AnimationInfo mThemeInfo = null;

    private ClockDrawableWrapper(AdaptiveIconDrawable base) {
        super(base.getBackground(), base.getForeground());
    }

    @Override
    public Drawable getMonochrome() {
        if (mThemeInfo == null) {
            return null;
        }
        Drawable d = mThemeInfo.baseDrawableState.newDrawable().mutate();
        if (d instanceof AdaptiveIconDrawable) {
            Drawable mono = ((AdaptiveIconDrawable) d).getForeground();
            mThemeInfo.applyTime(Calendar.getInstance(), (LayerDrawable) mono);
            return mono;
        }
        return null;
    }

    public static ClockDrawableWrapper forMeta(int targetSdkVersion,
                                               @NonNull ClockMetadata metadata, Supplier<Drawable> drawableProvider) {
        Drawable drawable = drawableProvider.get().mutate();
        if (!(drawable instanceof AdaptiveIconDrawable)) {
            return null;
        }

        ClockDrawableWrapper wrapper =
                new ClockDrawableWrapper((AdaptiveIconDrawable) drawable);
        wrapper.mTargetSdkVersion = targetSdkVersion;
        AnimationInfo info = wrapper.mAnimationInfo;

        info.baseDrawableState = drawable.getConstantState();

        info.hourLayerIndex = metadata.getHourLayerIndex();
        info.minuteLayerIndex = metadata.getMinuteLayerIndex();
        info.secondLayerIndex = metadata.getSecondLayerIndex();

        info.defaultHour = metadata.getDefaultHour();
        info.defaultMinute = metadata.getDefaultMinute();
        info.defaultSecond = metadata.getDefaultSecond();

        LayerDrawable foreground = (LayerDrawable) wrapper.getForeground();
        int layerCount = foreground.getNumberOfLayers();
        if (info.hourLayerIndex < 0 || info.hourLayerIndex >= layerCount) {
            info.hourLayerIndex = INVALID_VALUE;
        }
        if (info.minuteLayerIndex < 0 || info.minuteLayerIndex >= layerCount) {
            info.minuteLayerIndex = INVALID_VALUE;
        }
        if (info.secondLayerIndex < 0 || info.secondLayerIndex >= layerCount) {
            info.secondLayerIndex = INVALID_VALUE;
        } else if (DISABLE_SECONDS) {
            foreground.setDrawable(info.secondLayerIndex, null);
            info.secondLayerIndex = INVALID_VALUE;
        }
        info.applyTime(Calendar.getInstance(), foreground);
        return wrapper;
    }

    private static class AnimationInfo {

        public ConstantState baseDrawableState;

        public int hourLayerIndex;
        public int minuteLayerIndex;
        public int secondLayerIndex;
        public int defaultHour;
        public int defaultMinute;
        public int defaultSecond;

        public AnimationInfo copyForIcon(Drawable icon) {
            AnimationInfo result = new AnimationInfo();
            result.baseDrawableState = icon.getConstantState();
            result.defaultHour = defaultHour;
            result.defaultMinute = defaultMinute;
            result.defaultSecond = defaultSecond;
            result.hourLayerIndex = hourLayerIndex;
            result.minuteLayerIndex = minuteLayerIndex;
            result.secondLayerIndex = secondLayerIndex;
            return result;
        }

        boolean applyTime(Calendar time, LayerDrawable foregroundDrawable) {
            time.setTimeInMillis(System.currentTimeMillis());

            // We need to rotate by the difference from the default time if one is specified.
            int convertedHour = (time.get(Calendar.HOUR) + (12 - defaultHour)) % 12;
            int convertedMinute = (time.get(Calendar.MINUTE) + (60 - defaultMinute)) % 60;
            int convertedSecond = (time.get(Calendar.SECOND) + (60 - defaultSecond)) % 60;

            boolean invalidate = false;
            if (hourLayerIndex != INVALID_VALUE) {
                final Drawable hour = foregroundDrawable.getDrawable(hourLayerIndex);
                if (hour.setLevel(convertedHour * 60 + time.get(Calendar.MINUTE))) {
                    invalidate = true;
                }
            }

            if (minuteLayerIndex != INVALID_VALUE) {
                final Drawable minute = foregroundDrawable.getDrawable(minuteLayerIndex);
                if (minute.setLevel(time.get(Calendar.HOUR) * 60 + convertedMinute)) {
                    invalidate = true;
                }
            }

            if (secondLayerIndex != INVALID_VALUE) {
                final Drawable second = foregroundDrawable.getDrawable(secondLayerIndex);
                if (second.setLevel(convertedSecond * LEVELS_PER_SECOND)) {
                    invalidate = true;
                }
            }

            return invalidate;
        }
    }

    private static class ClockIconDrawable extends FastBitmapDrawable implements Runnable {

        private final Calendar mTime = Calendar.getInstance();

        private final float mBoundsOffset;
        private final AnimationInfo mAnimInfo;

        private final Bitmap mBG;
        private final Paint mBgPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        private final ColorFilter mBgFilter;
        private final int mThemedFgColor;

        private final AdaptiveIconDrawable mFullDrawable;
        private final LayerDrawable mFG;
        private final float mCanvasScale;

        ClockIconDrawable(ClockConstantState cs) {
            super(cs.mBitmap, cs.mIconColor);
            mBoundsOffset = cs.mBoundsOffset;
            mAnimInfo = cs.mAnimInfo;

            mBG = cs.mBG;
            mBgFilter = cs.mBgFilter;
            mBgPaint.setColorFilter(cs.mBgFilter);
            mThemedFgColor = cs.mThemedFgColor;

            mFullDrawable =
                    (AdaptiveIconDrawable) mAnimInfo.baseDrawableState.newDrawable().mutate();
            mFG = (LayerDrawable) mFullDrawable.getForeground();

            // Time needs to be applied here since drawInternal is NOT guaranteed to be called
            // before this foreground drawable is shown on the screen.
            mAnimInfo.applyTime(mTime, mFG);
            mCanvasScale = 1 - 2 * mBoundsOffset;
        }

        @Override
        public void setAlpha(int alpha) {
            super.setAlpha(alpha);
            mBgPaint.setAlpha(alpha);
            mFG.setAlpha(alpha);
        }

        @Override
        protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);

            // b/211896569 AdaptiveIcon does not work properly when bounds
            // are not aligned to top/left corner
            mFullDrawable.setBounds(0, 0, bounds.width(), bounds.height());
        }

        @Override
        public void drawInternal(Canvas canvas, Rect bounds) {
            if (mAnimInfo == null) {
                super.drawInternal(canvas, bounds);
                return;
            }
            canvas.drawBitmap(mBG, null, bounds, mBgPaint);

            // prepare and draw the foreground
            mAnimInfo.applyTime(mTime, mFG);
            int saveCount = canvas.save();
            canvas.translate(bounds.left, bounds.top);
            canvas.scale(mCanvasScale, mCanvasScale, bounds.width() / 2, bounds.height() / 2);
            canvas.clipPath(mFullDrawable.getIconMask());
            mFG.draw(canvas);
            canvas.restoreToCount(saveCount);

            reschedule();
        }

        @Override
        public boolean isThemed() {
            return mBgPaint.getColorFilter() != null;
        }

        @Override
        protected void updateFilter() {
            super.updateFilter();
            int alpha = mIsDisabled ? (int) (mDisabledAlpha * FULLY_OPAQUE) : FULLY_OPAQUE;
            setAlpha(alpha);
            mBgPaint.setColorFilter(mIsDisabled ? getDisabledColorFilter() : mBgFilter);
            mFG.setColorFilter(mIsDisabled ? getDisabledColorFilter() : null);
        }

        @Override
        public int getIconColor() {
            return isThemed() ? mThemedFgColor : super.getIconColor();
        }

        @Override
        public void run() {
            if (mAnimInfo.applyTime(mTime, mFG)) {
                invalidateSelf();
            } else {
                reschedule();
            }
        }

        @Override
        public boolean setVisible(boolean visible, boolean restart) {
            boolean result = super.setVisible(visible, restart);
            if (visible) {
                reschedule();
            } else {
                unscheduleSelf(this);
            }
            return result;
        }

        private void reschedule() {
            if (!isVisible()) {
                return;
            }
            unscheduleSelf(this);
            final long upTime = SystemClock.uptimeMillis();
            final long step = TICK_MS; /* tick every 200 ms */
            scheduleSelf(this, upTime - ((upTime % step)) + step);
        }

        @Override
        public FastBitmapConstantState newConstantState() {
            return new ClockConstantState(mBitmap, mIconColor, mThemedFgColor, mBoundsOffset,
                    mAnimInfo, mBG, mBgPaint.getColorFilter());
        }

        private static class ClockConstantState extends FastBitmapConstantState {

            private final float mBoundsOffset;
            private final AnimationInfo mAnimInfo;
            private final Bitmap mBG;
            private final ColorFilter mBgFilter;
            private final int mThemedFgColor;

            ClockConstantState(Bitmap bitmap, int color, int themedFgColor,
                    float boundsOffset, AnimationInfo animInfo, Bitmap bg, ColorFilter bgFilter) {
                super(bitmap, color);
                mBoundsOffset = boundsOffset;
                mAnimInfo = animInfo;
                mBG = bg;
                mBgFilter = bgFilter;
                mThemedFgColor = themedFgColor;
            }

            @Override
            public FastBitmapDrawable createDrawable() {
                return new ClockIconDrawable(this);
            }
        }
    }
}