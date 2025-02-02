package tk.zwander.common.compose.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

open class BasePreference<ValueType>(
    val title: @Composable () -> String,
    val summary: @Composable () -> String?,
    val key: @Composable () -> String,
    val icon: @Composable () -> Painter?,
    val defaultValue: @Composable () -> ValueType,
    val onClick: (() -> Unit)? = null,
    val widget: (@Composable () -> Unit)? = null,
    val widgetPosition: @Composable () -> WidgetPosition = { WidgetPosition.END },
    val enabled: @Composable () -> Boolean = { true },
    val visible: @Composable () -> Boolean = { true },
) {
    @Composable
    open fun Render(modifier: Modifier) {
        BasePreferenceLayout(
            title = title(),
            summary = summary(),
            modifier = modifier,
            icon = icon(),
            onClick = onClick,
            widget = widget,
            widgetPosition = widgetPosition(),
            enabled = enabled(),
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
    summaryMaxLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
) {
    val animatedAlpha by animateFloatAsState(if (enabled) 1f else 0.7f)

    Surface(
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .alpha(animatedAlpha)
                .then(
                    if (onClick != null && enabled) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier
                    },
                )
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier.width(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    icon?.let {
                        Icon(
                            painter = icon,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .animateContentSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                    )

                    AnimatedVisibility(
                        visible = summary != null,
                        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                    ) {
                        val tempSummary by remember { mutableStateOf(summary ?: "") }

                        Text(
                            text = tempSummary,
                            maxLines = summaryMaxLines,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                if (widgetPosition == WidgetPosition.END) {
                    widget?.invoke()
                }
            }

            if (widgetPosition == WidgetPosition.BOTTOM) {
                widget?.invoke()
            }
        }
    }
}

enum class WidgetPosition {
    END,
    BOTTOM,
    ;
}
