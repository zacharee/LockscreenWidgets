package tk.zwander.common.compose.settings

import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter

open class BasePreference<ValueType>(
    val title: String,
    val summary: String?,
    val key: String,
    val icon: Drawable?,
    val defaultValue: ValueType,
    val onClick: (() -> Unit)? = null,
    val widget: (@Composable () -> Unit)? = null,
    val widgetPosition: WidgetPosition = WidgetPosition.END,
) {
    @Composable
    open fun Render(modifier: Modifier) {
        BasePreferenceLayout(
            title = title,
            summary = summary,
            modifier = modifier,
            icon = icon?.let { rememberDrawablePainter(icon) },
            onClick = onClick,
            widget = widget,
            widgetPosition = widgetPosition,
        )
    }
}

@Composable
fun BasePreferenceLayout(
    title: String,
    summary: String?,
    modifier: Modifier = Modifier.fillMaxWidth(),
    icon: Painter? = null,
    onClick: (() -> Unit)? = null,
    widget: (@Composable () -> Unit)? = null,
    widgetPosition: WidgetPosition = WidgetPosition.END,
) {
    Row(
        modifier = modifier.then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            }.padding(vertical = 16.dp, horizontal = 8.dp),
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            icon?.let {
                Icon(
                    painter = icon,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
            )

            AnimatedVisibility(visible = summary != null) {
                val tempSummary by remember { mutableStateOf(summary ?: "") }

                Text(
                    text = tempSummary,
                )
            }

            if (widgetPosition == WidgetPosition.BOTTOM) {
                widget?.invoke()
            }
        }

        if (widgetPosition == WidgetPosition.END) {
            widget?.invoke()
        }
    }
}

enum class WidgetPosition {
    END,
    BOTTOM,
    ;
}
