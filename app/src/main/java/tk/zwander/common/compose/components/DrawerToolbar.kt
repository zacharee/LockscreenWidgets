package tk.zwander.common.compose.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import tk.zwander.common.compose.AppTheme
import tk.zwander.common.compose.util.OffsetOverscrollEffect
import tk.zwander.lockscreenwidgets.R

@Preview
@Composable
fun DrawerToolbarPreview() {
    AppTheme {
        Surface(
            modifier = Modifier.fillMaxSize()
                .systemBarsPadding(),
            color = Color.DarkGray,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                DrawerToolbar(
                    addWidget = {},
                    closeDrawer = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun DrawerToolbar(
    addWidget: () -> Unit,
    closeDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val actionBarSize = 56.dp

    val anchorState = remember {
        AnchoredDraggableState(
            initialValue = false,
            anchors = DraggableAnchors {
                with(density) {
                    true at -16.dp.toPx()
                    false at actionBarSize.toPx()
                }
            },
        )
    }
    val rotation by animateFloatAsState(if (anchorState.targetValue) 180f else 0f)
    val overscroll = remember { OffsetOverscrollEffect(scope) }

    Column(
        modifier = modifier
            .offset(y = with(density) { anchorState.requireOffset().toDp() })
            .padding(horizontal = 8.dp)
            .overscroll(overscroll),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(
            onClick = {
                scope.launch {
                    anchorState.animateTo(
                        targetValue = !anchorState.settledValue,
                        animationSpec = SpringSpec(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                        ),
                    )
                }
            },
            modifier = Modifier.anchoredDraggable(
                state = anchorState,
                orientation = Orientation.Vertical,
                flingBehavior = AnchoredDraggableDefaults.flingBehavior(
                    state = anchorState,
                    animationSpec = SpringSpec(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                    ),
                ),
                overscrollEffect = overscroll,
            ),
        ) {
            Icon(
                painter = painterResource(R.drawable.arrow_up),
                contentDescription = stringResource(R.string.open_close_toolbar),
                modifier = Modifier
                    .rotate(rotation)
                    .size(32.dp),
                tint = Color.White,
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(actionBarSize)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(
                    onClick = addWidget,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_add_24),
                        contentDescription = stringResource(R.string.add_widget),
                        modifier = Modifier.size(48.dp),
                    )
                }

                IconButton(
                    onClick = closeDrawer,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_add_24),
                        contentDescription = stringResource(R.string.close_widget_drawer),
                        modifier = Modifier
                            .rotate(45f)
                            .size(48.dp),
                    )
                }
            }
        }
    }
}
