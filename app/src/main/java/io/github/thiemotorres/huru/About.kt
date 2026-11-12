package io.github.thiemotorres.huru

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** What Huru is (and isn't), version, license and – outside the Amazon build – the Ko-fi QR code. */
@Composable
fun AboutPanel(modifier: Modifier = Modifier, donate: Boolean = BuildConfig.DONATE, version: String = BuildConfig.VERSION_NAME) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Image(painterResource(R.drawable.wordmark), contentDescription = "Huru", Modifier.height(44.dp))
        Text(stringResource(R.string.about_version, version), color = Color.White.copy(alpha = 0.6f))
        Text(stringResource(R.string.about_text), style = MaterialTheme.typography.bodyMedium)
        Text("github.com/thiemotorres/huru", color = Accent, style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.about_license),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f),
        )
        if (donate) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Image(
                    painterResource(R.drawable.kofi_qr), contentDescription = stringResource(R.string.about_donate_qr),
                    Modifier.size(132.dp).clip(RoundedCornerShape(10.dp)),
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.about_donate), style = MaterialTheme.typography.titleSmall)
                    Text("ko-fi.com/thiemotorres", color = Color.White.copy(alpha = 0.6f))
                }
            }
        }
    }
}
