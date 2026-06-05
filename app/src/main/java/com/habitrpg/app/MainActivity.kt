package com.habitrpg.app
import android.app.AlarmManager
import android.app.PendingIntent
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import android.Manifest
import java.util.*

// =============================================
// THEME & COLORS
// =============================================
val NeonBlue = Color(0xFF00D4FF)
val NeonGreen = Color(0xFF39FF14)
val NeonPink = Color(0xFFFF007F)
val NeonOrange = Color(0xFFFF6B00)
val DarkBackground = Color(0xFF0A0A1A)
val DarkSurface = Color(0xFF12122A)
val GlassColor = Color(0x1AFFFFFF)
val GlassBorder = Color(0x33FFFFFF)

@Composable
fun HabitRPGTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = NeonBlue,
            secondary = NeonGreen,
            background = DarkBackground,
            surface = DarkSurface,
            onBackground = Color.White,
            onSurface = Color.White
        ),
        content = content
    )
}

// =============================================
// DATA MODELS
// =============================================
enum class HabitFrequency(val label: String, val icon: String) {
    DAILY("Daily", "☀️"),
    WEEKLY("Weekly", "📅"),
    MONTHLY("Monthly", "🗓️"),
    CUSTOM("Custom", "⚙️")
}

enum class HabitCategory(val label: String, val icon: String, val color: Color) {
    HEALTH("Health", "❤️", NeonPink),
    PRODUCTIVITY("Productivity", "🔨", NeonBlue),
    LEARNING("Learning", "📚", NeonGreen),
    FITNESS("Fitness", "🏃", NeonOrange),
    MAGIC("Magic", "🪄", Color(0xFFAA00FF))
}

enum class HabitDifficulty(val label: String, val xp: Int, val gold: Int, val stars: Int) {
    EASY("Easy", 10, 5, 1),
    MEDIUM("Medium", 20, 10, 2),
    HARD("Hard", 30, 15, 3)
}

data class Habit(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val category: HabitCategory,
    val difficulty: HabitDifficulty,
    val frequency: HabitFrequency = HabitFrequency.DAILY,
    var streak: Int = 0,
    var isCompletedToday: Boolean = false,
    var lastCompletedDate: Long? = null
)

data class ShopItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String,
    val price: Int,
    val icon: String,
    val color: Color,
    val effect: String
)

data class Friend(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val avatar: String,
    val level: Int,
    val xp: Int,
    val isOnline: Boolean
)

data class DailyQuest(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val xpReward: Int,
    val goldReward: Int,
    var progress: Int = 0,
    val target: Int,
    var isCompleted: Boolean = false
)

data class Player(
    var level: Int = 1,
    var xp: Int = 0,
    var gold: Int = 50,
    var health: Int = 100,
    var maxHealth: Int = 100,
    var mana: Int = 50,
    var maxMana: Int = 50,
    var title: String = "Novice",
    var totalCompletions: Int = 0,
    var totalXPEarned: Int = 0,
    var avatarEmoji: String = "🧱",
    var achievements: MutableList<String> = mutableListOf()
) {
    val nextLevelXP: Int get() = level * 120

    fun addXP(amount: Int) {
        xp += amount
        totalXPEarned += amount
        val titles = listOf("Novice", "Warrior", "Mage", "Champion", "Legend")
        while (xp >= nextLevelXP) {
            level++
            xp -= nextLevelXP
            maxHealth += 10
            health = maxHealth
            maxMana += 5
            mana = maxMana
            title = titles[minOf(level - 1, titles.size - 1)]
        }
    }

    fun heal(amount: Int) {
        health = minOf(maxHealth, health + amount)
    }
}

// =============================================
// VIEW MODEL
// =============================================



// =============================================
// MAIN ACTIVITY
// =============================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HabitRPGTheme {
                val vm: GameViewModel = viewModel()

                // THIS BLOCK IS REQUIRED FOR ANDROID 13+
                val launcher = rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                ) { }

                LaunchedEffect(Unit) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                HabitRPGApp(vm)
            }
        }
    }
}

