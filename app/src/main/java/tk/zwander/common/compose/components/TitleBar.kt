package tk.zwander.common.compose.components

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import tk.zwander.lockscreenwidgets.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitleBar(
    title: String,
    modifier: Modifier = Modifier
) {
    val backPressedDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    TopAppBar(
        title = {
            Text(text = title)
        },
        navigationIcon = {
            IconButton(
                onClick = { backPressedDispatcher?.onBackPressed() },
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.baseline_arrow_back_24),
                    contentDescription = stringResource(id = R.string.back)
                )
            }
        },
        modifier = modifier.shadow(4.dp).zIndex(2f)
    )
}
