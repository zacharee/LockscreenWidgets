package tk.zwander.common.compose.main

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import tk.zwander.lockscreenwidgets.R
import tk.zwander.common.compose.data.MainPageLink
import tk.zwander.lockscreenwidgets.util.launchEmail
import tk.zwander.lockscreenwidgets.util.launchUrl
import tk.zwander.patreonsupportersretrieval.view.SupporterView

@Composable
fun rememberLinks(): List<MainPageLink> {
    val context = LocalContext.current

    return remember {
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
                "mailto:zachary@zwander.dev"
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
}

@Suppress("OPT_IN_IS_NOT_ENABLED")
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun LinkItem(option: MainPageLink) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            if (option.onClick != null) {
                option.onClick.invoke()
            } else {
                if (option.isEmail) {
                    context.launchEmail(
                        option.link,
                        context.resources.getString(R.string.app_name)
                    )
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