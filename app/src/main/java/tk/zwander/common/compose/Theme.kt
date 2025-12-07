package tk.zwander.common.compose

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import tk.zwander.common.util.LSDisplayManager
import tk.zwander.common.util.requireLsDisplayManager
import tk.zwander.lockscreenwidgets.R

val LocalLSDisplayManager = compositionLocalOf<LSDisplayManager> { error("LSDisplayManager not provided!") }

@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val lsDisplayManager = remember {
        context.requireLsDisplayManager
    }

    CompositionLocalProvider(
        LocalLSDisplayManager provides lsDisplayManager,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) {
                    dynamicDarkColorScheme(context)
                } else {
                    dynamicLightColorScheme(context)
                },
                content = content
            )
        } else {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) {
                    darkColorScheme(
                        primary = colorResource(R.color.colorPrimary),
                        primaryContainer = colorResource(R.color.colorPrimaryDark),
                        secondary = colorResource(R.color.colorAccent),
                    )
                } else {
                    lightColorScheme(
                        primary = colorResource(R.color.colorPrimary),
                        primaryContainer = colorResource(R.color.colorPrimaryDark),
                        secondary = colorResource(R.color.colorAccent),
                    )
                },
                content = content,
            )
        }
    }
}
