package tk.zwander.common.util

import android.content.Context
import android.content.ContextWrapper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import tk.zwander.common.views.remote.CatchingListView
import tk.zwander.common.views.remote.CatchingTextClock

class RemoteViewsLayoutInflaterContext(
    context: Context,
    private val widgetId: Int,
) : ContextWrapper(context) {
    private var inflater: LayoutInflater? = null

    override fun getSystemService(name: String): Any? {
        if (LAYOUT_INFLATER_SERVICE == name) {
            return inflater ?: LayoutInflater.from(baseContext).cloneInContext(this).also { layoutInflater ->
                layoutInflater.factory2 = object : LayoutInflater.Factory2 {
                    override fun onCreateView(
                        parent: View?,
                        name: String,
                        context: Context,
                        attrs: AttributeSet,
                    ): View? {
                        return when (name) {
                            "ListView" -> CatchingListView(context, attrs, widgetId)
                            "TextClock" -> CatchingTextClock(context, attrs)
                            else -> null
                        }
                    }

                    override fun onCreateView(
                        name: String,
                        context: Context,
                        attrs: AttributeSet,
                    ): View? {
                        return onCreateView(null, name, context, attrs)
                    }
                }
                inflater = layoutInflater
            }
        }

        return super.getSystemService(name)
    }
}
