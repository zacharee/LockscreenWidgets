package tk.zwander.lockscreenwidgets.compose.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.android.material.composethemeadapter.MdcTheme

@Suppress("OPT_IN_IS_NOT_ENABLED")
@Preview
@Composable
fun MainContent() {
    val features = rememberFeatureCards()
    val links = rememberLinks()

    MdcTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(200.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(features.size, span = { GridItemSpan(maxLineSpan) }) {
                    FeatureCard(info = features[it])
                }

                items(links.size, span = { GridItemSpan(maxLineSpan) }) {
                    LinkItem(option = links[it])
                }
            }
        }
    }
}