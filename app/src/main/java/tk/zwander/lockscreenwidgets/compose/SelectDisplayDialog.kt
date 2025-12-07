package tk.zwander.lockscreenwidgets.compose

import android.os.Build
import android.view.Display
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import tk.zwander.common.compose.LocalLSDisplayManager
import tk.zwander.common.compose.util.rememberPreferenceState
import tk.zwander.common.util.FrameSizeAndPosition
import tk.zwander.common.util.LSDisplay
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.R
import java.util.TreeSet
import kotlin.math.absoluteValue

@Composable
fun SelectDisplayDialog(
    dismiss: () -> Unit,
    onDisplaySelected: ((id: String) -> Unit)? = null,
    onFrameSelected: ((frameId: Int) -> Unit)? = null,
    showDefaultFrame: Boolean = true,
) {
    if (onDisplaySelected == null && onFrameSelected == null) {
        throw IllegalArgumentException("Either onDisplaySelected or onFrameSelected must be specified.")
    }

    if (onDisplaySelected != null && onFrameSelected != null) {
        throw IllegalArgumentException("Only one of onDisplaySelected or onFrameSelected can be specified.")
    }

    val context = LocalContext.current
    val frames by rememberPreferenceState(
        key = PrefManager.KEY_CURRENT_FRAMES_WITH_STRING_DISPLAY,
        value = { context.prefManager.currentSecondaryFramesWithStringDisplay },
    )

    val defaultFrameDisplay by rememberPreferenceState(
        key = PrefManager.KEY_PRIMARY_FRAME_DISPLAY,
        value = { context.prefManager.primaryFrameDisplay },
    )
    val defaultFrame by remember {
        derivedStateOf {
            -1 to defaultFrameDisplay
        }
    }
    val allFrames by remember {
        derivedStateOf {
            if (showDefaultFrame) {
                frames + defaultFrame
            } else {
                frames
            }
        }
    }

    val lsDisplayManager = LocalLSDisplayManager.current
    val displays by lsDisplayManager.availableDisplays.collectAsState()

    val defaultDisplay by remember {
        derivedStateOf {
            displays.values.firstOrNull { it.displayId == Display.DEFAULT_DISPLAY }
                ?: displays.values.first()
        }
    }
    val density = LocalDensity.current

    val framesForDefaultDisplay by remember {
        derivedStateOf {
            if (onDisplaySelected == null) {
                allFrames.filter { it.value == "${Display.DEFAULT_DISPLAY}" }
                    .keys.sorted()
            } else {
                listOf(-2)
            }
        }
    }

    val displaysToFramesMap by remember {
        derivedStateOf {
            val map = hashMapOf<LSDisplay, MutableSet<Int>>()

            if (onDisplaySelected == null) {
                allFrames.forEach { (frameId, displayId) ->
                    displays.values.firstOrNull {
                        it.uniqueIdCompat == displayId
                    }?.let { displayForId ->
                        if (map.containsKey(displayForId)) {
                            map[displayForId]!!.add(frameId)
                        } else {
                            map[displayForId] = TreeSet<Int>().apply { add(frameId) }
                        }
                    }
                }
            } else {
                // Dummy list to allow for selecting only displays (i.e. when adding frames).
                map.putAll(displays.values.associateWith { mutableSetOf() })
            }

            map.toSortedMap { o1, o2 ->
                o1.uniqueIdCompat.compareTo(o2.uniqueIdCompat)
            }
        }
    }

    AlertDialog(
        onDismissRequest = dismiss,
        title = {
            Text(
                text = stringResource(
                    id = if (onDisplaySelected != null) {
                        R.string.select_display
                    } else {
                        R.string.select_frame
                    },
                ),
            )
        },
        text = {
            var maxDisplayWidth by remember {
                mutableStateOf(0.dp)
            }

            val expandedMap = remember {
                mutableStateMapOf<String, Boolean>()
            }

            Box(
                modifier = Modifier,
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        if (framesForDefaultDisplay.isNotEmpty()) {
                            item(key = "DEFAULT_DISPLAY") {
                                DisplayCard(
                                    labelText = "${stringResource(R.string.default_display)} (${Display.DEFAULT_DISPLAY})",
                                    subtitleText = stringResource(R.string.default_display_desc),
                                    canExpand = onFrameSelected != null,
                                    onClick = {
                                        if (onDisplaySelected != null) {
                                            onDisplaySelected("${Display.DEFAULT_DISPLAY}")
                                        } else {
                                            expandedMap["DEFAULT_DISPLAY"] =
                                                !(expandedMap["DEFAULT_DISPLAY"] ?: true)
                                        }
                                    },
                                    isExpanded = expandedMap["DEFAULT_DISPLAY"] != false,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }

                        if (expandedMap["DEFAULT_DISPLAY"] != false && onFrameSelected != null) {
                            items(
                                items = framesForDefaultDisplay,
                                key = { "DEFAULT_DISPLAY_FRAME_$it" }) {
                                FrameItem(
                                    display = defaultDisplay,
                                    frameId = it,
                                    onSelected = {
                                        onFrameSelected.invoke(it)
                                    },
                                    modifier = Modifier.animateItem()
                                )
                            }
                        }
                    }

                    displaysToFramesMap.forEach { (display, frameIds) ->
                        item(key = display.uniqueIdCompat) {
                            DisplayCard(
                                labelText = "${display.display.name} (${
                                    descriptionForDisplay(
                                        display
                                    )
                                })",
                                accessory = {
                                    val (width, height) = remember(display.uniqueIdCompat) {
                                        with(density) {
                                            val screenSize = display.realSize
                                            val screenWidth = screenSize.x
                                            val screenHeight = screenSize.y

                                            val desiredHeight = 48.dp
                                            val actualHeight = screenHeight.toDp()

                                            val heightRatio = desiredHeight / actualHeight

                                            val scaledWidth = (screenWidth * heightRatio).toDp()

                                            if (scaledWidth > maxDisplayWidth) {
                                                maxDisplayWidth = scaledWidth
                                            }

                                            scaledWidth to desiredHeight
                                        }
                                    }

                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = if (maxDisplayWidth > 0.dp) {
                                            Modifier.width(maxDisplayWidth)
                                        } else {
                                            Modifier
                                        },
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .border(
                                                    width = 1.dp,
                                                    color = LocalContentColor.current,
                                                    shape = RoundedCornerShape(2.dp),
                                                )
                                                .width(width)
                                                .height(height),
                                        )
                                    }
                                },
                                onClick = {
                                    if (onDisplaySelected != null) {
                                        onDisplaySelected(display.uniqueIdCompat)
                                    } else {
                                        expandedMap[display.uniqueIdCompat] =
                                            !(expandedMap[display.uniqueIdCompat] ?: true)
                                    }
                                },
                                canExpand = onFrameSelected != null,
                                isExpanded = expandedMap[display.uniqueIdCompat] != false,
                                modifier = Modifier.animateItem(),
                            )
                        }

                        if (expandedMap[display.uniqueIdCompat] != false && onFrameSelected != null) {
                            items(items = frameIds.toList(), key = { "DISPLAY_FRAMES_$it" }) {
                                FrameItem(
                                    display = display,
                                    frameId = it,
                                    onSelected = {
                                        onFrameSelected.invoke(it)
                                    },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = dismiss,
            ) {
                Text(text = stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun DisplayCard(
    labelText: String,
    canExpand: Boolean,
    isExpanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitleText: String? = null,
    accessory: (@Composable () -> Unit)? = null,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (canExpand) {
                    val rotation by animateFloatAsState(if (isExpanded) 0f else 180f)

                    Icon(
                        painter = painterResource(R.drawable.arrow_up),
                        contentDescription = stringResource(R.string.expand),
                        modifier = Modifier.rotate(rotation),
                    )

                    Spacer(modifier = Modifier.size(8.dp))
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = labelText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                    )

                    subtitleText?.let {
                        Text(
                            text = it,
                        )
                    }
                }

                accessory?.let {
                    Spacer(modifier = Modifier.size(8.dp))

                    it()
                }
            }
        }
    }
}

@Composable
private fun FrameItem(
    display: LSDisplay,
    frameId: Int,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val frameSizeAndPosition = remember {
        FrameSizeAndPosition.getInstance(context)
    }
    val size = remember {
        frameSizeAndPosition.getSizeForType(
            type = FrameSizeAndPosition.FrameType.SecondaryLockscreen.Portrait(frameId),
            display = display,
        )
    }


    val (width, height) = remember(density) {
        with(density) {
            val screenWidth = size.x
            val screenHeight = size.y

            val desiredHeight = 48.dp
            val actualHeight = screenHeight.toDp()

            val heightRatio = desiredHeight / actualHeight

            val scaledWidth = (screenWidth * heightRatio).toDp()

            scaledWidth to desiredHeight
        }
    }

    Box(
        modifier = modifier.padding(start = 16.dp),
    ) {
        Card(
            onClick = onSelected,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "$frameId",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.size(8.dp))

                Box(
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = LocalContentColor.current,
                                shape = RoundedCornerShape(2.dp),
                            )
                            .width(width)
                            .height(height),
                    ) {
                        WidgetFramePreviewLayout(
                            modifier = Modifier,
                            frameId = frameId,
                            displayId = display.uniqueIdCompat,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun descriptionForDisplay(display: LSDisplay): String {
    val resources = LocalResources.current
    var description by remember {
        mutableStateOf("")
    }

    LaunchedEffect(display.displayId, resources) {
        val size = display.realSize
        val width = size.x
        val height = size.y

        val widthToHeightRatio = width.toDouble() / height.toDouble()

        val likelyTallThresholdRatio = 0.5
        val likelySquareThresholdRatio = 1.0

        if (widthToHeightRatio <= likelyTallThresholdRatio) {
            description = resources.getString(R.string.tall)
            return@LaunchedEffect
        }

        if (widthToHeightRatio >= likelySquareThresholdRatio) {
            description = resources.getString(R.string.square)
            return@LaunchedEffect
        }

        val distanceToTall = (widthToHeightRatio - likelyTallThresholdRatio).absoluteValue
        val distanceToSquare = (widthToHeightRatio - likelySquareThresholdRatio).absoluteValue

        description = if (distanceToTall < distanceToSquare) {
            resources.getString(R.string.tall)
        } else {
            resources.getString(R.string.square)
        }
    }

    return description
}
