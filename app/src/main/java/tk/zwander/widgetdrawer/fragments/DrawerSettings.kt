package tk.zwander.widgetdrawer.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.preference.*
import androidx.recyclerview.widget.RecyclerView
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.util.*

class DrawerSettings : PreferenceFragmentCompat() {
    private val prefsHandler = HandlerRegistry {
        handler(PrefManager.KEY_DRAWER_ENABLED) {
            findPreference<SwitchPreference>(PrefManager.KEY_DRAWER_ENABLED)?.isChecked =
                requireContext().prefManager.drawerEnabled
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.prefs_drawer, rootKey)
        prefsHandler.register(requireContext())

        findPreference<Preference>("open_drawer")?.setOnPreferenceClickListener {
            requireContext().eventManager.sendEvent(Event.ShowDrawer)
            true
        }
    }

    @SuppressLint("RestrictedApi")
    override fun onCreateAdapter(preferenceScreen: PreferenceScreen): RecyclerView.Adapter<*> {
        return object : PreferenceGroupAdapter(preferenceScreen) {
            @SuppressLint("RestrictedApi")
            override fun onBindViewHolder(holder: PreferenceViewHolder, position: Int) {
                super.onBindViewHolder(holder, position)
                val preference = getItem(position)
                if (preference is PreferenceCategory)
                    setZeroPaddingToLayoutChildren(holder.itemView)
                else
                    holder.itemView.findViewById<View?>(R.id.icon_frame)?.visibility =
                        if (preference?.icon == null) View.GONE else View.VISIBLE
            }
        }
    }

    private fun setZeroPaddingToLayoutChildren(view: View) {
        if (view !is ViewGroup)
            return
        val childCount = view.childCount
        for (i in 0 until childCount) {
            setZeroPaddingToLayoutChildren(view.getChildAt(i))
            view.setPaddingRelative(0, view.paddingTop, view.paddingEnd, view.paddingBottom)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        prefsHandler.unregister(requireContext())
    }
}