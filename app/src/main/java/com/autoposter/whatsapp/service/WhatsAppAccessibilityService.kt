package com.autoposter.whatsapp.service

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.autoposter.whatsapp.data.AutomationStep

class WhatsAppAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastActionTime = 0L
    private val actionDebounceMs = 600L
    private var hasSearchedCurrentTarget = false
    private var hasSelectedCurrentTarget = false
    private var hasClickedForwardFab = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!AutomationManager.isRunning) return
        val currentTarget = AutomationManager.getCurrentTarget() ?: return

        val now = System.currentTimeMillis()
        if (now - lastActionTime < actionDebounceMs) return

        val rootNode = rootInActiveWindow ?: return
        val packageName = event?.packageName?.toString() ?: ""

        if (packageName.contains("whatsapp")) {
            processWhatsAppUi(rootNode, currentTarget)
        }
    }

    private fun processWhatsAppUi(rootNode: AccessibilityNodeInfo, targetGroup: String) {
        val currentStep = AutomationManager.status.value.step

        // Reset tracking flags on new dispatch initiation
        if (currentStep == AutomationStep.INITIATED) {
            hasSearchedCurrentTarget = false
            hasSelectedCurrentTarget = false
            hasClickedForwardFab = false
            AutomationManager.updateStep(AutomationStep.SEARCHING_GROUP, "Searching for \"$targetGroup\"...")
        }

        // Phase 1: Search for Target Group
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
                    ?: findNodeById(rootNode, "com.whatsapp:id/menuitem_search")
                if (searchIcon != null) {
                    performClickOnNode(searchIcon)
                    lastActionTime = System.currentTimeMillis()
                    return
                }
            }
        }

        // Phase 2: Select the matched group from the list
        if (hasSearchedCurrentTarget && !hasSelectedCurrentTarget) {
            val matchedNode = findNodeContainingText(rootNode, targetGroup)
            if (matchedNode != null) {
                performClickOnNode(matchedNode)
                hasSelectedCurrentTarget = true
                lastActionTime = System.currentTimeMillis()
                AutomationManager.updateStep(AutomationStep.CONFIRMING_SEND, "Selected \"$targetGroup\". Forwarding...")
                return
            }
        }

        // Phase 3: Click green forward FAB button on the contact picker screen
        if (hasSelectedCurrentTarget && !hasClickedForwardFab) {
            val forwardFab = findNodeById(rootNode, "com.whatsapp:id/send")
                ?: findNodeById(rootNode, "com.whatsapp:id/fab")
                ?: findNodeByContentDescription(rootNode, "Send")
                ?: findNodeByContentDescription(rootNode, "Next")

            if (forwardFab != null) {
                performClickOnNode(forwardFab)
                hasClickedForwardFab = true
                lastActionTime = System.currentTimeMillis()
                AutomationManager.updateStep(AutomationStep.FINAL_SEND, "Media preview loaded. Dispatching...")
                return
            }
        }

        // Phase 4: Final Send button in the Media Caption / Preview screen
        if (hasClickedForwardFab) {
            // Optional Caption Fallback if intent text wasn't picked up automatically
            val caption = AutomationManager.captionText.value.trim()
            if (caption.isNotEmpty()) {
                val captionField = findNodeById(rootNode, "com.whatsapp:id/caption")
                    ?: findSearchEditText(rootNode)
                if (captionField != null && (captionField.text == null || captionField.text.isEmpty())) {
                    val args = Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, caption)
                    }
                    captionField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                }
            }

            val finalSendButton = findNodeById(rootNode, "com.whatsapp:id/send")
                ?: findNodeByContentDescription(rootNode, "Send")

            if (finalSendButton != null) {
                performClickOnNode(finalSendButton)
                lastActionTime = System.currentTimeMillis()

                // Successfully sent! Return to our app & initiate safe randomized cooldown
                mainHandler.postDelayed({
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    AutomationManager.onGroupDispatched(applicationContext)
                }, 1200)
            }
        }
    }

    private fun findSearchEditText(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val byId = node.findAccessibilityNodeInfosByViewId("com.whatsapp:id/search_src_text")
        if (byId.isNotEmpty() && byId[0].isEditable) return byId[0]

        return searchHierarchy(node) { it.isEditable && (it.className?.contains("EditText") == true) }
    }

    private fun findNodeById(node: AccessibilityNodeInfo, viewId: String): AccessibilityNodeInfo? {
        val list = node.findAccessibilityNodeInfosByViewId(viewId)
        return list.firstOrNull()
    }

    private fun findNodeByContentDescription(node: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        return searchHierarchy(node) {
            it.contentDescription?.toString()?.contains(desc, ignoreCase = true) == true
        }
    }

    private fun findNodeContainingText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val list = node.findAccessibilityNodeInfosByText(text)
        return list.firstOrNull {
            it.text?.toString()?.contains(text, ignoreCase = true) == true
        }
    }

    private fun performClickOnNode(target: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = target
        while (current != null) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
        }
        return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
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
