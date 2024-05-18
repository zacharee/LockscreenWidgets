package tk.zwander.common.compose.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.ocnyang.compose_loading.OppositeArc

@Composable
fun Loader(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val dp8 = with (LocalDensity.current) {
            8.dp.roundToPx()
        }

        OppositeArc(
            modifier = Modifier.fillMaxWidth(0.5f)
                .aspectRatio(1f),
            strokeWidth = dp8.toFloat(),
            colors = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.secondary,
                MaterialTheme.colorScheme.tertiary,
            ),
        )
    }
}
