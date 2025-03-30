package tk.zwander.common.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bugsnag.android.performance.compose.MeasuredComposable
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tk.zwander.common.compose.AppTheme
import tk.zwander.common.compose.add.SearchToolbar
import tk.zwander.common.compose.components.Loader
import tk.zwander.common.iconpacks.IconPackIcon
import tk.zwander.common.iconpacks.iconPackManager
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.R

class SelectIconFromPackActivity : BaseActivity() {
    companion object {
        private const val EXTRA_SHORTCUT_ID = "shortcut_id"
        private const val EXTRA_ICON_PACK_PACKAGE_NAME = "icon_pack_package_name"

        fun createIntent(context: Context, shortcutId: Int, iconPackPackageName: String): Intent {
            val intent = Intent(context, SelectIconFromPackActivity::class.java)
            intent.putExtra(EXTRA_SHORTCUT_ID, shortcutId)
            intent.putExtra(EXTRA_ICON_PACK_PACKAGE_NAME, iconPackPackageName)

            return intent
        }
    }

    private val shortcutId by lazy { intent.getIntExtra(EXTRA_SHORTCUT_ID, -1).takeIf { it != -1 } }
    private val iconPackPackageName by lazy { intent.getStringExtra(EXTRA_ICON_PACK_PACKAGE_NAME) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (shortcutId == null || iconPackPackageName == null) {
            finish()
            return
        }

        setContent {
            var filter by remember {
                mutableStateOf<String?>(null)
            }

            val items = remember {
                mutableStateListOf<IconPackIcon>()
            }

            val filteredItems by remember {
                derivedStateOf {
                    if (filter.isNullOrBlank()) {
                        return@derivedStateOf items
                    }
                    items.filter {
                        it.name.contains(filter ?: "", true) ||
                                it.component?.flattenToString()?.contains(filter ?: "", true) == true
                    }
                }
            }

            LaunchedEffect(null) {
                launch(Dispatchers.IO) {
                    items.clear()
                    items.addAll(iconPackManager.getIconPackIcons(iconPackPackageName!!))
                }
            }

            MeasuredComposable(name = "SetIconFromPack") {
                AppTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Crossfade(
                            modifier = Modifier.fillMaxSize(),
                            targetState = items.isEmpty(),
                            label = "SelectIconPack",
                        ) { loading ->
                            if (loading) {
                                Loader(modifier = Modifier.fillMaxSize())
                            } else {
                                var searchBarHeight by remember {
                                    mutableIntStateOf(0)
                                }

                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.TopCenter,
                                ) {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = WindowInsets
                                            .systemBars
                                            .add(WindowInsets.ime)
                                            .add(WindowInsets(top = searchBarHeight))
                                            .add(WindowInsets(top = 8.dp))
                                            .asPaddingValues(),
                                    ) {
                                        items(items = filteredItems, key = { item -> item.name }) { icon ->
                                            val loadedDrawable = remember(icon.loadDrawable)

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .heightIn(min = 48.dp)
                                                    .clickable {
                                                        prefManager.shortcutOverrideIcons = prefManager.shortcutOverrideIcons.apply {
                                                            this[shortcutId!!] = icon.entry
                                                        }
                                                        setResult(RESULT_OK)
                                                        finish()
                                                    }
                                                    .padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                            ) {
                                                Image(
                                                    painter = rememberDrawablePainter(loadedDrawable),
                                                    contentDescription = icon.name,
                                                    modifier = Modifier.size(48.dp),
                                                )

                                                Column(
                                                    modifier = Modifier.weight(1f),
                                                ) {
                                                    Text(
                                                        text = icon.name,
                                                        fontWeight = FontWeight.Bold,
                                                    )

                                                    icon.component?.let {
                                                        Text(
                                                            text = it.flattenToString(),
                                                        )
                                                    }
                                                }

                                                if (icon.name == prefManager.shortcutOverrideIcons[shortcutId!!]?.name &&
                                                    icon.entry.packPackageName == prefManager.selectedIconPackPackage) {
                                                    Icon(
                                                        painter = painterResource(R.drawable.baseline_check_24),
                                                        contentDescription = stringResource(R.string.selected_pack),
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .statusBarsPadding()
                                            .padding(horizontal = 8.dp),
                                    ) {
                                        SearchToolbar(
                                            filter = filter,
                                            onFilterChanged = { f -> filter = f },
                                            onBack = ::finish,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .onSizeChanged { size ->
                                                    searchBarHeight = size.height
                                                },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
