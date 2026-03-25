package com.aetherwave.player.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aetherwave.player.PlaybackService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqSheet(
    playbackService: PlaybackService,
    onDismiss: () -> Unit
) {
    val eqGains by playbackService.eqGains.collectAsState()
    val eqEnabled by playbackService.eqEnabled.collectAsState()

    val bands = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")

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
                Column {
                    Text("Master EQ", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        text = if (eqEnabled) "Engine processing active" else "Hardware bypass active",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (eqEnabled) Color(0xFF6C63FF) else Color.White.copy(alpha = 0.4f)
                    )
                }
                Switch(
                    checked = eqEnabled,
                    onCheckedChange = { playbackService.setEqEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF6C63FF),
                        uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                        uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // EQ Sliders
            Row(
                modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = if (eqEnabled) 1.0f else 0.4f },
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                bands.forEachIndexed { index, label ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${if (eqGains[index] > 0) "+" else ""}${eqGains[index].toInt()} dB",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF6C63FF)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.height(140.dp).width(40.dp), contentAlignment = Alignment.Center) {
                            Slider(
                                value = eqGains[index],
                                onValueChange = { playbackService.setEqBandGain(index, it) },
                                valueRange = -15f..15f,
                                modifier = Modifier
                                    .width(140.dp)
                                    .graphicsLayer {
                                        rotationZ = -90f
                                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                                    },
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color(0xFF6C63FF),
                                    inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.5f))
                    }
                }
            }

        }
    }
}
