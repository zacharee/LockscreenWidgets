package tk.zwander.lockscreenwidgets.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentContainerView
import androidx.preference.PreferenceFragmentCompat
import tk.zwander.common.activities.BaseActivity
import tk.zwander.common.compose.AppTheme
import tk.zwander.common.compose.components.TitleBar
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.fragments.SettingsFragment

/**
 * Host the app's settings. Most of the logic is either in [SettingsFragment]
 * or behind-the-scenes in AndroidX.
 */
class SettingsActivity : BaseActivity() {
    companion object {
        private const val EXTRA_FRAGMENT_CLASS = "fragment_class"

        fun launch(context: Context, fragment: Class<out PreferenceFragmentCompat>) {
            context.startActivity(Intent(context, SettingsActivity::class.java).apply {
                putExtra(EXTRA_FRAGMENT_CLASS, fragment)
            })
        }
    }

    @Suppress("UNCHECKED_CAST")
    private val fragmentClass by lazy { intent.getSerializableExtra(EXTRA_FRAGMENT_CLASS) as? Class<out PreferenceFragmentCompat> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val fragmentClass = fragmentClass

        if (fragmentClass == null) {
            finish()
            return
        }

        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                        .statusBarsPadding(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        TitleBar(title = title.toString())

                        val bottomPadding = with(LocalDensity.current) { WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().toPx() }

                        AndroidView(
                            factory = {
                                FragmentContainerView(it).apply {
                                    id = R.id.content
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                                .weight(1f),
                        ) { view ->
                            val fragment = supportFragmentManager.fragmentFactory.instantiate(
                                classLoader,
                                fragmentClass.canonicalName ?: throw IllegalStateException("Unable to instantiate $fragmentClass. Canonical name null!")
                            )

                            fragment.arguments = bundleOf("bottomInset" to bottomPadding)

                            supportFragmentManager.beginTransaction()
                                .setReorderingAllowed(true)
                                .replace(view.id, fragment, null)
                                .commitNowAllowingStateLoss()
                        }
                    }
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}