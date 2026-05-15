package com.aetherwave.player.ui

import android.graphics.RenderEffect
import androidx.activity.compose.BackHandler
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.aetherwave.player.PlaybackService
import com.aetherwave.player.data.LyricLine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import kotlin.math.*
import kotlin.random.Random

// ─── DATA MODELS ───
data class StardustParticle(var x: Float, var y: Float, var z: Float, val seed: Float)
data class ThemeOption(val name: String, val colors: List<Color>)

// ─── TASK 1: COMPACT ECLIPSE SHADER (0.08 Resize) ───
private const val SINGULARITY_SHADER = """
    uniform float2 u_resolution;
    uniform float u_time;
    uniform float u_energy;
    
    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / u_resolution.xy;
        float2 p = uv - float2(0.5, 0.20); 
        p.x *= u_resolution.x / u_resolution.y;
        
        float d = length(p); 
        
        // 1. SMALLER EVENT HORIZON (Reduced to 0.08)
        float rBH = 0.08; 
        if (d < rBH) { return half4(0.0, 0.0, 0.0, 1.0); }
        
        float energySmooth = smoothstep(0.0, 1.0, u_energy);
        
        // 2. TIGHTER CORONA EXPANSION (Sits tightly, expands gracefully)
        float maxCoronaSize = 0.12 + (energySmooth * 0.35);
        float coronaFalloff = smoothstep(maxCoronaSize, rBH, d);
        
        // 3. REFINED GLOW (Prevents light from bleeding too far)
        float glowIntensity = 0.001 + (energySmooth * 0.015);
        float coronaGlow = (glowIntensity / abs(d - rBH)) * coronaFalloff;
        
        half3 baseCore = half3(0.4, 0.6, 1.0); 
        half3 activeCore = half3(0.7, 0.9, 1.0); 
        half3 coreLight = mix(baseCore, activeCore, half(energySmooth));
        
        half3 baseOuter = half3(0.1, 0.05, 0.3); 
        half3 activeOuter = half3(0.3, 0.2, 0.6); 
        half3 outerLight = mix(baseOuter, activeOuter, half(energySmooth));
        
        half3 color = mix(coreLight, outerLight, smoothstep(rBH, maxCoronaSize, d));
        
        return half4(color * half(coronaGlow), 1.0);
    }
"""

