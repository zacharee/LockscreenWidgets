package tk.zwander.common.compose.settings

import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import tk.zwander.common.compose.util.rememberPreferenceState
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.R
import kotlin.math.max
import kotlin.math.min

open class ScaledSeekBarPreference(
    title: String,
    summary: String?,
    key: String,
    defaultValue: Int,
    val minValue: Int,
    val maxValue: Int,
    val scale: Float,
    icon: Drawable? = null,
) : BasePreference<Int>(
    title = title,
    summary = summary,
    key = key,
    icon = icon,
    widget = null,
    widgetPosition = WidgetPosition.BOTTOM,
    defaultValue = defaultValue,
) {
    @Composable
    override fun Render(modifier: Modifier) {
        ScaledSeekBarPreference(
            title = title,
            summary = summary,
            modifier = modifier,
            defaultValue = defaultValue,
            minValue = minValue,
            maxValue = maxValue,
            scale = scale,
            key = key,
            icon = rememberDrawablePainter(icon),
        )
    }
}

@Composable
fun ScaledSeekBarPreference(
    title: String,
    summary: String?,
    defaultValue: Int,
    minValue: Int,
    maxValue: Int,
    scale: Float,
    key: String,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
) {
    val context = LocalContext.current
    var value by rememberPreferenceState(
        key = key,
        value = { context.prefManager.getInt(key, defaultValue) * scale },
        onChanged = { k, v -> context.prefManager.putInt(k, (v / scale).toInt()) },
    )

    SeekBarPreference(
        title = title,
        summary = summary,
        minValue = minValue * scale,
        maxValue = maxValue * scale,
        defaultValue = defaultValue * scale,
        modifier = modifier,
        icon = icon,
        value = value,
        onValueChanged = { value = it },
    )
}

@Composable
fun SeekBarPreference(
    title: String,
    summary: String?,
    value: Float,
    onValueChanged: (Float) -> Unit,
    defaultValue: Float,
    minValue: Float,
    maxValue: Float,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
) {
    BasePreferenceLayout(
        title = title,
        summary = summary,
        modifier = modifier,
        icon = icon,
        widgetPosition = WidgetPosition.BOTTOM,
        widget = {
            SeekBarLayout(
                value = value,
                onValueChanged = onValueChanged,
                modifier = Modifier.fillMaxWidth(),
                defaultValue = defaultValue,
                minValue = minValue,
                maxValue = maxValue,
            )
        },
    )
}

@Composable
private fun SeekBarLayout(
    value: Float,
    onValueChanged: (Float) -> Unit,
    defaultValue: Float,
    minValue: Float,
    maxValue: Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = { onValueChanged(defaultValue) },
        ) {
            Icon(
                painter = painterResource(R.drawable.undo),
                contentDescription = stringResource(R.string.reset),
            )
        }

        Slider(
            value = value,
            onValueChange = onValueChanged,
            valueRange = minValue..maxValue,
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(
                onClick = { onValueChanged(min(maxValue, value + 0.01f)) },
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_up),
                    contentDescription = stringResource(R.string.increase),
                )
            }

            IconButton(
                onClick = { onValueChanged(max(minValue, value - 0.1f)) },
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_up),
                    contentDescription = stringResource(R.string.decrease),
                    modifier = Modifier.rotate(180f),
                )
            }
        }
    }
}