// =============================================
// APP ROOT
// =============================================
@Composable
fun HabitRPGApp(vm: GameViewModel) {
    var selectedTab by remember { mutableStateOf(0) }

    // Level up dialog
    if (vm.showLevelUp) {
        LevelUpDialog(level = vm.levelUpValue) { vm.showLevelUp = false }
    }

    Scaffold(
        containerColor = DarkBackground,
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                contentColor = NeonBlue
            ) {
                val tabs = listOf(
                    Triple("Quest", "🏠", 0),
                    Triple("Habits", "✅", 1),
                    Triple("Shop", "🛒", 2),
                    Triple("Ranking", "🏆", 3),
                    Triple("Workout", "🏋️", 4),
                    Triple("Profile", "👤", 5)
                )
                tabs.forEach { (label, icon, index) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Text(icon, fontSize = 20.sp)
                        },
                        label = {
                            Text(
                                label,
                                color = if (selectedTab == index) NeonBlue else Color.Gray,
                                fontSize = 10.sp
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NeonBlue,
                            indicatorColor = NeonBlue.copy(alpha = 0.15f)
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> DashboardScreen(vm = vm, onDevTabTrigger = { selectedTab = 99 })
                1 -> HabitsScreen(vm)
                2 -> ShopScreen(vm)
                3 -> LeaderboardScreen(vm)
                4 -> WorkoutTrackerScreen()      // ✅ Original Tracker is BACK
                5 -> ProfileScreen(vm)
                99 -> SecretWireframeDevScreen(vm) // 🛠️ Secret Dev HUD
            }
        }
    }
}

// =============================================
// SHARED COMPOSABLES
// =============================================
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    glowColor: Color = NeonBlue,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .background(GlassColor, RoundedCornerShape(16.dp))
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .padding(12.dp),
        content = content
    )
}

@Composable
fun NeonText(text: String, color: Color = NeonBlue, fontSize: TextUnit = 14.sp, fontWeight: FontWeight = FontWeight.Normal) {
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        modifier = Modifier.drawBehind {
            drawIntoCanvas { canvas ->
                val paint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    this.color = android.graphics.Color.TRANSPARENT
                    maskFilter = android.graphics.BlurMaskFilter(
                        8f, android.graphics.BlurMaskFilter.Blur.NORMAL
                    )
                }
                // glow effect hint
            }
        }
    )
}

@Composable
fun StatProgressBar(
    value: Int,
    max: Int,
    color: Color,
    label: String,
    modifier: Modifier = Modifier
) {
    val progress = if (max > 0) value.toFloat() / max.toFloat() else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "progress"
    )
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color.Gray, fontSize = 11.sp)
            Text("$value/$max", color = color, fontSize = 11.sp)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.1f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.horizontalGradient(listOf(color, color.copy(green = minOf(1f, color.green + 0.3f))))
                    )
            )
        }
    }
}

@Composable
fun PulsingDot(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.4f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "scale"
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .scale(scale)
            .background(color, CircleShape)
    )
}

fun Modifier.scale(scale: Float) = this.then(
    Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
)

@Composable
fun AnimatedGradientBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "bg")
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Reverse),
        label = "offset"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        DarkBackground,
                        Color(0xFF050520),
                        NeonBlue.copy(alpha = 0.08f + offset * 0.05f),
                        DarkBackground
                    )
                )
            )
    )
}

