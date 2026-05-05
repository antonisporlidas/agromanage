package com.example.agromanage.ui_screens

import android.Manifest
import android.content.Context
import android.location.LocationManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.agromanage.R
import com.example.agromanage.data.local.Cultivation
import com.example.agromanage.data.local.FarmField
import com.example.agromanage.view_model.FarmViewModel
import com.example.agromanage.view_model.WeatherViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldsScreen(viewModelFarm: FarmViewModel = viewModel(), viewModelWeather: WeatherViewModel = viewModel()) {
    val fields by viewModelFarm.allFields.collectAsState()
    val cultivations by viewModelFarm.currentCultivations.collectAsState()
    val context = LocalContext.current

    val locationPermissionRequest = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineLocationGranted || coarseLocationGranted) println("Η άδεια τοποθεσίας δόθηκε!")
    }

    LaunchedEffect(Unit) {
        locationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        val config = Configuration.getInstance()
        config.load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        config.userAgentValue = "AgroManageApp/1.0"
        viewModelFarm.syncAllFromCloud()
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("Όλα") }

    var selectedFieldForDetails by remember { mutableStateOf<FarmField?>(null) }
    var fieldToDelete by remember { mutableStateOf<FarmField?>(null) }
    var fieldToAddCultivation by remember { mutableStateOf<FarmField?>(null) }

    var showAddDialog by remember { mutableStateOf(false) }
    var showFilterMenu by remember { mutableStateOf(false) }

    LaunchedEffect(selectedFieldForDetails, fields) {
        // Επανατρέχει και όταν αλλάζει η λίστα fields (π.χ. μετά από sync) ώστε
        // να εμφανιστούν cultivations που μόλις κατέβηκαν.
        selectedFieldForDetails?.let { field ->
            viewModelFarm.refreshCultivationsForField(field.id)
        }
    }

    val listState = rememberLazyListState()
    val isFabExpanded by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 } }

    val filteredFields = fields.filter { field ->
        val matchesSearch = field.name.contains(searchQuery, ignoreCase = true) || field.location.contains(searchQuery, ignoreCase = true)
        val matchesType = (selectedType == "Όλα" || field.fieldType == selectedType)
        matchesSearch && matchesType
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                expanded = isFabExpanded,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Νέο Χωράφι") }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            // --- Search & Filter row ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Αναζήτηση οικοπέδου...") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    shape = MaterialTheme.shapes.medium,
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))

                Box {
                    FilledTonalIconButton(
                        onClick = { showFilterMenu = true },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = if (selectedType != "Όλα")
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (selectedType != "Όλα")
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = "Φίλτρα")
                    }

                    DropdownMenu(
                        expanded = showFilterMenu,
                        onDismissRequest = { showFilterMenu = false }
                    ) {
                        Text(
                            "Φίλτρο Τύπου",
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge
                        )
                        HorizontalDivider()
                        listOf("Όλα", "Δενδροκαλλιέργεια", "Θερμοκήπιο", "Εξωχώραφο").forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        option,
                                        fontWeight = if (selectedType == option) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedType == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon = if (selectedType == option) ({
                                    Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                }) else null,
                                onClick = { selectedType = option; showFilterMenu = false }
                            )
                        }
                    }
                }
            }

            if (filteredFields.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Δεν βρέθηκαν χωράφια",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(items = filteredFields, key = { _, f -> f.id }) { index, field ->

                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                when (value) {
                                    SwipeToDismissBoxValue.EndToStart -> {
                                        fieldToDelete = field
                                        false
                                    }
                                    SwipeToDismissBoxValue.StartToEnd -> {
                                        fieldToAddCultivation = field
                                        false
                                    }
                                    else -> false
                                }
                            }
                        )

                        LaunchedEffect(fieldToDelete, fieldToAddCultivation) {
                            if (fieldToDelete == null && fieldToAddCultivation == null) dismissState.reset()
                        }

                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(tween(500, index * 80)) + slideInVertically(initialOffsetY = { 40 })
                        ) {
                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = true,
                                enableDismissFromEndToStart = true,
                                backgroundContent = {
                                    val direction = dismissState.dismissDirection
                                    val bgColor = when (direction) {
                                        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                                        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                                        else -> MaterialTheme.colorScheme.surface
                                    }
                                    val icon = when (direction) {
                                        SwipeToDismissBoxValue.EndToStart -> Icons.Default.Delete
                                        SwipeToDismissBoxValue.StartToEnd -> Icons.Default.Eco
                                        else -> null
                                    }
                                    val iconTint = when (direction) {
                                        SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.onErrorContainer
                                        SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                    val alignment = when (direction) {
                                        SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                                        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                                        else -> Alignment.Center
                                    }
                                    if (icon != null) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(6.dp)
                                                .clip(MaterialTheme.shapes.large)
                                                .background(bgColor),
                                            contentAlignment = alignment
                                        ) {
                                            Icon(
                                                icon,
                                                null,
                                                tint = iconTint,
                                                modifier = Modifier.padding(horizontal = 24.dp)
                                            )
                                        }
                                    }
                                },
                                content = { FieldItem(field) { selectedFieldForDetails = field } }
                            )
                        }
                    }
                }
            }
        }

        // --- Add Field Dialog ---
        if (showAddDialog) {
            AddFieldDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { n, l, s, type, lat, lon ->
                    viewModelFarm.addField(FarmField(name = n, location = l, size = s, fieldType = type, userId = "", latitude = lat, longitude = lon))
                    showAddDialog = false
                }
            )
        }

        // --- Add Cultivation Dialog (swipe right) ---
        if (fieldToAddCultivation != null) {
            AddCultivationDialog(
                onDismiss = { fieldToAddCultivation = null },
                onConfirm = { crop, year, season ->
                    viewModelFarm.addCultivation(Cultivation(fieldId = fieldToAddCultivation!!.id, cropType = crop, year = year, season = season))
                    fieldToAddCultivation = null
                }
            )
        }

        // --- Field Details Dialog ---
        if (selectedFieldForDetails != null) {
            val field = selectedFieldForDetails!!
            val typeIcon = getFieldIcon(field.fieldType)
            var weatherInfo by remember { mutableStateOf("Φόρτωση...") }
            var isHistoryExpanded by remember { mutableStateOf(false) }

            LaunchedEffect(field.latitude, field.longitude) {
                weatherInfo = viewModelWeather.getWeatherForCoordinates(field.latitude, field.longitude, com.example.agromanage.BuildConfig.OPENWEATHER_API_KEY)
            }

            AlertDialog(
                onDismissRequest = { selectedFieldForDetails = null },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = typeIcon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(field.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        DetailRow(icon = Icons.Default.Place, label = "Τοποθεσία", value = field.location)
                        DetailRow(icon = Icons.Default.AspectRatio, label = "Μέγεθος", value = "${field.size} στρ.")

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(MaterialTheme.shapes.large)
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    MapView(ctx).apply {
                                        setTileSource(TileSourceFactory.MAPNIK)
                                        setMultiTouchControls(true)
                                        controller.setZoom(15.0)
                                        val point = GeoPoint(field.latitude, field.longitude)
                                        controller.setCenter(point)
                                        val marker = Marker(this).apply {
                                            position = point
                                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                            title = field.name
                                        }
                                        overlays.add(marker)
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.Cloud,
                                    null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = weatherInfo,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Cultivation history button
                        FilledTonalButton(
                            onClick = { isHistoryExpanded = !isHistoryExpanded },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(
                                if (isHistoryExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ιστορικό Σποράς", fontWeight = FontWeight.SemiBold)
                        }

                        if (isHistoryExpanded) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (cultivations.isEmpty()) {
                                    Text(
                                        "Δεν υπάρχει ιστορικό. Κάντε swipe δεξιά το χωράφι για να προσθέσετε σπορά!",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                } else {
                                    cultivations.forEach { cult ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = MaterialTheme.shapes.medium,
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                                            ),
                                            elevation = CardDefaults.cardElevation(0.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        Icons.Default.Grass,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Column {
                                                        Text(
                                                            cult.cropType,
                                                            style = MaterialTheme.typography.titleSmall,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                        Text(
                                                            "${cult.season} - ${cult.year}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                }
                                                IconButton(onClick = { viewModelFarm.deleteCultivation(cult) }) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = "Διαγραφή",
                                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedFieldForDetails = null }) { Text("Κλείσιμο") }
                }
            )
        }

        // --- Delete Field Dialog ---
        if (fieldToDelete != null) {
            AlertDialog(
                onDismissRequest = { fieldToDelete = null },
                icon = {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                },
                title = { Text("Διαγραφή Χωραφιού;") },
                text = { Text("Είστε σίγουροι για τη διαγραφή του χωραφιού «${fieldToDelete?.name}»;") },
                confirmButton = {
                    Button(
                        onClick = { viewModelFarm.deleteField(fieldToDelete!!); fieldToDelete = null },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) { Text("Διαγραφή") }
                },
                dismissButton = {
                    TextButton(onClick = { fieldToDelete = null }) { Text("Ακύρωση") }
                }
            )
        }
    }
}

@Composable
fun FieldItem(field: FarmField, onClick: () -> Unit) {
    val fieldIcon = getFieldIcon(field.fieldType)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        fieldIcon,
                        null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    field.name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Place,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        field.location,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    field.fieldType,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    "${field.size} στρ.",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFieldDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, Double, String, Double, Double) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var size by remember { mutableStateOf("") }
    var fieldType by remember { mutableStateOf("Δενδροκαλλιέργεια") }
    var selectedLat by remember { mutableStateOf(39.0742) }
    var selectedLon by remember { mutableStateOf(21.8243) }
    val types = listOf("Δενδρο", "Θερμοκ.", "Εξωχώρ.")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Νέο Χωράφι", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Όνομα Χωραφιού") },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Περιοχή") },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Τοποθεσία στον χάρτη:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            MapView(ctx).apply {
                                setTileSource(TileSourceFactory.MAPNIK)
                                setMultiTouchControls(true)
                                controller.setZoom(16.0)
                                val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this)
                                locationOverlay.enableMyLocation()
                                locationOverlay.enableFollowLocation()
                                overlays.add(locationOverlay)
                                val startPoint = GeoPoint(selectedLat, selectedLon)
                                controller.setCenter(startPoint)
                                val marker = Marker(this).apply {
                                    position = startPoint
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                }
                                overlays.add(marker)
                                overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                                    override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                        selectedLat = p.latitude
                                        selectedLon = p.longitude
                                        marker.position = p
                                        invalidate()
                                        return true
                                    }
                                    override fun longPressHelper(p: GeoPoint): Boolean = false
                                }))
                                locationOverlay.runOnFirstFix {
                                    post {
                                        locationOverlay.myLocation?.let { myLoc ->
                                            controller.animateTo(myLoc)
                                            marker.position = myLoc
                                            selectedLat = myLoc.latitude
                                            selectedLon = myLoc.longitude
                                            invalidate()
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Text("Τύπος Χωραφιού", style = MaterialTheme.typography.labelMedium)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    types.forEachIndexed { index, label ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size),
                            onClick = {
                                fieldType = when (label) {
                                    "Δενδρο" -> "Δενδροκαλλιέργεια"
                                    "Θερμοκ." -> "Θερμοκήπιο"
                                    else -> "Εξωχώραφο"
                                }
                            },
                            selected = fieldType.startsWith(label.substring(0, 3))
                        ) { Text(label) }
                    }
                }
                OutlinedTextField(
                    value = size,
                    onValueChange = { size = it },
                    label = { Text("Στρέμματα") },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name, location, size.toDoubleOrNull() ?: 0.0, fieldType, selectedLat, selectedLon) }) {
                Text("Αποθήκευση")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Ακύρωση") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCultivationDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var cropType by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("2026") }
    var season by remember { mutableStateOf("Καλοκαιρινή") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Προσθήκη Σποράς", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = cropType,
                    onValueChange = { cropType = it },
                    label = { Text("Καλλιέργεια (π.χ. Σιτάρι)") },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = year,
                    onValueChange = { year = it },
                    label = { Text("Έτος (π.χ. 2026)") },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = season,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Περίοδος") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        listOf("Καλοκαιρινή", "Χειμερινή").forEach { s ->
                            DropdownMenuItem(text = { Text(s) }, onClick = { season = s; expanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(cropType, year, season) }) { Text("Προσθήκη") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Ακύρωση") } }
    )
}

@Composable
fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun getFieldIcon(fieldType: String): ImageVector {
    return when (fieldType) {
        "Δενδροκαλλιέργεια" -> Icons.Default.Park
        "Θερμοκήπιο" -> ImageVector.vectorResource(id = R.drawable.ic_greenhouse)
        "Εξωχώραφο" -> ImageVector.vectorResource(id = R.drawable.ic_open_field)
        else -> Icons.Default.Agriculture
    }
}