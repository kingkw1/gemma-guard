package com.gemmaguard.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
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
    
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                "HITL Review", 
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.height(8.dp))

            // Summary bar with Hackathon aesthetic
            GlassCard(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                containerColor = if (items.isEmpty()) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                    else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                    if (items.isNotEmpty()) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = if (items.isEmpty()) "Media is clean."
                            else "${items.size} flags. $checkedCount selected.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            selectedPreview?.let { item ->
                Spacer(modifier = Modifier.height(8.dp))
                ContextualPreviewPlayer(item, videoFilePath)
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
                enabled = checkedCount > 0 || items.isEmpty(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    if (checkedCount > 0) "Mute $checkedCount Segments"
                    else if (items.isEmpty()) "Approve Media"
                    else "Select segments",
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Integrated Performance Overlay
        PerformanceOverlay(
            metrics = metrics,
            visible = metrics != null,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        )
    }
}

@Composable
fun PerformanceOverlay(
    metrics: InferenceMetrics?,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible && metrics != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        metrics?.let {
            GlassCard(
                modifier = Modifier.width(160.dp),
                containerColor = Color.Black.copy(alpha = 0.7f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("EDGE COMPUTE", style = MaterialTheme.typography.labelSmall, color = Color.Cyan, fontWeight = FontWeight.ExtraBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    MetricRow("Latency", "${it.totalInferenceTimeMs}ms")
                    MetricRow("Speed", "${"%.1f".format(it.tokensPerSecond)} t/s")
                    MetricRow("RAM", "${it.memoryUsageMb}MB")
                }
            }
        }
    }
}

@Composable
fun MetricRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
        Text(value, style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
    ) {
        Column(content = content)
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
    
    Box(modifier = Modifier
        .fillMaxWidth()
        .height(180.dp)
        .clip(RoundedCornerShape(16.dp))
        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
    ) {
        AndroidView(
            factory = {
                PlayerView(context).apply {
                    player = exoPlayer
                    useController = true
                }
            },
            modifier = Modifier.fillMaxSize()
                .semantics { contentDescription = "Previewing segment: ${item.flaggedData.text}" }
        )
    }
}

@Composable
fun HitlItemRow(item: HitlItem, onToggle: () -> Unit, onPreviewSelect: () -> Unit) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onPreviewSelect)
            .semantics { 
                contentDescription = "Flagged: ${item.flaggedData.text}. Category: ${item.flaggedData.category}." 
            }
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = item.isCheckedForCut,
                onCheckedChange = { onToggle() },
                modifier = Modifier.semantics { contentDescription = "Toggle mute for ${item.flaggedData.text}" }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    item.flaggedData.text, 
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    "${item.flaggedData.category} • Severity ${item.flaggedData.severity}", 
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}
