package tk.zwander.common.compose.settings

import androidx.annotation.ColorInt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.github.skydoves.colorpicker.compose.AlphaSlider
import com.github.skydoves.colorpicker.compose.AlphaTile
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import tk.zwander.common.compose.components.AnimatedBottomSheet
import tk.zwander.common.compose.util.rememberPreferenceState
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.R

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

    val bottomSheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )

    val controller = rememberColorPickerController()
    val currentControllerColor by controller.selectedColor

    var textFieldContents by remember(showingPickerDialog) {
        mutableStateOf(color.toArgb().toHexString())
    }
    var textFieldValid by remember(showingPickerDialog) {
        mutableStateOf(true)
    }
    var initialized by remember(showingPickerDialog) {
        mutableStateOf(false)
    }

    LaunchedEffect(showingPickerDialog) {
        controller.selectByColor(color, false)
        initialized = true
    }

    LaunchedEffect(currentControllerColor) {
        if (initialized) {
            val newColorInt = currentControllerColor.toArgb()
            val currentColorInt = try {
                "#${textFieldContents}".toColorInt()
            } catch (_: IllegalArgumentException) {
                null
            }

            if (newColorInt != currentColorInt) {
                textFieldContents = newColorInt.toHexString()
            }
        }
    }

    LaunchedEffect(textFieldContents) {
        textFieldValid = try {
            val newColor = Color("#${textFieldContents}".toColorInt())
            controller.selectByColor(newColor, false)
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }

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

    AnimatedBottomSheet(
        onDismissRequest = { showingPickerDialog = false },
        sheetState = bottomSheetState,
        isVisible = showingPickerDialog,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HsvColorPicker(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(450.dp),
                controller = controller,
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

            Spacer(modifier = Modifier.size(8.dp))

            TextField(
                value = textFieldContents,
                onValueChange = {
                    textFieldContents = it.replace(Regex("[^a-fA-F0-9]"), "")
                },
                isError = !textFieldValid,
                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                prefix = { Text(text = "#") },
            )

            AlphaTile(
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(8.dp)),
                controller = controller,
            )

            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = { showingPickerDialog = false },
                ) {
                    Text(text = stringResource(R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showingPickerDialog = false
                        onColorChanged(controller.selectedColor.value)
                    },
                    enabled = textFieldValid,
                ) {
                    Text(text = stringResource(R.string.apply))
                }
            }
        }
    }
}
