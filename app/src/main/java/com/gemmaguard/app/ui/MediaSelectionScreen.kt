package com.gemmaguard.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Represents a pre-loaded demo clip with its asset VTT name and on-device video filename.
 *
 * @param title Human-readable display name.
 * @param vttAssetName Basename for the .vtt file bundled in app/src/main/assets/ (without extension).
 * @param videoFileName Filename of the .mp4 pushed to getExternalFilesDir(null) on the device.
 * @param description Short description shown on the card.
 */
data class DemoClip(
    val title: String,
    val vttAssetName: String,
    val videoFileName: String,
    val description: String
)

val demoClips = listOf(
    DemoClip(
        "IASIP - Boat Clip",
        "iasip_boatClip",
        "iasip_boatClip.mp4",
        "Short clip with explicit profanity. Tests strict filtering."
    ),
    DemoClip(
        "IASIP - House Clip",
        "iasip_houseClip",
        "iasip_houseClip.mp4",
        "Longer clip with thematic content and mild language."
    )
)

@Composable
fun MediaSelectionScreen(
    onMediaSelected: (vttAssetName: String, videoFileName: String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Select Media to Sanitize", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Choose a pre-loaded demo clip. The VTT transcript will be parsed and each sentence classified by Gemma.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        demoClips.forEach { clip ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable { onMediaSelected(clip.vttAssetName, clip.videoFileName) }
                    .semantics { contentDescription = "Select media ${clip.title}" }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = clip.title, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = clip.description, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Video: ${clip.videoFileName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
