package tk.zwander.common.compose.main

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tk.zwander.lockscreenwidgets.data.MainPageButton

@Composable
fun ExtraButton(info: MainPageButton, modifier: Modifier = Modifier) {
    SubduedOutlinedButton(
        onClick = { info.onClick() },
        modifier = modifier
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(id = info.icon),
                contentDescription = stringResource(id = info.title)
            )
            Spacer(Modifier.size(8.dp))
            Text(text = stringResource(id = info.title))
        }
    }
}