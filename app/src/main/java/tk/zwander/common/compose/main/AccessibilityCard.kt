package tk.zwander.common.compose.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import tk.zwander.common.activities.OnboardingActivity
import tk.zwander.common.compose.components.ContentColoredOutlinedButton
import tk.zwander.common.util.openAccessibilitySettings
import tk.zwander.lockscreenwidgets.R

@OptIn(ExperimentalLayoutApi::class)
@Preview
@Composable
fun AccessibilityCard() {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
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
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                ContentColoredOutlinedButton(
                    onClick = {
                        OnboardingActivity.start(
                            context,
                            OnboardingActivity.RetroMode.BATTERY
                        )
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    border = ButtonDefaults.outlinedButtonBorder(true).copy(brush = SolidColor(MaterialTheme.colorScheme.onErrorContainer)),
                    modifier = Modifier.padding(end = 4.dp),
                ) {
                    Text(text = stringResource(id = R.string.battery_whitelist))
                }

                ContentColoredOutlinedButton(
                    onClick = { context.openAccessibilitySettings() },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    border = ButtonDefaults.outlinedButtonBorder(true).copy(brush = SolidColor(MaterialTheme.colorScheme.onErrorContainer)),
                    modifier = Modifier.padding(start = 4.dp),
                ) {
                    Text(text = stringResource(id = R.string.accessibility_settings))
                }
            }
        }
    }
}