// =============================================
// DASHBOARD SCREEN
// =============================================
@Composable
fun DashboardScreen(vm: GameViewModel, onDevTabTrigger: () -> Unit) { // Add this parameter
    // ... inside the LazyColumn ...


        val p = vm.player
        val habits = vm.habits
        val completedCount = habits.count { it.isCompletedToday }
        val maxStreak = habits.maxOfOrNull { it.streak } ?: 0
        val totalCombo = maxOf(1, habits.sumOf { it.streak } / 10)

        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedGradientBackground()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Text(
                        "⚔️ Quest Log",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                // Avatar & Stats card
                item {
                    AvatarStatsCard(
                        p = p,
                        onLongClick = { onDevTabTrigger() } // This matches your secret dev index
                    )
                }
                // XP Bar
                item {
                    XPBarCard(p)
                }
                // HP & Mana
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassCard(modifier = Modifier.weight(1f)) {
                            StatProgressBar(p.health, p.maxHealth, Color.Red, "❤️ HP")
                        }
                        GlassCard(modifier = Modifier.weight(1f)) {
                            StatProgressBar(p.mana, p.maxMana, NeonBlue, "💧 MANA")
                        }
                    }
                }
                // Daily quest progress
                item {
                    DailyQuestProgressCard(completedCount, habits.size)
                }
                // Challenges
                if (vm.dailyQuests.isNotEmpty()) {
                    item {
                        ChallengesCard(vm.dailyQuests)
                    }
                }
                // Streak & Combo
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassCard(modifier = Modifier.weight(1f)) {
                            Text("🔥 STREAK", color = Color.Gray, fontSize = 11.sp)
                            Spacer(Modifier.height(4.dp))
                            StreakDisplay(maxStreak)
                        }
                        GlassCard(modifier = Modifier.weight(1f)) {
                            Text("⚡ COMBO", color = Color.Gray, fontSize = 11.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "x$totalCombo",
                                color = NeonOrange,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
                // Today's tasks
                item {
                    TodayTasksCard(habits, vm)
                }
            }
        }
    }


@Composable
fun AvatarStatsCard(p: Player, onLongClick: () -> Unit = {}) { // ✅ Added onLongClick
    val infiniteTransition = rememberInfiniteTransition(label = "avatar")
    val avatarScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "avatarScale"
    )

    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // --- THE AVATAR BOX (The Secret Entrance) ---
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = { onLongClick() } // ✅ This triggers the dev tab
                    )
                }
            ) {
                // Glow ring
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(NeonBlue.copy(alpha = 0.2f), CircleShape)
                        .scale(avatarScale)
                )
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(
                            Brush.radialGradient(listOf(NeonBlue.copy(0.3f), DarkSurface)),
                            CircleShape
                        )
                        .border(2.dp, NeonBlue.copy(0.7f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(p.avatarEmoji, fontSize = 28.sp)
                }

                // Level badge
                Box(
                    modifier = Modifier
                        .offset(x = 20.dp, y = 20.dp)
                        .size(22.dp)
                        .background(NeonBlue, CircleShape)
                        .border(1.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${p.level}", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Black)
                }
            }
            // ----------------------------------------------

            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(p.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Level ${p.level}", color = Color.Gray, fontSize = 12.sp)
            }
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🪙", fontSize = 16.sp)
                    Spacer(Modifier.width(4.dp))
                    Text("${p.gold}", color = Color.Yellow, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun XPBarCard(p: Player) {
    val progress = if (p.nextLevelXP > 0) p.xp.toFloat() / p.nextLevelXP else 0f
    val animatedProgress by animateFloatAsState(targetValue = progress, animationSpec = spring(), label = "xp")
    val infiniteTransition = rememberInfiniteTransition(label = "shine")
    val shineOffset by infiniteTransition.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "shine"
    )
    GlassCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("⚡ XP", color = Color.Gray, fontSize = 11.sp)
            Text("${p.xp}/${p.nextLevelXP}", color = NeonBlue, fontSize = 11.sp)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(Color.White.copy(0.1f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        Brush.horizontalGradient(listOf(NeonBlue, NeonGreen))
                    )
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "${(progress * 100).toInt()}% to Level ${p.level + 1}",
            color = Color.Gray, fontSize = 10.sp
        )
    }
}

@Composable
fun DailyQuestProgressCard(completed: Int, total: Int) {
    val progress = if (total > 0) completed.toFloat() / total else 0f
    val animatedProgress by animateFloatAsState(targetValue = progress, animationSpec = spring(), label = "dq")
    GlassCard(glowColor = NeonGreen) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("🗡️ Daily Quests", color = Color.White, fontWeight = FontWeight.SemiBold)
            Text("$completed/$total", color = NeonGreen, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(0.1f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brush.horizontalGradient(listOf(NeonGreen, Color.Yellow)))
            )
        }
        Spacer(Modifier.height(4.dp))
        Text("${(progress * 100).toInt()}% Complete", color = Color.Gray, fontSize = 10.sp)
    }
}

