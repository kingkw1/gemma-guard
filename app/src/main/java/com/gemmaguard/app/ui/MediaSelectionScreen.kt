package com.gemmaguard.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.gemmaguard.app.viewmodels.DiscoveredMedia

@Composable
fun MediaSelectionScreen(
    discoveredMedia: List<DiscoveredMedia>,
    onMediaSelected: (DiscoveredMedia) -> Unit,
    onRefresh: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Select Media to Sanitize", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "The app scans for .mp4 and .vtt files pushed to the device's media folder.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (discoveredMedia.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("No media files found", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Push .mp4 and .vtt files with:\n./push_media.sh",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Text("Refresh")
            }
        } else {
            discoveredMedia.forEach { media ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clickable { onMediaSelected(media) }
                        .semantics { contentDescription = "Select media ${media.displayName}" }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = media.displayName, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = media.videoFile.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val vttLabel = when (media.vttSource) {
                            "device" -> "📄 Transcript: ${media.vttFile?.name}"
                            "assets" -> "📦 Transcript: bundled in app"
                            else -> "⚠️ No transcript found (will fail)"
                        }
                        Text(
                            text = vttLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (media.vttSource == "none") MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${"%.1f".format(media.videoFile.length() / (1024.0 * 1024.0))} MB",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) {
                Text("Rescan Media Folder")
            }
        }
    }
}
