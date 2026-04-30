package com.gemmaguard.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

data class DemoClip(val title: String, val assetBaseName: String, val description: String)

val demoClips = listOf(
    DemoClip("Explicit Profanity", "explicit_profanity", "Contains explicit language for testing strict lockdown."),
    DemoClip("Intense Thematic", "intense_thematic", "Contains frightening thematic elements but no explicit profanity."),
    DemoClip("Borderline Mild", "borderline_mild", "Contains mild language/insults to test borderline severity.")
)

@Composable
fun MediaSelectionScreen(
    onMediaSelected: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Select Media to Sanitize", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        demoClips.forEach { clip ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable { onMediaSelected(clip.assetBaseName) }
                    .semantics { contentDescription = "Select media ${clip.title}" }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = clip.title, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = clip.description, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