@Composable
fun ChallengesCard(quests: List<DailyQuest>) {
    GlassCard {
        Text("📜 Challenges", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        quests.forEach { quest ->
            QuestRow(quest)
            if (quest != quests.last()) Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun QuestRow(quest: DailyQuest) {
    val progress = if (quest.target > 0) quest.progress.toFloat() / quest.target else 0f
    val animatedProgress by animateFloatAsState(targetValue = progress, animationSpec = spring(), label = "qp")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (quest.isCompleted) "✅" else "🔲", fontSize = 18.sp)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(quest.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(quest.description, color = Color.Gray, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(0.1f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (quest.isCompleted) NeonGreen else NeonBlue)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text("⭐${quest.xpReward}", color = NeonBlue, fontSize = 10.sp)
            Text("🪙${quest.goldReward}", color = Color.Yellow, fontSize = 10.sp)
        }
    }
}

@android.annotation.SuppressLint("ScheduleExactAlarm")
fun scheduleHabitReminder(context: Context, habit: Habit) {
    // 1. Get the system service
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // 2. Build the intent
    val intent = Intent(context, HabitNotificationReceiver::class.java).apply {
        putExtra("HABIT_NAME", habit.name)
    }

    // 3. Create the PendingIntent
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        habit.id.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // 4. Set the trigger time (10 seconds)
    val triggerAt = System.currentTimeMillis() + 10_000

    // 5. Schedule the alarm
    alarmManager.setExactAndAllowWhileIdle(
        AlarmManager.RTC_WAKEUP,
        triggerAt,
        pendingIntent
    )
}

@Composable
fun StreakDisplay(streak: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "fire")
    val fireScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "fire"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("🔥", fontSize = 28.sp, modifier = Modifier.scale(fireScale))
        Spacer(Modifier.width(4.dp))
        Text(
            "$streak",
            color = NeonOrange,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
fun TodayTasksCard(habits: List<Habit>, vm: GameViewModel) {
    val context = LocalContext.current // ✅ ADD THIS

    GlassCard {
        Text("📋 Today's Tasks", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Spacer(Modifier.height(8.dp))
        if (habits.isEmpty()) {
            Text("No habits yet!", color = Color.Gray, fontSize = 13.sp)
        } else {
            habits.take(5).forEach { habit ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (!habit.isCompletedToday) {
                                vm.completeHabit(habit)
                                scheduleHabitReminder(context, habit) // ✅ ADD THIS
                            }
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // ... (rest of your existing Box and Text code)
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(
                                if (habit.isCompletedToday) NeonGreen.copy(0.3f) else Color.White.copy(0.1f),
                                CircleShape
                            )
                            .border(
                                1.5.dp,
                                if (habit.isCompletedToday) NeonGreen else Color.Gray,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (habit.isCompletedToday) Text("✓", color = NeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(habit.category.icon, fontSize = 16.sp)
                    Spacer(Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            habit.name,
                            color = if (habit.isCompletedToday) Color.Gray else Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            textDecoration = if (habit.isCompletedToday) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                        )
                    }
                    if (habit.streak > 0) {
                        Text("🔥${habit.streak}", color = NeonOrange, fontSize = 11.sp)
                    }
                }
                if (habit != habits.take(5).last()) {
                    HorizontalDivider(color = Color.White.copy(0.07f))
                }
            }
        }
    }
}

// =============================================
// HABITS SCREEN
// =============================================
@Composable
fun HabitsScreen(vm: GameViewModel) {
    var selectedFilter by remember { mutableStateOf<HabitFrequency?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    val filtered = if (selectedFilter != null)
        vm.habits.filter { it.frequency == selectedFilter } else vm.habits

    if (showAddDialog) {
        AddHabitDialog(onDismiss = { showAddDialog = false }, onAdd = { vm.addHabit(it) })
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedGradientBackground()
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("✅ Habits", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = { showAddDialog = true }) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(NeonBlue.copy(0.2f), CircleShape)
                            .border(1.dp, NeonBlue, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+", color = NeonBlue, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            // Filter chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChipView("All", selectedFilter == null) { selectedFilter = null }
                }
                items(HabitFrequency.values().toList()) { freq ->
                    FilterChipView("${freq.icon} ${freq.label}", selectedFilter == freq) {
                        selectedFilter = if (selectedFilter == freq) null else freq
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("➕", fontSize = 48.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("No habits yet. Tap + to add one!", color = Color.Gray, textAlign = TextAlign.Center)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.id }) { habit ->
                        HabitRowCard(habit = habit,
                            onComplete = { if (!habit.isCompletedToday) vm.completeHabit(habit) },
                            onDelete = { vm.deleteHabit(habit) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FilterChipView(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) NeonBlue.copy(0.3f) else GlassColor)
            .border(1.dp, if (isSelected) NeonBlue else GlassBorder, RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(label, color = if (isSelected) NeonBlue else Color.Gray, fontSize = 13.sp)
    }
}

@Composable
fun HabitRowCard(habit: Habit, onComplete: () -> Unit, onDelete: () -> Unit) {
    val animScale by animateFloatAsState(
        targetValue = if (habit.isCompletedToday) 0.98f else 1f,
        animationSpec = spring(),
        label = "habitScale"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(animScale)
            .background(GlassColor, RoundedCornerShape(14.dp))
            .border(
                1.dp,
                if (habit.isCompletedToday) NeonGreen.copy(0.5f) else GlassBorder,
                RoundedCornerShape(14.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category color indicator
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(50.dp)
                    .background(habit.category.color, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(10.dp))
            Text(habit.category.icon, fontSize = 22.sp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    habit.name,
                    color = if (habit.isCompletedToday) Color.Gray else Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    textDecoration = if (habit.isCompletedToday) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                )
                Text(habit.description, color = Color.Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${habit.frequency.icon} ${habit.frequency.label}", color = Color.Gray, fontSize = 10.sp)
                    Text("•", color = Color.Gray, fontSize = 10.sp)
                    Text(habit.difficulty.label, color = habit.category.color, fontSize = 10.sp)
                    if (habit.streak > 0) {
                        Text("• 🔥${habit.streak}", color = NeonOrange, fontSize = 10.sp)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Complete button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (habit.isCompletedToday) NeonGreen.copy(0.2f) else NeonBlue.copy(0.2f),
                            CircleShape
                        )
                        .border(
                            1.5.dp,
                            if (habit.isCompletedToday) NeonGreen else NeonBlue,
                            CircleShape
                        )
                        .clickable { onComplete() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (habit.isCompletedToday) "✓" else "◎",
                        color = if (habit.isCompletedToday) NeonGreen else NeonBlue,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                // Delete button
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color.Red.copy(0.15f), CircleShape)
                        .clickable { onDelete() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("🗑", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun AddHabitDialog(onDismiss: () -> Unit, onAdd: (Habit) -> Unit) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(HabitCategory.HEALTH) }
    var difficulty by remember { mutableStateOf(HabitDifficulty.MEDIUM) }
    var frequency by remember { mutableStateOf(HabitFrequency.DAILY) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text("✨ Add New Habit", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Habit Name", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = NeonBlue, unfocusedBorderColor = Color.Gray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description", color = Color.Gray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                        focusedBorderColor = NeonBlue, unfocusedBorderColor = Color.Gray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Category", color = Color.Gray, fontSize = 12.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(HabitCategory.values().toList()) { cat ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (category == cat) cat.color.copy(0.3f) else GlassColor)
                                .border(1.dp, if (category == cat) cat.color else GlassBorder, RoundedCornerShape(8.dp))
                                .clickable { category = cat }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("${cat.icon} ${cat.label}", color = if (category == cat) cat.color else Color.Gray, fontSize = 11.sp)
                        }
                    }
                }
                Text("Difficulty", color = Color.Gray, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HabitDifficulty.values().forEach { diff ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (difficulty == diff) NeonBlue.copy(0.2f) else GlassColor)
                                .border(1.dp, if (difficulty == diff) NeonBlue else GlassBorder, RoundedCornerShape(8.dp))
                                .clickable { difficulty = diff }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(diff.label, color = if (difficulty == diff) NeonBlue else Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
                Text("Frequency", color = Color.Gray, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HabitFrequency.values().take(2).forEach { freq ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (frequency == freq) NeonGreen.copy(0.2f) else GlassColor)
                                .border(1.dp, if (frequency == freq) NeonGreen else GlassBorder, RoundedCornerShape(8.dp))
                                .clickable { frequency = freq }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${freq.icon} ${freq.label}", color = if (frequency == freq) NeonGreen else Color.Gray, fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onAdd(Habit(name = name, description = desc, category = category, difficulty = difficulty, frequency = frequency))
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonBlue)
            ) {
                Text("Add Habit", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color.Gray) }
        }
    )
}

// =============================================
// SHOP SCREEN
// =============================================
@Composable
fun ShopScreen(vm: GameViewModel) {
    var shakeItem by remember { mutableStateOf<String?>(null) }
    var purchasedItem by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedGradientBackground()
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🛒 Magic Shop", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🪙", fontSize = 16.sp)
                        Spacer(Modifier.width(4.dp))
                        Text("${vm.player.gold}", color = Color.Yellow, fontWeight = FontWeight.Bold)
                    }
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(vm.shopItems) { item ->
                    val isShaking = shakeItem == item.id
                    val isPurchased = purchasedItem == item.id
                    val shakeOffset by animateFloatAsState(
                        targetValue = if (isShaking) 8f else 0f,
                        animationSpec = if (isShaking) spring(stiffness = Spring.StiffnessHigh) else spring(),
                        label = "shake"
                    )
                    ShopItemCard(
                        item = item,
                        canAfford = vm.player.gold >= item.price,
                        isPurchased = isPurchased,
                        shakeOffset = shakeOffset,
                        onTap = {
                            val success = vm.purchaseItem(item)
                            if (success) {
                                purchasedItem = item.id
                                scope.launch {
                                    delay(800)
                                    purchasedItem = null
                                }
                            } else {
                                shakeItem = item.id
                                scope.launch {
                                    delay(500)
                                    shakeItem = null
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ShopItemCard(item: ShopItem, canAfford: Boolean, isPurchased: Boolean, shakeOffset: Float, onTap: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = if (isPurchased) 1.05f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset(x = shakeOffset.dp)
            .scale(scale)
            .background(GlassColor, RoundedCornerShape(16.dp))
            .border(
                1.5.dp,
                if (isPurchased) NeonGreen else if (!canAfford) Color.Red.copy(0.4f) else item.color.copy(0.5f),
                RoundedCornerShape(16.dp)
            )
            .clickable { onTap() }
            .padding(14.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(item.color.copy(0.2f), CircleShape)
                    .border(1.dp, item.color.copy(0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(item.icon, fontSize = 26.sp)
            }
            Spacer(Modifier.height(8.dp))
            Text(item.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, textAlign = TextAlign.Center)
            Text(item.description, color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (canAfford) Color.Yellow.copy(0.15f) else Color.Red.copy(0.15f))
                    .border(1.dp, if (canAfford) Color.Yellow.copy(0.5f) else Color.Red.copy(0.5f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    if (isPurchased) "✅ Bought!" else "🪙 ${item.price}",
                    color = if (isPurchased) NeonGreen else if (canAfford) Color.Yellow else Color.Red,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// =============================================
// LEADERBOARD SCREEN
// =============================================
@Composable
fun LeaderboardScreen(vm: GameViewModel) {
    var selectedTab by remember { mutableStateOf(0) }
    val globalFriends = listOf(
        Friend(name = "DragonSlayer", avatar = "🐉", level = 45, xp = 15000, isOnline = true),
        Friend(name = "MageMaster", avatar = "🧙", level = 38, xp = 12400, isOnline = false),
        Friend(name = "Shadow", avatar = "👤", level = 27, xp = 8200, isOnline = true),
        Friend(name = "ElfArcher", avatar = "🏹", level = 22, xp = 6100, isOnline = true),
        Friend(name = "OrcWarrior", avatar = "👹", level = 19, xp = 4800, isOnline = false)
    )

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedGradientBackground()
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                "🏆 Leaderboard",
                color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
            )
            // Segment control
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .background(GlassColor, RoundedCornerShape(12.dp))
                    .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                    .padding(4.dp)
            ) {
                listOf("Friends", "Global").forEachIndexed { i, label ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selectedTab == i) NeonBlue.copy(0.3f) else Color.Transparent)
                            .clickable { selectedTab = i }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, color = if (selectedTab == i) NeonBlue else Color.Gray, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            val list = if (selectedTab == 0) vm.friends.sortedByDescending { it.level } else globalFriends
            if (selectedTab == 1 || list.isEmpty()) {
                if (selectedTab == 1) {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(list) { idx, friend ->
                            LeaderboardRow(rank = idx + 1, friend = friend, isGlobal = true)
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("👥", fontSize = 48.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("No friends yet. Add some!", color = Color.Gray)
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(list) { idx, friend ->
                        LeaderboardRow(rank = idx + 1, friend = friend, isGlobal = false)
                    }
                }
            }
        }
    }
}

@Composable
fun LeaderboardRow(rank: Int, friend: Friend, isGlobal: Boolean) {
    val rankColor = when (rank) {
        1 -> Color(0xFFFFD700)
        2 -> Color(0xFFC0C0C0)
        3 -> Color(0xFFCD7F32)
        else -> Color.Gray
    }
    val rankEmoji = when (rank) { 1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> "#$rank" }
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(rankEmoji, fontSize = if (rank <= 3) 22.sp else 14.sp, color = rankColor, modifier = Modifier.width(36.dp))
            Text(friend.avatar, fontSize = 28.sp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(friend.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Level ${friend.level}", color = Color.Gray, fontSize = 12.sp)
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(if (friend.isOnline) NeonGreen else Color.Gray, CircleShape)
                    )
                }
            }
            Text("${friend.xp} XP", color = if (isGlobal) NeonGreen else NeonBlue, fontWeight = FontWeight.Bold)
        }
    }
}

// =============================================
// PROFILE SCREEN
// =============================================
val avatarEmojis = listOf("🧱", "🧙", "⚔️", "🧝", "🧟", "🐉", "🦸", "🧜", "🧚", "🪄", "👑", "🛡️")

@Composable
fun ProfileScreen(vm: GameViewModel) {
    var showAvatarPicker by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val p = vm.player

    if (showAvatarPicker) {
        AvatarPickerDialog(current = p.avatarEmoji, onSelect = { vm.updateAvatar(it); showAvatarPicker = false }, onDismiss = { showAvatarPicker = false })
    }
    if (showSettings) {
        SettingsDialog(onDismiss = { showSettings = false })
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedGradientBackground()
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text("👤 Profile", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            // Avatar card
            item {
                GlassCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                                .background(
                                    Brush.linearGradient(listOf(NeonBlue, NeonPink)),
                                    CircleShape
                                )
                                .border(2.dp, Color.White.copy(0.3f), CircleShape)
                                .clickable { showAvatarPicker = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(p.avatarEmoji, fontSize = 32.sp)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(p.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Level ${p.level}", color = NeonBlue, fontSize = 14.sp)
                            Text("Tap avatar to change", color = Color.Gray, fontSize = 10.sp)
                        }
                    }
                }
            }
            // Stats
            item {
                GlassCard {
                    Text("📊 Stats", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Spacer(Modifier.height(10.dp))
                    val stats = listOf(
                        "⭐ Level" to "${p.level}",
                        "⚡ XP" to "${p.xp}/${p.nextLevelXP}",
                        "🪙 Gold" to "${p.gold}",
                        "❤️ Health" to "${p.health}/${p.maxHealth}",
                        "💧 Mana" to "${p.mana}/${p.maxMana}",
                        "✅ Total Completions" to "${p.totalCompletions}",
                        "🔢 Total XP Earned" to "${p.totalXPEarned}"
                    )
                    stats.forEach { (label, value) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label, color = Color.Gray, fontSize = 13.sp)
                            Text(value, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        }
                        if (label != stats.last().first) HorizontalDivider(color = Color.White.copy(0.06f))
                    }
                }
            }
            // Achievements
            item {
                GlassCard {
                    Text("🏅 Achievements", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    if (p.achievements.isEmpty()) {
                        Text("Complete habits to earn achievements!", color = Color.Gray, fontSize = 13.sp)
                    } else {
                        p.achievements.forEach { ach ->
                            Text("• $ach", color = NeonGreen, fontSize = 13.sp, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
            }
            // Settings button
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GlassColor, RoundedCornerShape(14.dp))
                        .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                        .clickable { showSettings = true }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚙️ Settings", color = NeonBlue, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
fun AvatarPickerDialog(current: String, onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text("Choose Avatar", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(200.dp)
            ) {
                items(avatarEmojis) { emoji ->
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .background(
                                if (current == emoji) NeonBlue.copy(0.3f) else GlassColor,
                                CircleShape
                            )
                            .border(1.5.dp, if (current == emoji) NeonBlue else GlassBorder, CircleShape)
                            .clickable { onSelect(emoji) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, fontSize = 24.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = NeonBlue) }
        }
    )
}

@Composable
fun SettingsDialog(onDismiss: () -> Unit) {
    var sound by remember { mutableStateOf(true) }
    var haptics by remember { mutableStateOf(true) }
    var reduceMotion by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text("⚙️ Settings", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("🔊 Sound Effects", color = Color.White)
                    Switch(checked = sound, onCheckedChange = { sound = it }, colors = SwitchDefaults.colors(checkedThumbColor = NeonBlue, checkedTrackColor = NeonBlue.copy(0.4f)))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("📳 Haptic Feedback", color = Color.White)
                    Switch(checked = haptics, onCheckedChange = { haptics = it }, colors = SwitchDefaults.colors(checkedThumbColor = NeonBlue, checkedTrackColor = NeonBlue.copy(0.4f)))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("🎭 Reduce Motion", color = Color.White)
                    Switch(checked = reduceMotion, onCheckedChange = { reduceMotion = it }, colors = SwitchDefaults.colors(checkedThumbColor = NeonBlue, checkedTrackColor = NeonBlue.copy(0.4f)))
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = NeonBlue)) {
                Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    )
}

// =============================================
// LEVEL UP DIALOG
// =============================================
@Composable
fun LevelUpDialog(level: Int, onDismiss: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "levelup")
    val glow by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "glow"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("✨🎉✨", fontSize = 32.sp)
                Spacer(Modifier.height(4.dp))
                Text("LEVEL UP!", color = NeonBlue.copy(alpha = glow), fontSize = 24.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("You reached", color = Color.Gray, textAlign = TextAlign.Center)
                Text("Level $level", color = Color.Yellow, fontSize = 36.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text("Max HP and Mana increased!", color = NeonGreen, textAlign = TextAlign.Center, fontSize = 13.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = NeonBlue)
            ) {
                Text("⚡ Continue Adventure", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
fun SecretWireframeDevScreen(vm: GameViewModel) {
    val context = LocalContext.current

    // Start the sensors when this secret screen opens
    LaunchedEffect(Unit) {
        vm.initSensors(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050505)) // Pure black
            .padding(24.dp)
    ) {
        Text("SYSTEM_WIREFRM_V1.0", color = Color.White, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 12.sp)
        Spacer(Modifier.height(32.dp))

        // Data Boxes with simple wireframe borders
        WireDataBox("AXIS_X_PITCH", vm.gyroX)
        WireDataBox("AXIS_Y_ROLL", vm.gyroY)
        WireDataBox("AXIS_Z_YAW", vm.gyroZ)

        Spacer(Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).background(Color.Green, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text("RAW_DATA_FEED: LIVE", color = Color.Green, fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        }
    }
}

@Composable
fun WireDataBox(label: String, value: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .border(1.dp, Color.White.copy(alpha = 0.2f))
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.Gray, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontSize = 10.sp)
            Text(
                text = String.format("%.4f", value),
                color = Color.White,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}