package tk.zwander.common.views.remote

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.AttributeSet
import android.widget.TextClock

class CatchingTextClock(context: Context, attrs: AttributeSet) : TextClock(context, attrs) {
    private val voidHandler = object : Handler(Looper.getMainLooper()) {
        override fun dispatchMessage(msg: Message) {}
        override fun handleMessage(msg: Message) {}
        override fun sendMessageAtTime(msg: Message, uptimeMillis: Long): Boolean = true
    }

    override fun getHandler(): Handler {
        return super.getHandler() ?: voidHandler
    }
}
