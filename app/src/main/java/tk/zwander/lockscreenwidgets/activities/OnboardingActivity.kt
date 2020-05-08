package tk.zwander.lockscreenwidgets.activities

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import com.heinrichreimersoftware.materialintro.app.IntroActivity
import com.heinrichreimersoftware.materialintro.slide.SimpleSlide
import tk.zwander.lockscreenwidgets.util.isAccessibilityEnabled
import tk.zwander.systemuituner.lockscreenwidgets.R

class OnboardingActivity : IntroActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        addSlide(
            SimpleSlide.Builder()
                .title(R.string.intro_done_title)
                .description(R.string.intro_done_desc)
                .background(R.color.slide_4)
                .image(R.drawable.ic_baseline_done_24)
                .build()
        )
    }
}