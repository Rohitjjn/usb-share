package com.example.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.client.RemoteFile
import com.example.viewmodel.MainViewModel
import com.example.viewmodel.TransferTask
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Custom premium monochrome light palette
val DeepBackground = Color(0xFFFFFFFF)  // Pure White Background
val SurfaceCard = Color(0xFFF5F5F7)     // Apple soft gray card background
val SurfaceBorder = Color(0xFFE5E5E7)   // Apple thin border gray
val AccentCyan = Color(0xFF000000)      // Pure Black accent
val AccentTeal = Color(0xFF000000)      // Pure Black accent
val StatusGreen = Color(0xFF34C759)     // Apple dynamic green
val StatusRed = Color(0xFFFF3B30)       // Apple dynamic red
val MutedText = Color(0xFF86868B)       // Apple muted secondary text
val PrimaryText = Color(0xFF1D1D1F)     // Apple charcoal primary text

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf(0) }

    // Binds state flows
    val isServerRunning by viewModel.isServerRunning.collectAsState()
    val serverIp by viewModel.serverIp.collectAsState()
    val serverPort by viewModel.serverPort.collectAsState()
    val localServerPassword by viewModel.localServerPassword.collectAsState()

    val activeTransfers by viewModel.activeTransfers.collectAsState()
    val runningTransfersCount = activeTransfers.count { it.status == "Running" }

    // Run permissions check on load
    var showPermissionDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.initLocalDirectory(context)
        viewModel.refreshInterfaces()
        
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val storageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
        
        if (!storageGranted || !notificationGranted) {
            showPermissionDialog = true
        }
    }

    if (showPermissionDialog) {
        PermissionDialog(onDismiss = { showPermissionDialog = false })
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = DeepBackground,
                tonalElevation = 8.dp,
                modifier = Modifier.shadow(16.dp)
            ) {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    icon = {
                        BadgedBox(badge = {
                            if (isServerRunning) {
                                Badge(containerColor = StatusGreen) {
                                    Box(modifier = Modifier.size(4.dp).background(StatusGreen, CircleShape))
                                }
                            }
                        }) {
                            Icon(Icons.Default.Dns, contentDescription = "Dashboard")
                        }
                    },
                    label = { Text("Dashboard", fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentCyan,
                        selectedTextColor = AccentCyan,
                        unselectedIconColor = MutedText,
                        unselectedTextColor = MutedText,
                        indicatorColor = SurfaceBorder
                    )
                )

                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    icon = { Icon(Icons.Default.Smartphone, contentDescription = "My Phone") },
                    label = { Text("My Phone", fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentCyan,
                        selectedTextColor = AccentCyan,
                        unselectedIconColor = MutedText,
                        unselectedTextColor = MutedText,
                        indicatorColor = SurfaceBorder
                    )
                )

                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    icon = { Icon(Icons.Default.Computer, contentDescription = "PC Files") },
                    label = { Text("PC Files", fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentCyan,
                        selectedTextColor = AccentCyan,
                        unselectedIconColor = MutedText,
                        unselectedTextColor = MutedText,
                        indicatorColor = SurfaceBorder
                    )
                )

                NavigationBarItem(
                    selected = currentTab == 3,
                    onClick = { currentTab = 3 },
                    icon = {
                        BadgedBox(badge = {
                            if (runningTransfersCount > 0) {
                                Badge(containerColor = AccentTeal) {
                                    Text(runningTransfersCount.toString(), color = Color.Black)
                                }
                            }
                        }) {
                            Icon(Icons.Default.SwapCalls, contentDescription = "Transfers")
                        }
                    },
                    label = { Text("Transfers", fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = AccentCyan,
                        selectedTextColor = AccentCyan,
                        unselectedIconColor = MutedText,
                        unselectedTextColor = MutedText,
                        indicatorColor = SurfaceBorder
                    )
                )
            }
        },
        containerColor = DeepBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DeepBackground)
        ) {
            when (currentTab) {
                0 -> DashboardScreen(viewModel)
                1 -> MyPhoneScreen(viewModel)
                2 -> PcFilesScreen(viewModel)
                3 -> TransfersScreen(viewModel)
            }
        }
    }
}

