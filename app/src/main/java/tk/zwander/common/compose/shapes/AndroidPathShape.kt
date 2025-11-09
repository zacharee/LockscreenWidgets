package tk.zwander.common.compose.shapes

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import tk.zwander.common.util.peekLogUtils

class AndroidPathShape(
    private val displayPath: android.graphics.Path,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val displayPath = android.graphics.Path(displayPath)

        peekLogUtils?.normalLog("Got path", null)

        return displayPath.asComposePath().let { composePath ->
            val bounds = composePath.getBounds()

            val desiredHeight = size.height
            val currentHeight = bounds.bottom - bounds.top

            val scale = desiredHeight / currentHeight

            peekLogUtils?.normalLog("scale ${scale}, $desiredHeight, $currentHeight", null)

            composePath.transform(
                matrix = Matrix().apply {
                    scale(scale, scale)
                },
            )
            Outline.Generic(composePath)
        }
    }
}
