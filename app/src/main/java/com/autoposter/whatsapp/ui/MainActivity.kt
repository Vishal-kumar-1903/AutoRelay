package com.autoposter.whatsapp.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import com.autoposter.whatsapp.data.AutomationStep
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
                    AutoRelayMainDashboard()
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
fun AutoRelayMainDashboard() {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var isAccessibilityEnabled by remember { mutableStateOf(checkAccessibilityService(context)) }
    val isDark by AutomationManager.isDarkMode.collectAsState()

    var showEditProfileDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isAccessibilityEnabled = checkAccessibilityService(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Brush.linearGradient(listOf(BrandEmerald, BrandTealDark))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "AutoRelay",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    color = if (isDark) DarkTextPrimary else LightTextPrimary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(BrandEmerald.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("PRO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = BrandEmerald)
                                }
                            }
                            Text(
                                text = "WhatsApp Marketing Suite",
                                fontSize = 11.sp,
                                color = if (isDark) DarkTextSecondary else LightTextSecondary
                            )
                        }
                    }
                },
                actions = {
                    // Quick Light / Dark Mode Toggle Button
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
        bottomBar = {
            NavigationBar(
                containerColor = if (isDark) DarkSurface else LightSurface,
                tonalElevation = 2.dp,
                modifier = Modifier.border(
                    width = 0.5.dp,
                    color = if (isDark) DarkCardBorder else LightCardBorder,
                    shape = RoundedCornerShape(0.dp)
                )
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(if (selectedTab == 0) Icons.Default.FlashOn else Icons.Outlined.FlashOn, contentDescription = "Broadcast") },
                    label = { Text("Broadcast", fontSize = 11.sp, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = BrandEmerald,
                        selectedTextColor = BrandEmerald,
                        unselectedIconColor = if (isDark) DarkTextMuted else LightTextMuted,
                        unselectedTextColor = if (isDark) DarkTextMuted else LightTextMuted,
                        indicatorColor = BrandEmerald.copy(alpha = 0.12f)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(if (selectedTab == 1) Icons.Default.Folder else Icons.Outlined.Folder, contentDescription = "Folders") },
                    label = { Text("Folders", fontSize = 11.sp, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentSky,
                        selectedTextColor = AccentSky,
                        unselectedIconColor = if (isDark) DarkTextMuted else LightTextMuted,
                        unselectedTextColor = if (isDark) DarkTextMuted else LightTextMuted,
                        indicatorColor = AccentSky.copy(alpha = 0.12f)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(if (selectedTab == 2) Icons.Default.BarChart else Icons.Outlined.BarChart, contentDescription = "Metrics") },
                    label = { Text("Analytics", fontSize = 11.sp, fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentViolet,
                        selectedTextColor = AccentViolet,
                        unselectedIconColor = if (isDark) DarkTextMuted else LightTextMuted,
                        unselectedTextColor = if (isDark) DarkTextMuted else LightTextMuted,
                        indicatorColor = AccentViolet.copy(alpha = 0.12f)
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(if (isDark) DarkBg else LightBg)
        ) {
            when (selectedTab) {
                0 -> BroadcastMainScreen(
                    context = context,
                    isDark = isDark,
                    isAccessibilityEnabled = isAccessibilityEnabled,
                    onOpenProfileDialog = { showEditProfileDialog = true },
                    onRefreshPermission = { isAccessibilityEnabled = checkAccessibilityService(context) }
                )
                1 -> FoldersMainScreen(isDark = isDark)
                2 -> MetricsMainScreen(isDark = isDark)
            }
        }
    }

    if (showEditProfileDialog) {
        EditProfileModal(
            isDark = isDark,
            onDismiss = { showEditProfileDialog = false }
        )
    }
}

@Composable
fun BroadcastMainScreen(
    context: Context,
    isDark: Boolean,
    isAccessibilityEnabled: Boolean,
    onOpenProfileDialog: () -> Unit,
    onRefreshPermission: () -> Unit
) {
    val groups = AutomationManager.getFilteredGroups()
    val mediaUri by AutomationManager.selectedMediaUri.collectAsState()
    val caption by AutomationManager.captionText.collectAsState()
    val status by AutomationManager.status.collectAsState()
    val minDelay by AutomationManager.minDelaySeconds.collectAsState()
    val maxDelay by AutomationManager.maxDelaySeconds.collectAsState()
    val categories by AutomationManager.categories.collectAsState()
    val activeFilter by AutomationManager.activeCategoryFilter.collectAsState()
    val profileName by AutomationManager.userProfileName.collectAsState()

    var newGroupName by remember { mutableStateOf("") }

    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            AutomationManager.selectedMediaUri.value = uri
            val mimeType = context.contentResolver.getType(uri) ?: "image/*"
            AutomationManager.mediaMimeType.value = mimeType
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
    ) {

        // 1. Client-Facing Connected WhatsApp Profile Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = if (isDark) DarkCard else LightCard),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = if (isDark) 0.dp else 2.dp, shape = RoundedCornerShape(16.dp))
                    .border(0.5.dp, if (isDark) DarkCardBorder else LightCardBorder, RoundedCornerShape(16.dp))
                    .clickable { onOpenProfileDialog() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(BrandEmerald.copy(alpha = 0.15f))
                            .border(1.5.dp, BrandEmerald, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = profileName.take(2).uppercase(),
                            fontWeight = FontWeight.ExtraBold,
                            color = BrandEmerald,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = profileName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = if (isDark) DarkTextPrimary else LightTextPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Verified",
                                tint = BrandEmerald,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(BrandEmerald)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "WhatsApp Active • Ready to Broadcast",
                                fontSize = 11.sp,
                                color = if (isDark) DarkTextSecondary else LightTextSecondary
                            )
                        }
                    }

                    IconButton(onClick = onOpenProfileDialog, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = "Edit Profile",
                            tint = if (isDark) DarkTextMuted else LightTextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // 2. Permission Banner (If disabled)
        if (!isAccessibilityEnabled) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = if (isDark) DarkCard else LightCard),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.border(1.dp, AccentAmber.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = AccentAmber, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Accessibility Permission Needed", fontWeight = FontWeight.Bold, color = AccentAmber, fontSize = 13.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Enable AutoRelay under Downloaded apps to allow automatic group message sending.",
                            fontSize = 12.sp,
                            color = if (isDark) DarkTextSecondary else LightTextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                context.startActivity(intent)
                                onRefreshPermission()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Enable Now", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // 3. Media Selector Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = if (isDark) DarkCard else LightCard),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = if (isDark) 0.dp else 1.5.dp, shape = RoundedCornerShape(18.dp))
                    .border(0.5.dp, if (isDark) DarkCardBorder else LightCardBorder, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Attached Media",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (isDark) DarkTextPrimary else LightTextPrimary
                        )
                        if (mediaUri != null) {
                            Text(
                                "Remove",
                                fontSize = 12.sp,
                                color = AccentRose,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable { AutomationManager.selectedMediaUri.value = null }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (mediaUri != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isDark) DarkInputBg else LightInputBg)
                        ) {
                            AsyncImage(
                                model = mediaUri,
                                contentDescription = "Media preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(95.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isDark) DarkInputBg else LightInputBg)
                                .border(1.dp, if (isDark) DarkCardBorder else LightCardBorder, RoundedCornerShape(14.dp))
                                .clickable { mediaPickerLauncher.launch("*/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(BrandEmerald.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null, tint = BrandEmerald, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Pick Photo, Video or Sticker", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = if (isDark) DarkTextPrimary else LightTextPrimary)
                                    Text("Tap to select from phone gallery", fontSize = 11.sp, color = if (isDark) DarkTextMuted else LightTextMuted)
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Caption / Message Input
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = if (isDark) DarkCard else LightCard),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = if (isDark) 0.dp else 1.5.dp, shape = RoundedCornerShape(18.dp))
                    .border(0.5.dp, if (isDark) DarkCardBorder else LightCardBorder, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Caption / Message (Optional)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = if (isDark) DarkTextPrimary else LightTextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = caption,
                        onValueChange = { AutomationManager.captionText.value = it },
                        placeholder = { Text("Write your message to deliver with media...", fontSize = 13.sp, color = if (isDark) DarkTextMuted else LightTextMuted) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isDark) DarkInputBg else LightInputBg, RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandEmerald.copy(alpha = 0.5f),
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = if (isDark) DarkTextPrimary else LightTextPrimary,
                            unfocusedTextColor = if (isDark) DarkTextPrimary else LightTextPrimary
                        ),
                        minLines = 2,
                        maxLines = 4
                    )
                }
            }
        }

        // 5. ⭐ PRIMARY FEATURE CARD: 1-Tap Direct WhatsApp Multi-Picker & Send
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = if (isDark) DarkCard else LightCard),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = if (isDark) 0.dp else 2.dp, shape = RoundedCornerShape(18.dp))
                    .border(1.dp, BrandEmerald.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(BrandEmerald.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, tint = BrandEmerald, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Send via WhatsApp", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (isDark) DarkTextPrimary else LightTextPrimary)
                            Text("Pick any groups or people directly in WhatsApp", fontSize = 11.sp, color = if (isDark) DarkTextSecondary else LightTextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { AutomationManager.openDirectWhatsAppPicker(context) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Open WhatsApp & Select Groups",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }

        // 6. Automated Queue Manager (Optional Multi-Loop)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = if (isDark) DarkCard else LightCard),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = if (isDark) 0.dp else 1.5.dp, shape = RoundedCornerShape(18.dp))
                    .border(0.5.dp, if (isDark) DarkCardBorder else LightCardBorder, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Scheduled Queue", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (isDark) DarkTextPrimary else LightTextPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(AccentSky.copy(alpha = 0.15f))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text("${groups.size}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentSky)
                            }
                        }
                        if (groups.isNotEmpty()) {
                            Text(
                                "Clear",
                                fontSize = 12.sp,
                                color = AccentRose,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable { AutomationManager.clearGroups() }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Queue group names to loop through them autonomously with anti-ban delay.",
                        fontSize = 11.sp,
                        color = if (isDark) DarkTextMuted else LightTextMuted
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Folder Pills
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            LightFolderChip(
                                label = "All",
                                isDark = isDark,
                                isSelected = activeFilter == "All",
                                onClick = { AutomationManager.activeCategoryFilter.value = "All" }
                            )
                        }
                        items(categories) { cat ->
                            LightFolderChip(
                                label = cat,
                                isDark = isDark,
                                isSelected = activeFilter == cat,
                                onClick = { AutomationManager.activeCategoryFilter.value = cat }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Input group name
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newGroupName,
                            onValueChange = { newGroupName = it },
                            placeholder = { Text("Group name...", fontSize = 13.sp, color = if (isDark) DarkTextMuted else LightTextMuted) },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .background(if (isDark) DarkInputBg else LightInputBg, RoundedCornerShape(12.dp)),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentSky.copy(alpha = 0.5f),
                                unfocusedBorderColor = Color.Transparent,
                                focusedTextColor = if (isDark) DarkTextPrimary else LightTextPrimary,
                                unfocusedTextColor = if (isDark) DarkTextPrimary else LightTextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newGroupName.isNotBlank()) {
                                    val cat = if (activeFilter != "All") activeFilter else "General"
                                    AutomationManager.addGroup(newGroupName, cat)
                                    newGroupName = ""
                                } else {
                                    Toast.makeText(context, "Enter a group name first!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentSky),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text("Add", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    if (groups.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(groups) { item ->
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isDark) DarkInputBg else LightInputBg,
                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, if (isDark) DarkCardBorder else LightCardBorder)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(item.name, fontSize = 12.sp, color = if (isDark) DarkTextPrimary else LightTextPrimary)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove",
                                            tint = if (isDark) DarkTextMuted else LightTextMuted,
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clickable { AutomationManager.removeGroup(item.name) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 7. Anti-Ban Timing
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = if (isDark) DarkCard else LightCard),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = if (isDark) 0.dp else 1.5.dp, shape = RoundedCornerShape(18.dp))
                    .border(0.5.dp, if (isDark) DarkCardBorder else LightCardBorder, RoundedCornerShape(18.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Shield, contentDescription = null, tint = BrandEmerald, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Anti-Ban Timing", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (isDark) DarkTextPrimary else LightTextPrimary)
                        Spacer(modifier = Modifier.weight(1f))
                        Text("${minDelay}s - ${maxDelay}s", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = BrandEmerald)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Randomized delay between sends simulates human pacing.",
                        fontSize = 12.sp,
                        color = if (isDark) DarkTextSecondary else LightTextSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Slider(
                        value = minDelay.toFloat(),
                        onValueChange = {
                            AutomationManager.minDelaySeconds.value = it.toInt()
                            AutomationManager.maxDelaySeconds.value = (it.toInt() + 6).coerceAtMost(30)
                        },
                        valueRange = 5f..25f,
                        steps = 4,
                        colors = SliderDefaults.colors(
                            thumbColor = BrandEmerald,
                            activeTrackColor = BrandEmerald,
                            inactiveTrackColor = if (isDark) DarkInputBg else LightInputBg
                        )
                    )
                }
            }
        }

    }
}

