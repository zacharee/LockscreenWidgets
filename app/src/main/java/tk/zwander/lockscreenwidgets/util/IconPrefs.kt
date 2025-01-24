package tk.zwander.lockscreenwidgets.util

import android.content.Context
import android.graphics.Bitmap
import tk.zwander.common.util.base64ToBitmap
import tk.zwander.common.util.prefManager
import tk.zwander.common.util.toBase64

object IconPrefs {
    private const val BASE_ICON_KEY = "widget_icon_for_id_"

    fun getIconForWidget(context: Context, id: Int): Bitmap? {
        return context.prefManager.getString(generateIconKey(id), null)?.base64ToBitmap()
    }

    fun setIconForWidget(context: Context, id: Int, icon: Bitmap?) {
        setIconForWidget(context, id, icon?.toBase64())
    }

    fun setIconForWidget(context: Context, id: Int, icon: String?) {
        context.prefManager.putString(generateIconKey(id), icon)
    }

    fun removeIcon(context: Context, id: Int) {
        context.prefManager.remove(generateIconKey(id))
    }

    fun generateIconKey(id: Int): String {
        return BASE_ICON_KEY + id
    }
}
