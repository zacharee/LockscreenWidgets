package tk.zwander.lockscreenwidgets.activities

import android.content.Intent
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
        const val EXTRA_RETROACTIVE_FOR_NOTIF = "RETRO_FOR_NOTIF"
        const val EXTRA_RETROACTIVE_FOR_ACC = "RETRO_FOR_ACC"
    }

    val retroForNotif by lazy { intent.getBooleanExtra(EXTRA_RETROACTIVE_FOR_NOTIF, false) }
    val retroForAcc by lazy { intent.getBooleanExtra(EXTRA_RETROACTIVE_FOR_ACC, false) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!retroForNotif && !retroForAcc) {
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

        if (!retroForNotif) {
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

        if (!retroForAcc) {
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
                        return !retroForNotif || isNotificationListenerActive
                    }
                }
            )
        }

        if (!retroForNotif && !retroForAcc) {
            addSlide(
                SimpleSlide.Builder()
                    .title(R.string.intro_done_title)
                    .description(R.string.intro_done_desc)
                    .background(R.color.slide_5)
                    .image(R.drawable.ic_baseline_done_24)
                    .build()
            )
        }
    }
}