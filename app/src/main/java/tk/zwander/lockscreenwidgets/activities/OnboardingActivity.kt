package tk.zwander.lockscreenwidgets.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.heinrichreimersoftware.materialintro.app.IntroActivity
import com.heinrichreimersoftware.materialintro.slide.SimpleSlide
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.util.isAccessibilityEnabled
import tk.zwander.lockscreenwidgets.util.isNotificationListenerActive

/**
 * The introduction for the app.
 * Tell the user about how it works and request permissions.
 * Also manage requesting permissions if they've been revoked.
 */
class OnboardingActivity : IntroActivity() {
    companion object {
        const val EXTRA_RETRO_MODE = "RETRO_MODE"

        const val REQ_STORAGE_PERM = 100

        fun start(context: Context, retroMode: RetroMode = RetroMode.NONE) {
            val intent = Intent(context, OnboardingActivity::class.java)
            intent.putExtra(EXTRA_RETRO_MODE, retroMode.toString())
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            context.startActivity(intent)
        }

        fun startForResult(activity: Activity, code: Int, retroMode: RetroMode = RetroMode.NONE) {
            val intent = Intent(activity, OnboardingActivity::class.java)
            intent.putExtra(EXTRA_RETRO_MODE, retroMode.toString())

            activity.startActivityForResult(intent, code)
        }
    }

    enum class RetroMode {
        ACCESSIBILITY,
        NOTIFICATION,
        STORAGE,
        NONE
    }

    val retroMode by lazy { RetroMode.valueOf(intent.getStringExtra(EXTRA_RETRO_MODE) ?: RetroMode.NONE.toString()) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (retroMode == RetroMode.NONE) {
            addSlide(
                SimpleSlide.Builder()
                    .title(R.string.intro_welcome_title)
                    .description(R.string.intro_welcome_desc)
                    .background(R.color.slide_1)
                    .image(R.drawable.ic_baseline_right_hand_24)
                    .build()
            )

            addSlide(
                SimpleSlide.Builder()
                    .title(R.string.intro_usage_title)
                    .description(R.string.intro_usage_desc)
                    .background(R.color.slide_2)
                    .image(R.drawable.ic_baseline_gesture_two_tap)
                    .build()
            )
        }

        if (retroMode == RetroMode.ACCESSIBILITY || retroMode == RetroMode.NONE) {
            addSlide(
                object : SimpleSlide(
                    Builder()
                        .title(R.string.intro_accessibility_title)
                        .description(R.string.accessibility_service_desc)
                        .background(R.color.slide_3)
                        .image(R.drawable.ic_baseline_accessibility_new_24)
                        .buttonCtaLabel(R.string.grant)
                        .buttonCtaClickListener {
                            val accIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            startActivity(accIntent)
                        }
                ) {
                    override fun canGoForward(): Boolean {
                        return isAccessibilityEnabled
                    }
                }
            )
        }

        if (retroMode == RetroMode.NOTIFICATION || retroMode == RetroMode.NONE) {
            addSlide(
                object : SimpleSlide(
                    Builder()
                        .title(R.string.intro_notification_listener_title)
                        .description(R.string.intro_notification_listener_desc)
                        .background(R.color.slide_4)
                        .image(R.drawable.ic_baseline_notifications_active_24)
                        .buttonCtaLabel(R.string.grant)
                        .buttonCtaClickListener {
                            val notifIntent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            startActivity(notifIntent)
                        }
                ) {
                    override fun canGoForward(): Boolean {
                        return retroMode != RetroMode.NOTIFICATION || isNotificationListenerActive
                    }
                }
            )
        }

        if (retroMode == RetroMode.STORAGE) {
            addSlide(
                object : SimpleSlide(
                    Builder()
                        .title(R.string.intro_read_storage_title)
                        .description(R.string.intro_read_storage_desc)
                        .background(R.color.slide_5)
                        .image(R.drawable.ic_baseline_sd_storage_24)
                        .buttonCtaLabel(R.string.grant)
                        .buttonCtaClickListener {
                            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1) {
                                requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), REQ_STORAGE_PERM)
                            }
                        }
                ) {
                    override fun canGoForward(): Boolean {
                        return retroMode != RetroMode.STORAGE
                                || checkCallingOrSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    }
                }
            )
        }

        if (retroMode == RetroMode.NONE) {
            addSlide(
                SimpleSlide.Builder()
                    .title(R.string.intro_done_title)
                    .description(R.string.intro_done_desc)
                    .background(R.color.slide_6)
                    .image(R.drawable.ic_baseline_done_24)
                    .build()
            )
        }
    }
}