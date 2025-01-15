package tk.zwander.common.compose.data

import tk.zwander.common.data.MainPageButton
import tk.zwander.common.util.EventObserver

data class FeatureCardInfo(
    val title: Int,
    val version: String,
    val enabledLabel: Int,
    val disabledLabel: Int,
    val enabledKey: String,
    val buttons: List<MainPageButton>,
    val onAddWidget: () -> Unit,
    val isEnabled: () -> Boolean,
    val onEnabledChanged: (Boolean) -> Unit,
    val eventObserver: EventObserver? = null,
)