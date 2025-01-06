package tk.zwander.common.iconpacks

import androidx.annotation.IntDef

object BitmapInfo {
    const val FLAG_THEMED: Int = 1
    const val FLAG_NO_BADGE: Int = 1 shl 1
    const val FLAG_SKIP_USER_BADGE: Int = 1 shl 2

    @IntDef(
        flag = true, value = [FLAG_THEMED, FLAG_NO_BADGE, FLAG_SKIP_USER_BADGE]
    )
    annotation class DrawableCreationFlags
}