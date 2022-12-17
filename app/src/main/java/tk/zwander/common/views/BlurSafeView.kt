package tk.zwander.common.views

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.widget.FrameLayout

class BlurSafeView(context: Context, attrs: AttributeSet) : FrameLayout(context, attrs) {
    override fun draw(canvas: Canvas) {
        if (!canvas.isHardwareAccelerated) {
            background = null
        }
        
        super.draw(canvas)
    }
}