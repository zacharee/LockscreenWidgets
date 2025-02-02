package tk.zwander.common.compose.settings

import android.graphics.drawable.Drawable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import tk.zwander.lockscreenwidgets.R

data class PreferenceCategory(
    val title: String?,
    val key: String,
    val items: List<BasePreference<*>>,
    val icon: Drawable? = null,
    val collapsible: Boolean = true,
    val enabled: @Composable () -> Boolean = { true },
)

@Composable
fun PreferenceCategory(
    category: PreferenceCategory,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    @Suppress("SimplifiableCallChain")
    BasePreferenceLayout(
        title = category.title ?: "",
        summary = if (!expanded) category.items.take(3).map { it.title() }
            .joinToString(", ") else null,
        onClick = if (category.collapsible) {{ onExpandChange(!expanded) }} else null,
        icon = category.icon?.let { rememberDrawablePainter(category.icon) },
        widget = {
            val rotation by animateFloatAsState(if (expanded) 0f else 180f)

            Icon(
                painter = painterResource(R.drawable.arrow_up),
                contentDescription = null,
                modifier = Modifier.rotate(rotation),
            )
        },
        modifier = modifier,
        summaryMaxLines = 1,
        enabled = enabled,
    )
}
