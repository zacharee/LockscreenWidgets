package tk.zwander.common.compose.main

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import tk.zwander.common.activities.WidgetStackListActivity
import tk.zwander.lockscreenwidgets.R

@Preview
@Composable
fun WidgetStacksCard(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Card(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(id = R.string.widget_stacks),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.headlineLarge,
                )

                Box(modifier = Modifier.fillMaxWidth(0.25f)) {
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                SubduedOutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(context, WidgetStackListActivity::class.java),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp),
                ) {
                    Text(
                        text = stringResource(id = R.string.manage_widget_stacks),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            }
        }
    }
}
