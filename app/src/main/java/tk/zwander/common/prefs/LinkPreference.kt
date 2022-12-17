package tk.zwander.common.prefs

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import tk.zwander.lockscreenwidgets.R
import tk.zwander.common.util.launchEmail
import tk.zwander.common.util.launchUrl

/**
 * A Preference that opens a link or sends an email (defined in XML).
 */
class LinkPreference(context: Context, attrs: AttributeSet) : Preference(context, attrs) {
    companion object {
        const val TYPE_LINK = 0
        const val TYPE_EMAIL = 1
    }

    private var linkText: String? = null
    private var linkType: Int = TYPE_LINK

    init {
        val array = context.theme.obtainStyledAttributes(attrs, R.styleable.LinkPreference, 0, 0)

        linkText = array.getString(R.styleable.LinkPreference_link)
        linkType = array.getInt(R.styleable.LinkPreference_link_type, linkType)
    }

    override fun onClick() {
        if (linkType == TYPE_LINK) {
            context.launchUrl(linkText ?: return)
        } else if (linkType == TYPE_EMAIL) {
            context.launchEmail("zachary@zwander.dev", context.resources.getString(R.string.app_name))
        }
    }
}