package tk.zwander.common.compose.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tk.zwander.common.compose.components.AnimatedBottomSheet
import tk.zwander.common.compose.util.rememberPreferenceState
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.R
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
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
        initialValue = defaultValue,
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

@OptIn(ExperimentalMaterial3Api::class)
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
    val density = LocalDensity.current

    var showingDialog by remember {
        mutableStateOf(false)
    }

    val formattedMinValue = remember(scale, minValue) {
        if (scale == 1.0) minValue.toString() else BigDecimal(minValue * scale).setScale(
            -log(
                scale,
                10.0
            ).roundToInt(), RoundingMode.HALF_UP
        ).toString()
    }
    val formattedMaxValue = remember(scale, maxValue) {
        if (scale == 1.0) maxValue.toString() else BigDecimal(maxValue * scale).setScale(
            -log(
                scale,
                10.0
            ).roundToInt(), RoundingMode.HALF_UP
        ).toString()
    }
    val formattedValue = remember(scale, value) {
        if (scale == 1.0) value.toString() else BigDecimal(value * scale).setScale(
            -log(
                scale,
                10.0
            ).roundToInt(), RoundingMode.HALF_UP
        ).toString()
    }

    var tempValueState by remember(showingDialog) {
        mutableStateOf(formattedValue)
    }
    var textInputHeight by remember(showingDialog) {
        mutableStateOf(0.dp)
    }
    var unitWidth by remember(showingDialog) {
        mutableStateOf(0.dp)
    }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )

    val decimalSeparator = remember {
        DecimalFormatSymbols.getInstance().decimalSeparator
    }
    val canParse = remember(tempValueState) {
        when {
            tempValueState.isBlank() -> false
            tempValueState.toFloatOrNull() == null -> false
            (tempValueState.toFloat() / scale).let { it > maxValue || it < minValue } -> false
            scale == 1.0 &&
                    tempValueState.contains(decimalSeparator) &&
                    tempValueState.toFloat().toInt()
                        .toFloat() != tempValueState.toFloat() -> false

            BigDecimal(tempValueState).scale() > -log(scale, 10.0).roundToInt() -> false
            else -> true
        }
    }

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
        val animatedValue =
            if (!interaction) {
                animateFloatAsState(
                    targetValue = (value * scale).toFloat(),
                    animationSpec = tween(),
                ).value
            } else {
                (value * scale).toFloat()
            }
        val sliderColors = SliderDefaults.colors()
        val animatedThumbColor by animateColorAsState(if (enabled) sliderColors.thumbColor else sliderColors.disabledThumbColor)
        val animatedThumbBackgroundColor by animateColorAsState(
            if (enabled) sliderColors.inactiveTrackColor else sliderColors.disabledInactiveTrackColor.compositeOver(
                MaterialTheme.colorScheme.surface,
            )
        )
        val animatedBorderWidth by animateDpAsState(if (value == defaultValue) 2.dp else 12.dp)

        val animatedActiveTrackColor by animateColorAsState(if (enabled) sliderColors.activeTrackColor else sliderColors.disabledActiveTrackColor)
        val animatedInactiveTrackColor by animateColorAsState(if (enabled) sliderColors.inactiveTrackColor else sliderColors.disabledInactiveTrackColor)
        val animatedActiveTickColor by animateColorAsState(if (enabled) sliderColors.activeTickColor else sliderColors.disabledActiveTickColor)
        val animatedInactiveTickColor by animateColorAsState(if (enabled) sliderColors.inactiveTickColor else sliderColors.disabledInactiveTickColor)

        val trackColors = sliderColors.copy(
            activeTrackColor = animatedActiveTrackColor,
            inactiveTrackColor = animatedInactiveTrackColor,
            activeTickColor = animatedActiveTickColor,
            inactiveTickColor = animatedInactiveTickColor,
            disabledActiveTrackColor = animatedActiveTrackColor,
            disabledInactiveTrackColor = animatedInactiveTrackColor,
            disabledActiveTickColor = animatedActiveTickColor,
            disabledInactiveTickColor = animatedInactiveTickColor,
        )

        Slider(
            value = animatedValue,
            onValueChange = {
                onValueChanged((it / scale).roundToInt())
            },
            valueRange = range,
            modifier = Modifier.weight(1f),
            interactionSource = interactionSource,
            enabled = enabled,
            colors = sliderColors,
            thumb = {
                Box(
                    modifier = Modifier
                        .shadow(
                            elevation = 8.dp,
                            shape = CircleShape,
                        )
                        .background(
                            color = animatedThumbBackgroundColor,
                            shape = CircleShape,
                        )
                        .border(
                            width = animatedBorderWidth,
                            shape = CircleShape,
                            color = animatedThumbColor,
                        )
                        .size(24.dp),
                )
            },
            track = {
                SliderDefaults.Track(
                    colors = trackColors,
                    enabled = enabled,
                    sliderState = it,
                    thumbTrackGapSize = 0.dp,
                )
            },
        )

        Box(
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false),
                    onClick = { showingDialog = true },
                )
                .padding(4.dp),
        ) {
            SubcomposeLayout { constraints ->
                val textWidth = with (density) {
                    subcompose(
                        slotId = "ValueText",
                        content = {
                            Text(
                                text = unit?.let { "${formattedMaxValue}${unit}" } ?: formattedMaxValue,
                                modifier = Modifier.alpha(0f),
                            )
                        },
                    ).map { it.measure(Constraints()) }[0].width.toDp()
                }

                val contents = subcompose(
                    slotId = "Contents",
                    content = {
                        Column(
                            modifier = Modifier
                                .width(textWidth),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            Text(
                                text = unit?.let { "${formattedValue}${unit}" } ?: formattedValue,
                                textAlign = TextAlign.Center,
                            )

                            HorizontalDivider()
                        }
                    },
                ).map { it.measure(constraints.copy(maxHeight = Constraints.Infinity)) }[0]

                layout(contents.width, contents.height) {
                    contents.place(0, 0)
                }
            }
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

    AnimatedBottomSheet(
        onDismissRequest = { showingDialog = false },
        sheetState = sheetState,
        isVisible = showingDialog,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SubcomposeLayout { constraints ->
                val textWidth = with (density) {
                    subcompose(
                        slotId = "ValueText",
                        content = {
                            Text(
                                text = formattedMaxValue,
                                modifier = Modifier.alpha(0f),
                            )
                        },
                    ).map { it.measure(Constraints()) }[0].width.toDp()
                }

                val contents = subcompose(
                    slotId = "Contents",
                    content = {
                        Row(
                            modifier = Modifier,
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .heightIn(min = textInputHeight)
                                    .width(IntrinsicSize.Max),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = formattedMinValue,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.widthIn(min = textWidth),
                                )

                                HorizontalDivider(
                                    modifier = Modifier.align(Alignment.BottomCenter),
                                )
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Default.KeyboardArrowLeft,
                                contentDescription = null,
                            )

                            OutlinedTextField(
                                value = tempValueState,
                                onValueChange = { tempValueState = it.replace(Regex("[^0-9${decimalSeparator}]"), "") },
                                singleLine = true,
                                isError = !canParse,
                                keyboardOptions = KeyboardOptions.Default.copy(
                                    autoCorrectEnabled = false,
                                    keyboardType = if (scale == 1.0) KeyboardType.Number else KeyboardType.Decimal,
                                ),
                                modifier = Modifier
                                    .onSizeChanged {
                                        textInputHeight = with(density) { it.height.toDp() }
                                    }
                                    .weight(1f, false)
                                    .widthIn(min = 16.dp, max = Dp.Infinity),
                                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center),
                                prefix = unit?.let {
                                    {
                                        Box(
                                            modifier = Modifier.width(unitWidth),
                                        )
                                    }
                                },
                                suffix = unit?.let {
                                    {
                                        Text(
                                            text = unit,
                                            modifier = Modifier.onSizeChanged {
                                                unitWidth = with(density) { it.width.toDp() }
                                            },
                                        )
                                    }
                                },
                            )

                            Icon(
                                imageVector = Icons.AutoMirrored.Default.KeyboardArrowLeft,
                                contentDescription = null,
                            )

                            Box(
                                modifier = Modifier
                                    .heightIn(min = textInputHeight)
                                    .width(IntrinsicSize.Max),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = formattedMaxValue,
                                    textAlign = TextAlign.Center,
                                )

                                HorizontalDivider(
                                    modifier = Modifier.align(Alignment.BottomCenter),
                                )
                            }
                        }
                    },
                ).map { it.measure(constraints.copy(maxHeight = Constraints.Infinity)) }[0]

                layout(constraints.maxWidth, contents.height) {
                    contents.place(0, 0)
                }
            }

            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = { showingDialog = false },
                ) {
                    Text(text = stringResource(R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showingDialog = false
                        tempValueState.toFloatOrNull()?.let {
                            onValueChanged((it / scale).toInt())
                        }
                    },
                    enabled = canParse,
                ) {
                    Text(text = stringResource(R.string.apply))
                }
            }
        }
    }
}