// --- Dashboard Screen ---
@Composable
fun DashboardScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val isServerRunning by viewModel.isServerRunning.collectAsState()
    val serverIp by viewModel.serverIp.collectAsState()
    val serverPort by viewModel.serverPort.collectAsState()
    val localServerPassword by viewModel.localServerPassword.collectAsState()
    
    var showWebdavInstructions by remember { mutableStateOf(false) }
    
    val networkInterfaces by viewModel.networkInterfaces.collectAsState()
    val detectedUsbIp by viewModel.detectedUsbIp.collectAsState()

    val glowColor by animateColorAsState(
        targetValue = if (isServerRunning) AccentCyan else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessLow)
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Hero Header
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                Text(
                    text = "Direct Share",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp,
                        color = PrimaryText
                    )
                )
                Text(
                    text = "Transfer files directly via USB cable",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
            }
        }

        // Host Status Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("host_status_card")
                    .shadow(4.dp, shape = RoundedCornerShape(16.dp))
                    .background(Color.White, shape = RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(if (isServerRunning) Color.Black else SurfaceCard, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isServerRunning) Icons.Default.SwapHoriz else Icons.Default.PortableWifiOff,
                            contentDescription = "Status Icon",
                            tint = if (isServerRunning) Color.White else MutedText,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (isServerRunning) "ACTIVE & READY" else "NOT SHARING",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = if (isServerRunning) StatusGreen else MutedText,
                        style = MaterialTheme.typography.titleMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = if (isServerRunning) {
                            val ip = serverIp.ifEmpty { "0.0.0.0" }
                            "Your PC can now access files at:\nhttp://$ip:$serverPort"
                        } else {
                            "Connect your phone to your PC via USB cable and turn on USB Tethering."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = PrimaryText,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { viewModel.toggleServer(context) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("server_toggle_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isServerRunning) StatusRed else Color.Black,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = if (isServerRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = "Action",
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isServerRunning) "STOP SHARING" else "START SHARING",
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Credentials / Pairing Card (Only when active)
        item {
            AnimatedVisibility(
                visible = isServerRunning,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                    border = BorderStroke(1.dp, SurfaceBorder),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "STEP-BY-STEP: CONNECT YOUR PC",
                            fontWeight = FontWeight.Bold,
                            color = PrimaryText,
                            style = MaterialTheme.typography.labelMedium,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "To view and manage your phone's files on your computer:\n\n1. On your Windows PC, open File Explorer.\n2. Right-click 'This PC' in the sidebar and choose 'Map Network Drive'.\n3. Copy the Connection Link below and paste it as the Folder location:",
                            color = MutedText,
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // URL Display Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White, RoundedCornerShape(8.dp))
                                .border(1.dp, SurfaceBorder, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Connection Link", style = MaterialTheme.typography.labelSmall, color = MutedText)
                                Text(
                                    text = "http://${serverIp.ifEmpty { "0.0.0.0" }}:$serverPort",
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = PrimaryText,
                                    fontSize = 13.sp
                                )
                            }
                            IconButton(onClick = {
                                copyToClipboard(context, "http://${serverIp.ifEmpty { "0.0.0.0" }}:$serverPort")
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy URL", tint = Color.Black)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Credentials Display Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Username Block
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Color.White, RoundedCornerShape(8.dp))
                                    .border(1.dp, SurfaceBorder, RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text("Username", style = MaterialTheme.typography.labelSmall, color = MutedText)
                                    Text("admin", fontWeight = FontWeight.Bold, color = PrimaryText, fontFamily = FontFamily.Monospace)
                                }
                            }
                            // Password PIN Block
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Color.White, RoundedCornerShape(8.dp))
                                    .border(1.dp, SurfaceBorder, RoundedCornerShape(8.dp))
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text("Security Password", style = MaterialTheme.typography.labelSmall, color = MutedText)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = localServerPassword,
                                            fontWeight = FontWeight.Bold,
                                            color = PrimaryText,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 1.sp
                                        )
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Lock",
                                            tint = Color.Black,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                showWebdavInstructions = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("how_to_mount_windows_btn"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Black.copy(alpha = 0.05f),
                                contentColor = Color.Black
                            ),
                            border = BorderStroke(1.dp, SurfaceBorder),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.HelpOutline,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = Color.Black
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "How to mount on Windows",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }

        // Connection Status Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, shape = RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                border = BorderStroke(1.dp, SurfaceBorder),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CONNECTION STATUS",
                            fontWeight = FontWeight.Bold,
                            color = PrimaryText,
                            style = MaterialTheme.typography.labelMedium,
                            letterSpacing = 0.5.sp
                        )
                        IconButton(onClick = { viewModel.refreshInterfaces() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh Links", tint = Color.Black)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (networkInterfaces.isEmpty()) {
                        Text(
                            text = "No active connections. Please turn on USB Tethering or Wi-Fi.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedText
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (iface in networkInterfaces) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White, RoundedCornerShape(10.dp))
                                        .border(1.dp, SurfaceBorder, RoundedCornerShape(10.dp))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (iface.isUsbTether) Icons.Default.Usb else Icons.Default.SettingsCell,
                                            contentDescription = "Interface Icon",
                                            tint = if (iface.isUsbTether) Color.Black else MutedText,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = iface.name,
                                                fontWeight = FontWeight.Bold,
                                                color = PrimaryText
                                            )
                                            Text(
                                                text = if (iface.isUsbTether) "Connected via USB Cable" else "Connected via Local Wi-Fi",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MutedText
                                            )
                                        }
                                    }
                                    Text(
                                        text = iface.ip,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryText
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showWebdavInstructions) {
        WebdavSetupInstructions(onDismiss = { showWebdavInstructions = false })
    }
}

