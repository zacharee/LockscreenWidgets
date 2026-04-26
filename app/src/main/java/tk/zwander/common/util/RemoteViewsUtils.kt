package tk.zwander.common.util

import android.content.res.ColorStateList
import android.os.Build
import android.widget.RemoteViews
import androidx.core.widget.RemoteViewsCompat.setProgressBarIndeterminateTintList
import tk.zwander.lockscreenwidgets.R

fun RemoteViews.setProgressIndeterminateTintListCompat(
    viewId: Int,
    tint: ColorStateList?,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        setProgressBarIndeterminateTintList(viewId, tint)
    } else {
        RemoteViews::class.java
            .getMethod(
                "setProgressIndeterminateTintList",
                Int::class.java,
                ColorStateList::class.java,
            )
            .invoke(
                this,
                R.id.page_dot_active,
                tint,
            )
    }
}
