package com.autoposter.whatsapp.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AutomationManager.loadPersistence(this)

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

    val activeFilter by AutomationManager.activeCategoryFilter.collectAsState()
    val categories by AutomationManager.categories.collectAsState()
    val recipients = AutomationManager.getFilteredRecipients()
    val mediaUri by AutomationManager.selectedMediaUri.collectAsState()
    val mediaName by AutomationManager.mediaFileName.collectAsState()
    val caption by AutomationManager.captionText.collectAsState()
    val status by AutomationManager.status.collectAsState()
    val minDelay by AutomationManager.minDelaySeconds.collectAsState()
    val maxDelay by AutomationManager.maxDelaySeconds.collectAsState()

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
                            Text("Active WhatsApp Link • Ready", fontSize = 11.sp, color = if (isDark) DarkTextSecondary else LightTextSecondary)
                        }
                    }
                },
                actions = {
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

            // 1. Clean Category Pills Bar (All, Business, Study, Friends, + Add)
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
                            count = AutomationManager.targetRecipients.value.size,
                            isSelected = activeFilter == "All",
                            isDark = isDark,
                            onClick = { AutomationManager.activeCategoryFilter.value = "All" }
                        )
                    }
                    items(categories) { cat ->
                        val count = AutomationManager.targetRecipients.value.count { it.category == cat }
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
                                Text("New Folder", fontSize = 11.sp, color = AccentSky, fontWeight = FontWeight.SemiBold)
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (activeFilter == "All") "All Target Chats (${recipients.size})" else "$activeFilter List (${recipients.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (isDark) DarkTextPrimary else LightTextPrimary
                        )
                        if (recipients.isNotEmpty()) {
                            Text(
                                "Clear",
                                fontSize = 12.sp,
                                color = AccentRose,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable { AutomationManager.clearActiveCategoryRecipients() }
                            )
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
                                modifier = Modifier.fillMaxWidth().padding(26.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background((if (isDark) DarkInputBg else LightInputBg)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Outlined.GroupAdd, contentDescription = null, tint = BrandEmerald, modifier = Modifier.size(24.dp))
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    "No groups or friends added yet",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = if (isDark) DarkTextPrimary else LightTextPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Tap the + button below to add WhatsApp groups or 1-to-1 friends to this folder.",
                                    fontSize = 12.sp,
                                    color = if (isDark) DarkTextSecondary else LightTextSecondary,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { showAddRecipientModal = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add Recipient", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                } else {
                    // Recipient Items
                    items(recipients) { recipient ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = if (isDark) DarkCard else LightCard),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(elevation = if (isDark) 0.dp else 1.dp, shape = RoundedCornerShape(14.dp))
                                .border(0.5.dp, if (isDark) DarkCardBorder else LightCardBorder, RoundedCornerShape(14.dp))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (recipient.type == RecipientType.INDIVIDUAL) AccentSky.copy(alpha = 0.15f)
                                                else BrandEmerald.copy(alpha = 0.15f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (recipient.type == RecipientType.INDIVIDUAL) Icons.Default.Person else Icons.Default.Groups,
                                            contentDescription = null,
                                            tint = if (recipient.type == RecipientType.INDIVIDUAL) AccentSky else BrandEmerald,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(recipient.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (isDark) DarkTextPrimary else LightTextPrimary)
                                        Text(
                                            text = if (recipient.type == RecipientType.INDIVIDUAL) "1-to-1 Private Chat • Separate DM" else "WhatsApp Group Chat",
                                            fontSize = 11.sp,
                                            color = if (isDark) DarkTextSecondary else LightTextSecondary
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { AutomationManager.removeRecipient(recipient.name) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = if (isDark) DarkTextMuted else LightTextMuted, modifier = Modifier.size(16.dp))
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

                    // 2 Main Actions: 1-Tap Pick in WhatsApp & Auto-Loop
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Button A: Direct 1-Tap WhatsApp Picker
                        Button(
                            onClick = { AutomationManager.openDirectWhatsAppPicker(context) },
                            modifier = Modifier.weight(1.2f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pick in WhatsApp", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                        }

                        // Button B: Auto-Loop to active folder
                        OutlinedButton(
                            onClick = {
                                if (!isAccessibilityEnabled) {
                                    Toast.makeText(context, "Please enable Accessibility permission first!", Toast.LENGTH_SHORT).show()
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
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = BrandEmerald, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Auto-Send (${recipients.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BrandEmerald)
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
                Text("Compose & Add", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = if (isDark) DarkTextPrimary else LightTextPrimary)
                Spacer(modifier = Modifier.height(14.dp))

                ActionSheetItem(
                    icon = Icons.Outlined.AddPhotoAlternate,
                    title = "Attach Photo / Video",
                    subtitle = "Pick media from phone gallery",
                    tint = BrandEmerald,
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
                    tint = AccentSky,
                    isDark = isDark,
                    onClick = {
                        showActionSheet = false
                        docPicker.launch("*/*")
                    }
                )

                ActionSheetItem(
                    icon = Icons.Outlined.Chat,
                    title = "Write Caption / Message",
                    subtitle = "Attach a custom text with your broadcast",
                    tint = AccentViolet,
                    isDark = isDark,
                    onClick = {
                        showActionSheet = false
                        showAddRecipientModal = true
                    }
                )

                ActionSheetItem(
                    icon = Icons.Outlined.GroupAdd,
                    title = "Add Target Recipient",
                    subtitle = "Add WhatsApp Group or 1-to-1 Friend",
                    tint = AccentAmber,
                    isDark = isDark,
                    onClick = {
                        showActionSheet = false
                        showAddRecipientModal = true
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
    onDismiss: () -> Unit
) {
    var recipientName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(RecipientType.GROUP) }
    var selectedCategory by remember { mutableStateOf(if (activeCategory == "All") "Business" else activeCategory) }
    var captionInput by remember { mutableStateOf(AutomationManager.captionText.value) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Recipient & Message", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                            .padding(vertical = 8.dp),
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
                            .padding(vertical = 8.dp),
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
                    text = if (selectedType == RecipientType.GROUP) "Enter the exact WhatsApp group name:"
                           else "Enter friend's exact WhatsApp name (sent privately 1-on-1):",
                    fontSize = 12.sp,
                    color = if (isDark) DarkTextSecondary else LightTextSecondary
                )

                OutlinedTextField(
                    value = recipientName,
                    onValueChange = { recipientName = it },
                    placeholder = { Text(if (selectedType == RecipientType.GROUP) "e.g. Study Group / Project Alpha" else "e.g. Rahul Sharma") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Category selector
                Text("Select Folder / Category:", fontSize = 12.sp, color = if (isDark) DarkTextSecondary else LightTextSecondary)
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

                // Optional caption edit
                Text("Caption / Message (Optional):", fontSize = 12.sp, color = if (isDark) DarkTextSecondary else LightTextSecondary)
                OutlinedTextField(
                    value = captionInput,
                    onValueChange = { captionInput = it },
                    placeholder = { Text("Message attached with media...") },
                    shape = RoundedCornerShape(10.dp),
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (recipientName.isNotBlank()) {
                        AutomationManager.addRecipient(recipientName.trim(), selectedCategory, selectedType)
                    }
                    AutomationManager.captionText.value = captionInput.trim()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
            ) {
                Text("Save", fontWeight = FontWeight.Bold, color = Color.White)
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
    var categoryName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Folder", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column {
                Text("Enter folder name (e.g. VIP, Study, Work):", fontSize = 12.sp, color = if (isDark) DarkTextSecondary else LightTextSecondary)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = categoryName,
                    onValueChange = { categoryName = it },
                    placeholder = { Text("Folder name...") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (categoryName.isNotBlank()) {
                        AutomationManager.addCategory(categoryName.trim())
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = AccentSky)
            ) {
                Text("Create", fontWeight = FontWeight.Bold, color = Color.White)
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