// --- Local Files Screen ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MyPhoneScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val currentDir by viewModel.currentLocalDirectory.collectAsState()
    val localFiles by viewModel.localFiles.collectAsState()
    val selectedFiles by viewModel.selectedLocalFiles.collectAsState()
    val isGridMode by viewModel.localGridMode.collectAsState()

    var showCreateDirDialog by remember { mutableStateOf(false) }
    var renameTargetFile by remember { mutableStateOf<File?>(null) }

    LaunchedEffect(currentDir) {
        viewModel.initLocalDirectory(context)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Local Folder Breadcrumbs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceCard)
                .border(BorderStroke(1.dp, SurfaceBorder))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.navigateUpLocal(context) },
                enabled = currentDir?.parentFile != null && currentDir?.absolutePath != viewModel.getLocalRootDir(context).absolutePath
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.Black)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentDir?.name?.ifEmpty { "My Shared Files" } ?: "Files Root",
                    fontWeight = FontWeight.Black,
                    color = PrimaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = currentDir?.absolutePath ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { viewModel.toggleLocalGridMode() }) {
                Icon(
                    imageVector = if (isGridMode) Icons.Default.ViewList else Icons.Default.GridView,
                    contentDescription = "Toggle View",
                    tint = Color.Black
                )
            }
        }

        // File List Content
        Box(modifier = Modifier.weight(1f)) {
            if (localFiles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FolderOpen, contentDescription = "Empty", tint = MutedText, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No files in this folder", color = MutedText)
                    }
                }
            } else {
                if (isGridMode) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        contentPadding = PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(localFiles) { file ->
                             LocalFileGridItem(
                                file = file,
                                isSelected = selectedFiles.contains(file),
                                onClick = {
                                    if (selectedFiles.isNotEmpty()) {
                                        viewModel.toggleLocalFileSelection(file)
                                    } else {
                                        if (file.isDirectory) {
                                            viewModel.navigateIntoLocalFolder(file, context)
                                        } else {
                                            viewModel.toggleLocalFileSelection(file)
                                        }
                                    }
                                },
                                onLongClick = { viewModel.toggleLocalFileSelection(file) }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(localFiles) { file ->
                            LocalFileListRow(
                                file = file,
                                isSelected = selectedFiles.contains(file),
                                onClick = {
                                    if (selectedFiles.isNotEmpty()) {
                                        viewModel.toggleLocalFileSelection(file)
                                    } else {
                                        if (file.isDirectory) {
                                            viewModel.navigateIntoLocalFolder(file, context)
                                        } else {
                                            viewModel.toggleLocalFileSelection(file)
                                        }
                                    }
                                },
                                onLongClick = { viewModel.toggleLocalFileSelection(file) },
                                onRename = { renameTargetFile = file }
                            )
                        }
                    }
                }
            }

            // Context Action FAB
            if (selectedFiles.isEmpty()) {
                FloatingActionButton(
                    onClick = { showCreateDirDialog = true },
                    containerColor = Color.Black,
                    contentColor = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(20.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Folder", tint = Color.White)
                }
            }
        }

        // Selection Action Bottom Panel
        AnimatedVisibility(
            visible = selectedFiles.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SurfaceCard,
                border = BorderStroke(1.dp, SurfaceBorder),
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${selectedFiles.size} selected",
                            fontWeight = FontWeight.Bold,
                            color = PrimaryText
                        )
                        Text(
                            text = "Actions",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedText
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                viewModel.uploadSelectedLocal(context)
                                Toast.makeText(context, "Upload added to queue", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                            enabled = viewModel.isPcConnected.collectAsState().value
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = "Send to PC")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Send to PC")
                        }

                        IconButton(
                            onClick = { viewModel.zipSelectedLocal(context) },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White)
                        ) {
                            Icon(Icons.Default.FolderZip, contentDescription = "ZIP Files", tint = Color.Black)
                        }

                        IconButton(
                            onClick = { viewModel.deleteSelectedLocal(context) },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StatusRed)
                        }

                        IconButton(
                            onClick = { viewModel.clearLocalSelection() },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection", tint = Color.Black)
                        }
                    }
                }
            }
        }
    }

    // Modal dialog for folder creation
    if (showCreateDirDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDirDialog = false },
            title = { Text("New Folder", color = PrimaryText, fontWeight = FontWeight.Bold) },
            text = {
                TextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    placeholder = { Text("Folder Name") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = PrimaryText,
                        unfocusedTextColor = PrimaryText,
                        focusedIndicatorColor = Color.Black,
                        unfocusedIndicatorColor = SurfaceBorder,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderName.isNotEmpty()) {
                            viewModel.createLocalFolder(folderName, context)
                        }
                        showCreateDirDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White)
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDirDialog = false }) { Text("Cancel", color = PrimaryText) }
            },
            containerColor = SurfaceCard
        )
    }

    // Modal dialog for file rename
    if (renameTargetFile != null) {
        var newName by remember { mutableStateOf(renameTargetFile?.name ?: "") }
        AlertDialog(
            onDismissRequest = { renameTargetFile = null },
            title = { Text("Rename File", color = PrimaryText, fontWeight = FontWeight.Bold) },
            text = {
                TextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = PrimaryText,
                        unfocusedTextColor = PrimaryText,
                        focusedIndicatorColor = Color.Black,
                        unfocusedIndicatorColor = SurfaceBorder,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val file = renameTargetFile
                        if (file != null && newName.isNotEmpty()) {
                            viewModel.renameLocalFile(file, newName, context)
                        }
                        renameTargetFile = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White)
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { renameTargetFile = null }) { Text("Cancel", color = PrimaryText) }
            },
            containerColor = SurfaceCard
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocalFileListRow(
    file: File,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: () -> Unit
) {
    val cardBg = if (isSelected) SurfaceBorder else SurfaceCard
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(if (file.isDirectory) Color.Black.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.04f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!file.isDirectory) {
                        Text(
                            text = formatSize(file.length()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedText
                        )
                    }
                    Text(
                        text = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(file.lastModified())),
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText
                    )
                }
            }

            IconButton(onClick = onRename) {
                Icon(Icons.Default.Edit, contentDescription = "Rename", tint = MutedText, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocalFileGridItem(
    file: File,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val cardBg = if (isSelected) SurfaceBorder else SurfaceCard
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(if (file.isDirectory) Color.Black.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.04f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = file.name,
                fontWeight = FontWeight.Bold,
                color = PrimaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

// --- PC Files Screen ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PcFilesScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val pcIp by viewModel.pcIp.collectAsState()
    val pcPort by viewModel.pcPort.collectAsState()
    val pcUsername by viewModel.pcUsername.collectAsState()
    val pcToken by viewModel.pcToken.collectAsState()
    val isPcConnected by viewModel.isPcConnected.collectAsState()

    val currentDir by viewModel.currentRemoteDirectory.collectAsState()
    val remoteFiles by viewModel.remoteFiles.collectAsState()
    val selectedFiles by viewModel.selectedRemoteFiles.collectAsState()
    val isGridMode by viewModel.remoteGridMode.collectAsState()
    val isDiscovering by viewModel.isDiscovering.collectAsState()

    val isRemoteLoading by viewModel.isRemoteLoading.collectAsState()
    val remoteError by viewModel.remoteError.collectAsState()

    var showCreateDirDialog by remember { mutableStateOf(false) }
    var renameTargetFile by remember { mutableStateOf<RemoteFile?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (!isPcConnected) {
            // Connection Settings Card
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Column {
                        Text(
                            text = "CONNECT TO PC APP",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = PrimaryText
                        )
                        Text(
                            text = "Link your phone to the PC Companion app to browse and transfer files.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedText
                        )
                    }
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                        border = BorderStroke(1.dp, SurfaceBorder),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("FIND PC AUTOMATICALLY", fontWeight = FontWeight.Bold, color = PrimaryText, style = MaterialTheme.typography.labelMedium)
                                if (isDiscovering) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                                } else {
                                    Button(
                                        onClick = { viewModel.scanSubnetForPC() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Scan Subnet")
                                    }
                                }
                            }

                            // Manual Fields
                            OutlinedTextField(
                                value = pcIp,
                                onValueChange = { viewModel.pcIp.value = it },
                                label = { Text("PC IP Address") },
                                leadingIcon = { Icon(Icons.Default.Dns, contentDescription = null, tint = Color.Black) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = PrimaryText,
                                    unfocusedTextColor = PrimaryText,
                                    focusedBorderColor = Color.Black,
                                    unfocusedBorderColor = SurfaceBorder,
                                    focusedLabelColor = Color.Black,
                                    unfocusedLabelColor = MutedText
                                )
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = pcPort,
                                    onValueChange = { viewModel.pcPort.value = it },
                                    label = { Text("Port") },
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = PrimaryText,
                                        unfocusedTextColor = PrimaryText,
                                        focusedBorderColor = Color.Black,
                                        unfocusedBorderColor = SurfaceBorder,
                                        focusedLabelColor = Color.Black,
                                        unfocusedLabelColor = MutedText
                                    )
                                )
                                OutlinedTextField(
                                    value = pcUsername,
                                    onValueChange = { viewModel.pcUsername.value = it },
                                    label = { Text("Username") },
                                    modifier = Modifier.weight(2.0f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = PrimaryText,
                                        unfocusedTextColor = PrimaryText,
                                        focusedBorderColor = Color.Black,
                                        unfocusedBorderColor = SurfaceBorder,
                                        focusedLabelColor = Color.Black,
                                        unfocusedLabelColor = MutedText
                                    )
                                )
                            }

                            OutlinedTextField(
                                value = pcToken,
                                onValueChange = { viewModel.pcToken.value = it },
                                label = { Text("Security PIN") },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Black) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = PrimaryText,
                                    unfocusedTextColor = PrimaryText,
                                    focusedBorderColor = Color.Black,
                                    unfocusedBorderColor = SurfaceBorder,
                                    focusedLabelColor = Color.Black,
                                    unfocusedLabelColor = MutedText
                                )
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            if (remoteError != null) {
                                Text(
                                    text = remoteError ?: "",
                                    color = StatusRed,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Button(
                                onClick = { viewModel.testPCConnection() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("connect_pc_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
                                shape = RoundedCornerShape(10.dp)
                              ) {
                                if (isRemoteLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                                } else {
                                    Text("CONNECT TO PC", fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Browsing Mode
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceCard)
                    .border(BorderStroke(1.dp, SurfaceBorder))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.navigateUpRemote() },
                    enabled = currentDir != "/"
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.Black)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Remote PC Files",
                        fontWeight = FontWeight.Black,
                        color = PrimaryText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentDir,
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = { viewModel.toggleRemoteGridMode() }) {
                    Icon(
                        imageVector = if (isGridMode) Icons.Default.ViewList else Icons.Default.GridView,
                        contentDescription = "Toggle View",
                        tint = Color.Black
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                if (isRemoteLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.Black)
                    }
                } else if (remoteFiles.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Empty", tint = MutedText, modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("No files in this folder", color = MutedText)
                        }
                    }
                } else {
                    if (isGridMode) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(remoteFiles) { file ->
                                RemoteFileGridItem(
                                    file = file,
                                    isSelected = selectedFiles.contains(file),
                                    onClick = {
                                        if (selectedFiles.isNotEmpty()) {
                                            viewModel.toggleRemoteFileSelection(file)
                                        } else {
                                            if (file.isDirectory) {
                                                viewModel.navigateIntoRemoteFolder(file.name)
                                            } else {
                                                viewModel.toggleRemoteFileSelection(file)
                                            }
                                        }
                                    },
                                    onLongClick = { viewModel.toggleRemoteFileSelection(file) }
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(remoteFiles) { file ->
                                RemoteFileListRow(
                                    file = file,
                                    isSelected = selectedFiles.contains(file),
                                    onClick = {
                                        if (selectedFiles.isNotEmpty()) {
                                            viewModel.toggleRemoteFileSelection(file)
                                        } else {
                                            if (file.isDirectory) {
                                                viewModel.navigateIntoRemoteFolder(file.name)
                                            } else {
                                                viewModel.toggleRemoteFileSelection(file)
                                            }
                                        }
                                    },
                                    onLongClick = { viewModel.toggleRemoteFileSelection(file) },
                                    onRename = { renameTargetFile = file }
                                )
                            }
                        }
                    }
                }

                // FAB
                if (selectedFiles.isEmpty()) {
                    FloatingActionButton(
                        onClick = { showCreateDirDialog = true },
                        containerColor = Color.Black,
                        contentColor = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(20.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Create PC Folder", tint = Color.White)
                    }
                }
            }

            // Remote Selected Actions Bar
            AnimatedVisibility(
                visible = selectedFiles.isNotEmpty(),
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = SurfaceCard,
                    border = BorderStroke(1.dp, SurfaceBorder),
                    tonalElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${selectedFiles.size} selected",
                                fontWeight = FontWeight.Bold,
                                color = PrimaryText
                            )
                            Text(
                                text = "PC Actions",
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedText
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    viewModel.downloadSelectedRemote(context)
                                    Toast.makeText(context, "Download added to queue", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = "Download")
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("To Phone")
                            }

                            IconButton(
                                onClick = { viewModel.deleteSelectedRemote() },
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete from PC", tint = StatusRed)
                            }

                            IconButton(
                                onClick = { viewModel.clearRemoteSelection() },
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Clear Selection", tint = Color.Black)
                            }
                        }
                    }
                }
            }
        }
    }

    // PC Dir Creation modal
    if (showCreateDirDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDirDialog = false },
            title = { Text("New Folder on PC", color = PrimaryText, fontWeight = FontWeight.Bold) },
            text = {
                TextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    placeholder = { Text("Folder Name") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = PrimaryText,
                        unfocusedTextColor = PrimaryText,
                        focusedIndicatorColor = Color.Black,
                        unfocusedIndicatorColor = SurfaceBorder,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderName.isNotEmpty()) {
                            viewModel.createRemoteFolder(folderName)
                        }
                        showCreateDirDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White)
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDirDialog = false }) { Text("Cancel", color = PrimaryText) }
            },
            containerColor = SurfaceCard
        )
    }

    // Rename PC file dialog
    if (renameTargetFile != null) {
        var newName by remember { mutableStateOf(renameTargetFile?.name ?: "") }
        AlertDialog(
            onDismissRequest = { renameTargetFile = null },
            title = { Text("Rename Remote File", color = PrimaryText, fontWeight = FontWeight.Bold) },
            text = {
                TextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = PrimaryText,
                        unfocusedTextColor = PrimaryText,
                        focusedIndicatorColor = Color.Black,
                        unfocusedIndicatorColor = SurfaceBorder,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val file = renameTargetFile
                        if (file != null && newName.isNotEmpty()) {
                            viewModel.renameRemoteFile(file.name, newName)
                        }
                        renameTargetFile = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White)
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { renameTargetFile = null }) { Text("Cancel", color = PrimaryText) }
            },
            containerColor = SurfaceCard
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RemoteFileListRow(
    file: RemoteFile,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: () -> Unit
) {
    val cardBg = if (isSelected) SurfaceBorder else SurfaceCard
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(if (file.isDirectory) Color.Black.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.04f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!file.isDirectory) {
                        Text(
                            text = formatSize(file.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedText
                        )
                    }
                    Text(
                        text = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(file.mtime)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText
                    )
                }
            }

            IconButton(onClick = onRename) {
                Icon(Icons.Default.Edit, contentDescription = "Rename", tint = MutedText, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RemoteFileGridItem(
    file: RemoteFile,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val cardBg = if (isSelected) SurfaceBorder else SurfaceCard
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(if (file.isDirectory) Color.Black.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.04f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = file.name,
                fontWeight = FontWeight.Bold,
                color = PrimaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

// --- Transfers Screen ---
@Composable
fun TransfersScreen(viewModel: MainViewModel) {
    val activeTransfers by viewModel.activeTransfers.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceCard)
                .border(BorderStroke(1.dp, SurfaceBorder))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("TRANSFER HISTORY", fontWeight = FontWeight.Black, color = PrimaryText)
                Text("Active uploads and downloads history", style = MaterialTheme.typography.labelSmall, color = MutedText)
            }
            Button(
                onClick = { viewModel.clearCompletedTransfers() },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Clear Done")
            }
        }

        if (activeTransfers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.SyncAlt, contentDescription = "Sync", tint = MutedText, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No file transfers in queue", color = MutedText)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(activeTransfers) { task ->
                    TransferTaskCard(task = task, onDismiss = { viewModel.removeTransferTask(task.id) })
                }
            }
        }
    }
}

