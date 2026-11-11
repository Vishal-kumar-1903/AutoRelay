package com.autoposter.whatsapp.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.autoposter.whatsapp.data.*
import com.autoposter.whatsapp.service.AutomationManager
import com.autoposter.whatsapp.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    companion object {
        const val ACTION_EXECUTE_SCHEDULED_BROADCAST = "com.autoposter.whatsapp.ACTION_EXECUTE_SCHEDULED_BROADCAST"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AutomationManager.loadPersistence(this)
        handleIncomingIntent(intent)

        setContent {
            val isDark by AutomationManager.isDarkMode.collectAsState()
            WhatsAppAutoPosterTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AutoRelayCleanWorkspace()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == ACTION_EXECUTE_SCHEDULED_BROADCAST) {
            val count = AutomationManager.getActiveQueue().size
            if (count > 0) {
                Toast.makeText(this, "⏰ Scheduled time reached! Starting broadcast to $count chats...", Toast.LENGTH_LONG).show()
                AutomationManager.startBroadcast(this)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        AutomationManager.savePersistence(this)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoRelayCleanWorkspace() {
    val context = LocalContext.current
    var isAccessibilityEnabled by remember { mutableStateOf(checkAccessibilityService(context)) }
    val isDark by AutomationManager.isDarkMode.collectAsState()

    var showActionSheet by remember { mutableStateOf(false) }
    var showAddRecipientModal by remember { mutableStateOf(false) }
    var showAddCategoryModal by remember { mutableStateOf(false) }
    var showMetricsModal by remember { mutableStateOf(false) }
    var showRestrictedHelpDialog by remember { mutableStateOf(false) }
    var showMoveModal by remember { mutableStateOf(false) }
    var showSyncChooserModal by remember { mutableStateOf(false) }
    var showScheduleModal by remember { mutableStateOf(false) }
    var showTemplatesModal by remember { mutableStateOf(false) }
    var showSafetyModal by remember { mutableStateOf(false) }
    var showCsvImportModal by remember { mutableStateOf(false) }
    var itemToMove by remember { mutableStateOf<String?>(null) }
    var typeFilter by remember { mutableStateOf("ALL") } // "ALL", "GROUPS", "INDIVIDUALS"
    var searchQuery by remember { mutableStateOf("") }

    val activeFilter by AutomationManager.activeCategoryFilter.collectAsState()
    val categories by AutomationManager.categories.collectAsState()
    val targetRecipients by AutomationManager.targetRecipients.collectAsState()
    val isScheduleActive by AutomationManager.isScheduleActive.collectAsState()
    val scheduledTimestamp by AutomationManager.scheduledTimestamp.collectAsState()
    val scheduleRepeatDaily by AutomationManager.scheduleRepeatDaily.collectAsState()
    val scheduledTargetCategory by AutomationManager.scheduledTargetCategory.collectAsState()

    val recipients = remember(targetRecipients, activeFilter, typeFilter, searchQuery) {
        val byCat = if (activeFilter == "All") targetRecipients else targetRecipients.filter { it.category == activeFilter }
        val byType = when (typeFilter) {
            "GROUPS" -> byCat.filter { it.type == RecipientType.GROUP }
            "INDIVIDUALS" -> byCat.filter { it.type == RecipientType.INDIVIDUAL }
            else -> byCat
        }
        if (searchQuery.isBlank()) byType
        else byType.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
    }
    val selectedCount = remember(recipients) {
        recipients.count { it.isSelected }
    }
    val totalInCat = remember(targetRecipients, activeFilter) {
        if (activeFilter == "All") targetRecipients.size else targetRecipients.count { it.category == activeFilter }
    }
    val groupCountInCat = remember(targetRecipients, activeFilter) {
        val byCat = if (activeFilter == "All") targetRecipients else targetRecipients.filter { it.category == activeFilter }
        byCat.count { it.type == RecipientType.GROUP }
    }
    val dmCountInCat = remember(targetRecipients, activeFilter) {
        val byCat = if (activeFilter == "All") targetRecipients else targetRecipients.filter { it.category == activeFilter }
        byCat.count { it.type == RecipientType.INDIVIDUAL }
    }

    val mediaUri by AutomationManager.selectedMediaUri.collectAsState()
    val mediaName by AutomationManager.mediaFileName.collectAsState()
    val caption by AutomationManager.captionText.collectAsState()
    val status by AutomationManager.status.collectAsState()
    val minDelay by AutomationManager.minDelaySeconds.collectAsState()
    val maxDelay by AutomationManager.maxDelaySeconds.collectAsState()

    // Pick from Phone Contacts (100% standard, no typing needed!)
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        if (uri != null) {
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.Contacts.DISPLAY_NAME),
                null,
                null,
                null
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val name = it.getString(nameIndex)
                        if (!name.isNullOrBlank()) {
                            val cat = if (activeFilter == "All") "Friends" else activeFilter
                            AutomationManager.addRecipient(name.trim(), cat, RecipientType.INDIVIDUAL, context)
                            Toast.makeText(context, "Added \"$name\" to $cat!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    // Pick Photo/Video
    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            AutomationManager.selectedMediaUri.value = uri
            val type = context.contentResolver.getType(uri) ?: "image/*"
            AutomationManager.mediaMimeType.value = type
            AutomationManager.mediaFileName.value = getFileName(context, uri) ?: "Attached Media"
        }
    }

    // Pick Any Document / File (PDF, docs, apk, zip)
    val docPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            AutomationManager.selectedMediaUri.value = uri
            val type = context.contentResolver.getType(uri) ?: "*/*"
            AutomationManager.mediaMimeType.value = type
            AutomationManager.mediaFileName.value = getFileName(context, uri) ?: "Document File"
        }
    }

    // Pick CSV / Text File for Bulk Import
    val csvPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val text = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                if (text.isNotBlank()) {
                    val added = AutomationManager.importFromCsvOrText(context, text, activeFilter)
                    Toast.makeText(context, "Imported $added contacts from file into $activeFilter!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Selected file is empty.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to read file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var currentTimeMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(isScheduleActive) {
        if (isScheduleActive) {
            while (true) {
                currentTimeMillis = System.currentTimeMillis()
                val targetTime = AutomationManager.scheduledTimestamp.value
                if (targetTime != null && currentTimeMillis >= targetTime) {
                    AutomationManager.onScheduleTriggered(context)
                    if (checkAccessibilityService(context)) {
                        Toast.makeText(context, "⏰ Scheduled time reached! Dispatching broadcast now...", Toast.LENGTH_LONG).show()
                        AutomationManager.startBroadcast(context)
                    }
                    break
                }
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    LaunchedEffect(Unit) {
        isAccessibilityEnabled = checkAccessibilityService(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(Brush.linearGradient(listOf(BrandEmerald, BrandTealDark))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("AutoRelay", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = if (isDark) DarkTextPrimary else LightTextPrimary)
                                Spacer(modifier = Modifier.width(5.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(BrandEmerald.copy(alpha = 0.15f))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text("PRO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = BrandEmerald)
                                }
                            }
                            Text(
                                if (isAccessibilityEnabled) "🟢 Auto-Pilot Ready" else "⚪ Direct Share Mode",
                                fontSize = 11.sp,
                                color = if (isAccessibilityEnabled) BrandEmerald else (if (isDark) DarkTextSecondary else LightTextSecondary)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (!isAccessibilityEnabled) {
                            Toast.makeText(context, "Please enable Accessibility to scan WhatsApp chats & groups", Toast.LENGTH_LONG).show()
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        } else {
                            showSyncChooserModal = true
                        }
                    }) {
                        Icon(Icons.Default.Sync, contentDescription = "Sync from WhatsApp", tint = BrandEmerald)
                    }
                    IconButton(onClick = { showMetricsModal = true }) {
                        Icon(Icons.Outlined.BarChart, contentDescription = "Metrics", tint = if (isDark) DarkTextSecondary else LightTextSecondary)
                    }
                    IconButton(onClick = { AutomationManager.isDarkMode.value = !isDark }) {
                        Icon(
                            imageVector = if (isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                            contentDescription = "Toggle Theme",
                            tint = if (isDark) AccentAmber else LightTextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDark) DarkSurface else LightSurface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showActionSheet = true },
                containerColor = BrandEmerald,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Content or Recipients", modifier = Modifier.size(26.dp))
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(if (isDark) DarkBg else LightBg)
        ) {

            // 1. Clean Category / Group Tabs Bar
            Surface(
                color = if (isDark) DarkSurface else LightSurface,
                modifier = Modifier.fillMaxWidth().border(0.5.dp, if (isDark) DarkCardBorder else LightCardBorder, RoundedCornerShape(0.dp))
            ) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        CategoryPill(
                            label = "All",
                            count = targetRecipients.size,
                            isSelected = activeFilter == "All",
                            isDark = isDark,
                            onClick = { AutomationManager.activeCategoryFilter.value = "All" }
                        )
                    }
                    items(categories) { cat ->
                        val count = targetRecipients.count { it.category == cat }
                        CategoryPill(
                            label = cat,
                            count = count,
                            isSelected = activeFilter == cat,
                            isDark = isDark,
                            onClick = { AutomationManager.activeCategoryFilter.value = cat }
                        )
                    }
                    item {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Transparent,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isDark) DarkCardBorder else LightCardBorder),
                            modifier = Modifier.clickable { showAddCategoryModal = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = AccentSky, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("New Group / List", fontSize = 11.sp, color = AccentSky, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // 2. Main Content Area (Recipients in selected folder)
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {

                // Permission Alert (if needed)
                if (!isAccessibilityEnabled) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = if (isDark) DarkCard else LightCard),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.border(1.dp, AccentAmber.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Default.LockClock, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Accessibility Permission Needed", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (isDark) DarkTextPrimary else LightTextPrimary)
                                    }
                                    Text(
                                        "Enable",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentAmber,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(AccentAmber.copy(alpha = 0.15f))
                                            .clickable {
                                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                }
                                                context.startActivity(intent)
                                                isAccessibilityEnabled = checkAccessibilityService(context)
                                            }
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isDark) DarkInputBg else LightInputBg)
                                        .clickable { showRestrictedHelpDialog = true }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "⚠️ Greyed out \"Restricted Setting\"? Tap to unlock",
                                        fontSize = 11.sp,
                                        color = AccentAmber,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Icon(Icons.Default.Info, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                // Header for active folder
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (activeFilter == "All") "All Target Chats (${recipients.size})" else "$activeFilter List (${recipients.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = if (isDark) DarkTextPrimary else LightTextPrimary
                            )

                            if (recipients.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (selectedCount == recipients.size) "Deselect All" else "Select All",
                                        fontSize = 12.sp,
                                        color = AccentSky,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.clickable {
                                            val newSelect = selectedCount != recipients.size
                                            val namesInView = recipients.map { it.name }.toSet()
                                            AutomationManager.setSelectionForNames(namesInView, newSelect, context)
                                        }
                                    )
                                    Text("•", color = if (isDark) DarkTextMuted else LightTextMuted)
                                    Text(
                                        "Clear",
                                        fontSize = 12.sp,
                                        color = AccentRose,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.clickable { AutomationManager.clearActiveCategoryRecipients(context) }
                                    )
                                }
                            }
                        }

                        if (recipients.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$selectedCount of ${recipients.size} selected for broadcast",
                                    fontSize = 11.sp,
                                    color = BrandEmerald,
                                    fontWeight = FontWeight.Medium
                                )

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = AccentAmber.copy(alpha = 0.15f),
                                    modifier = Modifier.clickable {
                                        val count = AutomationManager.autoCategorizeAllRecipients(context)
                                        Toast.makeText(context, "Sorted & corrected $count chats into Study, Business, Family & Friends!", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Auto-Sort & Fix", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentAmber)
                                    }
                                }
                            }
                        }
                    }
                }

                // Sub-Type Filters (All, Groups, 1-on-1 Chats)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TypeFilterChip(
                            label = "All ($totalInCat)",
                            isSelected = typeFilter == "ALL",
                            isDark = isDark,
                            onClick = { typeFilter = "ALL" }
                        )
                        TypeFilterChip(
                            label = "👥 Groups ($groupCountInCat)",
                            isSelected = typeFilter == "GROUPS",
                            isDark = isDark,
                            onClick = { typeFilter = "GROUPS" }
                        )
                        TypeFilterChip(
                            label = "👤 1-on-1 Chats ($dmCountInCat)",
                            isSelected = typeFilter == "INDIVIDUALS",
                            isDark = isDark,
                            onClick = { typeFilter = "INDIVIDUALS" }
                        )
                    }
                }

                // In-App Fast Search Bar
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by name or number...", fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = BrandEmerald, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = if (isDark) DarkCard else LightCard,
                            unfocusedContainerColor = if (isDark) DarkCard else LightCard,
                            focusedBorderColor = BrandEmerald,
                            unfocusedBorderColor = if (isDark) DarkCardBorder else LightCardBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Scheduled Broadcast Active Countdown Card
                if (isScheduleActive && scheduledTimestamp != null) {
                    item {
                        val remainingMillis = (scheduledTimestamp!! - currentTimeMillis).coerceAtLeast(0L)
                        val remainingHours = remainingMillis / (1000 * 60 * 60)
                        val remainingMinutes = (remainingMillis % (1000 * 60 * 60)) / (1000 * 60)
                        val remainingSeconds = (remainingMillis % (1000 * 60)) / 1000
                        val countdownStr = String.format(Locale.getDefault(), "%02dh %02dm %02ds", remainingHours, remainingMinutes, remainingSeconds)

                        val schedDate = remember(scheduledTimestamp) {
                            SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(scheduledTimestamp!!))
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = AccentAmber.copy(alpha = 0.12f)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, AccentAmber, RoundedCornerShape(14.dp))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(AccentAmber),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Schedule, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                "⏰ Scheduled Broadcast",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = AccentAmber
                                            )
                                            Text(
                                                "Target: $scheduledTargetCategory ${if (scheduleRepeatDaily) "(Daily)" else ""}",
                                                fontSize = 10.sp,
                                                color = if (isDark) DarkTextSecondary else LightTextSecondary
                                            )
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(AccentAmber.copy(alpha = 0.2f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            countdownStr,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AccentAmber
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Set for: $schedDate. Will automatically start sending when countdown finishes.",
                                    fontSize = 11.sp,
                                    color = if (isDark) DarkTextPrimary else LightTextPrimary
                                )

                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            AutomationManager.cancelSchedule(context)
                                            AutomationManager.startBroadcast(context)
                                        },
                                        modifier = Modifier.weight(1.2f).height(36.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Send Now Immediately", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            AutomationManager.cancelSchedule(context)
                                            Toast.makeText(context, "Scheduled broadcast cancelled.", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.weight(0.8f).height(36.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, AccentRose),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRose),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Cancel", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Dedicated Message Composer & Personalization Card
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (isDark) DarkCard else LightCard),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(elevation = if (isDark) 0.dp else 1.dp, shape = RoundedCornerShape(14.dp))
                            .border(0.5.dp, if (isDark) DarkCardBorder else LightCardBorder, RoundedCornerShape(14.dp))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.Chat, contentDescription = null, tint = BrandEmerald, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Broadcast Message", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (isDark) DarkTextPrimary else LightTextPrimary)
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = BrandEmerald.copy(alpha = 0.12f),
                                        modifier = Modifier.clickable { showTemplatesModal = true }
                                    ) {
                                        Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Outlined.Description, contentDescription = null, tint = BrandEmerald, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("Templates", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = BrandEmerald)
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = AccentAmber.copy(alpha = 0.12f),
                                        modifier = Modifier.clickable { showSafetyModal = true }
                                    ) {
                                        Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Outlined.Shield, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text("Anti-Ban", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentAmber)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = caption,
                                onValueChange = { AutomationManager.captionText.value = it },
                                placeholder = {
                                    Text("Type message... Use {name} for contact name, {Hi|Hello} for variations", fontSize = 12.sp)
                                },
                                minLines = 2,
                                maxLines = 4,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = if (isDark) DarkInputBg else LightInputBg,
                                    unfocusedContainerColor = if (isDark) DarkInputBg else LightInputBg,
                                    focusedBorderColor = BrandEmerald,
                                    unfocusedBorderColor = if (isDark) DarkCardBorder else LightCardBorder
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Quick Insertion Chips
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isDark) DarkInputBg else LightInputBg,
                                        border = androidx.compose.foundation.BorderStroke(0.5.dp, if (isDark) DarkCardBorder else LightCardBorder),
                                        modifier = Modifier.clickable {
                                            AutomationManager.captionText.value = (caption + " {name}").trimStart()
                                        }
                                    ) {
                                        Text("+ {name}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentSky, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                    }
                                }
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isDark) DarkInputBg else LightInputBg,
                                        border = androidx.compose.foundation.BorderStroke(0.5.dp, if (isDark) DarkCardBorder else LightCardBorder),
                                        modifier = Modifier.clickable {
                                            AutomationManager.captionText.value = (caption + " {Hi|Hello|Hey}").trimStart()
                                        }
                                    ) {
                                        Text("+ {Hi|Hello}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = BrandEmerald, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                    }
                                }
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isDark) DarkInputBg else LightInputBg,
                                        border = androidx.compose.foundation.BorderStroke(0.5.dp, if (isDark) DarkCardBorder else LightCardBorder),
                                        modifier = Modifier.clickable {
                                            AutomationManager.captionText.value = (caption + " {Thanks|Regards|Best}").trimStart()
                                        }
                                    ) {
                                        Text("+ {Thanks|Regards}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentViolet, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                    }
                                }
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isDark) DarkInputBg else LightInputBg,
                                        border = androidx.compose.foundation.BorderStroke(0.5.dp, if (isDark) DarkCardBorder else LightCardBorder),
                                        modifier = Modifier.clickable {
                                            mediaPicker.launch("image/*,video/*")
                                        }
                                    ) {
                                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(if (mediaUri != null) "Change Photo" else "+ Photo/Video", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentAmber)
                                        }
                                    }
                                }
                            }

                            // Real-time Preview Box
                            if (caption.isNotBlank()) {
                                val previewTarget = recipients.firstOrNull()?.name ?: "Harsh"
                                val isPreviewGroup = recipients.firstOrNull()?.type == RecipientType.GROUP
                                val previewResolved = remember(caption, previewTarget) {
                                    AutomationManager.getFormattedMessageForTarget(previewTarget, isPreviewGroup)
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = BrandEmerald.copy(alpha = 0.08f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Text("👁️ Live Preview (for $previewTarget):", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = BrandEmerald)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            previewResolved,
                                            fontSize = 11.sp,
                                            color = if (isDark) DarkTextPrimary else LightTextPrimary,
                                            maxLines = 2
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Bulk Move Banner (when recipients are selected)
                if (selectedCount > 0) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = BrandEmerald.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BrandEmerald.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "$selectedCount chat(s) selected",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BrandEmerald
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = BrandEmerald,
                                    modifier = Modifier.clickable {
                                        itemToMove = null
                                        showMoveModal = true
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.DriveFileMove, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Move to Folder", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }

                // Empty state if folder has no recipients
                if (recipients.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = if (isDark) DarkCard else LightCard),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(elevation = if (isDark) 0.dp else 1.dp, shape = RoundedCornerShape(16.dp))
                                .border(0.5.dp, if (isDark) DarkCardBorder else LightCardBorder, RoundedCornerShape(16.dp))
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(22.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background((if (isDark) DarkInputBg else LightInputBg)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Outlined.GroupAdd, contentDescription = null, tint = BrandEmerald, modifier = Modifier.size(24.dp))
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (activeFilter == "All") "No Chats or Groups Added Yet" else "No Members in \"$activeFilter\"",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = if (isDark) DarkTextPrimary else LightTextPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Choose from phone contacts or type WhatsApp group names:",
                                    fontSize = 12.sp,
                                    color = if (isDark) DarkTextSecondary else LightTextSecondary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = {
                                            if (!isAccessibilityEnabled) {
                                                Toast.makeText(context, "Please enable Accessibility to scan WhatsApp groups & chats", Toast.LENGTH_LONG).show()
                                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                                context.startActivity(intent)
                                            } else {
                                                showSyncChooserModal = true
                                            }
                                        },
                                        modifier = Modifier.weight(1.3f),
                                        colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Pick from WhatsApp", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }

                                    OutlinedButton(
                                        onClick = { showAddRecipientModal = true },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Type / Paste", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Recipient Items with Multi-Select Checkboxes
                    items(recipients) { recipient ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = if (isDark) DarkCard else LightCard),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(elevation = if (isDark) 0.dp else 1.dp, shape = RoundedCornerShape(14.dp))
                                .border(
                                    width = if (recipient.isSelected) 1.dp else 0.5.dp,
                                    color = if (recipient.isSelected) BrandEmerald else (if (isDark) DarkCardBorder else LightCardBorder),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .clickable { AutomationManager.toggleSelection(recipient.name, context) }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Checkbox(
                                        checked = recipient.isSelected,
                                        onCheckedChange = { AutomationManager.toggleSelection(recipient.name, context) },
                                        colors = CheckboxDefaults.colors(checkedColor = BrandEmerald)
                                    )

                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (recipient.type == RecipientType.INDIVIDUAL) AccentSky.copy(alpha = 0.15f)
                                                else BrandEmerald.copy(alpha = 0.15f)
                                            )
                                            .clickable {
                                                AutomationManager.toggleRecipientType(recipient.name, context)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (recipient.type == RecipientType.INDIVIDUAL) Icons.Default.Person else Icons.Default.Groups,
                                            contentDescription = "Toggle Group / Private DM",
                                            tint = if (recipient.type == RecipientType.INDIVIDUAL) AccentSky else BrandEmerald,
                                            modifier = Modifier.size(17.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(recipient.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (isDark) DarkTextPrimary else LightTextPrimary)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (recipient.type == RecipientType.INDIVIDUAL) "👤 1-to-1 DM" else "👥 WhatsApp Group",
                                                fontSize = 11.sp,
                                                color = if (recipient.type == RecipientType.INDIVIDUAL) AccentSky else BrandEmerald,
                                                fontWeight = FontWeight.Medium
                                            )
                                            if (activeFilter == "All") {
                                                Text(" • ", fontSize = 11.sp, color = if (isDark) DarkTextMuted else LightTextMuted)
                                                Text(recipient.category, fontSize = 11.sp, color = if (isDark) DarkTextSecondary else LightTextSecondary)
                                            }
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            itemToMove = recipient.name
                                            showMoveModal = true
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.DriveFileMove, contentDescription = "Move to Folder", tint = AccentSky, modifier = Modifier.size(17.dp))
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { AutomationManager.removeRecipient(recipient.name, context) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = if (isDark) DarkTextMuted else LightTextMuted, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // Status if active run is going on
                if (AutomationManager.isRunning) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = if (isDark) DarkCard else LightCard),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().border(1.dp, BrandEmerald, RoundedCornerShape(14.dp))
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(BrandEmerald))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Active Dispatcher Running", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BrandEmerald)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(status.logMessage, fontSize = 12.sp, color = if (isDark) DarkTextPrimary else LightTextPrimary)
                                if (status.step == AutomationStep.COOLDOWN_DELAY && status.delayCountdownSeconds > 0) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { status.delayCountdownSeconds.toFloat() / maxDelay.toFloat() },
                                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                                        color = AccentAmber,
                                        trackColor = if (isDark) DarkInputBg else LightInputBg
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { AutomationManager.stopBroadcast() },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentRose),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Stop", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

            }

            // 3. Bottom Action Dock (Attached Media Preview + 1-Tap WhatsApp / Loop Buttons)
            Surface(
                color = if (isDark) DarkSurface else LightSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 6.dp)
                    .border(0.5.dp, if (isDark) DarkCardBorder else LightCardBorder, RoundedCornerShape(0.dp))
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {

                    // Attached preview row (if any)
                    if (mediaUri != null || caption.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isDark) DarkInputBg else LightInputBg)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = if (mediaUri != null) Icons.Outlined.Attachment else Icons.Outlined.Chat,
                                    contentDescription = null,
                                    tint = BrandEmerald,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (mediaUri != null) mediaName else "Message: \"${caption.take(25)}...\"",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isDark) DarkTextPrimary else LightTextPrimary,
                                    maxLines = 1
                                )
                            }
                            Text(
                                "Clear",
                                fontSize = 11.sp,
                                color = AccentRose,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    AutomationManager.selectedMediaUri.value = null
                                    AutomationManager.mediaFileName.value = ""
                                    AutomationManager.captionText.value = ""
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // 3 Main Actions: 1-Tap Pick in WhatsApp, Auto-Send & Timer Schedule
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Button A: Direct 1-Tap WhatsApp Picker (No Permissions Needed!)
                        Button(
                            onClick = { AutomationManager.openDirectWhatsAppPicker(context) },
                            modifier = Modifier.weight(1.1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Column {
                                Text("Pick in WhatsApp", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.White)
                                Text("No Permission", fontSize = 8.sp, color = Color.White.copy(alpha = 0.8f))
                            }
                        }

                        // Button B: Auto-Loop to active folder
                        OutlinedButton(
                            onClick = {
                                if (!isAccessibilityEnabled) {
                                    Toast.makeText(context, "Auto-Send needs Accessibility to click buttons automatically. Use 'Pick in WhatsApp' for direct share!", Toast.LENGTH_LONG).show()
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    context.startActivity(intent)
                                    return@OutlinedButton
                                }
                                AutomationManager.startBroadcast(context)
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BrandEmerald)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = BrandEmerald, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Text("Auto-Send (${selectedCount})", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = BrandEmerald)
                                Text("Hands-free", fontSize = 8.sp, color = BrandEmerald.copy(alpha = 0.8f))
                            }
                        }

                        // Button C: ⏰ Schedule Timer Button
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isScheduleActive) AccentAmber else (if (isDark) DarkInputBg else LightInputBg),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isScheduleActive) AccentAmber else (if (isDark) DarkCardBorder else LightCardBorder)),
                            modifier = Modifier
                                .height(48.dp)
                                .clickable { showScheduleModal = true }
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Schedule,
                                    contentDescription = "Schedule Broadcast",
                                    tint = if (isScheduleActive) Color.White else (if (isDark) DarkTextPrimary else LightTextPrimary),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    "Timer",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isScheduleActive) Color.White else (if (isDark) DarkTextSecondary else LightTextSecondary)
                                )
                            }
                        }
                    }

                }
            }

        }
    }

    // Modal: Action Sheet (+)
    if (showActionSheet) {
        ModalBottomSheet(
            onDismissRequest = { showActionSheet = false },
            containerColor = if (isDark) DarkSurface else LightSurface
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text("Quick Actions & Compose", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = if (isDark) DarkTextPrimary else LightTextPrimary)
                Spacer(modifier = Modifier.height(14.dp))

                ActionSheetItem(
                    icon = Icons.Default.Download,
                    title = "Pick & Sync from WhatsApp",
                    subtitle = "Auto-scan and load your real WhatsApp groups & chats",
                    tint = BrandEmerald,
                    isDark = isDark,
                    onClick = {
                        showActionSheet = false
                        if (!isAccessibilityEnabled) {
                            Toast.makeText(context, "Please enable Accessibility to scan WhatsApp groups & chats", Toast.LENGTH_LONG).show()
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        } else {
                            showSyncChooserModal = true
                        }
                    }
                )

                ActionSheetItem(
                    icon = Icons.Default.AutoAwesome,
                    title = "⚡ Smart Auto-Categorize",
                    subtitle = "Automatically sort chats into Study, Business, Friends & Family",
                    tint = AccentAmber,
                    isDark = isDark,
                    onClick = {
                        showActionSheet = false
                        val count = AutomationManager.autoCategorizeAllRecipients(context)
                        Toast.makeText(context, "Sorted $count chats into Study, Business, Family & Friends!", Toast.LENGTH_SHORT).show()
                    }
                )

                ActionSheetItem(
                    icon = Icons.Outlined.GroupAdd,
                    title = "Add Group or Names (Type/Paste)",
                    subtitle = "Add exact WhatsApp group names or paste list",
                    tint = AccentSky,
                    isDark = isDark,
                    onClick = {
                        showActionSheet = false
                        showAddRecipientModal = true
                    }
                )

                ActionSheetItem(
                    icon = Icons.Outlined.CreateNewFolder,
                    title = "Create New Group / List",
                    subtitle = "Create custom category folder (VIP, Work, etc.)",
                    tint = AccentViolet,
                    isDark = isDark,
                    onClick = {
                        showActionSheet = false
                        showAddCategoryModal = true
                    }
                )

                ActionSheetItem(
                    icon = Icons.Outlined.AddPhotoAlternate,
                    title = "Attach Photo / Video",
                    subtitle = "Pick media from phone gallery",
                    tint = AccentAmber,
                    isDark = isDark,
                    onClick = {
                        showActionSheet = false
                        mediaPicker.launch("image/*,video/*")
                    }
                )

                ActionSheetItem(
                    icon = Icons.Outlined.FilePresent,
                    title = "Attach Document / Any File",
                    subtitle = "Send PDF, APK, document, or audio",
                    tint = BrandTealDark,
                    isDark = isDark,
                    onClick = {
                        showActionSheet = false
                        docPicker.launch("*/*")
                    }
                )

                ActionSheetItem(
                    icon = Icons.Default.Schedule,
                    title = "⏰ Schedule Broadcast Timer",
                    subtitle = "Set exact alarm or daily morning auto-dispatch",
                    tint = AccentAmber,
                    isDark = isDark,
                    onClick = {
                        showActionSheet = false
                        showScheduleModal = true
                    }
                )

                ActionSheetItem(
                    icon = Icons.Default.DriveFolderUpload,
                    title = "📁 Bulk Import from CSV / Text File",
                    subtitle = "Import 50+ contacts from .csv, .txt, or excel list",
                    tint = BrandEmerald,
                    isDark = isDark,
                    onClick = {
                        showActionSheet = false
                        showCsvImportModal = true
                    }
                )

                ActionSheetItem(
                    icon = Icons.Outlined.Description,
                    title = "📋 Message Templates",
                    subtitle = "Study notices, business deals & greetings",
                    tint = AccentSky,
                    isDark = isDark,
                    onClick = {
                        showActionSheet = false
                        showTemplatesModal = true
                    }
                )

                ActionSheetItem(
                    icon = Icons.Outlined.Shield,
                    title = "🛡️ Anti-Ban Safety Controls",
                    subtitle = "Configure Fast, Balanced, or Ultra-Safe delay",
                    tint = AccentViolet,
                    isDark = isDark,
                    onClick = {
                        showActionSheet = false
                        showSafetyModal = true
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // Modal: Add Recipient & Caption
    if (showAddRecipientModal) {
        AddRecipientDialog(
            isDark = isDark,
            activeCategory = activeFilter,
            categories = categories,
            onPickFromContacts = { contactPickerLauncher.launch(null) },
            onDismiss = { showAddRecipientModal = false }
        )
    }

    // Modal: Add Category
    if (showAddCategoryModal) {
        AddCategoryDialog(
            isDark = isDark,
            onDismiss = { showAddCategoryModal = false }
        )
    }

    // Modal: Move Recipient(s) to Category
    if (showMoveModal) {
        MoveToCategoryDialog(
            isDark = isDark,
            categories = categories,
            targetItemName = itemToMove,
            selectedCount = selectedCount,
            onMove = { destinationCat ->
                if (itemToMove != null) {
                    AutomationManager.moveRecipientToCategory(itemToMove!!, destinationCat, context)
                    Toast.makeText(context, "Moved \"$itemToMove\" to $destinationCat!", Toast.LENGTH_SHORT).show()
                } else {
                    val count = AutomationManager.moveSelectedRecipients(destinationCat, context)
                    Toast.makeText(context, "Moved $count chat(s) to $destinationCat!", Toast.LENGTH_SHORT).show()
                }
                itemToMove = null
            },
            onDismiss = {
                showMoveModal = false
                itemToMove = null
            }
        )
    }

    // Modal: WhatsApp / WhatsApp Business Sync Chooser
    if (showSyncChooserModal) {
        SyncChooserDialog(
            isDark = isDark,
            categories = categories,
            activeCategory = activeFilter,
            onStartSync = { useBusiness, targetCat ->
                if (!isAccessibilityEnabled) {
                    Toast.makeText(context, "Please enable Accessibility to scan WhatsApp groups & chats", Toast.LENGTH_LONG).show()
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    context.startActivity(intent)
                } else {
                    AutomationManager.startWhatsAppSync(context, targetCat, useBusiness)
                }
            },
            onDismiss = { showSyncChooserModal = false }
        )
    }

    // Modal: Metrics & History
    if (showMetricsModal) {
        MetricsDialog(
            isDark = isDark,
            onDismiss = { showMetricsModal = false }
        )
    }

    // Modal: Restricted Setting Guide for Android 13/14
    if (showRestrictedHelpDialog) {
        AlertDialog(
            onDismissRequest = { showRestrictedHelpDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(22.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unlock Restricted Setting", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Android 13/14 blocks sideloaded apps from Accessibility by default. Here is how to unlock it in 3 steps:",
                        fontSize = 13.sp,
                        color = if (isDark) DarkTextPrimary else LightTextPrimary
                    )
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (isDark) DarkInputBg else LightInputBg),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("1️⃣ Tap \"Open App Info\" below.", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("2️⃣ In App Info, tap the 3 dots (⋮) in the top-right corner.", fontSize = 12.sp)
                            Text("3️⃣ Choose \"Allow restricted settings\" & enter your phone PIN / Fingerprint.", fontSize = 12.sp)
                            Text("4️⃣ Return to Accessibility and turn ON AutoRelay!", fontSize = 12.sp, color = BrandEmerald, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open App Info directly", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
                ) {
                    Text("Open App Info (Step 1)")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestrictedHelpDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Modal: Schedule Broadcast Dialog
    if (showScheduleModal) {
        ScheduleBroadcastDialog(
            isDark = isDark,
            categories = categories,
            activeCategory = activeFilter,
            onSchedule = { triggerMillis, targetCat, repeatDaily ->
                AutomationManager.scheduleBroadcast(context, triggerMillis, targetCat, repeatDaily)
                val schedStr = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(triggerMillis))
                Toast.makeText(context, "⏰ Scheduled for $schedStr (${if (repeatDaily) "Repeats Daily" else "One-time"})!", Toast.LENGTH_LONG).show()
            },
            onDismiss = { showScheduleModal = false }
        )
    }

    // Modal: Message Templates
    if (showTemplatesModal) {
        TemplatesDialog(
            isDark = isDark,
            onSelectTemplate = { text ->
                AutomationManager.captionText.value = text
                Toast.makeText(context, "Template applied to message!", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showTemplatesModal = false }
        )
    }

    // Modal: Anti-Ban Safety Controls
    if (showSafetyModal) {
        SafetySettingsDialog(
            isDark = isDark,
            currentMin = minDelay,
            currentMax = maxDelay,
            onSelectMode = { mode ->
                AutomationManager.setSafetyMode(mode, context)
                Toast.makeText(context, "Safety mode set to ${mode.label}!", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showSafetyModal = false }
        )
    }

    // Modal: Bulk CSV / Text File Import
    if (showCsvImportModal) {
        CsvImportDialog(
            isDark = isDark,
            activeCategory = activeFilter,
            onPickFile = { csvPicker.launch("text/*") },
            onPasteImport = { pastedText ->
                val added = AutomationManager.importFromCsvOrText(context, pastedText, activeFilter)
                Toast.makeText(context, "Imported $added contacts into $activeFilter!", Toast.LENGTH_LONG).show()
            },
            onDismiss = { showCsvImportModal = false }
        )
    }
}

@Composable
fun CategoryPill(
    label: String,
    count: Int,
    isSelected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) BrandEmerald else (if (isDark) DarkInputBg else LightInputBg),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            if (isSelected) BrandEmerald else (if (isDark) DarkCardBorder else LightCardBorder)
        ),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else (if (isDark) DarkTextPrimary else LightTextPrimary)
            )
            if (count > 0) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isSelected) Color.White.copy(alpha = 0.25f) else (if (isDark) DarkCardBorder else LightCardBorder))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "$count",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.White else (if (isDark) DarkTextSecondary else LightTextSecondary)
                    )
                }
            }
        }
    }
}

