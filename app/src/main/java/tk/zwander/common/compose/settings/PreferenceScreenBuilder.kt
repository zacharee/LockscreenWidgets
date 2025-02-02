package tk.zwander.common.compose.settings

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
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

    fun <T> preference(
        title: @Composable () -> String,
        summary: @Composable () -> String?,
        key: @Composable () -> String,
        icon: @Composable () -> Painter?,
        defaultValue: @Composable () -> T,
        onClick: (() -> Unit)? = null,
        widget: (@Composable () -> Unit)? = null,
        widgetPosition: @Composable () -> WidgetPosition = { WidgetPosition.END },
        enabled: @Composable () -> Boolean = { true },
        visible: @Composable () -> Boolean = { true },
    ) {
        preference(
            BasePreference<T>(
            title = title,
            summary = summary,
            key = key,
            icon = icon,
            defaultValue = defaultValue,
            onClick = onClick,
            widget = widget,
            widgetPosition = widgetPosition,
            enabled = enabled,
            visible = visible,
        )
        )
    }

    fun switchPreference(
        title: @Composable () -> String,
        summary: @Composable () -> String?,
        key: @Composable () -> String,
        defaultValue: @Composable () -> Boolean = { false },
        icon: @Composable () -> Painter? = { null },
        enabled: @Composable () -> Boolean = { true },
        visible: @Composable () -> Boolean = { true },
        canChange: (Boolean) -> Boolean = { true },
    ) {
        preference(SwitchPreference(
            title = title,
            summary = summary,
            key = key,
            defaultValue = defaultValue,
            icon = icon,
            enabled = enabled,
            visible = visible,
            canChange = canChange,
        ))
    }

    fun seekBarPreference(
        title: @Composable () -> String,
        summary: @Composable () -> String?,
        key: @Composable () -> String,
        defaultValue: @Composable () -> Int,
        minValue: @Composable () -> Int,
        maxValue: @Composable () -> Int,
        scale: @Composable () -> Double,
        icon: @Composable () -> Painter? = { null },
        unit: @Composable () -> String? = { null },
        increment: @Composable () -> Int = { 1 },
        enabled: @Composable () -> Boolean = { true },
        visible: @Composable () -> Boolean = { true },
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
            visible = visible,
        ))
    }

    fun colorPickerPreference(
        title: @Composable () -> String,
        summary: @Composable () -> String?,
        key: @Composable () -> String,
        defaultValue: @Composable () -> Int,
        icon: @Composable () -> Painter? = { null },
        enabled: @Composable () -> Boolean = { true },
        visible: @Composable () -> Boolean = { true },
    ) {
        preference(ColorPickerPreference(
            title = title,
            summary = summary,
            key = key,
            defaultValue = defaultValue,
            icon = icon,
            enabled = enabled,
            visible = visible,
        ))
    }
}
