package tk.zwander.widgetdrawer.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import tk.zwander.common.util.Event
import tk.zwander.common.util.eventManager

class TriggerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        eventManager.sendEvent(Event.ShowDrawer)
        finish()
    }
}
