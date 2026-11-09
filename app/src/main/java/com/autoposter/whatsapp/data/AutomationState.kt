package com.autoposter.whatsapp.data

enum class AutomationStep {
    IDLE,
    INITIATED,
    SEARCHING_GROUP,
    SELECTING_GROUP,
    CONFIRMING_SEND,
    FINAL_SEND,
    COOLDOWN_DELAY,
    COMPLETED,
    ERROR
}

enum class RecipientType {
    GROUP,       // Group Chat
    INDIVIDUAL   // 1-to-1 Private Friend Chat (Sent separately)
}

data class TargetRecipient(
    val name: String,
    val category: String = "General",
    val type: RecipientType = RecipientType.GROUP,
    val isSelected: Boolean = true
)

data class BroadcastRecord(
    val id: String = System.currentTimeMillis().toString(),
    val groupName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val mediaType: String,
    val captionPreview: String = "",
    val isSuccess: Boolean = true
)

data class BroadcastStats(
    val totalDispatched: Int = 0,
    val totalSuccess: Int = 0,
    val minutesSaved: Double = 0.0
)

data class AutomationStatus(
    val step: AutomationStep = AutomationStep.IDLE,
    val currentGroupName: String = "",
    val currentIndex: Int = 0,
    val totalCount: Int = 0,
    val delayCountdownSeconds: Int = 0,
    val logMessage: String = "Ready"
)