@Composable
fun LightFolderChip(label: String, isDark: Boolean, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) BrandEmerald.copy(alpha = 0.15f) else (if (isDark) DarkInputBg else LightInputBg),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            if (isSelected) BrandEmerald.copy(alpha = 0.6f) else (if (isDark) DarkCardBorder else LightCardBorder)
        ),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) BrandEmerald else (if (isDark) DarkTextSecondary else LightTextSecondary),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun EditProfileModal(isDark: Boolean, onDismiss: () -> Unit) {
    var nameInput by remember { mutableStateOf(AutomationManager.userProfileName.value) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Client / Business Profile", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Display your or your client's name on the top dashboard card:",
                    fontSize = 13.sp,
                    color = if (isDark) DarkTextSecondary else LightTextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    placeholder = { Text("e.g. Vishal Kumar Juni / Acme Media") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nameInput.isNotBlank()) {
                        AutomationManager.userProfileName.value = nameInput.trim()
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
            ) {
                Text("Save Profile", color = Color.White, fontWeight = FontWeight.Bold)
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
fun FoldersMainScreen(isDark: Boolean) {
    val categories by AutomationManager.categories.collectAsState()
    val allGroups by AutomationManager.targetGroups.collectAsState()
    var newCategoryName by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Folders & Presets", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = if (isDark) DarkTextPrimary else LightTextPrimary)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Group contacts into targeted sets for 1-click broadcast.", fontSize = 13.sp, color = if (isDark) DarkTextSecondary else LightTextSecondary)
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newCategoryName,
                onValueChange = { newCategoryName = it },
                placeholder = { Text("New folder name...", fontSize = 13.sp, color = if (isDark) DarkTextMuted else LightTextMuted) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .background(if (isDark) DarkInputBg else LightInputBg, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentSky.copy(alpha = 0.5f),
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = if (isDark) DarkTextPrimary else LightTextPrimary,
                    unfocusedTextColor = if (isDark) DarkTextPrimary else LightTextPrimary
                )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (newCategoryName.isNotBlank()) {
                        AutomationManager.addCategory(newCategoryName)
                        newCategoryName = ""
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentSky),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text("Create", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(categories) { category ->
                val count = allGroups.count { it.category == category }
                Card(
                    colors = CardDefaults.cardColors(containerColor = if (isDark) DarkCard else LightCard),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(elevation = if (isDark) 0.dp else 1.dp, shape = RoundedCornerShape(14.dp))
                        .border(0.5.dp, if (isDark) DarkCardBorder else LightCardBorder, RoundedCornerShape(14.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Folder, contentDescription = null, tint = AccentSky, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(category, fontWeight = FontWeight.SemiBold, color = if (isDark) DarkTextPrimary else LightTextPrimary, fontSize = 14.sp)
                        }
                        Text("$count groups", color = if (isDark) DarkTextSecondary else LightTextSecondary, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun MetricsMainScreen(isDark: Boolean) {
    val stats by AutomationManager.stats.collectAsState()
    val history by AutomationManager.broadcastHistory.collectAsState()
    val timeFormat = remember { SimpleDateFormat("HH:mm - dd MMM", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Performance Metrics", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = if (isDark) DarkTextPrimary else LightTextPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Delivery tracking and time optimization overview.", fontSize = 13.sp, color = if (isDark) DarkTextSecondary else LightTextSecondary)
        }

        // Metrics Grid
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LightMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Dispatched",
                    value = stats.totalDispatched.toString(),
                    color = AccentSky,
                    isDark = isDark
                )
                LightMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Success Rate",
                    value = if (stats.totalDispatched > 0) "${(stats.totalSuccess * 100) / stats.totalDispatched}%" else "100%",
                    color = BrandEmerald,
                    isDark = isDark
                )
                LightMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Time Saved",
                    value = "${String.format(Locale.US, "%.1f", stats.minutesSaved)}m",
                    color = AccentViolet,
                    isDark = isDark
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text("Activity History", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (isDark) DarkTextPrimary else LightTextPrimary)
        }

        if (history.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = if (isDark) DarkCard else LightCard),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(elevation = if (isDark) 0.dp else 1.dp, shape = RoundedCornerShape(14.dp))
                        .border(0.5.dp, if (isDark) DarkCardBorder else LightCardBorder, RoundedCornerShape(14.dp))
                ) {
                    Box(modifier = Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No broadcast activity logged yet.", fontSize = 12.sp, color = if (isDark) DarkTextMuted else LightTextMuted)
                    }
                }
            }
        } else {
            items(history) { record ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = if (isDark) DarkCard else LightCard),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(elevation = if (isDark) 0.dp else 1.dp, shape = RoundedCornerShape(14.dp))
                        .border(0.5.dp, if (isDark) DarkCardBorder else LightCardBorder, RoundedCornerShape(14.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(record.groupName, fontWeight = FontWeight.SemiBold, color = if (isDark) DarkTextPrimary else LightTextPrimary, fontSize = 13.sp)
                            if (record.captionPreview.isNotEmpty()) {
                                Text("\"${record.captionPreview}\"", fontSize = 11.sp, color = if (isDark) DarkTextSecondary else LightTextSecondary, maxLines = 1)
                            }
                            Text(timeFormat.format(Date(record.timestamp)), fontSize = 10.sp, color = if (isDark) DarkTextMuted else LightTextMuted)
                        }
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BrandEmerald, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun LightMetricCard(modifier: Modifier = Modifier, title: String, value: String, color: Color, isDark: Boolean) {
    Card(
        modifier = modifier
            .shadow(elevation = if (isDark) 0.dp else 1.5.dp, shape = RoundedCornerShape(14.dp))
            .border(0.5.dp, if (isDark) DarkCardBorder else LightCardBorder, RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = if (isDark) DarkCard else LightCard),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, fontSize = 11.sp, color = if (isDark) DarkTextSecondary else LightTextSecondary, maxLines = 1)
            Spacer(modifier = Modifier.height(6.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

private fun checkAccessibilityService(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
    val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
    return enabledServices.any { it.resolveInfo.serviceInfo.packageName == context.packageName }
}
