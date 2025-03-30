@file:Suppress("unused")

package tk.zwander.common.util.contracts

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityOptionsCompat
import androidx.fragment.app.Fragment
import com.anggrayudi.storage.file.DocumentFileCompat
import com.anggrayudi.storage.media.FileDescription

@Composable
fun rememberCreateDocumentLauncherWithDownloadFallback(
    mimeType: String = "*/*",
    onResult: (Uri?) -> Unit,
): ActivityResultLauncher<String> {
    val context = LocalContext.current

    return rememberLauncherForActivityResultWithFallback(
        contract = ActivityResultContracts.CreateDocument(mimeType),
        fallback = {
            DocumentFileCompat.createDownloadWithMediaStoreFallback(
                context,
                FileDescription(it, "", mimeType),
            )?.uri
        },
        onResult = onResult,
    )
}

@Composable
fun <I, O> rememberLauncherForActivityResultWithFallback(
    contract: ActivityResultContract<I, O>,
    fallback: (I) -> O,
    onResult: (O) -> Unit,
): ActivityResultLauncher<I> {
    val realLauncher = rememberLauncherForActivityResult(contract, onResult)

    return remember(realLauncher, fallback) {
        object : ActivityResultLauncher<I>() {
            override val contract: ActivityResultContract<I, *>
                get() = realLauncher.contract

            @Suppress("DeprecatedCallableAddReplaceWith")
            @Deprecated("Registration is automatically handled by rememberLauncherForActivityResult")
            override fun unregister() {
                @Suppress("DEPRECATION")
                realLauncher.unregister()
            }

            override fun launch(input: I, options: ActivityOptionsCompat?) {
                try {
                    realLauncher.launch(input, options)
                } catch (_: ActivityNotFoundException) {
                    onResult(fallback(input))
                }
            }
        }
    }
}

fun Fragment.registerCreateDocumentLauncherWithDownloadFallback(
    mimeType: String = "*/*",
    onResult: (Uri?) -> Unit,
): ActivityResultLauncher<String> {
    return registerForActivityResultWithFallback(
        contract = ActivityResultContracts.CreateDocument(mimeType),
        fallback = {
            DocumentFileCompat.createDownloadWithMediaStoreFallback(
                requireContext(),
                FileDescription(it, "", mimeType),
            )?.uri
        },
        onResult = onResult,
    )
}

fun <I, O> ActivityResultCaller.registerForActivityResultWithFallback(
    contract: ActivityResultContract<I, O>,
    fallback: (I) -> O,
    onResult: (O) -> Unit,
): ActivityResultLauncher<I> {
    val realLauncher = registerForActivityResult(contract, onResult)

    return object : ActivityResultLauncher<I>() {
        override val contract: ActivityResultContract<I, *>
            get() = realLauncher.contract

        @Suppress("DeprecatedCallableAddReplaceWith")
        @Deprecated("Registration is automatically handled by rememberLauncherForActivityResult")
        override fun unregister() {
            realLauncher.unregister()
        }

        override fun launch(input: I, options: ActivityOptionsCompat?) {
            try {
                realLauncher.launch(input, options)
            } catch (_: ActivityNotFoundException) {
                onResult(fallback(input))
            }
        }
    }
}
