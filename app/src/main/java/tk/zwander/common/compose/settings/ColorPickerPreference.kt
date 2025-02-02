package tk.zwander.common.compose.settings

import androidx.annotation.ColorInt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.github.skydoves.colorpicker.compose.AlphaSlider
import com.github.skydoves.colorpicker.compose.AlphaTile
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import tk.zwander.common.compose.util.rememberPreferenceState
import tk.zwander.common.util.prefManager

class ColorPickerPreference(
    title: @Composable () -> String,
    summary: @Composable () -> String?,
    key: @Composable () -> String,
    defaultValue: @Composable () -> Int,
    icon: @Composable () -> Painter? = { null },
    enabled: @Composable () -> Boolean = { true },
    visible: @Composable () -> Boolean = { true },
) : BasePreference<Int>(
    title = title,
    summary = summary,
    key = key,
    defaultValue = defaultValue,
    icon = icon,
    enabled = enabled,
    visible = visible,
) {
    @Composable
    override fun Render(modifier: Modifier) {
        ColorPickerPreference(
            title = title(),
            summary = summary(),
            key = key(),
            defaultValue = Color(defaultValue()),
            modifier = modifier,
            icon = icon(),
            enabled = enabled(),
        )
    }
}

@Composable
fun ColorPickerPreference(
    title: String,
    summary: String?,
    key: String,
    @ColorInt defaultValue: Color,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    enabled: Boolean = true,
) {
    val context = LocalContext.current
    var value by rememberPreferenceState(
        key = key,
        value = { Color(context.prefManager.getInt(it, defaultValue.toArgb())) },
        onChanged = { k, v -> context.prefManager.putInt(k, v.toArgb()) },
    )

    ColorPickerPreference(
        title = title,
        summary = summary,
        color = value,
        onColorChanged = { value = it },
        modifier = modifier,
        icon = icon,
        enabled = enabled,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerPreference(
    title: String,
    summary: String?,
    color: Color,
    onColorChanged: (color: Color) -> Unit,
    modifier: Modifier = Modifier,
    icon: Painter? = null,
    enabled: Boolean = true,
) {
    var showingPickerDialog by remember {
        mutableStateOf(false)
    }

    val controller = rememberColorPickerController()

    BasePreferenceLayout(
        title = title,
        summary = summary,
        modifier = modifier,
        icon = icon,
        onClick = { showingPickerDialog = true },
        widget = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = color,
                        shape = CircleShape,
                    )
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.onSurface,
                        shape = CircleShape,
                    ),
            )
        },
        enabled = enabled,
    )

    if (showingPickerDialog) {
        val state = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
        )

        ModalBottomSheet(
            onDismissRequest = { showingPickerDialog = false },
            sheetState = state,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HsvColorPicker(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(450.dp),
                    controller = controller,
                    onColorChanged = {
                        onColorChanged(it.color)
                    },
                    initialColor = color,
                )

                AlphaSlider(
                    controller = controller,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                )

                BrightnessSlider(
                    controller = controller,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                )

                AlphaTile(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    controller = controller,
                )
            }
        }
    }
}
