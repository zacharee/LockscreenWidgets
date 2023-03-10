package tk.zwander.lockscreenwidgets.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.preference.PreferenceFragmentCompat
import tk.zwander.common.activities.BaseActivity
import tk.zwander.lockscreenwidgets.databinding.ActivitySettingsBinding
import tk.zwander.lockscreenwidgets.fragments.SettingsFragment

/**
 * Host the app's settings. Most of the logic is either in [SettingsFragment]
 * or behind-the-scenes in AndroidX.
 */
class SettingsActivity : BaseActivity(true) {
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

        val binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val fragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            fragmentClass.canonicalName ?: throw IllegalStateException("Unable to instantiate $fragmentClass. Canonical name null!")
        )
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(binding.fragment.id, fragment, null)
            .commitNowAllowingStateLoss()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
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