@Composable
fun ActionSheetItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (isDark) DarkTextPrimary else LightTextPrimary)
            Text(subtitle, fontSize = 11.sp, color = if (isDark) DarkTextSecondary else LightTextSecondary)
        }
    }
}

@Composable
fun AddRecipientDialog(
    isDark: Boolean,
    activeCategory: String,
    categories: List<String>,
    onPickFromContacts: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var recipientName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(RecipientType.GROUP) }
    var selectedCategory by remember { mutableStateOf(if (activeCategory == "All") "Friends" else activeCategory) }
    var captionInput by remember { mutableStateOf(AutomationManager.captionText.value) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Recipients & Groups", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                // 1-Tap Pick from WhatsApp Button
                Button(
                    onClick = {
                        onDismiss()
                        if (!checkAccessibilityService(context)) {
                            Toast.makeText(context, "Please enable Accessibility to scan WhatsApp chats & groups automatically", Toast.LENGTH_LONG).show()
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        } else {
                            AutomationManager.startWhatsAppSync(context, selectedCategory)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("📥 Pick from WhatsApp (Auto-Scan)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = if (isDark) DarkCardBorder else LightCardBorder)
                    Text("  OR TYPE / PASTE  ", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isDark) DarkTextMuted else LightTextMuted)
                    HorizontalDivider(modifier = Modifier.weight(1f), color = if (isDark) DarkCardBorder else LightCardBorder)
                }

                // Type Switcher: Group vs 1-to-1 Friend
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isDark) DarkInputBg else LightInputBg)
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedType == RecipientType.GROUP) BrandEmerald else Color.Transparent)
                            .clickable { selectedType = RecipientType.GROUP }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "👥 WhatsApp Group",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedType == RecipientType.GROUP) Color.White else (if (isDark) DarkTextSecondary else LightTextSecondary)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedType == RecipientType.INDIVIDUAL) AccentSky else Color.Transparent)
                            .clickable { selectedType = RecipientType.INDIVIDUAL }
                            .padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "👤 1-to-1 Friend (DM)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedType == RecipientType.INDIVIDUAL) Color.White else (if (isDark) DarkTextSecondary else LightTextSecondary)
                        )
                    }
                }

                Text(
                    text = if (selectedType == RecipientType.GROUP) "Group name(s) (comma separated for multiple):"
                           else "Friend name(s) (comma separated for multiple):",
                    fontSize = 11.sp,
                    color = if (isDark) DarkTextSecondary else LightTextSecondary
                )

                OutlinedTextField(
                    value = recipientName,
                    onValueChange = { recipientName = it },
                    placeholder = { Text(if (selectedType == RecipientType.GROUP) "e.g. Study Group, Project Alpha, VIP" else "e.g. Rahul, Sneha, Mohit") },
                    shape = RoundedCornerShape(10.dp),
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                // Category selector
                Text("Save in Category / List:", fontSize = 11.sp, color = if (isDark) DarkTextSecondary else LightTextSecondary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categories) { cat ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (selectedCategory == cat) BrandEmerald.copy(alpha = 0.15f) else (if (isDark) DarkInputBg else LightInputBg),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (selectedCategory == cat) BrandEmerald else Color.Transparent),
                            modifier = Modifier.clickable { selectedCategory = cat }
                        ) {
                            Text(cat, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = if (selectedCategory == cat) BrandEmerald else (if (isDark) DarkTextSecondary else LightTextSecondary), modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (recipientName.isNotBlank()) {
                        val count = AutomationManager.addMultipleRecipients(recipientName.trim(), selectedCategory, selectedType, context)
                        Toast.makeText(context, "Saved $count target(s) to $selectedCategory!", Toast.LENGTH_SHORT).show()
                    }
                    if (captionInput.isNotBlank()) {
                        AutomationManager.captionText.value = captionInput.trim()
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
            ) {
                Text("Save Targets", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AddCategoryDialog(isDark: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var categoryName by remember { mutableStateOf("") }
    val presets = listOf("VIP Clients", "Study Group", "Marketing", "College", "Family")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Group / List", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Enter a name for this group / broadcast list:", fontSize = 12.sp, color = if (isDark) DarkTextSecondary else LightTextSecondary)
                OutlinedTextField(
                    value = categoryName,
                    onValueChange = { categoryName = it },
                    placeholder = { Text("e.g. VIP Clients, Sunday Team...") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Quick suggestions:", fontSize = 11.sp, color = if (isDark) DarkTextMuted else LightTextMuted)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(presets) { p ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isDark) DarkInputBg else LightInputBg,
                            modifier = Modifier.clickable { categoryName = p }
                        ) {
                            Text(p, fontSize = 10.sp, color = AccentSky, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (categoryName.isNotBlank()) {
                        AutomationManager.addCategory(categoryName.trim(), context)
                        Toast.makeText(context, "Created \"$categoryName\"!", Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
            ) {
                Text("Create List", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun MetricsDialog(isDark: Boolean, onDismiss: () -> Unit) {
    val stats by AutomationManager.stats.collectAsState()
    val history by AutomationManager.broadcastHistory.collectAsState()
    val timeFormat = remember { SimpleDateFormat("HH:mm - dd MMM", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delivery Analytics", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = if (isDark) DarkCard else LightCard)) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Sent", fontSize = 11.sp, color = if (isDark) DarkTextSecondary else LightTextSecondary)
                            Text("${stats.totalDispatched}", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AccentSky)
                        }
                    }
                    Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = if (isDark) DarkCard else LightCard)) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Success", fontSize = 11.sp, color = if (isDark) DarkTextSecondary else LightTextSecondary)
                            Text("${if (stats.totalDispatched > 0) (stats.totalSuccess * 100) / stats.totalDispatched else 100}%", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = BrandEmerald)
                        }
                    }
                    Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = if (isDark) DarkCard else LightCard)) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Saved", fontSize = 11.sp, color = if (isDark) DarkTextSecondary else LightTextSecondary)
                            Text("${String.format(Locale.US, "%.1f", stats.minutesSaved)}m", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AccentViolet)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text("Recent Activity:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))

                if (history.isEmpty()) {
                    Text("No logs yet.", fontSize = 12.sp, color = if (isDark) DarkTextMuted else LightTextMuted)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(history) { log ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isDark) DarkInputBg else LightInputBg)
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(log.groupName, fontWeight = FontWeight.Medium, fontSize = 12.sp, color = if (isDark) DarkTextPrimary else LightTextPrimary)
                                    Text(timeFormat.format(Date(log.timestamp)), fontSize = 10.sp, color = if (isDark) DarkTextMuted else LightTextMuted)
                                }
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BrandEmerald, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun TypeFilterChip(
    label: String,
    isSelected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) (if (isDark) DarkCard else LightCard) else Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(
            if (isSelected) 1.5.dp else 0.5.dp,
            if (isSelected) BrandEmerald else (if (isDark) DarkCardBorder else LightCardBorder)
        ),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) BrandEmerald else (if (isDark) DarkTextSecondary else LightTextSecondary),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
fun MoveToCategoryDialog(
    isDark: Boolean,
    categories: List<String>,
    targetItemName: String?,
    selectedCount: Int,
    onMove: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCat by remember { mutableStateOf(categories.firstOrNull() ?: "General") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (targetItemName != null) "Move \"$targetItemName\" to Folder"
                else "Move $selectedCount Chat(s) to Folder",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Select destination folder:",
                    fontSize = 12.sp,
                    color = if (isDark) DarkTextSecondary else LightTextSecondary
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(categories) { cat ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selectedCat == cat) BrandEmerald.copy(alpha = 0.15f) else (if (isDark) DarkInputBg else LightInputBg),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (selectedCat == cat) BrandEmerald else (if (isDark) DarkCardBorder else LightCardBorder)
                            ),
                            modifier = Modifier.fillMaxWidth().clickable { selectedCat = cat }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = if (selectedCat == cat) BrandEmerald else AccentSky,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        cat,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 13.sp,
                                        color = if (selectedCat == cat) BrandEmerald else (if (isDark) DarkTextPrimary else LightTextPrimary)
                                    )
                                }
                                if (selectedCat == cat) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = BrandEmerald, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onMove(selectedCat)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
            ) {
                Text("Move Now", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SyncChooserDialog(
    isDark: Boolean,
    categories: List<String>,
    activeCategory: String,
    onStartSync: (useBusiness: Boolean, targetCategory: String) -> Unit,
    onDismiss: () -> Unit
) {
    var useBusiness by remember { mutableStateOf(false) }
    var chosenCat by remember { mutableStateOf(if (activeCategory == "All") "⚡ Auto-Sort (Smart)" else activeCategory) }
    val displayCategories = remember(categories) {
        listOf("⚡ Auto-Sort (Smart)") + categories
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Download, contentDescription = null, tint = BrandEmerald, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Pick & Sync from WhatsApp", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "AutoRelay will open WhatsApp for 3 seconds, scan your real groups & contacts (0 messages sent), and bring them into your list!",
                    fontSize = 12.sp,
                    color = if (isDark) DarkTextSecondary else LightTextSecondary
                )

                Text("1. Choose WhatsApp Version:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (isDark) DarkTextPrimary else LightTextPrimary)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (!useBusiness) BrandEmerald.copy(alpha = 0.15f) else (if (isDark) DarkInputBg else LightInputBg),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (!useBusiness) BrandEmerald else (if (isDark) DarkCardBorder else LightCardBorder)),
                        modifier = Modifier.weight(1f).clickable { useBusiness = false }
                    ) {
                        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Chat, contentDescription = null, tint = if (!useBusiness) BrandEmerald else (if (isDark) DarkTextMuted else LightTextMuted), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Regular WhatsApp", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (!useBusiness) BrandEmerald else (if (isDark) DarkTextPrimary else LightTextPrimary))
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (useBusiness) BrandEmerald.copy(alpha = 0.15f) else (if (isDark) DarkInputBg else LightInputBg),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (useBusiness) BrandEmerald else (if (isDark) DarkCardBorder else LightCardBorder)),
                        modifier = Modifier.weight(1f).clickable { useBusiness = true }
                    ) {
                        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Business, contentDescription = null, tint = if (useBusiness) BrandEmerald else (if (isDark) DarkTextMuted else LightTextMuted), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("WhatsApp Business", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (useBusiness) BrandEmerald else (if (isDark) DarkTextPrimary else LightTextPrimary))
                        }
                    }
                }

                Text("2. Save Imported Chats Into Folder:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if (isDark) DarkTextPrimary else LightTextPrimary)

                Text(
                    "💡 Auto-Sort detects Study groups, Business clients, & Friends automatically!",
                    fontSize = 11.sp,
                    color = BrandEmerald,
                    fontWeight = FontWeight.Medium
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(displayCategories) { cat ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (chosenCat == cat) BrandEmerald.copy(alpha = 0.15f) else (if (isDark) DarkInputBg else LightInputBg),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (chosenCat == cat) BrandEmerald else Color.Transparent),
                            modifier = Modifier.clickable { chosenCat = cat }
                        ) {
                            Text(
                                cat,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (chosenCat == cat) BrandEmerald else (if (isDark) DarkTextSecondary else LightTextSecondary),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalCat = if (chosenCat.startsWith("⚡") || chosenCat.startsWith("Auto")) "All" else chosenCat
                    onStartSync(useBusiness, finalCat)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
            ) {
                Text("Start Import", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun getFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) result = cursor.getString(index)
            }
        }
    }
    return result ?: uri.lastPathSegment
}

