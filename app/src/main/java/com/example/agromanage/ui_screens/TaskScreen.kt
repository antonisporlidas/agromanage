package com.example.agromanage.ui_screens

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.agromanage.data.local.Cultivation
import com.example.agromanage.data.local.FarmField
import com.example.agromanage.data.local.FarmTask
import com.example.agromanage.view_model.FarmViewModel
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreen(viewModelFarm: FarmViewModel = viewModel()) {

    val fields by viewModelFarm.allFields.collectAsState()
    val cultivations by viewModelFarm.currentCultivations.collectAsState()
    val tasks by viewModelFarm.allTasks.collectAsState()
    val context = LocalContext.current

    var selectedField by remember { mutableStateOf<FarmField?>(null) }
    var selectedCultivation by remember { mutableStateOf<Cultivation?>(null) }

    var showAddDialog by remember { mutableStateOf(false) }
    var showCultivationSelectorDialog by remember { mutableStateOf(false) }
    var selectedTasksForDeletion by remember { mutableStateOf(setOf<FarmTask>()) }

    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Λίπανση", "Ράντισμα", "Συγκομιδή")

    LaunchedEffect(Unit) {
        viewModelFarm.syncAllFromCloud()
    }

    LaunchedEffect(selectedField) {
        if (selectedField != null) {
            viewModelFarm.refreshCultivationsForField(selectedField!!.id)
            showCultivationSelectorDialog = true
        } else {
            showCultivationSelectorDialog = false
        }
    }

    LaunchedEffect(selectedCultivation) {
        selectedTasksForDeletion = emptySet()
        if (selectedCultivation != null) {
            viewModelFarm.refreshTasksForCultivation(selectedCultivation!!.id)
            selectedTabIndex = 0
        }
    }

    // ==========================================
    // SCREEN 1: FIELD SELECTION
    // ==========================================
    if (selectedCultivation == null) {
        var searchQuery by remember { mutableStateOf("") }
        val filteredFields = fields.filter { field ->
            field.name.contains(searchQuery, ignoreCase = true) || field.location.contains(searchQuery, ignoreCase = true)
        }

        Scaffold { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Text(
                    "Ημερολόγιο Εργασιών",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    placeholder = { Text("Αναζήτηση χωραφιού...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    shape = MaterialTheme.shapes.medium,
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (filteredFields.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
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
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredFields) { field ->
                            FieldTaskSelectorItem(field) { selectedField = field }
                        }
                    }
                }
            }
        }

        if (showCultivationSelectorDialog && selectedField != null) {
            AlertDialog(
                onDismissRequest = {
                    showCultivationSelectorDialog = false
                    selectedField = null
                },
                title = {
                    Text(
                        "Επιλογή Σποράς",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                text = {
                    Column {
                        Text(
                            "Επιλέξτε για ποια καλλιέργεια στο «${selectedField!!.name}» θέλετε να δείτε το ημερολόγιο:",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        if (cultivations.isEmpty()) {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Δεν υπάρχει καταγεγραμμένη σπορά σε αυτό το χωράφι.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                                items(cultivations) { cult ->
                                    Card(
                                        onClick = {
                                            selectedCultivation = cult
                                            showCultivationSelectorDialog = false
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        shape = MaterialTheme.shapes.large,
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                modifier = Modifier.size(40.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        Icons.Default.Grass,
                                                        null,
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(14.dp))
                                            Column {
                                                Text(
                                                    cult.cropType,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    "${cult.season} ${cult.year}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(modifier = Modifier.weight(1f))
                                            Icon(
                                                Icons.Default.ChevronRight,
                                                null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showCultivationSelectorDialog = false; selectedField = null }) {
                        Text("Κλείσιμο")
                    }
                }
            )
        }
    }

    // ==========================================
    // SCREEN 2: TASKS WITH 3 TABS
    // ==========================================
    else {
        BackHandler {
            selectedCultivation = null
            selectedField = null
        }

        val displayedTasks = tasks.filter { task ->
            when (selectedTabIndex) {
                0 -> task.title.startsWith("Λίπανση")
                1 -> task.title.startsWith("Ράντισμα")
                2 -> task.title.startsWith("Συγκομιδή")
                else -> true
            }
        }

        Scaffold(
            topBar = {
                Column {
                    CenterAlignedTopAppBar(
                        title = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    selectedCultivation!!.cropType,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (selectedTasksForDeletion.isNotEmpty()) {
                                    Text(
                                        "${selectedTasksForDeletion.size} επιλέχθηκαν",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    )
                                } else {
                                    Text(
                                        "${selectedCultivation!!.season} ${selectedCultivation!!.year}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { selectedCultivation = null; selectedField = null }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Πίσω",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        },
                        actions = {
                            if (tasks.isNotEmpty() && selectedTasksForDeletion.isEmpty()) {
                                IconButton(onClick = {
                                    exportTasksToPdf(context, "${selectedField?.name} - ${selectedCultivation!!.cropType}", tasks)
                                }) {
                                    Icon(
                                        Icons.Default.PictureAsPdf,
                                        contentDescription = "PDF",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                            if (selectedTasksForDeletion.isNotEmpty()) {
                                IconButton(onClick = {
                                    selectedTasksForDeletion.forEach { task -> viewModelFarm.deleteTask(task) }
                                    selectedTasksForDeletion = emptySet()
                                }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Διαγραφή",
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    // Custom pill tab bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tabs.forEachIndexed { index, title ->
                            val isSelected = selectedTabIndex == index
                            Surface(
                                onClick = {
                                    selectedTabIndex = index
                                    selectedTasksForDeletion = emptySet()
                                },
                                modifier = Modifier.weight(1f),
                                shape = MaterialTheme.shapes.extraLarge,
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) { Icon(Icons.Default.Add, "Προσθήκη Εργασίας") }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (displayedTasks.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.EventNote,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Δεν υπάρχουν καταγραφές σε αυτή την κατηγορία.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(displayedTasks) { _, task ->
                            val isSelected = selectedTasksForDeletion.contains(task)
                            TaskItem(
                                task = task,
                                isSelected = isSelected,
                                onSelectChange = { checked ->
                                    selectedTasksForDeletion = if (checked) selectedTasksForDeletion + task else selectedTasksForDeletion - task
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            AddTaskSmartDialog(
                tabIndex = selectedTabIndex,
                cultivationName = selectedCultivation!!.cropType,
                onDismiss = { showAddDialog = false },
                onConfirm = { title, date ->
                    viewModelFarm.addTask(
                        task = FarmTask(title = title, cultivationId = selectedCultivation!!.id, date = date, cost = 0.0),
                        cultivationId = selectedCultivation!!.id
                    )
                    showAddDialog = false
                }
            )
        }
    }
}

// -------------------------------------------------------------
// UI COMPONENTS
// -------------------------------------------------------------

@Composable
fun TaskItem(task: FarmTask, isSelected: Boolean, onSelectChange: (Boolean) -> Unit) {
    val (taskIcon, indicatorColor) = when {
        task.title.startsWith("Λίπανση") ->
            Icons.Default.Eco to Color(0xFF2196F3)
        task.title.startsWith("Ράντισμα") ->
            Icons.Default.Science to Color(0xFF9C27B0)
        task.title.startsWith("Συγκομιδή") ->
            Icons.Default.Agriculture to Color(0xFFFF9800)
        else ->
            Icons.Default.Build to MaterialTheme.colorScheme.primary
    }

    val cardColor = if (isSelected) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.surface

    Card(
        onClick = { onSelectChange(!isSelected) },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(if (isSelected) 0.dp else 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(indicatorColor)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Surface(
                shape = CircleShape,
                color = indicatorColor.copy(0.12f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(taskIcon, null, tint = indicatorColor, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f).padding(vertical = 12.dp)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelected) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CalendarToday,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = task.date,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Checkbox(
                checked = isSelected,
                onCheckedChange = { onSelectChange(it) },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.error,
                    checkmarkColor = MaterialTheme.colorScheme.onError
                )
            )
        }
    }
}

@Composable
fun FieldTaskSelectorItem(field: FarmField, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Agriculture,
                        null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    field.name,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Place,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        "${field.location} • ${field.size} στρ.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskSmartDialog(
    tabIndex: Int,
    cultivationName: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var date by remember(tabIndex) { mutableStateOf(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())) }
    val taskType = when (tabIndex) {
        0 -> "Λίπανση"
        1 -> "Ράντισμα"
        else -> "Συγκομιδή"
    }
    var preparation by remember(tabIndex) { mutableStateOf("") }
    var quantity by remember(tabIndex) { mutableStateOf("") }
    var harvestKilos by remember(tabIndex) { mutableStateOf("") }
    var harvestNotes by remember(tabIndex) { mutableStateOf("") }

    val dialogTitle = when (tabIndex) {
        0 -> "Νέα Λίπανση"
        1 -> "Νέο Ράντισμα"
        else -> "Καταγραφή Συγκομιδής"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        "Αφορά: $cultivationName",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                if (tabIndex != 2) {
                    val preparationLabel = when (tabIndex) {
                        0 -> "Λίπασμα (π.χ. Ουρία, Νερό, NPK)"
                        1 -> "Φάρμακο / Σκεύασμα"
                        else -> "Είδος"
                    }
                    OutlinedTextField(
                        value = preparation,
                        onValueChange = { preparation = it },
                        label = { Text(preparationLabel) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    )
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = { Text("Ποσότητα (π.χ. 5L, 20kg)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    )
                } else {
                    OutlinedTextField(
                        value = harvestKilos,
                        onValueChange = { harvestKilos = it },
                        label = { Text("Ποσότητα Συγκομιδής (π.χ. 5000 κιλά)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    )
                    OutlinedTextField(
                        value = harvestNotes,
                        onValueChange = { harvestNotes = it },
                        label = { Text("Σημειώσεις / Ποιότητα (π.χ. Α' Διαλογή)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    )
                }

                DatePickerField(
                    value = date,
                    onValueChange = { date = it },
                    label = "Ημερομηνία"
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val finalTitle = if (tabIndex == 2) {
                    "Συγκομιδή: $harvestKilos ${if (harvestNotes.isNotEmpty()) "($harvestNotes)" else ""}"
                } else {
                    "$taskType: $preparation ($quantity)"
                }
                onConfirm(finalTitle, date)
            }) { Text("Αποθήκευση") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Ακύρωση") } }
    )
}

fun exportTasksToPdf(context: Context, headerName: String, tasks: List<FarmTask>) {
    val pdfDocument = PdfDocument()
    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
    val page = pdfDocument.startPage(pageInfo)
    val canvas = page.canvas
    val titlePaint = Paint().apply { textSize = 20f; isFakeBoldText = true; color = android.graphics.Color.BLACK }
    val textPaint = Paint().apply { textSize = 14f; color = android.graphics.Color.DKGRAY }

    canvas.drawText("Πρόγραμμα Εργασιών: $headerName", 50f, 50f, titlePaint)
    canvas.drawText("=======================================", 50f, 70f, textPaint)

    var yPosition = 110f
    tasks.forEachIndexed { index, task ->
        canvas.drawText("${index + 1}. ${task.title} | ${task.date}", 50f, yPosition, textPaint)
        yPosition += 35f
    }

    pdfDocument.finishPage(page)
    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val file = File(downloadsDir, "Ergasies_${headerName.replace(" ", "_").replace("/", "-")}.pdf")

    try {
        pdfDocument.writeTo(FileOutputStream(file))
        Toast.makeText(context, "Το PDF αποθηκεύτηκε!", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Σφάλμα PDF", Toast.LENGTH_SHORT).show()
    } finally {
        pdfDocument.close()
    }
}