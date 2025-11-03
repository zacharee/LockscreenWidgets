package tk.zwander.common.compose.util

import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import tk.zwander.common.util.setThemedContent
import tk.zwander.common.util.themedLayoutInflater
import tk.zwander.lockscreenwidgets.databinding.ComposeViewHolderBinding

context(context: Context)
fun <T> T.createComposeViewHolder(block: @Composable T.(View) -> Unit): View {
    return ComposeViewHolderBinding.inflate(context.themedLayoutInflater).root.apply {
        setThemedContent { this@createComposeViewHolder.block(this) }
    }
}
