package tk.zwander.common.compose.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.agrawalsuneet.dotsloader.loaders.AllianceLoader

@Composable
fun Loader(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val dp30 = with (LocalDensity.current) {
            30.dp.roundToPx()
        }
        val dp8 = with (LocalDensity.current) {
            8.dp.roundToPx()
        }
        val dotColor = MaterialTheme.colorScheme.secondary.toArgb()

        AndroidView(
            factory = { ctx ->
                AllianceLoader(
                    context = ctx,
                    dotsRadius = dp30,
                    distanceMultiplier = 4,
                    drawOnlyStroke = true,
                    strokeWidth = dp8,
                    firsDotColor = dotColor,
                    secondDotColor = dotColor,
                    thirdDotColor = dotColor
                )
            }
        )
    }
}
