package tk.zwander.common.util

import android.app.ActivityOptions
import android.app.Application
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.hardware.display.DisplayManagerGlobal
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Display
import android.widget.AbsListView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import androidx.core.net.toUri
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentSkipListMap


/**
 * Various utility functions
 */

//A global reference to the main-thread Handler
val mainHandler = Handler(Looper.getMainLooper())

//Convenience method to check if debug logging is enabled
val Context.isDebug: Boolean
    get() = prefManager.debugLog

//Safely launch a URL.
//If no matching Activity is found, silently fail.
fun Context.launchUrl(url: String) {
    try {
        val browserIntent =
            Intent(Intent.ACTION_VIEW, url.toUri())
        startActivity(browserIntent)
    } catch (e: Exception) {
        logUtils.debugLog("Unable to launch URL", e)
    }
}

//Safely start an email draft.
//If no matching email client is found, silently fail.
fun Context.launchEmail(to: String, subject: String) {
    try {
        val intent = Intent(Intent.ACTION_SENDTO)
        intent.setDataAndType("mailto:${Uri.encode(to)}?subject=${Uri.encode(subject)}".toUri(), "text/plain")

        startActivity(intent)
    } catch (e: Exception) {
        logUtils.debugLog("Unable to launch email", e)
    }
}

//Take an integer and make it even.
//If the integer == 0, return itself (0).
//If the integer is 1, return 2.
//If the integer is -1, return -2.
//If the integer is even, return itself.
//If the integer is odd and negative, return itself - 1
//If the integer is odd and positive, return itself + 1
@Suppress("KotlinConstantConditions")
fun Int.makeEven(): Int {
    return when {
        this == 0 -> 0
        this == 1 -> 2
        this == -1 -> -2
        this % 2 == 0 -> this
        else -> this + if (this < 0) -1 else 1
    }
}

suspend inline fun <T> Collection<T>.forEachParallel(crossinline action: suspend CoroutineScope.(T) -> Unit) {
    coroutineScope {
        val awaits = ArrayList<Deferred<*>>(size)

        forEach { awaits.add(async { action(it) }) }

        awaits.awaitAll()
    }
}

suspend inline fun <T, S> Collection<T>.mapIndexedParallel(crossinline action: suspend CoroutineScope.(Int, T) -> S): List<S> {
    return coroutineScope {
        val awaits = ArrayList<Deferred<*>>(size)
        val results = ConcurrentSkipListMap<Int, S>()

        forEachIndexed { index, item -> awaits.add(async { results[index] = action(index, item) }) }

        awaits.awaitAll()

        results.values.toList()
    }
}

val Context.safeApplicationContext: Context
    get() = this as? Application ?: applicationContext

fun AppWidgetProviderInfo.loadPreviewOrIconDrawable(context: Context, density: Int = 0): Drawable? {
    return (loadPreviewImage(context, density) ?: loadIcon(context, density))
}

fun AppWidgetProviderInfo.loadPreviewOrIcon(context: Context, density: Int = 0, maxSize: Dp = 128.dp): Bitmap? {
    return loadPreviewOrIconDrawable(context, density)?.toSafeBitmap(context.density, maxSize = maxSize)
}

fun AppWidgetProviderInfo.createPersistablePreviewBitmap(context: Context): String? {
    return loadPreviewOrIcon(context, maxSize = 128.dp)?.toBase64()
}

val Context.defaultDisplayCompat: Display
    get() {
        val displayId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            displayNoVerify?.displayId ?: Display.DEFAULT_DISPLAY
        } else {
            Display.DEFAULT_DISPLAY
        }

        return DisplayManagerGlobal.getInstance().getRealDisplay(displayId)
    }

fun Context.vibrate(duration: Long = 50L) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(duration, 100))
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(duration)
    }
}

val AbsListView.verticalScrollOffset: Int
    get() {
        return AbsListView::class.java.getDeclaredMethod("computeVerticalScrollOffset")
            .apply { isAccessible = true }
            .invoke(this) as Int
    }

val ActivityOptionsCompat.internalActivityOptions: ActivityOptions?
    get() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return null
        }

        return this::class.java.getDeclaredField("mActivityOptions")
            .apply { isAccessible = true }
            .get(this) as? ActivityOptions
    }

fun Throwable.stringify(): String {
    return GsonBuilder().create().toJson(this)
}
