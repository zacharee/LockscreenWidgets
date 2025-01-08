package tk.zwander.common.util.compat

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatViewInflater

class LayoutInflaterFactory2Compat : LayoutInflater.Factory2 {
    private val compatInflater = AppCompatViewInflater()

    override fun onCreateView(name: String, context: Context, attrs: AttributeSet): View? {
        return onCreateView(null, name, context, attrs)
    }

    override fun onCreateView(
        parent: View?,
        name: String,
        context: Context,
        attrs: AttributeSet
    ): View? {
        return compatInflater.createView(
            parent, name, context, attrs,
            true, false, true, false,
        )
    }
}