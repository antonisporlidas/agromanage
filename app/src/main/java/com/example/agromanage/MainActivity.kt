package com.example.agromanage

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth

import com.example.agromanage.ui_screens.FieldsScreen
import com.example.agromanage.ui_screens.TaskScreen
import com.example.agromanage.ui_screens.LoginScreen
import com.example.agromanage.ui_screens.RevenueScreen
import com.example.agromanage.ui_screens.MarketScreen
import com.example.agromanage.ui_screens.ScannerScreen
import com.example.agromanage.ui.theme.AgroManageTheme
import com.example.agromanage.view_model.FarmViewModel
import androidx.work.*
import com.example.agromanage.view_model.MarketViewModel
import com.example.agromanage.view_model.NotificationViewModel
import com.example.agromanage.view_model.ScannerViewModel
import com.example.agromanage.view_model.WeatherViewModel
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Fresh-install / νέα συσκευή detection μέσω marker file στο noBackupFilesDir
        // (εξαιρείται εγγυημένα από κάθε Auto Backup / device transfer).
        // Αν λείπει ο marker → καθαρίζουμε stale tracking keys ώστε να μην αναπαραχθεί
        // το παλιό ιστορικό ειδοποιήσεων που μπορεί να επανέφερε το Auto Backup.
        val initMarker = java.io.File(applicationContext.noBackupFilesDir, "_install_marker")
        if (!initMarker.exists()) {
            val staleKeysPrefs = applicationContext.getSharedPreferences("AgroPrefs", Context.MODE_PRIVATE)
            val keysToRemove = staleKeysPrefs.all.keys.filter { key ->
                key.startsWith("saved_notifications_") ||
                key.startsWith("post_") ||
                key.startsWith("weather_alert_")
            }
            val editor = staleKeysPrefs.edit()
            keysToRemove.forEach { editor.remove(it) }
            editor.apply()
            initMarker.createNewFile()
        }

        setContent {
            val weatherWorkRequest = PeriodicWorkRequestBuilder<WeatherWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
                "WeatherAlertWork",
                ExistingPeriodicWorkPolicy.KEEP,
                weatherWorkRequest
            )
            AgroManageTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val permissionLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.RequestPermission(),
                            onResult = { isGranted ->
                                if (isGranted) {
                                    println("Ο χρήστης επέτρεψε τις ειδοποιήσεις!")
                                } else {
                                    println("Ο χρήστης απέρριψε τις ειδοποιήσεις.")
                                }
                            }
                        )
                        LaunchedEffect(Unit) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }

                    var isLoggedIn by remember {
                        mutableStateOf(FirebaseAuth.getInstance().currentUser != null)
                    }

                    if (!isLoggedIn) {
                        LoginScreen(onLoginSuccess = { isLoggedIn = true })
                    } else {
                        val viewModelFarm: FarmViewModel = viewModel()
                        val viewModelMarket: MarketViewModel = viewModel()
                        val viewModelWeather: WeatherViewModel = viewModel()
                        val viewModelNotification: NotificationViewModel = viewModel()
                        val viewModelScanner: ScannerViewModel = viewModel()

                        val userRole by viewModelFarm.userRole.collectAsState()

                        LaunchedEffect(Unit) {
                            viewModelFarm.fetchUserRole()
                        }

                        when (userRole) {
                            "" -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                            "Έμπορος" -> {
                                MerchantApp(onSignOut = { isLoggedIn = false })
                            }
                            else -> {
                                AgroManageApp(
                                    viewModelNotification = viewModelNotification,
                                    viewModelFarm = viewModelFarm,
                                    viewModelWeather = viewModelWeather,
                                    viewModelMarket = viewModelMarket,
                                    viewModelScanner = viewModelScanner,
                                    onSignOut = { isLoggedIn = false }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgroTopBar(
    viewModelFarm: FarmViewModel,
    viewModelWeather: WeatherViewModel,
    viewModelMarket: MarketViewModel,
    viewModelNotification: NotificationViewModel,
    title: String,
    onLogoutClick: () -> Unit
) {
    val notifications by viewModelNotification.notifications.collectAsState()
    val unreadCount = notifications.count { !it.isRead }
    var showNotifDialog by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "bell_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (unreadCount > 0) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale_animation"
    )

    CenterAlignedTopAppBar(
        title = {
            Text(
                title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.primary,
            titleContentColor = MaterialTheme.colorScheme.onPrimary,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
        ),
        navigationIcon = {
            IconButton(onClick = onLogoutClick) {
                Icon(
                    Icons.Default.Logout,
                    contentDescription = "Αποσύνδεση",
                    tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                )
            }
        },
        actions = {
            IconButton(onClick = {
                showNotifDialog = true
                viewModelNotification.markNotificationsAsRead()
            }) {
                BadgedBox(
                    badge = {
                        if (unreadCount > 0) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            ) {
                                Text("$unreadCount")
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (unreadCount > 0) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                        contentDescription = "Ειδοποιήσεις",
                        modifier = Modifier.scale(scale),
                        tint = if (unreadCount > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    )

    if (showNotifDialog) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        ModalBottomSheet(
            onDismissRequest = { showNotifDialog = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Οι Ειδοποιήσεις μου",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (notifications.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.NotificationsOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Δεν έχετε καμία νέα ειδοποίηση.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(items = notifications, key = { it.id }) { notif ->
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        viewModelNotification.deleteNotification(notif.id)
                                        true
                                    } else false
                                }
                            )

                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = false,
                                backgroundContent = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(MaterialTheme.shapes.large)
                                            .background(MaterialTheme.colorScheme.errorContainer),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Διαγραφή",
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(end = 24.dp)
                                        )
                                    }
                                },
                                content = {
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        ),
                                        shape = MaterialTheme.shapes.large,
                                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .padding(16.dp)
                                                .fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                modifier = Modifier.size(40.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        Icons.Default.Info,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    notif.title,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    notif.message,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

data class NavInfo(val name: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String)

@Composable
fun RowScope.NavItem(navInfo: NavInfo, selectedItem: String, onClick: () -> Unit) {
    val selected = selectedItem == navInfo.name
    NavigationBarItem(
        icon = {
            Icon(
                imageVector = navInfo.icon,
                contentDescription = navInfo.name,
                modifier = Modifier.size(26.dp)
            )
        },
        label = {
            Text(
                navInfo.label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        },
        selected = selected,
        onClick = onClick,
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            indicatorColor = MaterialTheme.colorScheme.primaryContainer
        )
    )
}

@Composable
fun AgroManageApp(
    viewModelFarm: FarmViewModel,
    viewModelNotification: NotificationViewModel,
    viewModelWeather: WeatherViewModel,
    viewModelMarket: MarketViewModel,
    viewModelScanner: ScannerViewModel,
    onSignOut: () -> Unit
) {
    var selectedItem by remember { mutableStateOf("Χωράφια") }

    val leftNavItems = listOf(
        NavInfo("Χωράφια", Icons.Default.Agriculture, "Χωράφια"),
        NavInfo("Εργασίες", Icons.Default.Assignment, "Εργασίες")
    )
    val rightNavItems = listOf(
        NavInfo("Τζίρος", Icons.Default.Payments, "Τζίρος"),
        NavInfo("Αγορά", Icons.Default.Storefront, "Αγορά")
    )

    Scaffold(
        topBar = {
            AgroTopBar(
                viewModelFarm = viewModelFarm,
                viewModelWeather = viewModelWeather,
                viewModelNotification = viewModelNotification,
                viewModelMarket = viewModelMarket,
                title = selectedItem,
                onLogoutClick = {
                    FirebaseAuth.getInstance().signOut()
                    onSignOut()
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { selectedItem = "Scanner" },
                shape = CircleShape,
                containerColor = if (selectedItem == "Scanner")
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.primary,
                contentColor = if (selectedItem == "Scanner")
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 6.dp,
                    pressedElevation = 2.dp
                ),
                modifier = Modifier
                    .size(70.dp)
                    .offset(y = 60.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = "AI Scanner",
                    modifier = Modifier.size(32.dp)
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
        bottomBar = {
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.height(90.dp)
            ) {
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceAround) {
                    leftNavItems.forEach { item ->
                        NavItem(navInfo = item, selectedItem = selectedItem) {
                            selectedItem = item.name
                        }
                    }
                }

                Spacer(modifier = Modifier.width(90.dp))

                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceAround) {
                    rightNavItems.forEach { item ->
                        NavItem(navInfo = item, selectedItem = selectedItem) {
                            selectedItem = item.name
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Crossfade(targetState = selectedItem, label = "ScreenTransition") { screen ->
            Box(
                modifier = Modifier
                    .padding(paddingValues)
                    .fillMaxSize()
                    .padding(bottom = 10.dp)
            ) {
                when (screen) {
                    "Χωράφια" -> FieldsScreen()
                    "Εργασίες" -> TaskScreen()
                    "Scanner" -> ScannerScreen()
                    "Τζίρος" -> RevenueScreen()
                    "Αγορά" -> MarketScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantApp(onSignOut: () -> Unit) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Αγορά Προϊόντων",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    titleContentColor = MaterialTheme.colorScheme.onSecondary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSecondary
                ),
                navigationIcon = {
                    IconButton(
                        onClick = {
                            FirebaseAuth.getInstance().signOut()
                            onSignOut()
                        }
                    ) {
                        Icon(
                            Icons.Default.Logout,
                            "Αποσύνδεση",
                            tint = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.8f)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            MarketScreen()
        }
    }
}