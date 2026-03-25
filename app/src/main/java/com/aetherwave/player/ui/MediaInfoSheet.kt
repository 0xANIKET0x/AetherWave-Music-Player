package com.aetherwave.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aetherwave.player.PlaybackService
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaInfoSheet(
    playbackService: PlaybackService,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentTrackPath by playbackService.currentTrackPath.collectAsState()
    val inputBitrate by playbackService.inputBitrate.collectAsState()
    val inputBitDepth by playbackService.inputBitDepth.collectAsState()
    val outputSampleRate by playbackService.outputSampleRate.collectAsState()
    val outputDeviceInfo by playbackService.outputDeviceInfo.collectAsState()
    val isDolbyAtmos by playbackService.isDolbyAtmosFile.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF121212),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.2f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                Icon(Icons.Default.Info, null, tint = Color.White, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Media Properties",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Technical Specs Card
            InfoCard("Technical Specifications") {
                InfoRow("Format", if (isDolbyAtmos) "Dolby Atmos / EAC3" else "Standard PCM / FLAC")
                InfoRow("Input Bitrate", "${if (inputBitrate > 0) inputBitrate / 1000 else 0} kbps")
                InfoRow("Bit Depth", "${inputBitDepth}-bit")
                InfoRow("Sample Rate", "$outputSampleRate Hz")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Output Card
            InfoCard("Audio Routing") {
                Text(
                    text = outputDeviceInfo.ifBlank { "System Default Output" },
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                    lineHeight = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Path Card
            InfoCard("File Location") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = currentTrackPath ?: "Unknown Location",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        currentTrackPath?.let {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = android.content.ClipData.newPlainText("File Path", it)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Path copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Icon(Icons.Default.ContentCopy, "Copy", tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.4f),
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.White.copy(alpha = 0.5f), style = MaterialTheme.typography.bodyMedium)
        Text(value, color = Color.White.copy(alpha = 0.9f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
