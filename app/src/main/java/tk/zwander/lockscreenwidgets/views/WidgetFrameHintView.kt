package tk.zwander.lockscreenwidgets.views

import android.content.Context
import android.text.method.ScrollingMovementMethod
import android.util.AttributeSet
import androidx.core.view.isVisible
import com.google.android.material.card.MaterialCardView
import kotlinx.android.synthetic.main.widget_frame_hint.view.*
import tk.zwander.lockscreenwidgets.util.prefManager

class WidgetFrameHintView(context: Context, attrs: AttributeSet) : MaterialCardView(context, attrs) {
    override fun onFinishInflate() {
        super.onFinishInflate()

        close_hint.setOnClickListener {
            isVisible = false
            context.prefManager.firstViewing = false
        }

        hint_text.movementMethod = ScrollingMovementMethod()
    }
}