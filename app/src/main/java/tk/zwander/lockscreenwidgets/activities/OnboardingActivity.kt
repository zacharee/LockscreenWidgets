package tk.zwander.lockscreenwidgets.activities

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.heinrichreimersoftware.materialintro.app.IntroActivity
import com.heinrichreimersoftware.materialintro.slide.SimpleSlide
import tk.zwander.lockscreenwidgets.R
import tk.zwander.lockscreenwidgets.services.isAccessibilityEnabled
import tk.zwander.lockscreenwidgets.services.isNotificationListenerActive
import tk.zwander.lockscreenwidgets.util.hasStoragePermission
import tk.zwander.lockscreenwidgets.util.launchUrl
import tk.zwander.lockscreenwidgets.util.logUtils

/**
 * The introduction for the app.
 * Tell the user about how it works and request permissions.
 * Also manage requesting permissions if they've been revoked.
 */
class OnboardingActivity : IntroActivity() {
    companion object {
        const val EXTRA_RETRO_MODE = "RETRO_MODE"

        /**
         * Start [OnboardingActivity] with the requested [RetroMode]
         *
         * @param context a Context object
         * @param retroMode specify a [RetroMode] if this instance is being used
         * to request a specific permission and not showing the entire intro
         */
        fun start(context: Context, retroMode: RetroMode = RetroMode.NONE) {
            val intent = Intent(context, OnboardingActivity::class.java)
            intent.putExtra(EXTRA_RETRO_MODE, retroMode.toString())
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            context.startActivity(intent)
        }

        /**
         * Same as [OnboardingActivity.start] but will return a result stating
         * whether or not the user successfully completed the setup
         *
         * @param activity an Activity reference
         * @param request the request for returning the result
         * @param retroMode specify a [RetroMode] if this instance is being used
         * to request a specific permission and not showing the entire intro
         */
        fun startForResult(activity: Activity, request: ActivityResultLauncher<Intent>, retroMode: RetroMode = RetroMode.NONE) {
            val intent = Intent(activity, OnboardingActivity::class.java)
            intent.putExtra(EXTRA_RETRO_MODE, retroMode.toString())

            request.launch(intent)
        }
    }

    /**
     * Enum class to keep track of whether this instance is
     * showing the entire intro sequence or just requesting a
     * specific permission
     */
    enum class RetroMode {
        ACCESSIBILITY,
        NOTIFICATION,
        STORAGE,
        NONE
    }

    private val retroMode by lazy { RetroMode.valueOf(intent.getStringExtra(EXTRA_RETRO_MODE) ?: RetroMode.NONE.toString()) }

    private val storagePermReq = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        buttonBackFunction = BUTTON_BACK_FUNCTION_BACK

        //Only add these slides if this instance is the full intro
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

            addSlide(
                SimpleSlide.Builder()
                    .title(R.string.privacy_policy)
                    .description(R.string.intro_privacy_policy_desc)
                    .background(R.color.slide_3)
                    .image(R.drawable.info)
                    .buttonCtaLabel(R.string.more_info)
                    .buttonCtaClickListener {
                        launchUrl("https://github.com/zacharee/LockscreenWidgets/blob/master/PRIVACY.md")
                    }
                    .build()
            )
        }

        //Add this slide if we're requesting Accessibility permission or if we're running
        //the full intro.
        if (retroMode == RetroMode.ACCESSIBILITY || retroMode == RetroMode.NONE) {
            addSlide(
                object : SimpleSlide(
                    Builder()
                        .title(R.string.intro_accessibility_title)
                        .description(R.string.accessibility_service_desc)
                        .background(R.color.slide_4)
                        .image(R.drawable.ic_baseline_accessibility_new_24)
                        .buttonCtaLabel(R.string.more_info)
                        .buttonCtaClickListener {
                            MaterialAlertDialogBuilder(this)
                                .setTitle(R.string.intro_accessibility_title)
                                .setMessage(R.string.intro_accessibility_desc)
                                .setPositiveButton(R.string.grant) { _, _ ->
                                    //Samsung devices have a separate Activity for listing
                                    //installed Accessibility Services, for some reason.
                                    //It's exported and permission-free, at least on Android 10,
                                    //so attempt to launch it. A "dumb" try-catch is simpler
                                    //than a check for the existence and state of this Activity.
                                    //If the Installed Services Activity can't be launched,
                                    //just launch the normal Accessibility Activity.
                                    try {
                                        val accIntent = Intent(Intent.ACTION_MAIN)
                                        accIntent.`package` = "com.android.settings"
                                        accIntent.component = ComponentName("com.android.settings", "com.android.settings.Settings\$AccessibilityInstalledServiceActivity")
                                        startActivity(accIntent)
                                    } catch (e: Exception) {
                                        logUtils.debugLog("Error opening Installed Services:", e)
                                        val accIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                        startActivity(accIntent)
                                    }
                                }
                                .setNegativeButton(R.string.close_app) { _, _ ->
                                    finish()
                                }
                                .show()
                        }
                ) {
                    override fun canGoForward(): Boolean {
                        return isAccessibilityEnabled
                    }
                }
            )
        }

        //Add this slide if we're requesting Notification access or if we're running
        //the full intro.
        if (retroMode == RetroMode.NOTIFICATION || retroMode == RetroMode.NONE) {
            addSlide(
                object : SimpleSlide(
                    Builder()
                        .title(R.string.intro_notification_listener_title)
                        .description(R.string.intro_notification_listener_desc)
                        .background(R.color.slide_5)
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

        //Add this slide if we're requesting storage access or if we're running
        //the full intro.
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1 && (retroMode == RetroMode.STORAGE || retroMode == RetroMode.NONE)) {
            addSlide(
                object : SimpleSlide(
                    Builder()
                        .title(R.string.intro_read_storage_title)
                        .description(R.string.intro_read_storage_desc)
                        .background(R.color.slide_6)
                        .image(R.drawable.ic_baseline_sd_storage_24)
                        .buttonCtaLabel(R.string.grant)
                        .buttonCtaClickListener {
                            storagePermReq.launch(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    android.Manifest.permission.READ_MEDIA_IMAGES
                                } else {
                                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                                }
                            )
                        }
                ) {
                    override fun canGoForward(): Boolean {
                        return retroMode != RetroMode.STORAGE || hasStoragePermission
                    }
                }
            )
        }

        //Only add the battery optimization and "done" slides if we're running the full intro.
        if (retroMode == RetroMode.NONE) {
            addSlide(
                SimpleSlide.Builder()
                    .title(R.string.intro_battery_optimization)
                    .description(R.string.intro_battery_optimization_desc)
                    .background(R.color.slide_7)
                    .image(R.drawable.ic_baseline_battery_alert_24)
                    .buttonCtaLabel(R.string.more_info)
                    .buttonCtaClickListener {
                        launchUrl("https://dontkillmyapp.com/?app=Lockscreen%20Widgets")
                    }
                    .build()
            )

            addSlide(
                SimpleSlide.Builder()
                    .title(R.string.intro_done_title)
                    .description(R.string.intro_done_desc)
                    .background(R.color.slide_8)
                    .image(R.drawable.ic_baseline_done_24)
                    .build()
            )
        }
    }
}