private val PREDEFINED_THEMES = listOf(
    ThemeOption("Supernova Crimson", listOf(Color.Red, Color(0xFFFFD700))),
    ThemeOption("Abyssal Void", listOf(Color.Black, Color(0xFF191970))),
    ThemeOption("Neon Synthwave", listOf(Color.Magenta, Color.Cyan)),
    ThemeOption("Aurora Borealis", listOf(Color(0xFF006400), Color(0xFF800080))),
    ThemeOption("Event Horizon", listOf(Color(0xFF8A2BE2), Color.White))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsScreen(playbackService: PlaybackService, onBack: () -> Unit) {
    BackHandler { onBack() }
    val currentTrack by playbackService.currentTrack.collectAsState(initial = null)
    val lyricsData by playbackService.lyrics.collectAsState(initial = null)
    val lyricsMode by playbackService.lyricsMode.collectAsState()
    val scrollState = rememberLazyListState()
    val audioEnergy by playbackService.audioEnergy.collectAsState()
    val currentPosition by playbackService.currentPosition.collectAsState()

    var selectedTheme by remember { mutableStateOf(PREDEFINED_THEMES[0]) }
    var showColorDropdown by remember { mutableStateOf(false) }

    val activeIndexState = remember {
        derivedStateOf {
            val currentMs = (currentPosition * 1000).toLong()
            lyricsData?.lines?.indexOfLast { it.timeMs <= currentMs }?.coerceAtLeast(0) ?: 0
        }
    }

    var frameTime by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameTime = it }
        }
    }

    val isDragged by scrollState.interactionSource.collectIsDraggedAsState()
    var isAutoScrollEnabled by remember { mutableStateOf(true) }
    var isFirstLaunch by remember { mutableStateOf(true) }

    LaunchedEffect(isDragged) {
        if (isDragged) {
            isAutoScrollEnabled = false
        } else {
            delay(3000)
            isAutoScrollEnabled = true
        }
    }

    LaunchedEffect(activeIndexState.value, isAutoScrollEnabled) {
        if (lyricsData?.isSynced == true && isAutoScrollEnabled) {
            if (isFirstLaunch) {
                scrollState.scrollToItem(activeIndexState.value, scrollOffset = -250)
                isFirstLaunch = false
            } else {
                scrollState.animateScrollToItem(activeIndexState.value, scrollOffset = -250)
            }
        }
    }

    val configuration = LocalConfiguration.current
    val dynamicVerticalPadding = (configuration.screenHeightDp.dp / 2) - 64.dp

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxSize().zIndex(0f)) {
            if (currentTrack != null) {
                AsyncImage(
                    model = currentTrack?.albumArtUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(80.dp).alpha(0.5f)
                )
            }
            when (lyricsMode) {
                0 -> AppleMusicMeshBackground(audioEnergy, selectedTheme.colors)
                1 -> ContinuousGameSnowBackground(audioEnergy, frameTime)
                2 -> GargantuaShaderBackground(audioEnergy, frameTime)
            }
        }

        Scaffold(
            modifier = Modifier.zIndex(10f),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(currentTrack?.title ?: "Lyrics", color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Text(currentTrack?.artist ?: "", color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.Close, "Close", tint = Color.White)
                        }
                    },
                    actions = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (lyricsMode == 0) {
                                Box {
                                    IconButton(onClick = { showColorDropdown = true }) {
                                        Icon(Icons.Default.Palette, "Colors", tint = Color.White)
                                    }
                                    DropdownMenu(
                                        expanded = showColorDropdown,
                                        onDismissRequest = { showColorDropdown = false },
                                        offset = androidx.compose.ui.unit.DpOffset(0.dp, 8.dp),
                                        modifier = Modifier.background(Color.Black.copy(alpha = 0.9f)).border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                    ) {
                                        PREDEFINED_THEMES.forEach { theme ->
                                            DropdownMenuItem(
                                                text = {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(Brush.linearGradient(theme.colors)))
                                                        Spacer(Modifier.width(12.dp))
                                                        Text(theme.name, color = Color.White, fontSize = 14.sp)
                                                    }
                                                },
                                                onClick = {
                                                    selectedTheme = theme
                                                    showColorDropdown = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            IconButton(onClick = { playbackService.lyricsMode.value = (lyricsMode + 1) % 3 }) {
                                Icon(Icons.Default.AutoMode, "Switch Mode", tint = Color.White)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                if (lyricsData == null || lyricsData!!.lines.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                } else {
                    LazyColumn(
                        state = scrollState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = dynamicVerticalPadding),
                        verticalArrangement = Arrangement.spacedBy(32.dp)
                    ) {
                        itemsIndexed(lyricsData!!.lines) { index, line ->
                            LyricLineItem(
                                line = line,
                                index = index,
                                mode = lyricsMode,
                                audioEnergy = audioEnergy,
                                activeIndexProvider = { activeIndexState.value },
                                listState = scrollState,
                                glowColor = selectedTheme.colors[1],
                                onClick = { if (lyricsData!!.isSynced) playbackService.seekTo(line.timeMs / 1000f) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContinuousGameSnowBackground(energy: Float, timeNanos: Long) {
    val particles = remember {
        List(150) { 
            StardustParticle(
                Random.nextFloat() * 1000f, 
                Random.nextFloat() * 2000f, 
                Random.nextFloat() * 2.5f + 0.5f,
                Random.nextFloat() * 100f // Organic Seed
            ) 
        }
    }
    val prevTimeNanos = remember { mutableStateOf(timeNanos) }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val h = size.height
        val w = size.width
        val timeSec = timeNanos / 1_000_000_000f
        val dt = ((timeNanos - prevTimeNanos.value) / 1_000_000_000f).coerceIn(0.008f, 0.1f)
        prevTimeNanos.value = timeNanos
        
        val explosiveBoost = (energy * energy * energy) * 12000f 
        val currentSpeedBase = 150f + explosiveBoost

        particles.forEach { p ->
            val currentSpeed = currentSpeedBase / p.z
            p.y += currentSpeed * dt
            
            val driftX = sin(timeSec + p.seed) * (50f / p.z)
            
            if (p.y > h + 50f) {
                p.y = -50f
                p.x = Random.nextFloat() * w
                p.z = Random.nextFloat() * 2.5f + 0.5f
            }
            
            val baseRadius = 8f / p.z
            val radius = baseRadius * (1f + (energy * 4.0f))
            val alpha = (1f / p.z).coerceIn(0.2f, 1f)
            
            drawCircle(
                color = Color.White.copy(alpha = alpha), 
                center = Offset(p.x + driftX, p.y), 
                radius = radius
            )
        }
    }
}

@Composable
fun GargantuaShaderBackground(energy: Float, timeNanos: Long) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shader = remember { RuntimeShader(SINGULARITY_SHADER) }
        val timeSec = (timeNanos / 1_000_000_000f) % 1000f
        Canvas(modifier = Modifier.fillMaxSize()) {
            shader.setFloatUniform("u_resolution", size.width, size.height)
            shader.setFloatUniform("u_time", timeSec)
            shader.setFloatUniform("u_energy", energy)
            drawRect(brush = ShaderBrush(shader))
        }
    } else {
        val pulse = 1.0f + (energy * 0.5f)
        Box(Modifier.fillMaxSize().background(
            Brush.radialGradient(
                (0.15f * pulse).coerceAtMost(1f) to Color.Black,
                (0.3f * pulse).coerceAtMost(1f) to Color(0xFF4A90E2).copy(alpha = 0.5f), // Oceanic blue fallback
                (0.6f * pulse).coerceAtMost(1f) to Color.Transparent,
                center = Offset(0.5f, 0.20f)
            )
        ))
    }
}

@Composable
fun AppleMusicMeshBackground(energy: Float, colors: List<Color>) {
    val infiniteTransition = rememberInfiniteTransition(label = "mesh")
    val time by infiniteTransition.animateFloat(0f, 2 * PI.toFloat(), infiniteRepeatable(tween(durationMillis = 20000, easing = LinearEasing)), label = "time")

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black).then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(150.dp) else Modifier)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val energyBoost = 1.0f + (energy * 0.5f)
            val meshColors = listOf(Color(0xFFFF00FF), Color(0xFF00FFFF), colors[0], colors[1], Color(0xFF9E1B42))
            meshColors.forEachIndexed { i, color ->
                val freq = 0.5f + i * 0.2f
                val radius = (w * 0.4f + i * 50f) * energyBoost
                val offsetX = (sin(time * freq) * w * 0.3f) + (w / 2f)
                val offsetY = (cos(time * (freq * 0.7f)) * h * 0.3f) + (h / 2f)
                drawCircle(
                    brush = Brush.radialGradient(0.0f to color.copy(alpha = 0.6f), 1.0f to Color.Transparent, center = Offset(offsetX, offsetY), radius = radius),
                    radius = radius, center = Offset(offsetX, offsetY)
                )
            }
        }
    }
}

@Composable
fun LyricLineItem(
    line: LyricLine,
    index: Int,
    mode: Int,
    audioEnergy: Float,
    activeIndexProvider: () -> Int,
    listState: LazyListState,
    glowColor: Color,
    onClick: () -> Unit
) {
    val itemLocationProvider = remember {
        derivedStateOf {
            val item = listState.layoutInfo.visibleItemsInfo.find { it.index == index }
            item?.let { (it.offset + it.size / 2).toFloat() } ?: Float.MAX_VALUE
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .graphicsLayer {
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                val activeIndex = activeIndexProvider()
                val isActive = index == activeIndex
                val currentY = itemLocationProvider.value
                val bhCenterY = size.height * 0.20f
                val energy = audioEnergy.coerceAtMost(1f)
                val breathingScale = if (isActive) 1.0f + (energy * 0.05f) else 1.0f

                if (isActive) {
                    val shake = energy * 15f
                    translationX = Random.nextFloat() * shake - (shake / 2f)
                    translationY = Random.nextFloat() * shake - (shake / 2f)
                }

                // ─── TIGHTER BOOK-LIKE CONSUMPTION ───
                val consumptionStart = bhCenterY + 75f // Reduced from 120f to match the smaller hole
                
                if (mode == 2 && currentY < consumptionStart && !isActive) {
                    val distanceToHole = (currentY - bhCenterY).coerceAtLeast(0f)
                    val maxDistance = consumptionStart - bhCenterY
                    val rawProgress = (1f - (distanceToHole / maxDistance)).coerceIn(0f, 1f)
                    val smoothProgress = FastOutSlowInEasing.transform(rawProgress)
                    
                    scaleX = 1f - smoothProgress
                    scaleY = 1f - smoothProgress
                    alpha = (1f - smoothProgress).coerceIn(0f, 1f)
                    translationY = -smoothProgress * distanceToHole 
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val blurPx = smoothProgress * 15f
                        renderEffect = RenderEffect.createBlurEffect(blurPx, blurPx, android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect()
                    }
                } else {
                    alpha = if (isActive) 1.0f else 0.4f
                    scaleX = (if (isActive) 1.15f else 0.95f) * breathingScale
                    scaleY = scaleX
                    if (mode == 1) rotationX = if (isActive) 0f else 35f
                    
                    if (!isActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        renderEffect = RenderEffect.createBlurEffect(4f, 4f, android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect()
                    } else if (isActive) {
                        renderEffect = null
                    }
                }
            }
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val isActive = activeIndexProvider() == index
        val itemCenterY = itemLocationProvider.value
        
        val textColor = if (mode == 2 && itemCenterY < 350f && !isActive) {
            val prog = (1f - (itemCenterY / 350f)).coerceIn(0f, 1f)
            if (prog > 0.4f) lerp(Color.White, Color(0xFFFFD700), (prog - 0.4f) * 2f) else Color.White
        } else Color.White

        Text(
            text = line.text,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Black, fontSize = 32.sp, lineHeight = 40.sp,
                fontFamily = if (mode == 2) FontFamily.Serif else FontFamily.Default,
                shadow = if (isActive && mode == 0) Shadow(color = glowColor.copy(alpha = 0.8f), blurRadius = 30f) else null
            ),
            color = textColor, textAlign = TextAlign.Start
        )
    }
}
