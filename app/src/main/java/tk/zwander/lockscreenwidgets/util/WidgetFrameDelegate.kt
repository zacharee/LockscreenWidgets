package tk.zwander.lockscreenwidgets.util

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.database.ContentObserver
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.graphics.Point
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.ImageView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.widget_frame.view.*
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.activities.AddWidgetActivity
import tk.zwander.lockscreenwidgets.activities.RequestUnlockActivity
import tk.zwander.lockscreenwidgets.adapters.WidgetFrameAdapter
import tk.zwander.lockscreenwidgets.host.WidgetHostCompat
import kotlin.math.roundToInt
import kotlin.math.sign

class WidgetFrameDelegate private constructor(context: Context) : ContextWrapper(context), SharedPreferences.OnSharedPreferenceChangeListener {
    companion object {
        private var instance: WidgetFrameDelegate? = null

        fun getInstance(context: Context): WidgetFrameDelegate {
            return instance ?: WidgetFrameDelegate(context).also {
                instance = it
            }
        }
    }

    var updatedForMove = false

    val params = WindowManager.LayoutParams().apply {
        type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        width = dpAsPx(prefManager.frameWidthDp)
        height = dpAsPx(prefManager.frameHeightDp)

        x = prefManager.posX
        y = prefManager.posY

        gravity = Gravity.CENTER

        flags =
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        format = PixelFormat.RGBA_8888
    }
    val wallpaper = getSystemService(Context.WALLPAPER_SERVICE) as WallpaperManager
    val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val widgetManager = AppWidgetManager.getInstance(this)
    val widgetHost = WidgetHostCompat.getInstance(this, 1003) {
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, RequestUnlockActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }, 100)
    }
    val view = LayoutInflater.from(ContextThemeWrapper(this, R.style.AppTheme))
        .inflate(R.layout.widget_frame, null)
    val adapter = WidgetFrameAdapter(widgetManager, widgetHost, params) { item ->
        prefManager.currentWidgets = prefManager.currentWidgets.apply {
            remove(item)
            widgetHost.deleteAppWidgetId(item.id)
        }
    }
    val blockSnapHelper = SnapToBlock(1)
    val touchHelperCallback = object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT or ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            return adapter.onMove(viewHolder.adapterPosition, target.adapterPosition).also {
                if (it) {
                    updatedForMove = true
                    prefManager.currentWidgets = LinkedHashSet(adapter.widgets)
                    adapter.currentRemoveButtonPosition = -1
                    updatedForMove = false
                }
            }
        }

        override fun getDragDirs(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int {
            return if (viewHolder is WidgetFrameAdapter.AddWidgetVH) 0
            else super.getDragDirs(recyclerView, viewHolder)
        }

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
                viewHolder?.itemView?.alpha = 0.5f

                val adapterPos = viewHolder?.adapterPosition ?: -1
                adapter.currentRemoveButtonPosition = if (adapter.currentRemoveButtonPosition == adapterPos) -1 else adapterPos
            }

            super.onSelectedChanged(viewHolder, actionState)
        }

        override fun clearView(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ) {
            super.clearView(recyclerView, viewHolder)

            viewHolder.itemView.alpha = 1.0f
        }

        override fun interpolateOutOfBoundsScroll(
            recyclerView: RecyclerView,
            viewSize: Int,
            viewSizeOutOfBounds: Int,
            totalSize: Int,
            msSinceStartScroll: Long
        ): Int {
            val direction = sign(viewSizeOutOfBounds.toFloat()).toInt()
            return (viewSize * 0.01f * direction).roundToInt()
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
    }
    val nightModeListener = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            when (uri) {
                Settings.Secure.getUriFor(Settings.Secure.UI_NIGHT_MODE) -> {
                    adapter.notifyDataSetChanged()
                }
            }
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            PrefManager.KEY_CURRENT_WIDGETS -> {
                //Make sure the adapter knows of any changes to the widget list
                if (!updatedForMove) {
                    //Only run the update if it wasn't generated by a reorder event
                    adapter.updateWidgets(prefManager.currentWidgets.toList())
                }
            }
            PrefManager.KEY_PAGE_INDICATOR_BEHAVIOR -> {
                view.frame.updatePageIndicatorBehavior()
            }
            PrefManager.KEY_FRAME_ROW_COUNT,
            PrefManager.KEY_FRAME_COL_COUNT -> {
                updateSpanCountAndOrientation()
                adapter.notifyDataSetChanged()
            }
            PrefManager.KEY_OPACITY_MODE -> {
                view.frame.updateFrameBackground()
                updateWallpaperLayerIfNeeded()
            }
            PrefManager.KEY_SHOW_DEBUG_ID_VIEW,
                PrefManager.KEY_DEBUG_LOG -> {
                view.frame.updateDebugIdViewVisibility()
            }
        }
    }

    fun onCreate() {
        prefManager.prefs.registerOnSharedPreferenceChangeListener(this)
        view.widgets_pager.apply {
            adapter = this@WidgetFrameDelegate.adapter
            setHasFixedSize(true)
            blockSnapHelper.attachToRecyclerView(this)
            ItemTouchHelper(touchHelperCallback).attachToRecyclerView(this)
        }
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.UI_NIGHT_MODE),
            true,
            nightModeListener
        )

        updateSpanCountAndOrientation()
        adapter.updateWidgets(prefManager.currentWidgets.toList())

        blockSnapHelper.setSnapBlockCallback(object : SnapToBlock.SnapBlockCallback {
            override fun onBlockSnap(snapPosition: Int) {}
            override fun onBlockSnapped(snapPosition: Int) {
                prefManager.currentPage = snapPosition
            }
        })

        view.frame.onAddListener = {
            val intent = Intent(this, AddWidgetActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            startActivity(intent)
        }

        view.frame.attachmentStateListener = {
            if (it) {
                updateWallpaperLayerIfNeeded()
                widgetHost.startListening()
                adapter.notifyDataSetChanged()
            } else {
                widgetHost.stopListening()
            }
        }

        view.widgets_pager.layoutManager?.apply {
            try {
                scrollToPosition(prefManager.currentPage)
            } catch (e: Exception) {}
        }
    }

    fun onDestroy() {
        prefManager.prefs.unregisterOnSharedPreferenceChangeListener(this)
        contentResolver.unregisterContentObserver(nightModeListener)
    }

    fun updateSpanCountAndOrientation() {
        (view.widgets_pager.layoutManager as GridLayoutManager).apply {
            val rowCount = prefManager.frameRowCount

            this.spanCount = rowCount

            blockSnapHelper.maxFlingBlocks = rowCount
            blockSnapHelper.attachToRecyclerView(view.widgets_pager)
        }
    }

    @SuppressLint("MissingPermission")
    fun updateWallpaperLayerIfNeeded() {
        if (prefManager.opacityMode == PrefManager.VALUE_OPACITY_MODE_MASKED) {
            try {
                val fastW = wallpaper.fastDrawable

                fastW?.mutate()?.apply {
                    view.wallpaper_background.setImageDrawable(this)
                    view.wallpaper_background.scaleType = ImageView.ScaleType.MATRIX
                    view.wallpaper_background.imageMatrix = Matrix().apply {
                        val realSize = Point().apply { wm.defaultDisplay.getRealSize(this) }
                        val loc = view.locationOnScreen ?: intArrayOf(0, 0)

                        val dwidth: Int = intrinsicWidth
                        val dheight: Int = intrinsicHeight

                        val wallpaperAdjustmentX = (dwidth - realSize.x) / 2f
                        val wallpaperAdjustmentY = (dheight - realSize.y) / 2f

                        setTranslate(
                            (-loc[0].toFloat() - wallpaperAdjustmentX),
                            //TODO: LGUX 9 doesn't like this Y-translation for some reason
                            (-loc[1].toFloat() - wallpaperAdjustmentY)
                        )
                    }
                } ?: view.wallpaper_background.setImageDrawable(null)
            } catch (e: Exception) {
                view.wallpaper_background.setImageDrawable(null)
            }
        } else {
            view.wallpaper_background.setImageDrawable(null)
        }
    }
}