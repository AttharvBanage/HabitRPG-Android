package com.habitrpg.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import kotlin.math.sqrt

// =============================================
// EXERCISE TYPES
// =============================================
enum class ExerciseType(
    val label: String,
    val icon: String,
    val description: String,
    val threshold: Float  // acceleration threshold to count a rep
) {
    CURL("Bicep Curl", "💪", "Hold phone, curl arm up & down", 3.5f),
    PUSHUP("Push-Up", "🏋️", "Place phone on back, do push-ups", 2.8f),
    SQUAT("Squat", "🦵", "Hold phone, squat up & down", 3.2f),
    SHOULDER_PRESS("Shoulder Press", "🙌", "Hold phone, press up & down", 3.0f),
    JUMPING_JACK("Jumping Jack", "⭐", "Hold phone, do jumping jacks", 4.0f)
}

// =============================================
// SENSOR MANAGER HELPER
// =============================================
class RepCounter(
    context: Context,
    private val exerciseType: ExerciseType,
    private val onRepCounted: (Int) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var repCount = 0
    private val gravity = FloatArray(3) { 0f }
    private val magnitudeHistory = ArrayDeque<Float>(10)

    // State machine
    private enum class Phase { IDLE, UP, DOWN }
    private var phase = Phase.IDLE
    private var lastRepTime = 0L
    private val cooldownMs = 600L

    fun start() {
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    fun reset() {
        repCount = 0
        phase = Phase.IDLE
        magnitudeHistory.clear()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        // Low-pass filter for gravity
        val alpha = 0.85f
        gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0]
        gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1]
        gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2]

        // Linear acceleration (remove gravity)
        val linearX = event.values[0] - gravity[0]
        val linearY = event.values[1] - gravity[1]
        val linearZ = event.values[2] - gravity[2]

        val magnitude = sqrt(linearX * linearX + linearY * linearY + linearZ * linearZ)

        // Smooth with rolling average
        magnitudeHistory.addLast(magnitude)
        if (magnitudeHistory.size > 8) magnitudeHistory.removeFirst()
        val smoothed = magnitudeHistory.average().toFloat()

        val threshold = exerciseType.threshold
        val now = System.currentTimeMillis()

        // State machine: IDLE -> UP -> DOWN -> count rep
        when (phase) {
            Phase.IDLE -> {
                if (smoothed > threshold) phase = Phase.UP
            }
            Phase.UP -> {
                if (smoothed < threshold * 0.3f) phase = Phase.DOWN
            }
            Phase.DOWN -> {
                if (smoothed > threshold * 0.6f) {
                    // Full cycle complete = 1 rep
                    if (now - lastRepTime > cooldownMs) {
                        repCount++
                        lastRepTime = now
                        onRepCounted(repCount)
                    }
                    phase = Phase.UP
                } else if (smoothed < threshold * 0.1f) {
                    phase = Phase.IDLE
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

// =============================================
// WORKOUT SCREEN
// =============================================
@Composable
fun WorkoutTrackerScreen() {
    var selectedExercise by remember { mutableStateOf<ExerciseType?>(null) }
    var isTracking by remember { mutableStateOf(false) }
    var repCount by remember { mutableStateOf(0) }
    var targetReps by remember { mutableStateOf(10) }
    var sets by remember { mutableStateOf(0) }
    var completedSets by remember { mutableStateOf(listOf<Int>()) }
    val context = LocalContext.current

    // Rep counter instance
    var repCounter by remember { mutableStateOf<RepCounter?>(null) }

    // Pulse animation when rep counted
    val pulseScale = remember { Animatable(1f) }
    LaunchedEffect(repCount) {
        if (repCount > 0) {
            pulseScale.animateTo(1.2f, animationSpec = tween(100))
            pulseScale.animateTo(1f, animationSpec = tween(100))
        }
    }

    // Cleanup on leave
    DisposableEffect(Unit) {
        onDispose { repCounter?.stop() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedGradientBackground()
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    "🏋️ Workout Tracker",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Phone detects your reps automatically",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }

            // Exercise picker
            item {
                GlassCard {
                    Text("Choose Exercise", color = Color.Gray, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    ExerciseType.values().forEach { exercise ->
                        val isSelected = selectedExercise == exercise
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) NeonBlue.copy(0.2f) else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (isSelected) NeonBlue else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    if (!isTracking) {
                                        selectedExercise = exercise
                                        repCount = 0
                                    }
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(exercise.icon, fontSize = 24.sp)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(exercise.label, color = Color.White, fontWeight = FontWeight.Medium)
                                Text(exercise.description, color = Color.Gray, fontSize = 11.sp)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }

            // Target reps picker
            item {
                GlassCard {
                    Text("Target Reps per Set", color = Color.Gray, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(8, 10, 12, 15, 20).forEach { reps ->
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(if (targetReps == reps) NeonGreen.copy(0.3f) else GlassColor)
                                    .border(1.dp, if (targetReps == reps) NeonGreen else GlassBorder, CircleShape)
                                    .clickable { if (!isTracking) targetReps = reps },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("$reps", color = if (targetReps == reps) NeonGreen else Color.Gray, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Rep counter display
            item {
                GlassCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "REPS",
                            color = Color.Gray,
                            fontSize = 13.sp,
                            letterSpacing = 3.sp
                        )
                        Spacer(Modifier.height(8.dp))

                        // Big rep counter
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .graphicsLayer(
                                    scaleX = pulseScale.value,
                                    scaleY = pulseScale.value
                                )
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            if (repCount >= targetReps) NeonGreen.copy(0.3f) else NeonBlue.copy(0.2f),
                                            Color.Transparent
                                        )
                                    ),
                                    CircleShape
                                )
                                .border(
                                    2.dp,
                                    if (repCount >= targetReps) NeonGreen else NeonBlue,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "$repCount",
                                    color = if (repCount >= targetReps) NeonGreen else Color.White,
                                    fontSize = 56.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    "/ $targetReps",
                                    color = Color.Gray,
                                    fontSize = 16.sp
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Progress bar
                        val progress = (repCount.toFloat() / targetReps).coerceIn(0f, 1f)
                        val animProgress by animateFloatAsState(progress, animationSpec = spring(), label = "rep")
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color.White.copy(0.1f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(animProgress)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(NeonBlue, NeonGreen)
                                        )
                                    )
                            )
                        }

                        if (repCount >= targetReps && isTracking) {
                            Spacer(Modifier.height(8.dp))
                            Text("✅ Set Complete! Tap Stop Set.", color = NeonGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Control buttons
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Start / Stop button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isTracking) Color.Red.copy(0.3f) else NeonBlue.copy(0.3f)
                            )
                            .border(
                                1.5.dp,
                                if (isTracking) Color.Red else NeonBlue,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                if (selectedExercise == null) return@clickable
                                if (isTracking) {
                                    // Stop tracking
                                    repCounter?.stop()
                                    repCounter = null
                                    isTracking = false
                                    if (repCount > 0) {
                                        completedSets = completedSets + repCount
                                        sets++
                                    }
                                    repCount = 0
                                } else {
                                    // Start tracking
                                    repCount = 0
                                    val counter = RepCounter(context, selectedExercise!!) { count ->
                                        repCount = count
                                    }
                                    counter.start()
                                    repCounter = counter
                                    isTracking = true
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (isTracking) "⏹ Stop Set" else "▶ Start Set",
                            color = if (isTracking) Color.Red else NeonBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    // Reset button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(GlassColor)
                            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                            .clickable {
                                if (!isTracking) {
                                    repCount = 0
                                    sets = 0
                                    completedSets = listOf()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🔄 Reset", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }

            // Completed sets history
            if (completedSets.isNotEmpty()) {
                item {
                    GlassCard {
                        Text("📊 Completed Sets", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(10.dp))
                        completedSets.forEachIndexed { index, reps ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Set ${index + 1}", color = Color.Gray)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("$reps reps", color = Color.White, fontWeight = FontWeight.Medium)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        if (reps >= targetReps) "✅" else "⚠️",
                                        fontSize = 16.sp
                                    )
                                }
                            }
                            if (index < completedSets.size - 1) HorizontalDivider(color = Color.White.copy(0.07f))
                        }
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = NeonBlue.copy(0.3f))
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Reps", color = Color.Gray)
                            Text("${completedSets.sum()}", color = NeonBlue, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }

            // Tips card
            item {
                GlassCard {
                    Text("💡 Tips for best accuracy", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    listOf(
                        "Hold phone firmly in your hand",
                        "Do full range of motion reps",
                        "Keep a steady pace",
                        "Avoid jerky or sudden movements between reps"
                    ).forEach { tip ->
                        Text("• $tip", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
        }
    }
}