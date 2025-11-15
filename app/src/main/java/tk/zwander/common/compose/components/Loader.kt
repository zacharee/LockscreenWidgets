package tk.zwander.common.compose.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ehsanmsz.mszprogressindicator.progressindicator.BallTrianglePathProgressIndicator

@Composable
fun Loader(
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(0.5f)
            .aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        val constraints = this

        BallTrianglePathProgressIndicator(
            modifier = Modifier,
            strokeWidth = 8.dp,
            ballDiameter = 48.dp,
            width = constraints.maxWidth / 2f,
            height = constraints.maxHeight / 2f,
        )
    }
}
