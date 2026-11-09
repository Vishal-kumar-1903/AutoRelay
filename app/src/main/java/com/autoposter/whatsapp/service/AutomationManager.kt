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

    // User Profile & Theme state
    val userProfileName = MutableStateFlow("Vishal Kumar (Active)")
    val isDarkMode = MutableStateFlow(false) // Default Light

    // Categories & Recipients
    val categories = MutableStateFlow(listOf("Business", "Study", "Friends", "Clients"))
    val activeCategoryFilter = MutableStateFlow("All")
    val targetRecipients = MutableStateFlow<List<TargetRecipient>>(emptyList())

    // Media & File & Caption Content
    val selectedMediaUri = MutableStateFlow<Uri?>(null)
    val mediaMimeType = MutableStateFlow("image/*")
    val mediaFileName = MutableStateFlow("")
    val captionText = MutableStateFlow("")

    // Anti-ban timing
    val minDelaySeconds = MutableStateFlow(6)
    val maxDelaySeconds = MutableStateFlow(12)

    // Execution state
    private val _status = MutableStateFlow(AutomationStatus())
    val status: StateFlow<AutomationStatus> = _status.asStateFlow()

    // Analytics
    val broadcastHistory = MutableStateFlow<List<BroadcastRecord>>(emptyList())
    val stats = MutableStateFlow(BroadcastStats())

    // WhatsApp Group & Chat Sync State
    val isSyncingFromWhatsApp = MutableStateFlow(false)
    val syncTargetCategory = MutableStateFlow("All")

    private var activeQueue: List<TargetRecipient> = emptyList()

    val isRunning: Boolean
        get() = _status.value.step != AutomationStep.IDLE &&
                _status.value.step != AutomationStep.COMPLETED &&
                _status.value.step != AutomationStep.ERROR

    fun startWhatsAppSync(context: Context, targetCategory: String = "All", useBusiness: Boolean = false) {
        syncTargetCategory.value = targetCategory
        isSyncingFromWhatsApp.value = true

        val pkg = if (useBusiness) "com.whatsapp.w4b" else "com.whatsapp"
        var intent = context.packageManager.getLaunchIntentForPackage(pkg)

        if (intent == null) {
            intent = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
                ?: context.packageManager.getLaunchIntentForPackage("com.whatsapp.w4b")
        }

        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            val appTitle = if (pkg.contains("w4b")) "WhatsApp Business" else "WhatsApp"
            Toast.makeText(context, "Scanning $appTitle groups and chats...", Toast.LENGTH_SHORT).show()
        } else {
            isSyncingFromWhatsApp.value = false
            Toast.makeText(context, "Neither WhatsApp nor WhatsApp Business was found.", Toast.LENGTH_LONG).show()
        }
    }

    fun openDirectWhatsAppPicker(context: Context) {
        val uri = selectedMediaUri.value
        val caption = captionText.value.trim()

        if (uri == null && caption.isEmpty()) {
            Toast.makeText(context, "Please attach a photo/file or write a message first!", Toast.LENGTH_SHORT).show()
            return
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            if (uri != null) {
                type = mediaMimeType.value
                putExtra(Intent.EXTRA_STREAM, uri)
                if (caption.isNotEmpty()) {
                    putExtra(Intent.EXTRA_TEXT, caption)
                }
            } else {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, caption)
            }

            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            context.startActivity(shareIntent)
            val record = BroadcastRecord(
                groupName = "WhatsApp Multi-Select",
                mediaType = if (uri != null) mediaMimeType.value else "text/plain",
                captionPreview = caption.take(40),
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
                Toast.makeText(context, "WhatsApp is not installed on this device.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun addRecipient(name: String, category: String = "General", type: RecipientType = RecipientType.GROUP, context: Context? = null) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty() && targetRecipients.value.none { it.name.equals(trimmed, ignoreCase = true) }) {
            targetRecipients.value = targetRecipients.value + TargetRecipient(trimmed, category, type, isSelected = true)
            if (context != null) savePersistence(context)
        }
    }

    fun addMultipleRecipients(rawNames: String, category: String = "General", type: RecipientType = RecipientType.GROUP, context: Context? = null): Int {
        val names = rawNames.split(",", "\n", ";").map { it.trim() }.filter { it.isNotEmpty() }
        val current = targetRecipients.value.toMutableList()
        var added = 0
        names.forEach { n ->
            if (current.none { it.name.equals(n, ignoreCase = true) }) {
                current.add(TargetRecipient(n, category, type, isSelected = true))
                added++
            }
        }
        if (added > 0) {
            targetRecipients.value = current
            if (context != null) savePersistence(context)
        }
        return added
    }

    fun toggleSelection(name: String, context: Context? = null) {
        targetRecipients.value = targetRecipients.value.map {
            if (it.name.equals(name, ignoreCase = true)) it.copy(isSelected = !it.isSelected) else it
        }
        if (context != null) savePersistence(context)
    }

    fun selectAllInActiveFilter(select: Boolean, context: Context? = null) {
        val filter = activeCategoryFilter.value
        targetRecipients.value = targetRecipients.value.map {
            if (filter == "All" || it.category == filter) it.copy(isSelected = select) else it
        }
        if (context != null) savePersistence(context)
    }

    fun removeRecipient(name: String, context: Context? = null) {
        targetRecipients.value = targetRecipients.value.filter { it.name != name }
        if (context != null) savePersistence(context)
    }

    fun clearActiveCategoryRecipients(context: Context? = null) {
        val filter = activeCategoryFilter.value
        if (filter == "All") {
            targetRecipients.value = emptyList()
        } else {
            targetRecipients.value = targetRecipients.value.filter { it.category != filter }
        }
        if (context != null) savePersistence(context)
    }

    fun addCategory(categoryName: String, context: Context? = null) {
        val trimmed = categoryName.trim()
        if (trimmed.isNotEmpty() && !categories.value.contains(trimmed)) {
            categories.value = categories.value + trimmed
            activeCategoryFilter.value = trimmed
            if (context != null) savePersistence(context)
        }
    }

    fun deleteCategory(categoryName: String, context: Context? = null) {
        categories.value = categories.value.filter { it != categoryName }
        targetRecipients.value = targetRecipients.value.filter { it.category != categoryName }
        activeCategoryFilter.value = "All"
        if (context != null) savePersistence(context)
    }

    fun moveSelectedRecipients(newCategory: String, context: Context? = null): Int {
        var count = 0
        targetRecipients.value = targetRecipients.value.map {
            if (it.isSelected) {
                count++
                it.copy(category = newCategory)
            } else it
        }
        if (context != null) savePersistence(context)
        return count
    }

    fun moveRecipientToCategory(name: String, newCategory: String, context: Context? = null) {
        targetRecipients.value = targetRecipients.value.map {
            if (it.name.equals(name, ignoreCase = true)) it.copy(category = newCategory) else it
        }
        if (context != null) savePersistence(context)
    }

    fun toggleRecipientType(name: String, context: Context? = null) {
        targetRecipients.value = targetRecipients.value.map {
            if (it.name.equals(name, ignoreCase = true)) {
                val newType = if (it.type == RecipientType.GROUP) RecipientType.INDIVIDUAL else RecipientType.GROUP
                it.copy(type = newType)
            } else it
        }
        if (context != null) savePersistence(context)
    }

    fun getFilteredRecipients(): List<TargetRecipient> {
        val filter = activeCategoryFilter.value
        return if (filter == "All") {
            targetRecipients.value
        } else {
            targetRecipients.value.filter { it.category == filter }
        }
    }

    fun getActiveQueue(): List<TargetRecipient> {
        val list = getFilteredRecipients()
        val selected = list.filter { it.isSelected }
        return if (selected.isNotEmpty()) selected else list
    }

    fun startBroadcast(context: Context) {
        val queue = getActiveQueue()
        val uri = selectedMediaUri.value
        val caption = captionText.value.trim()

        if (queue.isEmpty()) {
            Toast.makeText(context, "No recipients in this tab! Tap + to add groups or 1-to-1 friends.", Toast.LENGTH_SHORT).show()
            return
        }

        if (uri == null && caption.isEmpty()) {
            Toast.makeText(context, "Please attach a photo/file or write a message!", Toast.LENGTH_SHORT).show()
            return
        }

        activeQueue = queue
        dispatchToRecipient(context, 0)
    }

    fun stopBroadcast() {
        delayJob?.cancel()
        _status.value = AutomationStatus(
            step = AutomationStep.IDLE,
            logMessage = "Broadcast halted."
        )
    }

    fun getCurrentTarget(): String? {
        val index = _status.value.currentIndex
        return if (index in activeQueue.indices) activeQueue[index].name else null
    }

    fun getCurrentRecipientType(): RecipientType {
        val index = _status.value.currentIndex
        return if (index in activeQueue.indices) activeQueue[index].type else RecipientType.GROUP
    }

    fun updateStep(step: AutomationStep, message: String) {
        _status.value = _status.value.copy(step = step, logMessage = message)
    }

    fun onGroupDispatched(context: Context) {
        val currentIndex = _status.value.currentIndex
        val targetName = getCurrentTarget() ?: "Chat"
        val recipientType = getCurrentRecipientType()

        val record = BroadcastRecord(
            groupName = (if (recipientType == RecipientType.INDIVIDUAL) "👤 $targetName (Private)" else "👥 $targetName"),
            mediaType = if (selectedMediaUri.value != null) mediaMimeType.value else "text/plain",
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
                logMessage = "Completed! Reached all $total recipients."
            )
            return
        }

        val minSec = minDelaySeconds.value
        val maxSec = maxDelaySeconds.value.coerceAtLeast(minSec)
        val delaySec = Random.nextInt(minSec, maxSec + 1)

        _status.value = _status.value.copy(
            step = AutomationStep.COOLDOWN_DELAY,
            delayCountdownSeconds = delaySec,
            logMessage = "Delivered to ${currentIndex + 1}/$total. Anti-ban interval: ${delaySec}s..."
        )

        delayJob?.cancel()
        delayJob = scope.launch {
            for (remaining in delaySec downTo 1) {
                _status.value = _status.value.copy(delayCountdownSeconds = remaining)
                delay(1000)
            }
            dispatchToRecipient(context, nextIndex)
        }
    }

    private fun dispatchToRecipient(context: Context, index: Int) {
        val uri = selectedMediaUri.value
        val recipient = activeQueue[index]
        val isPrivate = recipient.type == RecipientType.INDIVIDUAL
        val caption = captionText.value.trim()

        _status.value = AutomationStatus(
            step = AutomationStep.INITIATED,
            currentGroupName = recipient.name,
            currentIndex = index,
            totalCount = activeQueue.size,
            logMessage = if (isPrivate) "Sending 1-on-1 private chat to \"${recipient.name}\" (${index + 1}/${activeQueue.size})"
                         else "Sending to group \"${recipient.name}\" (${index + 1}/${activeQueue.size})"
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            if (uri != null) {
                type = mediaMimeType.value
                putExtra(Intent.EXTRA_STREAM, uri)
                if (caption.isNotEmpty()) {
                    putExtra(Intent.EXTRA_TEXT, caption)
                }
            } else {
                type = "text/plain"
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
                    logMessage = "WhatsApp application not found."
                )
            }
        }
    }

    fun loadPersistence(context: Context) {
        try {
            val prefs = context.getSharedPreferences("wa_poster_data", Context.MODE_PRIVATE)

            userProfileName.value = prefs.getString("user_profile_name", "Vishal Kumar (Active)") ?: "Vishal Kumar (Active)"
            isDarkMode.value = prefs.getBoolean("is_dark_mode", false)

            val categoriesJson = prefs.getString("categories_json", null)
            if (categoriesJson != null) {
                val catArr = JSONArray(categoriesJson)
                val catList = mutableListOf<String>()
                for (i in 0 until catArr.length()) {
                    catList.add(catArr.getString(i))
                }
                if (catList.isNotEmpty()) {
                    categories.value = catList
                }
            }

            val recipientsJson = prefs.getString("recipients_json", null)
            if (recipientsJson != null) {
                val array = JSONArray(recipientsJson)
                val list = mutableListOf<TargetRecipient>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val typeStr = obj.optString("type", "GROUP")
                    val type = if (typeStr == "INDIVIDUAL") RecipientType.INDIVIDUAL else RecipientType.GROUP
                    val isSel = obj.optBoolean("selected", true)
                    list.add(TargetRecipient(obj.getString("name"), obj.optString("category", "General"), type, isSel))
                }
                targetRecipients.value = list
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

            val catArray = JSONArray()
            categories.value.forEach { catArray.put(it) }
            editor.putString("categories_json", catArray.toString())

            val array = JSONArray()
            targetRecipients.value.forEach {
                val obj = JSONObject()
                obj.put("name", it.name)
                obj.put("category", it.category)
                obj.put("type", it.type.name)
                obj.put("selected", it.isSelected)
                array.put(obj)
            }
            editor.putString("recipients_json", array.toString())
            editor.putInt("stats_dispatched", stats.value.totalDispatched)
            editor.putInt("stats_success", stats.value.totalSuccess)
            editor.putFloat("stats_saved_min", stats.value.minutesSaved.toFloat())
            editor.apply()
        } catch (_: Exception) {}
    }
}
