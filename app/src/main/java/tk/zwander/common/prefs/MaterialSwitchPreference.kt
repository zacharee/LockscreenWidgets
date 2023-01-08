package tk.zwander.common.prefs

import android.content.Context
import android.util.AttributeSet
import androidx.preference.SwitchPreferenceCompat
import tk.zwander.lockscreenwidgets.R

class MaterialSwitchPreference : SwitchPreferenceCompat {
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context) : super(context)

    init {
        widgetLayoutResource = R.layout.material_switch_layout
    }
}