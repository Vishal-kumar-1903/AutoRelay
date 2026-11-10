package com.autoposter.whatsapp.service

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

    private val scannedNames = mutableSetOf<String>()
    private var scrollCount = 0
    private var syncStartTime = 0L

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

    private fun handleWhatsAppSync(rootNode: AccessibilityNodeInfo) {
        if (syncStartTime == 0L) {
            syncStartTime = System.currentTimeMillis()
            scannedNames.clear()
            scrollCount = 0
        }

        val now = System.currentTimeMillis()
        if (now - lastActionTime < 700L) return

        val newNames = extractChatNamesFromScreen(rootNode)
        scannedNames.addAll(newNames)

        // Try scrolling down to grab more chats (up to 3 pages)
        val scrollable = findScrollableNode(rootNode)
        if (scrollCount < 3 && scrollable != null) {
            scrollCount++
            scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            lastActionTime = System.currentTimeMillis()
            return
        }

        completeWhatsAppSync()
    }

    private fun completeWhatsAppSync() {
        val category = AutomationManager.syncTargetCategory.value
        val currentRecipients = AutomationManager.targetRecipients.value.toMutableList()
        var added = 0

        scannedNames.forEach { name ->
            val clean = name.trim()
            if (clean.length > 1 && currentRecipients.none { it.name.equals(clean, ignoreCase = true) }) {
                val isGroup = isLikelyGroupName(clean)
                // When category is "All" or "Auto", intelligently sort into Study, Business, Friends, or Family!
                val assignedCat = if (category == "All" || category.startsWith("⚡") || category.startsWith("Auto")) {
                    AutomationManager.detectCategory(clean)
                } else {
                    category
                }
                currentRecipients.add(TargetRecipient(clean, assignedCat, if (isGroup) RecipientType.GROUP else RecipientType.INDIVIDUAL, isSelected = true))
                added++
            }
        }

        // Upgrade any legacy "General" items
        val cleaned = currentRecipients.map {
            if (it.category == "General") it.copy(category = AutomationManager.detectCategory(it.name)) else it
        }

        AutomationManager.targetRecipients.value = cleaned
        AutomationManager.savePersistence(applicationContext)
        AutomationManager.isSyncingFromWhatsApp.value = false
        val total = scannedNames.size
        scannedNames.clear()
        scrollCount = 0
        syncStartTime = 0L

        mainHandler.post {
            Toast.makeText(applicationContext, "Imported $added WhatsApp chats & groups! Auto-sorted into Study, Business, Family & Friends.", Toast.LENGTH_LONG).show()
        }

        // Return to AutoRelay
        mainHandler.postDelayed({
            performGlobalAction(GLOBAL_ACTION_BACK)
        }, 500)
    }

    private fun isLikelyGroupName(name: String): Boolean {
        val keywords = listOf("group", "grp", "team", "family", "friends", "class", "batch", "club", "office", "work", "squad", "circle", "project", "gang")
        val lower = name.lowercase()
        return keywords.any { lower.contains(it) } || name.contains("👥") || name.contains("&") || name.split(" ").size >= 3
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

    private fun extractChatNamesFromScreen(root: AccessibilityNodeInfo): List<String> {
        val list = mutableListOf<String>()

        val knownIds = listOf(
            "conversations_row_contact_name",
            "contactpicker_row_name",
            "conversation_contact_name",
            "chat_title"
        )
        for (idName in knownIds) {
            val nodes1 = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/$idName")
            nodes1.forEach { n ->
                val text = n.text?.toString()?.trim()
                if (!text.isNullOrBlank() && text.length in 2..50 && !list.contains(text)) {
                    list.add(text)
                }
            }
            val nodes2 = root.findAccessibilityNodeInfosByViewId("com.whatsapp.w4b:id/$idName")
            nodes2.forEach { n ->
                val text = n.text?.toString()?.trim()
                if (!text.isNullOrBlank() && text.length in 2..50 && !list.contains(text)) {
                    list.add(text)
                }
            }
        }

        if (list.size < 2) {
            searchHierarchy(root) { n ->
                val id = n.viewIdResourceName ?: ""
                val text = n.text?.toString()?.trim() ?: ""
                if ((id.contains("name") || id.contains("title")) && text.length in 2..45 && !list.contains(text)) {
                    val ignore = listOf("Chats", "Updates", "Calls", "Communities", "Search", "Settings", "Yesterday", "Today")
                    if (!ignore.contains(text) && !text.matches(Regex("\\d{1,2}:\\d{2}\\s*(am|pm)?", RegexOption.IGNORE_CASE))) {
                        list.add(text)
                    }
                }
                false
            }
        }
        return list
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