private fun checkAccessibilityService(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
    val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
    return enabledServices.any { it.resolveInfo.serviceInfo.packageName == context.packageName }
}

@Composable
fun ScheduleBroadcastDialog(
    isDark: Boolean,
    categories: List<String>,
    activeCategory: String,
    onSchedule: (triggerMillis: Long, targetCategory: String, repeatDaily: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMinutesFromNow by remember { mutableStateOf(15) }
    var selectedCategory by remember { mutableStateOf(if (activeCategory == "All") "All" else activeCategory) }
    var repeatDaily by remember { mutableStateOf(false) }

    val presets = listOf(
        Pair("⏱️ In 5 Mins", 5),
        Pair("⏱️ In 15 Mins", 15),
        Pair("⏱️ In 30 Mins", 30),
        Pair("⏰ In 1 Hour", 60),
        Pair("⏰ In 2 Hours", 120),
        Pair("🌅 Tomorrow 9 AM", -1)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Schedule Broadcast Timer", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Choose when AutoRelay should automatically dispatch messages to your WhatsApp chats:",
                    fontSize = 12.sp,
                    color = if (isDark) DarkTextSecondary else LightTextSecondary
                )

                // Presets
                Text("Quick Timing Presets:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isDark) DarkTextPrimary else LightTextPrimary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(presets) { preset ->
                        val isSelected = selectedMinutesFromNow == preset.second
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) AccentAmber else (if (isDark) DarkInputBg else LightInputBg),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) AccentAmber else (if (isDark) DarkCardBorder else LightCardBorder)),
                            modifier = Modifier.clickable { selectedMinutesFromNow = preset.second }
                        ) {
                            Text(
                                preset.first,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else (if (isDark) DarkTextPrimary else LightTextPrimary),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Target Folder
                Text("Target Broadcast Folder:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (isDark) DarkTextPrimary else LightTextPrimary)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val allFolders = listOf("All") + categories
                    items(allFolders) { folder ->
                        val isSel = selectedCategory == folder
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSel) BrandEmerald else (if (isDark) DarkInputBg else LightInputBg),
                            modifier = Modifier.clickable { selectedCategory = folder }
                        ) {
                            Text(
                                folder,
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSel) Color.White else (if (isDark) DarkTextSecondary else LightTextSecondary),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                // Repeat Daily Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isDark) DarkInputBg else LightInputBg)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Repeat Daily", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isDark) DarkTextPrimary else LightTextPrimary)
                        Text("Auto-dispatch at the same time every day", fontSize = 10.sp, color = if (isDark) DarkTextSecondary else LightTextSecondary)
                    }
                    Switch(
                        checked = repeatDaily,
                        onCheckedChange = { repeatDaily = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = BrandEmerald, checkedTrackColor = BrandEmerald.copy(alpha = 0.5f))
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentAmber.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "ℹ️ Android alarm will wake up AutoRelay and dispatch the broadcast even if your phone screen is off.",
                        fontSize = 10.sp,
                        color = AccentAmber,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val now = System.currentTimeMillis()
                    val targetMillis = if (selectedMinutesFromNow == -1) {
                        val cal = Calendar.getInstance().apply {
                            add(Calendar.DAY_OF_YEAR, 1)
                            set(Calendar.HOUR_OF_DAY, 9)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                        }
                        cal.timeInMillis
                    } else {
                        now + (selectedMinutesFromNow * 60 * 1000L)
                    }
                    onSchedule(targetMillis, selectedCategory, repeatDaily)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentAmber)
            ) {
                Text("Set Schedule", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun TemplatesDialog(
    isDark: Boolean,
    onSelectTemplate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val templates = AutomationManager.defaultTemplates

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Description, contentDescription = null, tint = BrandEmerald, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Message Templates", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(templates) { t ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = if (isDark) DarkInputBg else LightInputBg),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectTemplate(t.text)
                                onDismiss()
                            }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(t.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (isDark) DarkTextPrimary else LightTextPrimary)
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = BrandEmerald.copy(alpha = 0.15f),
                                    modifier = Modifier.padding(4.dp)
                                ) {
                                    Text(t.category, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = BrandEmerald, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(t.text, fontSize = 11.sp, color = if (isDark) DarkTextSecondary else LightTextSecondary, maxLines = 3)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("👉 Tap to use", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = BrandEmerald)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun SafetySettingsDialog(
    isDark: Boolean,
    currentMin: Int,
    currentMax: Int,
    onSelectMode: (SafetyMode) -> Unit,
    onDismiss: () -> Unit
) {
    val modes = listOf(
        SafetyMode.FAST,
        SafetyMode.BALANCED,
        SafetyMode.ULTRA_SAFE
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Shield, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Anti-Ban Safety Controls", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Adjust human-like pauses between broadcasts to protect your WhatsApp account from being flagged:",
                    fontSize = 12.sp,
                    color = if (isDark) DarkTextSecondary else LightTextSecondary
                )

                modes.forEach { mode ->
                    val isCurrent = currentMin == mode.minSec && currentMax == mode.maxSec
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrent) BrandEmerald.copy(alpha = 0.12f) else (if (isDark) DarkInputBg else LightInputBg)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, if (isCurrent) BrandEmerald else Color.Transparent, RoundedCornerShape(10.dp))
                            .clickable {
                                onSelectMode(mode)
                                onDismiss()
                            }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(mode.label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (isDark) DarkTextPrimary else LightTextPrimary)
                                val desc = when (mode) {
                                    SafetyMode.FAST -> "Best for sending to small friend circles & <10 groups"
                                    SafetyMode.BALANCED -> "Recommended: Perfect mix of speed and anti-ban safety"
                                    SafetyMode.ULTRA_SAFE -> "Maximum protection for 50+ business chats and unknown contacts"
                                }
                                Text(desc, fontSize = 11.sp, color = if (isDark) DarkTextSecondary else LightTextSecondary)
                            }
                            if (isCurrent) {
                                Icon(Icons.Default.Check, contentDescription = "Active", tint = BrandEmerald, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun CsvImportDialog(
    isDark: Boolean,
    activeCategory: String,
    onPickFile: () -> Unit,
    onPasteImport: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pastedText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DriveFolderUpload, contentDescription = null, tint = BrandEmerald, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Bulk Import Contacts", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Import 100+ contacts directly into \"$activeCategory\" from a file or text:",
                    fontSize = 12.sp,
                    color = if (isDark) DarkTextSecondary else LightTextSecondary
                )

                Button(
                    onClick = {
                        onDismiss()
                        onPickFile()
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select .CSV / .TXT / .VCF File", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = if (isDark) DarkCardBorder else LightCardBorder)
                    Text("  OR PASTE LIST  ", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isDark) DarkTextMuted else LightTextMuted)
                    HorizontalDivider(modifier = Modifier.weight(1f), color = if (isDark) DarkCardBorder else LightCardBorder)
                }

                OutlinedTextField(
                    value = pastedText,
                    onValueChange = { pastedText = it },
                    placeholder = {
                        Text("Paste contacts or phone numbers:\nHarsh Pandey, Study\n+91 98765 43210\nSneha, Business", fontSize = 11.sp)
                    },
                    minLines = 4,
                    maxLines = 6,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pastedText.isNotBlank()) {
                        onPasteImport(pastedText)
                    }
                    onDismiss()
                },
                enabled = pastedText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
            ) {
                Text("Import Contacts", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
