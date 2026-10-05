package tk.zwander.common.appwidget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Box
import androidx.glance.text.Text

class GlanceTestWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = GlanceTestWidget()
}

class GlanceTestWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            Box {
                Text(text = "Hello")
            }
        }
    }
}
