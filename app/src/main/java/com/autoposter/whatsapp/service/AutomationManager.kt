package com.autoposter.whatsapp.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.autoposter.whatsapp.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

object AutomationManager {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var delayJob: Job? = null

    // User Profile & Theme state (Client-Facing Display)
    val userProfileName = MutableStateFlow("Vishal Kumar (Active)")
    val userProfilePhone = MutableStateFlow("Connected via WhatsApp")
    val isDarkMode = MutableStateFlow(false) // Default Light Mode!

    // Target configuration
    val targetGroups = MutableStateFlow<List<TargetGroupItem>>(emptyList())
    val categories = MutableStateFlow(listOf("General", "Marketing", "Clients", "VIP"))
    val activeCategoryFilter = MutableStateFlow("All")

    // Media & Caption Content
    val selectedMediaUri = MutableStateFlow<Uri?>(null)
    val mediaMimeType = MutableStateFlow("image/*")
    val captionText = MutableStateFlow("")

    // Anti-ban randomized timing
    val minDelaySeconds = MutableStateFlow(6)
    val maxDelaySeconds = MutableStateFlow(12)

    // Execution state
    private val _status = MutableStateFlow(AutomationStatus())
    val status: StateFlow<AutomationStatus> = _status.asStateFlow()

    // Analytics & History
    val broadcastHistory = MutableStateFlow<List<BroadcastRecord>>(emptyList())
    val stats = MutableStateFlow(BroadcastStats())

    private var activeQueue: List<TargetGroupItem> = emptyList()

    val isRunning: Boolean
        get() = _status.value.step != AutomationStep.IDLE &&
                _status.value.step != AutomationStep.COMPLETED &&
                _status.value.step != AutomationStep.ERROR

