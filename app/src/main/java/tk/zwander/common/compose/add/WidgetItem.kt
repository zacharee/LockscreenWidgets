package tk.zwander.common.compose.add

import android.graphics.drawable.Drawable
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.accompanist.drawablepainter.rememberDrawablePainter

@Composable
fun WidgetItem(
    image: Drawable?,
    previewLayout: View? = null,
    label: String?,
    subLabel: String?,
    onClick: () -> Unit,
) {
    OutlinedCard(
        border = CardDefaults.outlinedCardBorder(true),
    ) {
        Box {
            Column(
                modifier = Modifier
                    .size(200.dp)
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = label ?: "",
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = subLabel ?: "",
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.size(8.dp))

                when {
                    previewLayout != null -> {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            AndroidView(
                                factory = { previewLayout },
                                update = {},
                            )
                        }
                    }

                    image != null -> {
                        Image(
                            painter = rememberDrawablePainter(image),
                            contentDescription = label,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    else -> {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(
                        onClick = onClick,
                    ),
            )
        }
    }
}