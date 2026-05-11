package com.gemmaguard.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import com.gemmaguard.app.models.InferenceMetrics
import com.gemmaguard.app.viewmodels.HitlItem

@Composable
fun HitlDashboardScreen(
    items: List<HitlItem>,
    metrics: InferenceMetrics?,
    videoFilePath: String,
    onToggle: (HitlItem) -> Unit,
    onApprove: () -> Unit
) {
    var selectedPreview by remember { mutableStateOf<HitlItem?>(null) }
    val checkedCount = items.count { it.isCheckedForCut }
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("HITL Review Dashboard", style = MaterialTheme.typography.headlineMedium)
        
        metrics?.let {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Inference Metrics", style = MaterialTheme.typography.titleMedium)
                    Text("Time to First Token: ${it.timeToFirstTokenMs} ms")
                    Text("Total Time: ${it.totalInferenceTimeMs} ms")
                    Text("Speed: ${"%.2f".format(it.tokensPerSecond)} tok/sec")
                    Text("RAM Usage: ${it.memoryUsageMb} MB")
                }
            }
        }

        // Summary bar
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (items.isEmpty()) MaterialTheme.colorScheme.tertiaryContainer
                    else MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Text(
                text = if (items.isEmpty()) "✅ No issues found — media is clean."
                    else "⚠️ ${items.size} items flagged. $checkedCount selected for muting.",
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        
        selectedPreview?.let { item ->
            ContextualPreviewPlayer(item, videoFilePath)
            Spacer(modifier = Modifier.height(16.dp))
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(items) { item ->
                HitlItemRow(
                    item = item,
                    onToggle = { onToggle(item) },
                    onPreviewSelect = { selectedPreview = item }
                )
            }
        }
        
        Button(
            onClick = onApprove,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            enabled = checkedCount > 0 || items.isEmpty()
        ) {
            Text(
                if (checkedCount > 0) "Approve & Mute $checkedCount Segments"
                else if (items.isEmpty()) "Approve — No Changes Needed"
                else "Select segments to mute"
            )
        }
    }
}

@Composable
fun ContextualPreviewPlayer(item: HitlItem, videoFilePath: String) {
    val context = LocalContext.current
    val exoPlayer = remember { ExoPlayer.Builder(context).build() }
    
    DisposableEffect(item, videoFilePath) {
        val mediaItem = MediaItem.fromUri("file://$videoFilePath")
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.seekTo(item.flaggedData.timestampStartMs)
        exoPlayer.play()
        
        onDispose {
            exoPlayer.release()
        }
    }
    
    AndroidView(
        factory = {
            PlayerView(context).apply {
                player = exoPlayer
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .semantics { contentDescription = "Video preview player for ${item.flaggedData.text}" }
    )
}

@Composable
fun HitlItemRow(item: HitlItem, onToggle: () -> Unit, onPreviewSelect: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(onClick = onPreviewSelect)
            .semantics { contentDescription = "Flagged item: ${item.flaggedData.text}. Category: ${item.flaggedData.category}." }
    ) {
        Row(modifier = Modifier.padding(16.dp)) {
            Checkbox(
                checked = item.isCheckedForCut,
                onCheckedChange = { onToggle() },
                modifier = Modifier.semantics { contentDescription = "Toggle cut for ${item.flaggedData.text}" }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(item.flaggedData.text, style = MaterialTheme.typography.bodyLarge)
                Text("${item.flaggedData.category} - Severity: ${item.flaggedData.severity}", style = MaterialTheme.typography.bodyMedium)
                Text(item.flaggedData.reasoning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${item.flaggedData.timestampStartMs}ms — ${item.flaggedData.timestampEndMs}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
