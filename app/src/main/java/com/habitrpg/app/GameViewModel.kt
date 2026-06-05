package com.habitrpg.app

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import java.util.*

class GameViewModel : ViewModel(), SensorEventListener {
    // --- RPG STATE ---
    var player by mutableStateOf(Player())
        private set
    var habits by mutableStateOf(listOf<Habit>())
        private set
    var dailyQuests by mutableStateOf(listOf<DailyQuest>())
        private set
    var showLevelUp by mutableStateOf(false)
    var levelUpValue by mutableStateOf(1)
    var showAchievement by mutableStateOf(false)
    var achievementText by mutableStateOf("")

    // --- SENSOR STATE (For Secret Wireframe HUD) ---
    var gyroX by mutableStateOf(0f)
    var gyroY by mutableStateOf(0f)
    var gyroZ by mutableStateOf(0f)

    private var sensorManager: SensorManager? = null
    private var gyroscope: Sensor? = null

    // --- DATA LISTS ---
    val shopItems = listOf(
        ShopItem(name = "Health Potion", description = "Restore 20 HP", price = 30, icon = "❤️", color = Color.Red, effect = "heal"),
        ShopItem(name = "XP Elixir", description = "+50 XP instantly", price = 50, icon = "⭐", color = NeonBlue, effect = "xp"),
        ShopItem(name = "Gold Charm", description = "+20 Gold", price = 40, icon = "🪙", color = Color.Yellow, effect = "gold"),
        ShopItem(name = "Shield", description = "Protects next health loss", price = 60, icon = "🛡️", color = Color.Gray, effect = "shield"),
        ShopItem(name = "Streak Freeze", description = "Protects streak for one day", price = 80, icon = "❄️", color = Color.Cyan, effect = "freeze"),
        ShopItem(name = "Mana Crystal", description = "+10 Mana", price = 35, icon = "💎", color = Color(0xFFAA00FF), effect = "mana")
    )

    val friends = listOf(
        Friend(name = "Alex", avatar = "👤", level = 12, xp = 850, isOnline = true),
        Friend(name = "Jamie", avatar = "🧙", level = 8, xp = 420, isOnline = false),
        Friend(name = "Taylor", avatar = "⚔️", level = 15, xp = 1340, isOnline = true),
        Friend(name = "Sam", avatar = "🧝", level = 5, xp = 210, isOnline = true),
        Friend(name = "Jordan", avatar = "🧟", level = 20, xp = 2100, isOnline = false)
    )

    init {
        habits = listOf(
            Habit(name = "Morning Meditation", description = "10 min mindfulness", category = HabitCategory.HEALTH, difficulty = HabitDifficulty.EASY),
            Habit(name = "Read 30 pages", description = "Daily reading habit", category = HabitCategory.LEARNING, difficulty = HabitDifficulty.MEDIUM),
            Habit(name = "Workout", description = "Exercise for 45 min", category = HabitCategory.FITNESS, difficulty = HabitDifficulty.HARD),
            Habit(name = "Deep Work", description = "2hr focused session", category = HabitCategory.PRODUCTIVITY, difficulty = HabitDifficulty.HARD),
            Habit(name = "Drink water", description = "8 glasses per day", category = HabitCategory.HEALTH, difficulty = HabitDifficulty.EASY)
        )
        generateDailyQuests()
    }

    // --- SENSOR METHODS ---
    fun initSensors(context: Context) {
        if (sensorManager == null) {
            sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
            gyroscope?.also {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_GYROSCOPE) {
            event.values?.let { v ->
                gyroX = v[0]
                gyroY = v[1]
                gyroZ = v[2]
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onCleared() {
        super.onCleared()
        sensorManager?.unregisterListener(this)
    }

    // --- RPG METHODS ---
    fun generateDailyQuests() {
        dailyQuests = listOf(
            DailyQuest(title = "Habit Hero", description = "Complete 3 habits today", xpReward = 50, goldReward = 20, target = 3),
            DailyQuest(title = "Streak Master", description = "Reach a streak of 5", xpReward = 40, goldReward = 25, target = 5),
            DailyQuest(title = "Shopaholic", description = "Buy any item from the shop", xpReward = 30, goldReward = 15, target = 1)
        )
    }

    fun completeHabit(habit: Habit) {
        val idx = habits.indexOfFirst { it.id == habit.id }
        if (idx < 0) return
        val updated = habits.toMutableList()
        updated[idx] = updated[idx].copy(isCompletedToday = true, streak = updated[idx].streak + 1, lastCompletedDate = System.currentTimeMillis())
        habits = updated
        val oldLevel = player.level
        player = player.copy().also {
            it.addXP(habit.difficulty.xp)
            it.gold += habit.difficulty.gold
            it.heal(2)
            it.totalCompletions++
        }
        if (player.level > oldLevel) {
            levelUpValue = player.level
            showLevelUp = true
        }
    }

    fun deleteHabit(habit: Habit) { habits = habits.filter { it.id != habit.id } }
    fun addHabit(habit: Habit) { habits = habits + habit }
    fun updateAvatar(emoji: String) { player = player.copy(avatarEmoji = emoji) }

    fun purchaseItem(item: ShopItem): Boolean {
        if (player.gold < item.price) return false
        player = player.copy(gold = player.gold - item.price).also {
            when (item.effect) {
                "heal" -> it.heal(20)
                "xp" -> it.addXP(50)
                "gold" -> it.gold += 20
                "mana" -> it.mana = minOf(it.maxMana, it.mana + 10)
            }
        }
        return true
    }
}