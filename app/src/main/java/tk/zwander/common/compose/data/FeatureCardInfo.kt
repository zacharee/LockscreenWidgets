package tk.zwander.common.compose.data

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import tk.zwander.common.data.MainPageButton
import tk.zwander.common.util.EventObserver

data class FeatureCardInfo(
    @StringRes
    val title: Int,
    @StringRes
    val description: Int? = null,
    val buttons: List<MainPageButton> = [],
    val eventObserver: EventObserver? = null,
    val action: ActionInfo? = null,
    val enabled: EnabledInfo? = null,
)

data class ActionInfo(
    @StringRes
    val label: Int,
    @DrawableRes
    val icon: Int? = null,
    val onAction: () -> Unit,
)

data class EnabledInfo(
    @StringRes
    val enabledLabel: Int,
    @StringRes
    val disabledLabel: Int? = null,
    val key: String,
    val isEnabled: () -> Boolean,
    val onEnabledChanged: (Boolean) -> Unit,
)
