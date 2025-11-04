package tk.zwander.lockscreenwidgets.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tk.zwander.common.compose.util.rememberPreferenceState
import tk.zwander.common.util.PrefManager
import tk.zwander.common.util.collectAsMutableState
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.util.MainWidgetFrameDelegate

@Composable
fun MainWidgetFrameDelegate.WidgetFrameViewModel.HintIntroLayout(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var firstViewing by rememberPreferenceState(
        key = PrefManager.KEY_FIRST_VIEWING,
        value = { context.prefManager.firstViewing },
        onChanged = { _, value -> context.prefManager.firstViewing = value },
    )
    var acknowledgedTwoFingerTap by this.acknowledgedTwoFingerTap.collectAsMutableState()
    var acknowledgedThreeFingerTap by this.acknowledgedThreeFingerTap.collectAsMutableState()

    LaunchedEffect(acknowledgedThreeFingerTap) {
        if (acknowledgedThreeFingerTap) {
            firstViewing = false
        }
    }

    AnimatedVisibility(
        visible = firstViewing,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Column(
            modifier = modifier
                .padding(8.dp),
        ) {
            Crossfade(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                targetState = acknowledgedTwoFingerTap,
            ) { acknowledged ->
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_gesture_two_tap),
                        contentDescription = null,
                    )

                    Text(
                        text = stringResource(
                            when {
                                acknowledged == null -> R.string.edit_gesture_hint
                                !acknowledged -> R.string.edit_gesture_hint_2
                                else -> R.string.hide_gesture_hint
                            }
                        ),
                        fontSize = 18.sp,
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }

            OutlinedButton(
                onClick = {
                    when {
                        acknowledgedTwoFingerTap == null -> {
                            acknowledgedTwoFingerTap = false
                        }

                        acknowledgedTwoFingerTap == false -> {
                            acknowledgedTwoFingerTap = true
                        }

                        !acknowledgedThreeFingerTap -> {
                            acknowledgedThreeFingerTap = true
                        }
                    }
                },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(
                    text = stringResource(android.R.string.ok),
                )
            }
        }
    }
}
