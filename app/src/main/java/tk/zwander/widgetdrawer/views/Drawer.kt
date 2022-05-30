package tk.zwander.widgetdrawer.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.*
import android.content.pm.ActivityInfo
import android.graphics.PixelFormat
import android.os.*
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.View.OnClickListener
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.animation.doOnEnd
import androidx.recyclerview.widget.RecyclerView
import com.android.internal.R.attr.host
import com.android.internal.R.id.prefs
import com.arasthel.spannedgridlayoutmanager.SpannedGridLayoutManager
import com.tingyik90.snackprogressbar.SnackProgressBar
import com.tingyik90.snackprogressbar.SnackProgressBarManager
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.activities.DismissOrUnlockActivity
import tk.zwander.lockscreenwidgets.activities.add.AddDrawerWidgetActivity
import tk.zwander.lockscreenwidgets.adapters.WidgetFrameAdapter
import tk.zwander.lockscreenwidgets.data.WidgetData
import tk.zwander.lockscreenwidgets.data.WidgetType
import tk.zwander.lockscreenwidgets.databinding.DrawerLayoutBinding
import tk.zwander.lockscreenwidgets.host.WidgetHostCompat
import tk.zwander.lockscreenwidgets.util.*
import java.util.*
import kotlin.collections.LinkedHashSet

class Drawer : FrameLayout, SharedPreferences.OnSharedPreferenceChangeListener, EventObserver {
    companion object {
        const val ANIM_DURATION = 200L
    }

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    var hideListener: (() -> Unit)? = null
    var showListener: (() -> Unit)? = null

