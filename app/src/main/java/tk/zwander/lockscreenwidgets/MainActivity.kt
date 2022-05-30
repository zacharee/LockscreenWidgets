package tk.zwander.lockscreenwidgets

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.accompanist.flowlayout.FlowCrossAxisAlignment
import com.google.accompanist.flowlayout.FlowMainAxisAlignment
import com.google.accompanist.flowlayout.FlowRow
import com.google.accompanist.flowlayout.SizeMode
import com.google.android.material.composethemeadapter.MdcTheme
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import tk.zwander.lockscreenwidgets.activities.OnboardingActivity
import tk.zwander.lockscreenwidgets.activities.SettingsActivity
import tk.zwander.lockscreenwidgets.activities.UsageActivity
import tk.zwander.lockscreenwidgets.data.MainPageButton
import tk.zwander.lockscreenwidgets.data.MainPageLink
import tk.zwander.lockscreenwidgets.util.*
import tk.zwander.patreonsupportersretrieval.view.SupporterView

/**
 * Host the main page of the app (the social links). It also hosts the buttons to add a widget, view usage
 * details, and open the settings.
 *
 * If it's the user's first time running the app, or a required permission is missing (i.e. Accessibility),
 * this Activity will also make sure to start [OnboardingActivity] in the proper mode.
 */
class MainActivity : AppCompatActivity(), CoroutineScope by MainScope() {
    private val introRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            //The intro sequence or permissions request wasn't successful. Quit.
            finish()
        } else {
            //The user finished the intro sequence or granted the required permission.
            //Stay open, and make sure firstRun is false.
            prefManager.firstRun = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MainContent()
        }

        if (prefManager.firstRun || !isAccessibilityEnabled) {
            OnboardingActivity.startForResult(this, introRequest,
                if (!prefManager.firstRun) OnboardingActivity.RetroMode.ACCESSIBILITY else OnboardingActivity.RetroMode.NONE)
        }
    }

    override fun onStop() {
        super.onStop()

        WidgetFrameDelegate.peekInstance(this)?.updateState { it.copy(isPreview = false) }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancel()
    }
}

@Suppress("OPT_IN_IS_NOT_ENABLED")
@OptIn(ExperimentalMaterialApi::class)
@Preview
@Composable
fun MainContent() {
    val context = LocalContext.current

    val buttons = remember {
        listOf(
            MainPageButton(
                R.drawable.ic_baseline_preview_24,
                R.string.preview
            ) {
                WidgetFrameDelegate.retrieveInstance(context)?.updateState { it.copy(isPreview = !it.isPreview) }
            },
            MainPageButton(
                R.drawable.ic_baseline_settings_24,
                R.string.settings
            ) {
                context.startActivity(Intent(context, SettingsActivity::class.java))
            },
            MainPageButton(
                R.drawable.ic_baseline_help_outline_24,
                R.string.usage
            ) {
                context.startActivity(Intent(context, UsageActivity::class.java))
            },
        )
    }
    
    val links = remember {
        listOf(
            MainPageLink(
                R.drawable.info,
                R.string.privacy_policy,
                R.string.main_screen_privacy_policy_desc,
                "https://github.com/zacharee/LockscreenWidgets/blob/master/PRIVACY.md"
            ),
            MainPageLink(
                R.drawable.ic_baseline_twitter_24,
                R.string.main_screen_social_twitter,
                R.string.main_screen_social_twitter_desc,
                "https://twitter.com/Wander1236"
            ),
            MainPageLink(
                R.drawable.ic_baseline_earth_24,
                R.string.main_screen_social_website,
                R.string.main_screen_social_website_desc,
                "https://zwander.dev"
            ),
            MainPageLink(
                R.drawable.ic_baseline_email_24,
                R.string.main_screen_social_email,
                R.string.main_screen_social_email_desc,
                "zachary@zwander.dev"
            ),
            MainPageLink(
                R.drawable.ic_baseline_telegram_24,
                R.string.main_screen_social_telegram,
                R.string.main_screen_social_telegram_desc,
                "https://bit.ly/ZachareeTG"
            ),
            MainPageLink(
                R.drawable.ic_baseline_github_24,
                R.string.main_screen_social_github,
                R.string.main_screen_social_github_desc,
                "https://github.com/zacharee/LockscreenWidgets"
            ),
            MainPageLink(
                R.drawable.ic_baseline_patreon_24,
                R.string.main_screen_social_patreon,
                R.string.main_screen_social_patreon_desc,
                "https://patreon.com/zacharywander"
            ),
            MainPageLink(
                R.drawable.ic_baseline_heart_24,
                R.string.supporters,
                R.string.supporters_desc
            ) {
                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.supporters)
                    .setView(SupporterView(context))
                    .show()
            }
        )
    }

    MdcTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(200.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(id = R.string.app_name),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.h4
                            )

                            Spacer(Modifier.size(16.dp))

                            Text(
                                text = BuildConfig.VERSION_NAME
                            )

                            Box(modifier = Modifier.fillMaxWidth(0.25f)) {
                                Divider(
                                    modifier = Modifier.padding(top = 24.dp, bottom = 24.dp)
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    context.eventManager.sendEvent(Event.LaunchAddWidget)
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colors.onSurface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 64.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_baseline_add_24),
                                    contentDescription = stringResource(id = R.string.add_widget),
                                    contentScale = ContentScale.FillHeight,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(Modifier.size(16.dp))
                                Text(
                                    text = stringResource(id = R.string.add_widget),
                                    style = MaterialTheme.typography.h5
                                )
                            }

                            Spacer(Modifier.size(16.dp))

                            FlowRow(
                                mainAxisAlignment = FlowMainAxisAlignment.SpaceBetween,
                                crossAxisAlignment = FlowCrossAxisAlignment.Center,
                                mainAxisSpacing = 8.dp,
                                crossAxisSpacing = 8.dp,
                                mainAxisSize = SizeMode.Expand
                            ) {
                                buttons.forEach {
                                    OutlinedButton(
                                        onClick = { it.onClick() },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colors.onSurface)
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Image(
                                                painter = painterResource(id = it.icon),
                                                contentDescription = stringResource(id = it.title)
                                            )
                                            Spacer(Modifier.size(8.dp))
                                            Text(text = stringResource(id = it.title))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                items(links.size, span = { GridItemSpan(maxLineSpan) }) {
                    val option = links[it]
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            if (option.onClick != null) {
                                option.onClick.invoke()
                            } else {
                                if (option.isEmail) {
                                    context.launchEmail(option.link, context.resources.getString(R.string.app_name))
                                } else {
                                    context.launchUrl(option.link)
                                }
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(id = option.icon),
                                contentDescription = stringResource(id = option.title)
                            )
                            
                            Spacer(Modifier.size(16.dp))
                            
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = stringResource(id = option.title),
                                    color = MaterialTheme.colors.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                Spacer(Modifier.size(4.dp))
                                
                                Text(
                                    text = stringResource(id = option.desc),
                                    style = MaterialTheme.typography.caption
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
