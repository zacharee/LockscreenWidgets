package tk.zwander.lockscreenwidgets.tiles

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import tk.zwander.lockscreenwidgets.util.prefManager

/**
 * A QS tile to enable or disable the widget frame.
 */
@RequiresApi(Build.VERSION_CODES.N)
class EnableDisableTile : TileService() {
    override fun onStartListening() {
        updateState()
    }

    override fun onClick() {
        prefManager.widgetFrameEnabled = !prefManager.widgetFrameEnabled
        updateState()
    }

    fun updateState() {
        qsTile?.apply {
            state = (if (prefManager.widgetFrameEnabled) Tile.STATE_ACTIVE
                    else Tile.STATE_INACTIVE)
            updateTile()
        }
    }
}