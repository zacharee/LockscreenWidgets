package tk.zwander.common.compose.components

import android.graphics.Bitmap
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import tk.zwander.common.compose.util.rememberPreferenceState
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.R

@Composable
fun ShortcutItemLayout(
    cornerRadiusKey: String,
    icon: Bitmap?,
    name: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val widgetCornerRadius by rememberPreferenceState(
        key = cornerRadiusKey,
        value = {
            (context.prefManager.getInt(
                it,
                context.resources.getInteger(R.integer.def_corner_radius_dp_scaled_10x),
            ) / 10f).dp
        },
    )
    val animatedCornerRadius by animateDpAsState(widgetCornerRadius)

    Card(
        modifier = modifier,
        colors = CardDefaults.outlinedCardColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
        ),
        elevation = CardDefaults.outlinedCardElevation(),
        shape = RoundedCornerShape(animatedCornerRadius),
        onClick = onClick,
    ) {
        Image(
            bitmap = icon?.asImageBitmap() ?: ImageBitmap(1, 1),
            contentDescription = stringResource(R.string.shortcut_icon),
            modifier = Modifier.align(Alignment.CenterHorizontally)
                .weight(1f)
                .padding(top = 8.dp, start = 8.dp, end = 8.dp),
        )

        name?.let {
            Text(
                text = name,
                modifier = Modifier.align(Alignment.CenterHorizontally)
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}
