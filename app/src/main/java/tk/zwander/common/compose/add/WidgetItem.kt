package tk.zwander.common.compose.add

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Card
import androidx.compose.material.ContentAlpha
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun WidgetItem(
    image: Bitmap?,
    label: String?,
    subLabel: String?,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        backgroundColor = Color.Transparent,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled)
        ),
        elevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .size(200.dp)
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label ?: "",
                textAlign = TextAlign.Center
            )
            Text(
                text = subLabel ?: "",
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.size(8.dp))

            if (image != null) {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = label,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}