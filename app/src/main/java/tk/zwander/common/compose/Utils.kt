package tk.zwander.common.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import tk.zwander.common.util.FrameSizeAndPosition
import tk.zwander.common.util.LSDisplay

@Composable
fun LSDisplay.rememberScaledFrameSize(
    frameId: Int,
    desiredHeight: Dp = 48.dp,
): DpSize {
    val context = LocalContext.current
    val frameSizeAndPosition = remember(context) {
        FrameSizeAndPosition.getInstance(context)
    }
    val size = remember(this) {
        frameSizeAndPosition.getSizeForType(
            type = FrameSizeAndPosition.FrameType.SecondaryLockscreen.Portrait(frameId),
            display = this,
        )
    }

    return rememberScaledSize(
        originalSize = Size(size.x, size.y),
        desiredHeight = desiredHeight,

    )
}

@Composable
fun rememberScaledSize(
    originalSize: Size,
    desiredHeight: Dp = 48.dp,
): DpSize {
    val density = LocalDensity.current

    return remember(density, originalSize, desiredHeight) {
        with(density) {
            val actualHeight = originalSize.height.toFloat().toDp()

            val heightRatio = desiredHeight / actualHeight
            val scaledWidth = (originalSize.width.toFloat() * heightRatio).toDp()

            DpSize(scaledWidth, desiredHeight)
        }
    }
}

data class Size(
    val width: Number,
    val height: Number,
)
