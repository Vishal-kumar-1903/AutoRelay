package com.autoposter.whatsapp.service

import java.util.Locale

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.autoposter.whatsapp.data.*

class WhatsAppAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastActionTime = 0L
    private val actionDebounceMs = 500L
    private var hasSearchedCurrentTarget = false
    private var hasSelectedCurrentTarget = false
    private var hasClickedForwardFab = false
    private var stepStartTime = 0L
    private var hasFinishedCurrentTarget = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val rootNode = rootInActiveWindow ?: return
        val eventPkg = event?.packageName?.toString() ?: ""
        val rootPkg = rootNode.packageName?.toString() ?: ""

        if (eventPkg.contains("whatsapp") || rootPkg.contains("whatsapp")) {
            // Check if user requested WhatsApp Group & Chat Sync
            if (AutomationManager.isSyncingFromWhatsApp.value) {
                handleWhatsAppSync(rootNode)
                return
            }

            if (!AutomationManager.isRunning) return
            val currentTarget = AutomationManager.getCurrentTarget() ?: return

            val now = System.currentTimeMillis()
            if (now - lastActionTime < actionDebounceMs) return

            processWhatsAppUi(rootNode, currentTarget)
        }
    }

    data class ScannedItem(
        val name: String,
        val isGroup: Boolean
    )

    private var syncStartTime = 0L
    private var scrollCount = 0
    private var consecutiveNoNewItems = 0
    private val scannedItemsMap = mutableMapOf<String, ScannedItem>()

    private fun handleWhatsAppSync(rootNode: AccessibilityNodeInfo) {
        if (syncStartTime == 0L) {
            syncStartTime = System.currentTimeMillis()
            scannedItemsMap.clear()
            scrollCount = 0
            consecutiveNoNewItems = 0
            lastActionTime = 0L
        }

        val now = System.currentTimeMillis()
        if (now - lastActionTime < 600L) return

        val initialCount = scannedItemsMap.size
        val newItems = extractChatItemsFromScreen(rootNode)
        newItems.forEach { item ->
            val key = item.name.lowercase(Locale.getDefault())
            if (!scannedItemsMap.containsKey(key)) {
                scannedItemsMap[key] = item
            }
        }

        val addedCount = scannedItemsMap.size - initialCount
        if (addedCount > 0) {
            consecutiveNoNewItems = 0
        } else {
            consecutiveNoNewItems++
        }

        // Show live scan feedback Toast every 4 scrolls
        if (scrollCount > 0 && scrollCount % 4 == 0) {
            mainHandler.post {
                Toast.makeText(applicationContext, "Scanning WhatsApp... ${scannedItemsMap.size} chats found", Toast.LENGTH_SHORT).show()
            }
        }

        // Deep continuous scrolling: up to 25 scrolls or until 3 consecutive scrolls produce no new chats
        val maxScrolls = 25
        if (scrollCount < maxScrolls && consecutiveNoNewItems < 3) {
            scrollCount++
            lastActionTime = System.currentTimeMillis()

            val gestureDone = performScrollDownGesture()
            if (!gestureDone) {
                val scrollable = findScrollableNode(rootNode)
                scrollable?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            }
            return
        }

        // All chats scanned completely!
        completeWhatsAppSync()
    }

    private fun performScrollDownGesture(): Boolean {
        return try {
            val dm = resources.displayMetrics
            val width = dm.widthPixels.toFloat()
            val height = dm.heightPixels.toFloat()

            val startX = width * 0.5f
            val startY = height * 0.75f
            val endX = width * 0.5f
            val endY = height * 0.20f

            val swipePath = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }

            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(swipePath, 0, 250))
                .build()

            dispatchGesture(gesture, null, null)
        } catch (_: Exception) {
            false
        }
    }

    private fun extractChatItemsFromScreen(root: AccessibilityNodeInfo): List<ScannedItem> {
        val list = mutableListOf<ScannedItem>()

        val knownIds = listOf(
            "conversations_row_contact_name",
            "contactpicker_row_name",
            "conversation_contact_name",
            "chat_title"
        )

        for (idName in knownIds) {
            val nodes1 = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/$idName")
            val nodes2 = root.findAccessibilityNodeInfosByViewId("com.whatsapp.w4b:id/$idName")
            val allNodes = nodes1 + nodes2

            for (n in allNodes) {
                val text = n.text?.toString()?.trim() ?: continue
                if (text.length < 2 || text.length > 60) continue

                val ignore = listOf(
                    "Chats", "Updates", "Calls", "Communities", "Search", "Settings",
                    "Yesterday", "Today", "Archived", "New group", "New broadcast",
                    "Starred messages", "Linked devices", "Status", "Channels"
                )
                if (ignore.any { it.equals(text, ignoreCase = true) }) continue
                if (text.matches(Regex("\\d{1,2}:\\d{2}\\s*(am|pm)?", RegexOption.IGNORE_CASE))) continue

                // Check parent row to detect Group vs Individual vs Channel
                var isGroup = AutomationManager.isGroupByName(text)
                var isChannel = isChannelName(text)

                val rowNode = n.parent
                if (rowNode != null) {
                    val rowViewId = rowNode.viewIdResourceName ?: ""
                    if (rowViewId.contains("newsletter", ignoreCase = true) || rowViewId.contains("channel", ignoreCase = true)) {
                        isChannel = true
                    }

                    for (i in 0 until rowNode.childCount) {
                        val child = rowNode.getChild(i) ?: continue
                        val cId = child.viewIdResourceName ?: ""
                        val cDesc = child.contentDescription?.toString() ?: ""
                        val cText = child.text?.toString() ?: ""

                        if (cId.contains("newsletter", ignoreCase = true) || cId.contains("channel", ignoreCase = true) ||
                            cDesc.contains("newsletter", ignoreCase = true) || cDesc.contains("channel", ignoreCase = true) ||
                            cText.contains("followers", ignoreCase = true) || cText.contains("Follow", ignoreCase = true)) {
                            isChannel = true
                            break
                        }

                        if (cId.contains("msg_from_tv") || cDesc.contains("Group", ignoreCase = true)) {
                            isGroup = true
                        }
                    }
                }

                // Exclude channels from broadcast list!
                if (isChannel) continue

                if (list.none { it.name.equals(text, ignoreCase = true) }) {
                    list.add(ScannedItem(text, isGroup))
                }
            }
        }

        return list
    }

    private fun isChannelName(name: String): Boolean {
        val lower = name.lowercase(Locale.getDefault())
        val knownChannels = listOf(
            "whatsapp", "abp news", "aaj tak", "bbc news", "ndtv", "times of india",
            "dainik bhaskar", "news18", "india today", "icc", "bcci", "real madrid",
            "netflix", "star sports", "facttechz", "tech burner"
        )
        return knownChannels.any { lower == it || lower.startsWith("$it ") }
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findScrollableNode(child)
            if (found != null) return found
        }
        return null
    }

    private fun completeWhatsAppSync() {
        val category = AutomationManager.syncTargetCategory.value
        val currentRecipients = AutomationManager.targetRecipients.value.toMutableList()
        var added = 0

        scannedItemsMap.values.forEach { item ->
            val clean = item.name.trim()
            if (clean.length > 1 && currentRecipients.none { it.name.equals(clean, ignoreCase = true) }) {
                val assignedCat = if (category == "All" || category.startsWith("⚡") || category.startsWith("Auto")) {
                    AutomationManager.detectCategory(clean)
                } else {
                    category
                }
                val type = if (item.isGroup) RecipientType.GROUP else RecipientType.INDIVIDUAL
                currentRecipients.add(TargetRecipient(clean, assignedCat, type, isSelected = true))
                added++
            }
        }

        // Clean up and correct any existing misclassified items
        val cleaned = currentRecipients.map {
            var updated = it
            if (it.category == "General") {
                updated = updated.copy(category = AutomationManager.detectCategory(it.name))
            }
            if (it.type == RecipientType.GROUP && !AutomationManager.isGroupByName(it.name)) {
                updated = updated.copy(type = RecipientType.INDIVIDUAL)
            }
            updated
        }

        AutomationManager.targetRecipients.value = cleaned
        AutomationManager.savePersistence(applicationContext)
        AutomationManager.isSyncingFromWhatsApp.value = false
        val total = scannedItemsMap.size
        scannedItemsMap.clear()
        scrollCount = 0
        consecutiveNoNewItems = 0
        syncStartTime = 0L

        mainHandler.post {
            Toast.makeText(applicationContext, "Scan Complete! Loaded $total WhatsApp chats & groups ($added added).", Toast.LENGTH_LONG).show()
        }

        // Return to AutoRelay
        mainHandler.postDelayed({
            performGlobalAction(GLOBAL_ACTION_BACK)
        }, 500)
    }

    private fun processWhatsAppUi(rootNode: AccessibilityNodeInfo, targetGroup: String) {
        val currentStep = AutomationManager.status.value.step

        // Reset tracking flags on brand new dispatch initiation
        if (currentStep == AutomationStep.INITIATED) {
            hasSearchedCurrentTarget = false
            hasSelectedCurrentTarget = false
            hasClickedForwardFab = false
            hasFinishedCurrentTarget = false
            stepStartTime = System.currentTimeMillis()
            AutomationManager.updateStep(AutomationStep.SEARCHING_GROUP, "Searching for \"$targetGroup\"...")
        }

        if (hasFinishedCurrentTarget) return

        val isMediaAttached = AutomationManager.selectedMediaUri.value != null

        // -------------------------------------------------------------
        // Phase 1: Search for Target Group / Friend
        // -------------------------------------------------------------
        if (!hasSearchedCurrentTarget) {
            val searchInput = findSearchEditText(rootNode)
            if (searchInput != null) {
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, targetGroup)
                }
                searchInput.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                hasSearchedCurrentTarget = true
                lastActionTime = System.currentTimeMillis()
                AutomationManager.updateStep(AutomationStep.SELECTING_GROUP, "Filtering contacts for \"$targetGroup\"...")
                return
            } else {
                val searchIcon = findNodeByContentDescription(rootNode, "Search")
                    ?: findNodeByShortId(rootNode, "menuitem_search")
                    ?: findNodeByShortId(rootNode, "menu_search")
                if (searchIcon != null) {
                    clickNodeWithFallback(searchIcon)
                    lastActionTime = System.currentTimeMillis()
                    return
                }
            }
        }

        // -------------------------------------------------------------
        // Phase 2: Select the matched group / friend row
        // -------------------------------------------------------------
        if (hasSearchedCurrentTarget && !hasSelectedCurrentTarget) {
            val matchedNode = findNodeContainingText(rootNode, targetGroup)
            if (matchedNode != null) {
                clickNodeWithFallback(matchedNode)
                hasSelectedCurrentTarget = true
                lastActionTime = System.currentTimeMillis()
                AutomationManager.updateStep(AutomationStep.CONFIRMING_SEND, "Selected \"$targetGroup\". Ready to dispatch...")
                return
            }
        }

        // -------------------------------------------------------------
        // Phase 3 & 4: Confirmation / Forward FAB / Media Preview Send
        // -------------------------------------------------------------
        if (hasSelectedCurrentTarget) {

            // Check A: Instant confirmation dialog (e.g. "Send document to [Target]?" -> Click "Send")
            val dialogConfirm = findDialogSendButton(rootNode)
            if (dialogConfirm != null) {
                clickNodeWithFallback(dialogConfirm)
                lastActionTime = System.currentTimeMillis()
                completeDispatchSuccess()
                return
            }

            // Check B: Detect if we are currently on Media Preview screen
            val isMediaPreviewScreen = isMediaPreviewScreen(rootNode)

            if (isMediaPreviewScreen) {
                // We are on Media Preview Screen!
                // Fill caption if present
                val caption = AutomationManager.captionText.value.trim()
                if (caption.isNotEmpty()) {
                    val captionField = findNodeByShortId(rootNode, "caption")
                        ?: findNodeByShortId(rootNode, "entry")
                        ?: findSearchEditText(rootNode)
                    if (captionField != null && (captionField.text == null || captionField.text.isEmpty())) {
                        val args = Bundle().apply {
                            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, caption)
                        }
                        captionField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                    }
                }

                // Click final green send button on Media Preview Screen
                val mediaSendBtn = findActionFabOrSendButton(rootNode)
                if (mediaSendBtn != null) {
                    clickNodeWithFallback(mediaSendBtn)
                    lastActionTime = System.currentTimeMillis()
                    completeDispatchSuccess()
                    return
                }
            } else {
                // We are on ContactPicker screen (or WhatsApp forward screen)
                val forwardFab = findActionFabOrSendButton(rootNode)

                if (forwardFab != null) {
                    clickNodeWithFallback(forwardFab)
                    lastActionTime = System.currentTimeMillis()
                    hasClickedForwardFab = true

                    if (!isMediaAttached) {
                        // Plain Text: Clicking Next/Send on ContactPicker sends message IMMEDIATELY!
                        completeDispatchSuccess()
                        return
                    } else {
                        // Media Attached: Moving to Media Preview screen
                        AutomationManager.updateStep(AutomationStep.FINAL_SEND, "Media preview loading...")
                        return
                    }
                }
            }

            // Safety timeout: If stalled on this target for > 7 seconds, force-click any bottom-right button
            if (System.currentTimeMillis() - stepStartTime > 7000L) {
                val emergencyBtn = findBottomRightClickable(rootNode)
                if (emergencyBtn != null) {
                    clickNodeWithFallback(emergencyBtn)
                    lastActionTime = System.currentTimeMillis()
                    if (!isMediaAttached) {
                        completeDispatchSuccess()
                    }
                }
            }
        }
    }

    private fun completeDispatchSuccess() {
        if (hasFinishedCurrentTarget) return
        hasFinishedCurrentTarget = true

        mainHandler.postDelayed({
            performGlobalAction(GLOBAL_ACTION_BACK)
            AutomationManager.onGroupDispatched(applicationContext)
        }, 1200)
    }

    private fun isMediaPreviewScreen(root: AccessibilityNodeInfo): Boolean {
        // Media preview screen typically contains caption box or does NOT contain contact search box
        val hasCaptionBox = findNodeByShortId(root, "caption") != null
        val hasContactSearch = findNodeByShortId(root, "search_src_text") != null
        return hasCaptionBox || (!hasContactSearch && hasClickedForwardFab)
    }

    private fun findActionFabOrSendButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // 1. Check known WhatsApp / WA Business Resource IDs (ends with :id/...)
        val knownIds = listOf(
            "next",             // WhatsApp ContactPicker Next button (Primary!)
            "send",             // Send button on Chat / Media Preview / ContactPicker
            "fab",              // Standard Floating Action Button
            "forward_button",   // Forward picker FAB
            "next_btn",         // Next button variant
            "action_next",      // Action next
            "action_send",      // Action send
            "bottom_fab",       // Bottom FAB
            "send_btn",         // Send button variant
            "ok"                // Confirmation
        )
        for (idName in knownIds) {
            val node = findNodeByShortId(root, idName)
            if (node != null && node.isVisibleToUser) {
                return node
            }
        }

        // 2. Multilingual Content Descriptions (English, Hindi, etc.)
        val descriptions = listOf("next", "forward", "send", "done", "send message", "आगे", "भेजें")
        for (desc in descriptions) {
            val node = findNodeByContentDescription(root, desc)
            if (node != null && node.isVisibleToUser) {
                return node
            }
        }

        // 3. Fallback: Any clickable FAB/ImageButton located in the bottom-right corner
        return findBottomRightClickable(root)
    }

    private fun findDialogSendButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Matches "Send" button on system / WhatsApp confirmation popup
        val btn1 = root.findAccessibilityNodeInfosByViewId("android:id/button1").firstOrNull()
        if (btn1 != null && btn1.isVisibleToUser) return btn1

        val sendNode = findNodeContainingText(root, "Send") ?: findNodeContainingText(root, "SEND")
        if (sendNode != null && sendNode.isVisibleToUser && (sendNode.isClickable || sendNode.parent?.isClickable == true)) {
            val text = sendNode.text?.toString() ?: ""
            if (text.equals("Send", ignoreCase = true)) {
                return sendNode
            }
        }
        return null
    }

    private fun findBottomRightClickable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val screenBounds = Rect()
        root.getBoundsInScreen(screenBounds)
        val screenW = screenBounds.width()
        val screenH = screenBounds.height()

        var candidate: AccessibilityNodeInfo? = null
        searchHierarchy(root) { node ->
            if (node.isVisibleToUser && (node.isClickable || node.parent?.isClickable == true)) {
                val rect = Rect()
                node.getBoundsInScreen(rect)
                // Bottom-right quadrant (X >= 60% of width, Y >= 65% of height)
                if (rect.right >= screenW * 0.60f && rect.bottom >= screenH * 0.65f &&
                    rect.width() in 45..400 && rect.height() in 45..400) {
                    val cls = node.className?.toString() ?: ""
                    if (!cls.contains("Key") && !cls.contains("Keyboard")) {
                        candidate = node
                        return@searchHierarchy true
                    }
                }
            }
            false
        }
        return candidate
    }

    private fun findSearchEditText(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val byId = findNodeByShortId(node, "search_src_text")
        if (byId != null && byId.isEditable) return byId

        return searchHierarchy(node) { it.isEditable && (it.className?.contains("EditText") == true) }
    }

    private fun findNodeByShortId(node: AccessibilityNodeInfo, shortId: String): AccessibilityNodeInfo? {
        val list1 = node.findAccessibilityNodeInfosByViewId("com.whatsapp:id/$shortId")
        if (list1.isNotEmpty()) return list1[0]

        val list2 = node.findAccessibilityNodeInfosByViewId("com.whatsapp.w4b:id/$shortId")
        if (list2.isNotEmpty()) return list2[0]

        return searchHierarchy(node) {
            it.viewIdResourceName?.endsWith(":id/$shortId") == true
        }
    }

    private fun findNodeByContentDescription(node: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        return searchHierarchy(node) {
            it.contentDescription?.toString()?.contains(desc, ignoreCase = true) == true
        }
    }

    private fun findNodeContainingText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val clean = text.trim()
        val list = node.findAccessibilityNodeInfosByText(clean)
        val exactMatch = list.firstOrNull {
            it.text?.toString()?.contains(clean, ignoreCase = true) == true
        }
        if (exactMatch != null) return exactMatch

        return searchHierarchy(node) {
            val t = it.text?.toString() ?: ""
            val d = it.contentDescription?.toString() ?: ""
            t.contains(clean, ignoreCase = true) || d.contains(clean, ignoreCase = true)
        }
    }

    private fun clickNodeWithFallback(target: AccessibilityNodeInfo): Boolean {
        // Strategy 1: Standard accessibility action click on target or clickable parent
        var current: AccessibilityNodeInfo? = target
        while (current != null) {
            if (current.isClickable) {
                val ok = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (ok) return true
            }
            current = current.parent
        }

        val direct = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        if (direct) return true

        // Strategy 2: Physical hardware gesture tap at node's screen center coordinates
        val bounds = Rect()
        target.getBoundsInScreen(bounds)
        if (bounds.width() > 0 && bounds.height() > 0) {
            val clickPath = Path().apply {
                moveTo(bounds.centerX().toFloat(), bounds.centerY().toFloat())
            }
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(clickPath, 0, 80))
                .build()
            return dispatchGesture(gesture, null, null)
        }
        return false
    }

    private fun searchHierarchy(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (predicate(node)) return node

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = searchHierarchy(child, predicate)
            if (result != null) return result
        }
        return null
    }

    override fun onInterrupt() {
        AutomationManager.stopBroadcast()
    }
}