@Composable
fun TransferTaskCard(task: TransferTask, onDismiss: () -> Unit) {
    val progressAnimated by animateFloatAsState(targetValue = task.progress)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = BorderStroke(1.dp, SurfaceBorder),
        shape = RoundedCornerShape(12.dp)
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
                            .size(32.dp)
                            .background(Color.Black.copy(alpha = 0.08f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (task.isUpload) Icons.Default.CloudUpload else Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = task.fileName,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 200.dp)
                        )
                        Text(
                            text = if (task.isUpload) "Uploading to PC" else "Downloading to Phone",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedText
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val badgeColor = when (task.status) {
                        "Completed" -> StatusGreen
                        "Failed" -> StatusRed
                        else -> Color.Black
                    }
                    Text(
                        text = task.status.uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .background(badgeColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = MutedText, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (task.status == "Running") {
                LinearProgressIndicator(
                    progress = { progressAnimated },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                    color = Color.Black,
                    trackColor = SurfaceBorder
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${(progressAnimated * 100).toInt()}% completed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText
                    )
                }
            }
        }
    }
}

// --- Permission Explanation Dialog ---
@Composable
fun PermissionDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = "Alert", tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Permissions Needed", fontWeight = FontWeight.Bold, color = PrimaryText)
            }
        },
        text = {
            Text(
                text = "Direct Share requires the following offline-only permissions to operate:\n\n" +
                        "1. Notification Access: Allows the sharing server to run in a background system service without Android terminating it.\n\n" +
                        "2. Storage Access (MANAGE_EXTERNAL_STORAGE): Enables your Windows computer to browse and view files on your phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = PrimaryText
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    // Request notifications
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        try {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {}
                    }
                    // Request manage files
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {}
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Grant in Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = PrimaryText)
            }
        },
        containerColor = SurfaceCard
    )
}

