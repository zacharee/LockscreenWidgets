package tk.zwander.common.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.util.Base64
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import tk.zwander.lockscreenwidgets.App
import java.io.ByteArrayOutputStream
import java.io.IOException

@SuppressLint("DiscouragedApi")
fun Context.getRemoteDrawable(
    packageName: String,
    resource: Intent.ShortcutIconResource?
) : Bitmap? {
    val remRes = packageManager.getResourcesForApplication(packageName)

    return getRemoteDrawable(
        packageName,
        resource?.let {
            remRes.getIdentifier(
                it.packageName,
                "drawable",
                it.resourceName
            )
        } ?: 0,
        remRes
    )
}

fun Context.getRemoteDrawable(
    packageName: String,
    resourceId: Int,
    remRes: Resources = packageManager.getResourcesForApplication(packageName)
): Bitmap? {
    val defaultGetter = { packageManager.getApplicationIcon(packageName) }

    val drawable = when (resourceId) {
        0 -> defaultGetter()
        else -> {
            try {
                ResourcesCompat.getDrawable(remRes, resourceId, remRes.newTheme()) ?: defaultGetter()
            } catch (e: Resources.NotFoundException) {
                defaultGetter()
            }
        }
    }

    return drawable?.mutate()?.run {
        val width = intrinsicWidth
        val height = intrinsicHeight

        if (width <= 0 || height <= 0) {
            null
        } else {
            toBitmap().run { copy(config, false) }
        }
    }
}

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
    var image = toBitmap(width = Integer.max(1, intrinsicWidth), height = Integer.max(
        1,
        intrinsicHeight
    ), config = config)

    val width: Int = intrinsicWidth
    val height: Int = intrinsicHeight

    return if (maxHeight > 0 && maxWidth > 0 && width > 0 && height > 0) {
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

fun Bitmap.cropBitmapTransparency(): Bitmap {
    var minX = width
    var minY = height
    var maxX = -1
    var maxY = -1
    for (y in 0 until height) {
        for (x in 0 until width) {
            val alpha = getPixel(x, y) shr 24 and 255
            if (alpha > 0) // pixel is not 100% transparent
            {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
    }
    return if (maxX < minX || maxY < minY) {
        //Fully transparent, return unchanged
        this
    } else Bitmap.createBitmap(
        this,
        minX,
        minY,
        maxX - minX + 1,
        maxY - minY + 1
    )
}

fun String.textAsBitmap(textSize: Float, textColor: Int): Bitmap? {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.textSize = textSize
    paint.color = textColor
    paint.textAlign = Paint.Align.LEFT
    val baseline: Float = -paint.ascent() // ascent() is negative
    val width = (paint.measureText(this) + 0.5f).toInt() // round
    val height = (baseline + paint.descent() + 0.5f).toInt()
    val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(image)
    canvas.drawText(this, 0f, baseline, paint)
    return image
}
