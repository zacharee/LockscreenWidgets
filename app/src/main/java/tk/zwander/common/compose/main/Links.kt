package tk.zwander.common.compose.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import tk.zwander.common.compose.data.MainPageLink
import tk.zwander.common.util.launchEmail
import tk.zwander.common.util.launchUrl
import tk.zwander.lockscreenwidgets.R
import tk.zwander.patreonsupportersretrieval.view.SupporterView

@Composable
fun rememberLinks(): List<MainPageLink> {
    val context = LocalContext.current

    return remember {
        listOf(
            MainPageLink(
                R.drawable.baseline_translate_24,
                R.string.translate,
                R.string.translate_desc,
                "https://crowdin.com/project/lockscreen-widgets",
            ),
            MainPageLink(
                R.drawable.info,
                R.string.privacy_policy,
                R.string.main_screen_privacy_policy_desc,
                "https://github.com/zacharee/LockscreenWidgets/blob/master/PRIVACY.md",
            ),
            MainPageLink(
                R.drawable.ic_baseline_mastodon_24,
                R.string.main_screen_social_mastodon,
                R.string.main_screen_social_mastodon_desc,
                "https://androiddev.social/@wander1236",
            ),
            MainPageLink(
                R.drawable.ic_baseline_earth_24,
                R.string.main_screen_social_website,
                R.string.main_screen_social_website_desc,
                "https://zwander.dev",
            ),
            MainPageLink(
                R.drawable.ic_baseline_email_24,
                R.string.main_screen_social_email,
                R.string.main_screen_social_email_desc,
                "mailto:zachary@zwander.dev",
            ),
            MainPageLink(
                R.drawable.ic_baseline_github_24,
                R.string.main_screen_social_github,
                R.string.main_screen_social_github_desc,
                "https://github.com/zacharee/LockscreenWidgets",
            ),
            MainPageLink(
                R.drawable.ic_baseline_patreon_24,
                R.string.main_screen_social_patreon,
                R.string.main_screen_social_patreon_desc,
                "https://patreon.com/zacharywander",
            ),
            MainPageLink(
                R.drawable.ic_baseline_heart_24,
                R.string.supporters,
                R.string.supporters_desc,
            ) {
                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.supporters)
                    .setView(SupporterView(context))
                    .show()
            },
        )
    }
}

@Composable
fun LinkItem(
    option: MainPageLink,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resources = LocalResources.current

    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = {
            if (option.onClick != null) {
                option.onClick.invoke()
            } else {
                if (option.isEmail) {
                    context.launchEmail(
                        option.link,
                        resources.getString(R.string.app_name),
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
            verticalAlignment = Alignment.CenterVertically,
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
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(Modifier.size(4.dp))

                Text(
                    text = stringResource(id = option.desc),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}