    fun openDirectWhatsAppPicker(context: Context) {
        val uri = selectedMediaUri.value
        if (uri == null) {
            Toast.makeText(context, "Please select a photo, video or sticker first!", Toast.LENGTH_SHORT).show()
            return
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mediaMimeType.value
            putExtra(Intent.EXTRA_STREAM, uri)

            val caption = captionText.value.trim()
            if (caption.isNotEmpty()) {
                putExtra(Intent.EXTRA_TEXT, caption)
            }

            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            context.startActivity(shareIntent)
            val record = BroadcastRecord(
                groupName = "Direct WhatsApp Share",
                mediaType = mediaMimeType.value,
                captionPreview = captionText.value.take(40),
                isSuccess = true
            )
            broadcastHistory.value = listOf(record) + broadcastHistory.value.take(29)
            val cur = stats.value
            stats.value = BroadcastStats(cur.totalDispatched + 1, cur.totalSuccess + 1, cur.minutesSaved + 0.5)
            savePersistence(context)
        } catch (e: Exception) {
            try {
                shareIntent.setPackage("com.whatsapp.w4b")
                context.startActivity(shareIntent)
            } catch (ex: Exception) {
                Toast.makeText(context, "WhatsApp is not installed on this phone.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun addGroup(name: String, category: String = "General") {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty() && targetGroups.value.none { it.name.equals(trimmed, ignoreCase = true) }) {
            targetGroups.value = targetGroups.value + TargetGroupItem(trimmed, category)
        }
    }

    fun removeGroup(name: String) {
        targetGroups.value = targetGroups.value.filter { it.name != name }
    }

    fun clearGroups() {
        targetGroups.value = emptyList()
    }

    fun addCategory(categoryName: String) {
        val trimmed = categoryName.trim()
        if (trimmed.isNotEmpty() && !categories.value.contains(trimmed)) {
            categories.value = categories.value + trimmed
        }
    }

    fun getFilteredGroups(): List<TargetGroupItem> {
        val filter = activeCategoryFilter.value
        return if (filter == "All") {
            targetGroups.value
        } else {
            targetGroups.value.filter { it.category == filter }
        }
    }

    fun startBroadcast(context: Context) {
        val queue = getFilteredGroups()
        val uri = selectedMediaUri.value

        if (queue.isEmpty()) {
            Toast.makeText(context, "No groups in queue! Use 'Open WhatsApp & Pick' or add a group below.", Toast.LENGTH_SHORT).show()
            return
        }

        if (uri == null) {
            Toast.makeText(context, "Please select a photo, video or sticker first!", Toast.LENGTH_SHORT).show()
            return
        }

        activeQueue = queue
        dispatchToGroup(context, 0)
    }

    fun stopBroadcast() {
        delayJob?.cancel()
        _status.value = AutomationStatus(
            step = AutomationStep.IDLE,
            logMessage = "Broadcast stopped safely."
        )
    }

    fun getCurrentTarget(): String? {
        val index = _status.value.currentIndex
        return if (index in activeQueue.indices) activeQueue[index].name else null
    }

    fun updateStep(step: AutomationStep, message: String) {
        _status.value = _status.value.copy(step = step, logMessage = message)
    }

    fun onGroupDispatched(context: Context) {
        val currentIndex = _status.value.currentIndex
        val targetName = getCurrentTarget() ?: "Group"

        val record = BroadcastRecord(
            groupName = targetName,
            mediaType = mediaMimeType.value,
            captionPreview = captionText.value.take(40),
            isSuccess = true
        )
        broadcastHistory.value = listOf(record) + broadcastHistory.value.take(29)

        val currentStats = stats.value
        val newSuccess = currentStats.totalSuccess + 1
        val newTotal = currentStats.totalDispatched + 1
        val newSavedMinutes = currentStats.minutesSaved + 0.6
        stats.value = BroadcastStats(newTotal, newSuccess, newSavedMinutes)
        savePersistence(context)

        val nextIndex = currentIndex + 1
        val total = activeQueue.size

        if (nextIndex >= total) {
            _status.value = _status.value.copy(
                step = AutomationStep.COMPLETED,
                currentIndex = total,
                totalCount = total,
                logMessage = "Success! All $total groups reached."
            )
            return
        }

        val minSec = minDelaySeconds.value
        val maxSec = maxDelaySeconds.value.coerceAtLeast(minSec)
        val delaySec = Random.nextInt(minSec, maxSec + 1)

        _status.value = _status.value.copy(
            step = AutomationStep.COOLDOWN_DELAY,
            delayCountdownSeconds = delaySec,
            logMessage = "Dispatched to ${currentIndex + 1}/$total. Safe anti-ban cooldown: ${delaySec}s..."
        )

        delayJob?.cancel()
        delayJob = scope.launch {
            for (remaining in delaySec downTo 1) {
                _status.value = _status.value.copy(delayCountdownSeconds = remaining)
                delay(1000)
            }
            dispatchToGroup(context, nextIndex)
        }
    }

    private fun dispatchToGroup(context: Context, index: Int) {
        val uri = selectedMediaUri.value ?: return
        val currentGroup = activeQueue[index].name

        _status.value = AutomationStatus(
            step = AutomationStep.INITIATED,
            currentGroupName = currentGroup,
            currentIndex = index,
            totalCount = activeQueue.size,
            logMessage = "Dispatching to \"$currentGroup\" (${index + 1}/${activeQueue.size})"
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mediaMimeType.value
            putExtra(Intent.EXTRA_STREAM, uri)

            val caption = captionText.value.trim()
            if (caption.isNotEmpty()) {
                putExtra(Intent.EXTRA_TEXT, caption)
            }

            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            context.startActivity(shareIntent)
        } catch (e: Exception) {
            try {
                shareIntent.setPackage("com.whatsapp.w4b")
                context.startActivity(shareIntent)
            } catch (ex: Exception) {
                _status.value = _status.value.copy(
                    step = AutomationStep.ERROR,
                    logMessage = "WhatsApp application not detected on device."
                )
            }
        }
    }

    fun loadPersistence(context: Context) {
        try {
            val prefs = context.getSharedPreferences("wa_poster_data", Context.MODE_PRIVATE)

            userProfileName.value = prefs.getString("user_profile_name", "Vishal Kumar (Active)") ?: "Vishal Kumar (Active)"
            isDarkMode.value = prefs.getBoolean("is_dark_mode", false) // Default Light

            val groupsJson = prefs.getString("groups_json", null)
            if (groupsJson != null) {
                val array = JSONArray(groupsJson)
                val list = mutableListOf<TargetGroupItem>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(TargetGroupItem(obj.getString("name"), obj.optString("category", "General")))
                }
                targetGroups.value = list
            }

            val dispatched = prefs.getInt("stats_dispatched", 0)
            val success = prefs.getInt("stats_success", 0)
            val savedMin = prefs.getFloat("stats_saved_min", 0f).toDouble()
            stats.value = BroadcastStats(dispatched, success, savedMin)
        } catch (_: Exception) {}
    }

    fun savePersistence(context: Context) {
        try {
            val prefs = context.getSharedPreferences("wa_poster_data", Context.MODE_PRIVATE)
            val editor = prefs.edit()

            editor.putString("user_profile_name", userProfileName.value)
            editor.putBoolean("is_dark_mode", isDarkMode.value)

            val array = JSONArray()
            targetGroups.value.forEach {
                val obj = JSONObject()
                obj.put("name", it.name)
                obj.put("category", it.category)
                array.put(obj)
            }
            editor.putString("groups_json", array.toString())
            editor.putInt("stats_dispatched", stats.value.totalDispatched)
            editor.putInt("stats_success", stats.value.totalSuccess)
            editor.putFloat("stats_saved_min", stats.value.minutesSaved.toFloat())
            editor.apply()
        } catch (_: Exception) {}
    }
}
