package tk.zwander.common.compose.util

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp

@Composable
fun insetsContentPadding(
    vararg types: WindowInsets,
    extraPadding: PaddingValues = PaddingValues(0.dp),
): PaddingValues {
    val direction = LocalLayoutDirection.current

    return types.toSet().reduce { acc, windowInsets ->
        acc.add(windowInsets)
    }.add(
        WindowInsets(
            left = extraPadding.calculateLeftPadding(direction),
            top = extraPadding.calculateTopPadding(),
            right = extraPadding.calculateRightPadding(direction),
            bottom = extraPadding.calculateBottomPadding()
        )
    ).asPaddingValues()
}