    val params: WindowManager.LayoutParams
        get() = WindowManager.LayoutParams().apply {
            val displaySize = context.screenSize
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            width = displaySize.x
            height = WindowManager.LayoutParams.MATCH_PARENT
            format = PixelFormat.RGBA_8888
            gravity = Gravity.TOP
            screenOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

    private val wm by lazy { context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    private val host by lazy {
        WidgetHostCompat.getInstance(
            context,
            1003
        ) {
            DismissOrUnlockActivity.launch(context)
        }
    }
    private val manager by lazy { AppWidgetManager.getInstance(context.applicationContext) }
    private val shortcutIdManager by lazy { ShortcutIdManager.getInstance(context, host) }
    private val adapter by lazy {
        WidgetFrameAdapter(manager, host, params) { adapter, widget, position ->
            removeWidget(widget)
        }
    }

    private val gridLayoutManager =
        SpannedGridLayoutManager(context, RecyclerView.VERTICAL, 1, context.prefManager.drawerColCount)

    private val preferenceHandler = HandlerRegistry {
        handler(PrefManager.KEY_DRAWER_WIDGETS) {
            if (!)
        }
    }

    @Suppress("DEPRECATION")
    private val globalReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_CLOSE_SYSTEM_DIALOGS -> {
                    hideDrawer()
                }
            }
        }
    }

    private val binding by lazy { DrawerLayoutBinding.bind(this) }

    override fun onFinishInflate() {
        super.onFinishInflate()

        if (!isInEditMode) {
            binding.addWidget.setOnClickListener { pickWidget() }
            binding.closeDrawer.setOnClickListener { hideDrawer() }
            binding.toggleTransparent.setOnClickListener {
//                prefs.transparentWidgets = !prefs.transparentWidgets
//                adapter.transparentWidgets = prefs.transparentWidgets
            }

            binding.widgetGrid.layoutManager = gridLayoutManager
            gridLayoutManager.spanSizeLookup = adapter.spanSizeLookup

            val inAnim = AlphaAnimation(0f, 1f).apply {
                duration = ANIM_DURATION
                interpolator = DecelerateInterpolator()
            }
            val outAnim = AlphaAnimation(1f, 0f).apply {
                duration = ANIM_DURATION
                interpolator = AccelerateInterpolator()
            }

            val animListener = object : Animation.AnimationListener {
                override fun onAnimationRepeat(animation: Animation?) {}
                override fun onAnimationStart(animation: Animation?) {}
                override fun onAnimationEnd(animation: Animation?) {}
            }

            inAnim.setAnimationListener(animListener)
            outAnim.setAnimationListener(animListener)

            binding.actionBarWrapper.inAnimation = inAnim
            binding.actionBarWrapper.outAnimation = outAnim

//            adapter.transparentWidgets = prefs.transparentWidgets
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        host.startListening()
        Handler(Looper.getMainLooper()).postDelayed({
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
        }, 50)

        setPadding(paddingLeft, context.statusBarHeight, paddingRight, paddingBottom)

        handler?.postDelayed({
            val anim = ValueAnimator.ofFloat(0f, 1f)
            anim.interpolator = DecelerateInterpolator()
            anim.duration = ANIM_DURATION
            anim.addUpdateListener {
                alpha = it.animatedValue.toString().toFloat()
            }
            anim.doOnEnd {
                showListener?.invoke()
            }
            anim.start()
        }, 10)

//        setBackgroundColor(prefs.drawerBg)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        try {
            host.stopListening()
        } catch (e: NullPointerException) {
            //AppWidgetServiceImpl$ProviderId NPE
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            PrefsManager.TRANSPARENT_WIDGETS -> adapter.transparentWidgets =
                prefs.transparentWidgets
            PrefsManager.COLUMN_COUNT -> updateSpanCount()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event?.keyCode == KeyEvent.KEYCODE_BACK) {
            hideDrawer()
            return true
        }

        return super.dispatchKeyEvent(event)
    }

    override fun onEvent(event: Event) {

    }

    fun onCreate() {
        context.registerReceiver(globalReceiver, IntentFilter().apply {
            @Suppress("DEPRECATION")
            addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
        })


        binding.widgetGrid.adapter = adapter
        binding.widgetGrid.isNestedScrollingEnabled = true
        binding.widgetGrid.setHasFixedSize(true)
        updateSpanCount()
        adapter.updateWidgets(context.prefManager.drawerWidgets.toList())
        context.eventManager.addObserver(this)
    }

    fun onDestroy() {
        hideDrawer(false)
        context.prefManager.drawerWidgets = adapter.widgets

        context.unregisterReceiver(globalReceiver)
        context.prefManager.(this)
        context.eventManager.removeObserver(this)
    }

    fun showDrawer(wm: WindowManager = this.wm, overrideType: Int = params.type) {
        try {
            wm.addView(this, params.apply { type = overrideType })
        } catch (_: Exception) {}
    }

    fun hideDrawer(callListener: Boolean = true) {
        val anim = ValueAnimator.ofFloat(1f, 0f)
        anim.interpolator = AccelerateInterpolator()
        anim.duration = ANIM_DURATION
        anim.addUpdateListener {
            alpha = it.animatedValue.toString().toFloat()
        }
        anim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                if (callListener) hideListener?.invoke()
                handler?.postDelayed({
                    try {
                        wm.removeView(this@Drawer)
                    } catch (_: Exception) {
                    }
                }, 10)
            }
        })
        anim.start()
    }

    private fun updateSpanCount() {
        gridLayoutManager.columnCount = context.prefManager.drawerColCount
        gridLayoutManager.customHeight = context.dpAsPx(50f)
    }

    private fun pickWidget() {
        hideDrawer()
        context.startActivity(Intent(context, AddDrawerWidgetActivity::class.java))
    }

    private fun removeWidget(info: WidgetData) {
        if (info.type == WidgetType.WIDGET) host.deleteAppWidgetId(info.id)
        else if (info.type == WidgetType.SHORTCUT) shortcutIdManager.removeShortcutId(
            info.id
        )
        context.prefManager.drawerWidgets = LinkedHashSet(adapter.widgets)
    }
}