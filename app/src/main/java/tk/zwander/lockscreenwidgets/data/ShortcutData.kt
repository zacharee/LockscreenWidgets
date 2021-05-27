package tk.zwander.lockscreenwidgets.data

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ShortcutData(
    var label: String?,
    var iconRes: Intent.ShortcutIconResource?,
    var activityInfo: ActivityInfo?
) : Parcelable {
    override fun describeContents(): Int {
        return 0
    }
}