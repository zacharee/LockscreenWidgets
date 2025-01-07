package tk.zwander.common.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tk.zwander.common.compose.AppTheme
import tk.zwander.common.compose.add.SearchToolbar
import tk.zwander.common.compose.components.Loader
import tk.zwander.common.iconpacks.LoadedIconPack
import tk.zwander.common.iconpacks.iconPackManager
import tk.zwander.common.util.Event
import tk.zwander.common.util.eventManager
import tk.zwander.common.util.prefManager
import tk.zwander.lockscreenwidgets.R

class SelectIconPackActivity : BaseActivity() {
    companion object {
        private const val EXTRA_SHORTCUT_ID = "shortcut_id"
        private const val EXTRA_FROM_DRAWER = "from_drawer"

        fun launchForOverride(context: Context, shortcutId: Int, fromDrawer: Boolean = false) {
            val intent = Intent(context, SelectIconPackActivity::class.java)
            intent.putExtra(EXTRA_SHORTCUT_ID, shortcutId)
            intent.putExtra(EXTRA_FROM_DRAWER, fromDrawer)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            context.startActivity(intent)
        }
    }

    private val shortcutId by lazy { intent.getIntExtra(EXTRA_SHORTCUT_ID, -1).takeIf { it != -1 } }
    private val fromDrawer by lazy { intent.getBooleanExtra(EXTRA_FROM_DRAWER, false) }

    private val shortcutOverrideLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                if (fromDrawer) {
                    eventManager.sendEvent(Event.ShowDrawer)
                }
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            var filter by remember {
                mutableStateOf<String?>(null)
            }

            val items = remember {
                mutableStateListOf<LoadedIconPack>()
            }

            val filteredItems by remember {
                derivedStateOf {
                    if (filter.isNullOrBlank()) {
                        return@derivedStateOf items
                    }
                    items.filter { it.label?.contains(filter ?: "", true) == true }
                }
            }

            LaunchedEffect(null) {
                launch(Dispatchers.IO) {
                    items.clear()
                    items.addAll(iconPackManager.getIconPackPackages())
                }
            }

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
                                    items(
                                        items = filteredItems,
                                        key = { item ->
                                            item.packageName ?: "SystemIconPack__"
                                        }) { pack ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 48.dp)
                                                .clickable {
                                                    if (shortcutId == null) {
                                                        prefManager.selectedIconPackPackage =
                                                            pack.packageName
                                                        finish()
                                                    } else {
                                                        if (pack.packageName == null) {
                                                            prefManager.shortcutOverrideIcons =
                                                                prefManager.shortcutOverrideIcons.apply {
                                                                    remove(shortcutId)
                                                                }
                                                            finish()
                                                        } else {
                                                            shortcutOverrideLauncher.launch(
                                                                SelectIconFromPackActivity.createIntent(
                                                                    this@SelectIconPackActivity,
                                                                    shortcutId!!,
                                                                    pack.packageName,
                                                                ),
                                                            )
                                                        }
                                                    }
                                                }
                                                .padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                                        ) {
                                            Image(
                                                painter = rememberDrawablePainter(pack.packIcon),
                                                contentDescription = pack.label,
                                                modifier = Modifier.size(48.dp),
                                            )

                                            Column(
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                Text(
                                                    text = pack.label
                                                        ?: stringResource(R.string.system_icons),
                                                    fontWeight = FontWeight.Bold,
                                                )

                                                pack.packageName?.let {
                                                    Text(
                                                        text = it,
                                                    )
                                                }
                                            }

                                            if ((shortcutId == null && pack.packageName == prefManager.selectedIconPackPackage) ||
                                                (shortcutId != null && prefManager.shortcutOverrideIcons[shortcutId!!]?.packPackageName == pack.packageName)) {
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
