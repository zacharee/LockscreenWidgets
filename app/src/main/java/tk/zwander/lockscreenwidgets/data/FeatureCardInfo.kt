package tk.zwander.lockscreenwidgets.data

data class FeatureCardInfo(
    val title: Int,
    val version: String,
    val enabledLabel: Int,
    val enabledKey: String,
    val buttons: List<MainPageButton>,
    val onAddWidget: () -> Unit,
    val isEnabled: () -> Boolean,
    val onEnabledChanged: (Boolean) -> Unit,
)