package tk.zwander.lockscreenwidgets.tiles

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import tk.zwander.lockscreenwidgets.util.prefManager

/**
 * A QS tile to enable or disable the feature
 * to show the widget frame in the expanded
 * notification center.
 */
@RequiresApi(Build.VERSION_CODES.N)
class NCTile : TileService() {
    override fun onStartListening() {
        updateState()
    }

    override fun onClick() {
        prefManager.showInNotificationCenter = !prefManager.showInNotificationCenter
        updateState()
    }

    private fun updateState() {
        qsTile?.apply {
            state = (if (prefManager.showInNotificationCenter) Tile.STATE_ACTIVE
                    else Tile.STATE_INACTIVE)
            updateTile()
        }
    }
}