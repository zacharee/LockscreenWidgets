package tk.zwander.common.compose.settings

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
fun rememberPreferenceScreen(vararg keys: Any? = arrayOf(), content: PreferenceScreenScope.() -> Unit): List<PreferenceCategory> {
    val context = LocalContext.current

    return remember(keys) {
        context.preferenceScreen(content)
    }
}

fun Context.preferenceScreen(content: PreferenceScreenScope.() -> Unit): List<PreferenceCategory> {
    val screenScope = PreferenceScreenScope(this)
    screenScope.content()

    return screenScope.categories
}

class PreferenceScreenScope(private val context: Context) {
    val categories: MutableList<PreferenceCategory> = mutableListOf()

    fun category(
        key: String,
        title: String? = null,
        icon: Drawable? = null,
        collapsible: Boolean = true,
        content: PreferenceCategoryScope.() -> Unit,
    ) {
        val categoryScope = PreferenceCategoryScope(context)
        categoryScope.content()

        categories.add(PreferenceCategory(
            title = title,
            key = key,
            items = categoryScope.prefs,
            icon = icon,
            collapsible = collapsible,
        ))
    }
}

class PreferenceCategoryScope(private val context: Context) {
    val prefs: MutableList<BasePreference<*>> = mutableListOf()

    fun preference(pref: BasePreference<*>) {
        prefs.add(pref)
    }

    fun switchPreference(
        title: String,
        summary: String?,
        key: String,
        defaultValue: Boolean = false,
        icon: Drawable? = null,
        enabled: @Composable () -> Boolean = { true },
    ) {
        preference(SwitchPreference(
            title = title,
            summary = summary,
            key = key,
            defaultValue = defaultValue,
            icon = icon,
            enabled = enabled,
        ))
    }

    fun seekBarPreference(
        title: String,
        summary: String?,
        key: String,
        defaultValue: Int,
        minValue: Int,
        maxValue: Int,
        scale: Double,
        icon: Drawable? = null,
        unit: String? = null,
        increment: Int = 1,
        enabled: @Composable () -> Boolean = { true },
    ) {
        preference(SeekBarPreference(
            title = title,
            summary = summary,
            key = key,
            defaultValue = defaultValue,
            minValue = minValue,
            maxValue = maxValue,
            scale = scale,
            icon = icon,
            unit = unit,
            increment = increment,
            enabled = enabled,
        ))
    }

    fun colorPickerPreference(
        title: String,
        summary: String?,
        key: String,
        defaultValue: Int,
        icon: Drawable? = null,
        enabled: @Composable () -> Boolean = { true },
    ) {
        preference(ColorPickerPreference(
            title = title,
            summary = summary,
            key = key,
            defaultValue = defaultValue,
            icon = icon,
            enabled = enabled,
        ))
    }
}
