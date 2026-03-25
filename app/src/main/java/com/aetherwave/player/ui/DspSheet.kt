package com.aetherwave.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aetherwave.player.PlaybackService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DspSheet(
    playbackService: PlaybackService,
    onDismiss: () -> Unit
) {
    val currentEffectMode by playbackService.currentEffectMode.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1A1A2E),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "The Sonic Forge",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Close", tint = Color.White.copy(alpha = 0.6f))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Shape your sound with DSP effects",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            val modes = listOf(
                "Normal" to "Original audio, no processing",
                "Slowed + Reverb" to "Aesthetic slow with reverb tail",
                "Nightcore" to "Sped up with raised pitch",
                "Lofi" to "Warm, vintage filter",
                "3D Spatial" to "Immersive spatial audio",
                "Instrumental Focus" to "Reduce vocals and enhance instruments"
            )

            modes.forEachIndexed { index, (name, desc) ->
                val isSelected = currentEffectMode == index
                Surface(
                    color = if (isSelected) Color(0xFF6C63FF).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { playbackService.setEffectMode(index) }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { playbackService.setEffectMode(index) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFF6C63FF),
                                unselectedColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f)
                            )
                            Text(desc, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.4f))
                        }
                    }
                }
            }
        }
    }
}