// Helper methods
fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1] + ""
    return String.format(Locale.US, "%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}

fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = android.content.ClipData.newPlainText("Copied Text", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebdavSetupInstructions(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("usb_direct_share_prefs", Context.MODE_PRIVATE) }
    var showRegistryWarning by remember {
        mutableStateOf(!prefs.getBoolean("has_dismissed_registry_warning", false))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Computer,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Windows Mount Setup",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Black,
                        color = PrimaryText
                    )
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Conditionally show the prominent dismissible registry warning first
                if (showRegistryWarning) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(BorderStroke(1.dp, StatusRed.copy(alpha = 0.3f)), RoundedCornerShape(12.dp)),
                            colors = CardDefaults.cardColors(containerColor = StatusRed.copy(alpha = 0.05f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "Warning",
                                            tint = StatusRed,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Windows Basic Auth Bug",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelLarge,
                                            color = StatusRed
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            prefs.edit().putBoolean("has_dismissed_registry_warning", true).apply()
                                            showRegistryWarning = false
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Dismiss",
                                            tint = MutedText,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Text(
                                    text = "Windows blocks Map Network Drive over HTTP by default. To fix this, you must change a registry key on your PC.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PrimaryText,
                                    lineHeight = 16.sp
                                )
                                
                                Spacer(modifier = Modifier.height(10.dp))
                                
                                Text(
                                    text = "Option A: PowerShell (Fastest & Easiest)",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PrimaryText
                                )
                                
                                Text(
                                    text = "Open PowerShell as Administrator and run:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MutedText
                                )
                                
                                val psCommand = "Set-ItemProperty -Path \"HKLM:\\SYSTEM\\CurrentControlSet\\Services\\WebClient\\Parameters\" -Name \"BasicAuthLevel\" -Value 2\nRestart-Service WebClient"
                                
                                Spacer(modifier = Modifier.height(6.dp))
                                
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color.White, RoundedCornerShape(6.dp))
                                        .border(1.dp, SurfaceBorder, RoundedCornerShape(6.dp))
                                        .padding(8.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = psCommand,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            color = PrimaryText,
                                            lineHeight = 14.sp
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Button(
                                            onClick = { copyToClipboard(context, psCommand) },
                                            modifier = Modifier.height(32.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Copy Commands", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Text(
                                    text = "Option B: Manual Registry Edit",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PrimaryText
                                )
                                Text(
                                    text = "1. Open Registry Editor (regedit).\n" +
                                            "2. Go to: HKEY_LOCAL_MACHINE\\SYSTEM\\CurrentControlSet\\Services\\WebClient\\Parameters\n" +
                                            "3. Find or create \"BasicAuthLevel\" (DWORD).\n" +
                                            "4. Change its value to 2.\n" +
                                            "5. Open Services, find \"WebClient\", and restart it.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PrimaryText,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }

                // 2. Regular WebDAV Mount instructions
                item {
                    Text(
                        text = "Step-by-step Map Network Drive:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge,
                        color = PrimaryText
                    )
                }

                item {
                    Text(
                        text = "1. Open File Explorer on your Windows PC.\n" +
                                "2. Right-click 'This PC' in the sidebar or menu and select 'Map network drive...'\n" +
                                "3. Choose a drive letter (e.g., Z:).\n" +
                                "4. Paste the Connection Link (e.g. http://192.168.42.129:8080) into the 'Folder' box.\n" +
                                "5. Check 'Connect using different credentials' and click Finish.\n" +
                                "6. Enter the Username (admin) and the Security Password shown on your phone's screen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText,
                        lineHeight = 16.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Got It")
            }
        },
        containerColor = SurfaceCard
    )
}
