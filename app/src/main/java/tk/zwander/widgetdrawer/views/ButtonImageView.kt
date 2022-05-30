package tk.zwander.widgetdrawer.views

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import tk.zwander.helperlib.dpAsPx

class ButtonImageView : AppCompatImageView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    init {
        val attr = intArrayOf(android.R.attr.selectableItemBackgroundBorderless)
        val array = context.obtainStyledAttributes(attr)
        val drawable = array.getDrawable(0)
        array.recycle()

        isClickable = true
        isFocusable = true
        background = drawable

        val padding = context.dpAsPx(8)
        setPadding(padding, padding, padding, padding)

        imageTintList = ColorStateList.valueOf(Color.WHITE)
    }
}