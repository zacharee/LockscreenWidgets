package tk.zwander.common.compose

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import tk.zwander.lockscreenwidgets.R

@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

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
