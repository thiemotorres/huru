package io.github.thiemotorres.huru

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/** About page: what Huru is (and isn't), version, license and – outside the Amazon build – the Ko-fi QR code. */
@Composable
fun AboutScreen(header: @Composable () -> Unit, donate: Boolean = BuildConfig.DONATE, version: String = BuildConfig.VERSION_NAME) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        header()
        Row(
            Modifier.fillMaxSize().padding(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(72.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Image(painterResource(R.drawable.wordmark), contentDescription = "Huru", Modifier.height(56.dp))
                Text(stringResource(R.string.about_version, version), color = Color.White.copy(alpha = 0.6f))
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.about_text),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text("github.com/thiemotorres/huru", color = Accent)
                Text(
                    stringResource(R.string.about_license),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }
            if (donate) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Image(
                        painterResource(R.drawable.kofi_qr), contentDescription = stringResource(R.string.about_donate_qr),
                        Modifier.size(200.dp).clip(RoundedCornerShape(12.dp)),
                    )
                    Text(stringResource(R.string.about_donate), style = MaterialTheme.typography.titleSmall)
                    Text("ko-fi.com/thiemotorres", color = Color.White.copy(alpha = 0.6f))
                }
            }
        }
    }
}
