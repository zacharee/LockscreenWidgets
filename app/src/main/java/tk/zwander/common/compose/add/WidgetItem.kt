package tk.zwander.common.compose.add

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetItem(
    image: Bitmap?,
    label: String?,
    subLabel: String?,
    onClick: () -> Unit
) {
    OutlinedCard(
        onClick = onClick,
        border = CardDefaults.outlinedCardBorder(true)
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