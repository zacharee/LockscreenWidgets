package tk.zwander.common.compose.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tk.zwander.common.compose.util.rememberPreferenceState
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.R
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.log
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

open class SeekBarPreference(
    title: @Composable () -> String,
    summary: @Composable () -> String?,
    key: @Composable () -> String,
    defaultValue: @Composable () -> Int,
    val minValue: @Composable () -> Int,
    val maxValue: @Composable () -> Int,
    val scale: @Composable () -> Double,
    icon: @Composable () -> Painter? = { null },
    val unit: @Composable () -> String? = { null },
    val increment: @Composable () -> Int = { 1 },
    enabled: @Composable () -> Boolean = { true },
    visible: @Composable () -> Boolean = { true },
) : BasePreference<Int>(
    title = title,
    summary = summary,
    key = key,
    icon = icon,
    widget = null,
    widgetPosition = { WidgetPosition.BOTTOM },
    defaultValue = defaultValue,
    enabled = enabled,
    visible = visible,
) {
    @Composable
    override fun Render(modifier: Modifier) {
        SeekBarPreference(
            title = title(),
            summary = summary(),
            modifier = modifier,
            defaultValue = defaultValue(),
            minValue = minValue(),
            maxValue = maxValue(),
            scale = scale(),
            key = key(),
            icon = icon(),
            unit = unit(),
            increment = increment(),
            enabled = enabled(),
        )
    }
}

@Composable
fun SeekBarPreference(
    title: String,
    summary: String?,
    defaultValue: Int,
    minValue: Int,
    maxValue: Int,
    scale: Double,
    key: String,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    unit: String? = null,
    increment: Int = 1,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    var value by rememberPreferenceState(
        key = key,
        value = { context.prefManager.getInt(key, defaultValue) },
        onChanged = { k, v -> context.prefManager.putInt(k, v) },
    )

    SeekBarPreference(
        title = title,
        summary = summary,
        minValue = minValue,
        maxValue = maxValue,
        defaultValue = defaultValue,
        modifier = modifier,
        icon = icon,
        value = value,
        onValueChanged = { value = it },
        unit = unit,
        scale = scale,
        increment = increment,
        enabled = enabled,
    )
}

@Composable
fun SeekBarPreference(
    title: String,
    summary: String?,
    value: Int,
    onValueChanged: (Int) -> Unit,
    defaultValue: Int,
    minValue: Int,
    maxValue: Int,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    unit: String? = null,
    increment: Int = 1,
    scale: Double = 1.0,
    enabled: Boolean = true,
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
                unit = unit,
                increment = increment,
                scale = scale,
                enabled = enabled,
            )
        },
        enabled = enabled,
    )
}

@Composable
private fun SeekBarLayout(
    value: Int,
    onValueChanged: (Int) -> Unit,
    defaultValue: Int,
    minValue: Int,
    maxValue: Int,
    unit: String?,
    modifier: Modifier = Modifier,
    scale: Double = 1.0,
    increment: Int = 1,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(
            onClick = { onValueChanged(defaultValue) },
            enabled = enabled,
        ) {
            Icon(
                painter = painterResource(R.drawable.undo),
                contentDescription = stringResource(R.string.reset),
            )
        }

        val range = (minValue * scale).toFloat()..(maxValue * scale).toFloat()
        val interactionSource = remember { MutableInteractionSource() }
        val interaction by interactionSource.collectIsDraggedAsState()
        val animatedValue = if (!interaction) animateFloatAsState((value * scale).toFloat()).value else (value * scale).toFloat()

        Slider(
            value = animatedValue,
            onValueChange = {
                onValueChanged((it / scale).roundToInt())
            },
            valueRange = range,
            modifier = Modifier.weight(1f),
            interactionSource = interactionSource,
            enabled = enabled,
        )

        Box(
            modifier = Modifier,
            contentAlignment = Alignment.Center,
        ) {
            val formattedMaxValue = if (scale == 1.0) maxValue.toString() else BigDecimal(maxValue * scale).setScale(-log(scale, 10.0).roundToInt(), RoundingMode.HALF_UP).toString()
            val formattedValue = if (scale == 1.0) value.toString() else BigDecimal(value * scale).setScale(-log(scale, 10.0).roundToInt(), RoundingMode.HALF_UP).toString()

            Text(
                text = unit?.let { "${formattedMaxValue}${unit}" } ?: formattedMaxValue,
                modifier = Modifier.alpha(0f),
            )

            Text(
                text = unit?.let { "${formattedValue}${unit}" } ?: formattedValue,
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(
                onClick = { onValueChanged(min(maxValue, value + increment)) },
                enabled = enabled,
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_up),
                    contentDescription = stringResource(R.string.increase),
                )
            }

            IconButton(
                onClick = { onValueChanged(max(minValue, value - increment)) },
                enabled = enabled,
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
