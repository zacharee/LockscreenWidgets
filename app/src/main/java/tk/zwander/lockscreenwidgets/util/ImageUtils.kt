package tk.zwander.lockscreenwidgets.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
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
        Log.w("LockscreenWidgets", "Could not write bitmap")
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