package tk.zwander.lockscreenwidgets.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.util.Base64
import androidx.core.graphics.drawable.toBitmap
import com.android.internal.R.id.image
import tk.zwander.lockscreenwidgets.App
import java.io.ByteArrayOutputStream
import java.io.IOException


fun Bitmap?.toByteArray(): ByteArray? {
    if (this == null) return null

    val size: Int = width * height * 4
    val out = ByteArrayOutputStream(size)
    return try {
        compress(Bitmap.CompressFormat.PNG, 100, out)
        out.flush()
        out.close()
        out.toByteArray()
    } catch (e: IOException) {
        App.globalContext?.logUtils?.normalLog("Could not write bitmap", e)
        null
    }
}

fun Bitmap?.toBase64(): String? {
    return toByteArray()?.toBase64()
}

fun ByteArray?.toBase64(): String? {
    if (this == null) return null

    return Base64.encodeToString(this, 0, size, Base64.DEFAULT)
}

fun ByteArray?.toBitmap(): Bitmap? {
    if (this == null) return null

    return BitmapFactory.decodeByteArray(this, 0, size)
}

fun String?.base64ToByteArray(): ByteArray? {
    if (this == null) return null

    return Base64.decode(this, Base64.DEFAULT)
}

fun String?.base64ToBitmap(): Bitmap? {
    return base64ToByteArray()?.toBitmap()
}

fun Drawable.toBitmap(maxWidth: Int = intrinsicWidth, maxHeight: Int = intrinsicHeight, config: Bitmap.Config? = null): Bitmap {
    var image = toBitmap(width = intrinsicWidth, height = intrinsicHeight, config = config)

    return if (maxHeight > 0 && maxWidth > 0) {
        val width: Int = intrinsicWidth
        val height: Int = intrinsicHeight
        val ratioBitmap = width.toFloat() / height.toFloat()
        val ratioMax = maxWidth.toFloat() / maxHeight.toFloat()
        var finalWidth = maxWidth
        var finalHeight = maxHeight
        if (ratioMax > ratioBitmap) {
            finalWidth = (maxHeight.toFloat() * ratioBitmap).toInt()
        } else {
            finalHeight = (maxWidth.toFloat() / ratioBitmap).toInt()
        }
        image = Bitmap.createScaledBitmap(image, finalWidth, finalHeight, true)
        image
    } else {
        image
    }
}