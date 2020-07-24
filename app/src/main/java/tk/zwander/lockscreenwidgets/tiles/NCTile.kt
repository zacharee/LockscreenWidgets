package tk.zwander.lockscreenwidgets.tiles

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import tk.zwander.lockscreenwidgets.util.prefManager

@RequiresApi(Build.VERSION_CODES.N)
class NCTile : TileService() {
    override fun onStartListening() {
        updateState()
    }

    override fun onClick() {
        prefManager.showInNotificationCenter = !prefManager.showInNotificationCenter
        updateState()
    }

    fun updateState() {
        qsTile?.apply {
            state = (if (prefManager.showInNotificationCenter) Tile.STATE_ACTIVE
                    else Tile.STATE_INACTIVE)
            updateTile()
        }
    }
}