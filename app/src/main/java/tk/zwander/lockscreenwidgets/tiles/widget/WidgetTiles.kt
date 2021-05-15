package tk.zwander.lockscreenwidgets.tiles.widget

import android.os.Build
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.N)
class WidgetTileOne : BaseWidgetTile() {
    override val tileId = 1
}

@RequiresApi(Build.VERSION_CODES.N)
class WidgetTileTwo : BaseWidgetTile() {
    override val tileId = 2
}

@RequiresApi(Build.VERSION_CODES.N)
class WidgetTileThree : BaseWidgetTile() {
    override val tileId = 3
}

@RequiresApi(Build.VERSION_CODES.N)
class WidgetTileFour : BaseWidgetTile() {
    override val tileId = 4
}

@RequiresApi(Build.VERSION_CODES.N)
class WidgetTileFive : BaseWidgetTile() {
    override val tileId = 5
}