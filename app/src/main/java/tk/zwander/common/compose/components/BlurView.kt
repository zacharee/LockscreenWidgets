package tk.zwander.common.compose.components

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import tk.zwander.common.compose.util.rememberBooleanPreferenceState
import tk.zwander.common.compose.util.rememberPreferenceState
import tk.zwander.common.drawable.BackgroundBlurDrawableCompat
import tk.zwander.common.util.logUtils
import tk.zwander.common.util.prefManager
import tk.zwander.common.util.windowManager
import java.util.function.Consumer

@Composable
fun BlurView(
    blurKey: String,
    blurAmountKey: String,
    params: WindowManager.LayoutParams,
    updateWindow: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadiusKey: String? = null,
) {
    val view = LocalView.current
    val context = LocalContext.current
    val wm = remember {
        context.windowManager
    }
    var currentDrawable by remember {
        mutableStateOf<BackgroundBlurDrawableCompat?>(null)
    }
    var crossBlurEnabled by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                wm.isCrossWindowBlurEnabled
            } else {
                false
            },
        )
    }

    val shouldBlur by rememberBooleanPreferenceState(
        key = blurKey,
    )
    val blurAmount by rememberPreferenceState(
        key = blurAmountKey,
        value = { context.prefManager.getInt(blurAmountKey, 100) },
        onChanged = { _, _ -> },
    )
    val cornerRadius by if (cornerRadiusKey != null) {
        rememberPreferenceState(
            key = cornerRadiusKey,
            value = { context.prefManager.getInt(cornerRadiusKey, 20) / 10f },
            onChanged = { _, _ -> },
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    DisposableEffect(null) {
        val crossBlurEnabledListener = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Consumer<Boolean> {
                crossBlurEnabled = it
                if (!it) {
                    currentDrawable = null
                }
            }
        } else {
            null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            crossBlurEnabledListener?.let { wm.addCrossWindowBlurEnabledListener(it) }
        }

        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                crossBlurEnabledListener?.let { wm.removeCrossWindowBlurEnabledListener(it) }
            }
            currentDrawable = null
        }
    }

    LaunchedEffect(
        crossBlurEnabled,
        shouldBlur,
        blurAmount,
        cornerRadius,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            var newBlurDrawable: BackgroundBlurDrawableCompat? = currentDrawable

            if (blurAmount > 0 &&
                shouldBlur &&
                crossBlurEnabled &&
                view.rootView.viewRootImpl.isHardwareEnabled
            ) {
                if (newBlurDrawable == null) {
                    context.logUtils.debugLog("Creating BackgroundBlurDrawableCompat.", null)
                    newBlurDrawable = BackgroundBlurDrawableCompat(view.rootView.viewRootImpl)
                }
            } else {
                newBlurDrawable = null
            }

            newBlurDrawable?.setBlurRadius(blurAmount)
            newBlurDrawable?.setCornerRadius(cornerRadius)

            context.logUtils.debugLog("Setting blur drawable $newBlurDrawable on target view with current background ${currentDrawable}.", null)

            currentDrawable = newBlurDrawable
        } else {
            val f = try {
                params::class.java.getDeclaredField("samsungFlags")
            } catch (_: Exception) {
                return@LaunchedEffect
            }

            if (shouldBlur) {
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND

                f?.set(params, f.get(params) as Int or 64)
                params.dimAmount = blurAmount / 1000f
            } else {
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND.inv()

                f?.set(params, f.get(params) as Int and 64.inv())
                params.dimAmount = 0.0f
            }

            updateWindow()
        }
    }

    if (shouldBlur && currentDrawable != null) {
        Image(
            painter = rememberDrawablePainter(currentDrawable),
            contentDescription = null,
            modifier = modifier,
        )
    }
}
