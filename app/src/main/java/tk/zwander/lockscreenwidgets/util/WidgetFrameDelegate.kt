package tk.zwander.lockscreenwidgets.util

import android.annotation.SuppressLint
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.database.ContentObserver
import android.graphics.Canvas
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
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.arasthel.spannedgridlayoutmanager.SpannedGridLayoutManager
import kotlinx.android.synthetic.main.widget_frame.view.*
import tk.zwander.lockscreenwidgets.IRemoveConfirmCallback
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.activities.AddWidgetActivity
import tk.zwander.lockscreenwidgets.activities.RemoveWidgetDialogActivity
import tk.zwander.lockscreenwidgets.activities.RequestUnlockActivity
import tk.zwander.lockscreenwidgets.adapters.WidgetFrameAdapter
import tk.zwander.lockscreenwidgets.host.WidgetHostCompat
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Handle most of the logic involving the widget frame.
 * TODO: make this work with multiple frame "clients" (i.e. a preview in MainActivity).
 */
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
    var showingRemovalConfirmation = false

    //The size, position, and such of the widget frame on the lock screen.
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
    //The actual frame View
    val view = LayoutInflater.from(ContextThemeWrapper(this, R.style.AppTheme))
        .inflate(R.layout.widget_frame, null)
    var gridLayoutManager = SpannedLayoutManager()
    val adapter = WidgetFrameAdapter(widgetManager, widgetHost, params) { adapter, item ->
        showingRemovalConfirmation = true
        RemoveWidgetDialogActivity.start(this, object : IRemoveConfirmCallback.Stub() {
            override fun onWidgetRemovalConfirmed() {
                prefManager.currentWidgets = prefManager.currentWidgets.apply {
                    remove(item)
                    widgetHost.deleteAppWidgetId(item.id)
                }
                adapter.currentEditingInterfacePosition = -1
            }

            override fun onDismiss() {
                showingRemovalConfirmation = false
            }
        })
    }
    val blockSnapHelper = SnapToBlock(1)
    val touchHelperCallback = object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT or ItemTouchHelper.UP or ItemTouchHelper.DOWN,
        ItemTouchHelper.DOWN
    ) {
        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            return adapter.onMove(viewHolder.adapterPosition, target.adapterPosition).also {
                if (it) {
                    updatedForMove = true
                    prefManager.currentWidgets = LinkedHashSet(adapter.widgets)
                    adapter.currentEditingInterfacePosition = -1
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

        override fun getSwipeDirs(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int {
            return if (viewHolder is WidgetFrameAdapter.AddWidgetVH) 0
            else super.getSwipeDirs(recyclerView, viewHolder)
        }

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
                viewHolder?.itemView?.alpha = 0.5f
            }

            super.onSelectedChanged(viewHolder, actionState)
        }

        override fun clearView(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ) {
            super.clearView(recyclerView, viewHolder)

            viewHolder.itemView.alpha = 1.0f
            viewHolder.itemView.translationY = 0f
        }

        override fun interpolateOutOfBoundsScroll(
            recyclerView: RecyclerView,
            viewSize: Int,
            viewSizeOutOfBounds: Int,
            totalSize: Int,
            msSinceStartScroll: Long
        ): Int {
            //The default scrolling speed is *way* too fast. Slow it down a bit.
            val direction = sign(viewSizeOutOfBounds.toFloat()).toInt()
            return (viewSize * 0.01f * direction).roundToInt()
        }

        override fun onChildDraw(
            c: Canvas,
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            dX: Float,
            dY: Float,
            actionState: Int,
            isCurrentlyActive: Boolean
        ) {
            viewHolder.itemView.translationY = min(dY, viewHolder.itemView.height / 2f)
        }

        override fun getSwipeEscapeVelocity(defaultValue: Float): Float {
            return Float.POSITIVE_INFINITY
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            //The user has swiped a widget. Show the editing UI on that widget.
            //If the UI is already shown on it, hide it.
            val adapterPos = viewHolder.adapterPosition
            adapter.currentEditingInterfacePosition = if (adapter.currentEditingInterfacePosition == adapterPos) -1 else adapterPos
            viewHolder.itemView.translationY = 0f
            viewHolder.itemView.alpha = 1f
            adapter.notifyItemChanged(adapterPos)
        }
    }
    //Some widgets display differently depending on the system's dark mode.
    //Make sure the widgets are rebound if there's a change.
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
                updateRowCount()
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
        gridLayoutManager.spanSizeLookup = adapter.spanSizeLookup
//        gridLayoutManager.customWidth = widgetBlockWidth
        view.widgets_pager.apply {
            adapter = this@WidgetFrameDelegate.adapter
            layoutManager = gridLayoutManager
            setHasFixedSize(true)
            blockSnapHelper.attachToRecyclerView(this)
            ItemTouchHelper(touchHelperCallback).attachToRecyclerView(this)
        }
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.UI_NIGHT_MODE),
            true,
            nightModeListener
        )

        updateRowCount()
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

        //We only really want to be listening to widget changes
        //while the frame is on-screen. Otherwise, we're wasting battery.
        view.frame.attachmentStateListener = {
            if (it) {
                updateWallpaperLayerIfNeeded()
                widgetHost.startListening()
                //Even with the startListening() call above,
                //it doesn't seem like pending updates always get
                //dispatched. Rebinding all the widgets forces
                //them to update.
                adapter.notifyDataSetChanged()
            } else {
                widgetHost.stopListening()
            }
        }

//        gridLayoutManager.spanSizeLookup = adapter.spanSizeLookup

        //Scroll to the stored page, making sure to catch a potential
        //out-of-bounds error.
        gridLayoutManager.apply {
            try {
                scrollToPosition(prefManager.currentPage)
            } catch (e: Exception) {}
        }
    }

    fun onDestroy() {
        prefManager.prefs.unregisterOnSharedPreferenceChangeListener(this)
        contentResolver.unregisterContentObserver(nightModeListener)
    }

    /**
     * Make sure the number of rows in the widget frame reflects the user-selected value.
     */
    fun updateRowCount() {
        gridLayoutManager.apply {
            val rowCount = prefManager.frameRowCount
            val colCount = prefManager.frameColCount

            this.verticalSpanCount = rowCount
            this.horizontalSpanCount = colCount

            blockSnapHelper.maxFlingBlocks = rowCount
            blockSnapHelper.attachToRecyclerView(view.widgets_pager)
        }
    }

    /**
     * Compute and draw the appropriate portion of the wallpaper as the widget background,
     * if the opacity mode is set to masked.
     *
     * TODO: this doesn't work properly on a lot of devices. It seems to be something to do with the scale.
     * TODO: I don't know enough about [Matrix]es to fix it.
     * TODO: There also doesn't seem to be a way to retrieve the current lock screen wallpaper;
     * TODO: only the home screen's.
     */
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

    inner class SpannedLayoutManager : SpannedGridLayoutManager(this@WidgetFrameDelegate, RecyclerView.HORIZONTAL, prefManager.frameRowCount, prefManager.frameColCount) {
        override fun canScrollHorizontally(): Boolean {
            return adapter.currentEditingInterfacePosition == -1 && super.canScrollHorizontally()
        }
    }
}