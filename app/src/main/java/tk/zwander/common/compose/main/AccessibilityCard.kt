package tk.zwander.common.compose.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.accompanist.flowlayout.FlowMainAxisAlignment
import com.google.accompanist.flowlayout.FlowRow
import com.google.accompanist.flowlayout.SizeMode
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.activities.OnboardingActivity
import tk.zwander.lockscreenwidgets.services.openAccessibilitySettings

@Preview
@Composable
fun AccessibilityCard() {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth(),
        backgroundColor = MaterialTheme.colors.error
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.info),
                    contentDescription = null
                )

                Text(text = stringResource(id = R.string.main_screen_accessibility_not_started))
            }

            FlowRow(
                modifier = Modifier.align(Alignment.CenterHorizontally)
                    .fillMaxWidth(),
                crossAxisSpacing = 0.dp,
                mainAxisSpacing = 4.dp,
                mainAxisAlignment = FlowMainAxisAlignment.SpaceAround,
                mainAxisSize = SizeMode.Expand
            ) {
                OutlinedButton(
                    onClick = {
                        OnboardingActivity.start(
                            context,
                            OnboardingActivity.RetroMode.BATTERY
                        )
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        backgroundColor = Color.Transparent,
                        contentColor = MaterialTheme.colors.onError
                    ),
                    border = ButtonDefaults.outlinedBorder.copy(brush = SolidColor(MaterialTheme.colors.onError))
                ) {
                    Text(text = stringResource(id = R.string.battery_whitelist))
                }

                OutlinedButton(
                    onClick = { context.openAccessibilitySettings() },
                    colors = ButtonDefaults.outlinedButtonColors(
                        backgroundColor = Color.Transparent,
                        contentColor = MaterialTheme.colors.onError
                    ),
                    border = ButtonDefaults.outlinedBorder.copy(brush = SolidColor(MaterialTheme.colors.onError))
                ) {
                    Text(text = stringResource(id = R.string.accessibility_settings))
                }
            }
        }
    }
}
