package com.example.agromanage.ui_screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.agromanage.data.local.AppDatabase
import com.example.agromanage.data.local.Cultivation
import com.example.agromanage.data.local.FarmField
import com.example.agromanage.data.local.FarmTransaction
import com.example.agromanage.view_model.FarmViewModel
import com.example.agromanage.ui.theme.*
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RevenueScreen(viewModelFarm: FarmViewModel = viewModel()) {

    val context = LocalContext.current
    val transactions by viewModelFarm.allTransactions.collectAsState()
    val fields by viewModelFarm.allFields.collectAsState()

    // Κρατάμε όλες τις καλλιέργειες του χρήστη
    var allCultivations by remember { mutableStateOf<List<Cultivation>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }

    // Συγχρονισμός και ανάκτηση δεδομένων
    LaunchedEffect(Unit) {
        viewModelFarm.syncAllFromCloud()
    }

    LaunchedEffect(fields, transactions) {
        val dao = AppDatabase.getDatabase(context).farmDao()
        allCultivations = dao.getAllCultivationsForUser(viewModelFarm.currentUserId)
    }

    // Βρίσκουμε τα διαθέσιμα έτη από τις καλλιέργειες και τα γενικά έξοδα
    val yearsFromCultivations = allCultivations.map { it.year }
    val yearsFromGeneralTxs = transactions.filter { it.cultivationId == null }
        .mapNotNull { tx -> tx.date.split("/", "-", ".").lastOrNull()?.trim() }
        .filter { it.length == 4 }

    val availableYears = (yearsFromCultivations + yearsFromGeneralTxs + listOf("2026")).distinct().sortedDescending()
    var selectedYear by remember { mutableStateOf(if (availableYears.isNotEmpty()) availableYears.first() else "2026") }

    // 1. Γενικές Συναλλαγές (Επιχείρησης) για το επιλεγμένο έτος
    val generalTransactions = transactions.filter {
        it.cultivationId == null && it.date.contains(selectedYear)
    }

    // 2. Συναλλαγές Καλλιεργειών για το επιλεγμένο έτος
    val cultivationsOfYear = allCultivations.filter { it.year == selectedYear }
    val cultIds = cultivationsOfYear.map { it.id }
    val cultivationTransactions = transactions.filter { it.cultivationId in cultIds }

    val allTransactionsOfYear = generalTransactions + cultivationTransactions

    // Υπολογισμός Συνολικών Εσόδων & Εξόδων
    val totalIncome = allTransactionsOfYear.filter { it.type == "Έσοδο" }.sumOf { it.amount }
    val totalExpense = allTransactionsOfYear.filter { it.type == "Έξοδο" }.sumOf { it.amount }
    val netProfit = totalIncome - totalExpense

    // --- ΕΛΕΓΧΟΣ ΠΡΟΣΑΝΑΤΟΛΙΣΜΟΥ (ΓΙΑ ΤΗΝ ΕΡΓΑΣΙΑ) ---
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // ΑΦΑΙΡΕΘΗΚΕ ΤΟ TOPBAR ΑΠΟ ΤΟ SCAFFOLD
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            ) { Icon(Icons.Default.Add, null) }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {

            if (isLandscape) {
                // =======================================================
                // LANDSCAPE MODE (Πλάγια οθόνη - Χωρισμένη στα δύο)
                // =======================================================
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {

                    // ΑΡΙΣΤΕΡΗ ΠΛΕΥΡΑ: Λίστα και Φίλτρα
                    Column(modifier = Modifier.weight(1.2f).fillMaxHeight()) {
                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Έτος Ανάλυσης: ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            RevenueFilterDropdown(selectedYear, availableYears.ifEmpty { listOf("2026") }) { selectedYear = it }
                        }

                        Text(
                            text = "Ανάλυση ανά Σπορά ($selectedYear)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.primary
                        )

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (generalTransactions.isNotEmpty()) {
                                item {
                                    FieldProfitAccordion(
                                        title = "Γενικά Έξοδα/Έσοδα Επιχείρησης",
                                        transactions = generalTransactions,
                                        onDeleteTx = { viewModelFarm.deleteTransaction(it) }
                                    )
                                }
                            }
                            items(cultivationsOfYear) { cult ->
                                val txsForThisCult = cultivationTransactions.filter { it.cultivationId == cult.id }
                                val field = fields.find { it.id == cult.fieldId }
                                val fieldName = field?.name ?: "Άγνωστο Χωράφι"

                                FieldProfitAccordion(
                                    title = "$fieldName - ${cult.cropType} (${cult.season})",
                                    transactions = txsForThisCult,
                                    onDeleteTx = { viewModelFarm.deleteTransaction(it) }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(80.dp)) }
                        }
                    }

                    // ΔΕΞΙΑ ΠΛΕΥΡΑ: Κάρτα Οικονομικής Σύνοψης (Πλέον με Scroll)
                    Column(
                        modifier = Modifier
                            .weight(0.8f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()), // <--- ΕΔΩ Η ΛΥΣΗ! Επιτρέπει το σκρολάρισμα αν είναι κοντή η οθόνη.
                        verticalArrangement = Arrangement.Center
                    ) {
                        DashboardCard(totalIncome, totalExpense, netProfit, selectedYear)
                        Spacer(modifier = Modifier.height(80.dp)) // Χώρος για να μην το κρύβει το κουμπί (+)
                    }
                }
            } else {
                // =======================================================
                // PORTRAIT MODE (Όρθια οθόνη - Κλασική Προβολή)
                // =======================================================
                Column(modifier = Modifier.fillMaxSize()) {

                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Έτος Ανάλυσης: ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        RevenueFilterDropdown(selectedYear, availableYears.ifEmpty { listOf("2026") }) { selectedYear = it }
                    }

                    DashboardCard(totalIncome, totalExpense, netProfit, selectedYear)

                    Text(
                        text = "Ανάλυση ανά Σπορά ($selectedYear)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        color = MaterialTheme.colorScheme.primary
                    )

                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (generalTransactions.isNotEmpty()) {
                            item {
                                FieldProfitAccordion(
                                    title = "Γενικά Έξοδα/Έσοδα Επιχείρησης",
                                    transactions = generalTransactions,
                                    onDeleteTx = { viewModelFarm.deleteTransaction(it) }
                                )
                            }
                        }

                        items(cultivationsOfYear) { cult ->
                            val txsForThisCult = cultivationTransactions.filter { it.cultivationId == cult.id }
                            val field = fields.find { it.id == cult.fieldId }
                            val fieldName = field?.name ?: "Άγνωστο Χωράφι"

                            FieldProfitAccordion(
                                title = "$fieldName - ${cult.cropType} (${cult.season})",
                                transactions = txsForThisCult,
                                onDeleteTx = { viewModelFarm.deleteTransaction(it) }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(80.dp)) }
                    }
                }
            }
        }

        // Το Dialog μένει εκτός των if για να ανοίγει κανονικά και στις δύο προβολές
        if (showAddDialog) {
            AddTransactionSmartDialog(
                cultivationsOfYear = cultivationsOfYear,
                fields = fields,
                selectedYear = selectedYear,
                onDismiss = { showAddDialog = false },
                onConfirm = { tx ->
                    viewModelFarm.addTransaction(tx)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
fun DashboardCard(income: Double, expense: Double, profit: Double, year: String) {
    val isProfit = profit >= 0
    val profitColor = if (isProfit) SuccessGreen else DeleteRed

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Συνολικό Ταμείο",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        year,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (isProfit) SuccessGreen.copy(0.12f) else DeleteRed.copy(0.12f)
                ) {
                    Text(
                        text = "${if (isProfit) "+" else ""}${String.format("%.0f", profit)} €",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = profitColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "${String.format("%.2f", profit)} €",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = profitColor
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(shape = CircleShape, color = SuccessGreen.copy(0.1f), modifier = Modifier.size(36.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = SuccessGreen, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Έσοδα", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${String.format("%.2f", income)} €",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = SuccessGreen
                    )
                }
                Box(modifier = Modifier.width(1.dp).height(56.dp).background(MaterialTheme.colorScheme.outlineVariant))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(shape = CircleShape, color = DeleteRed.copy(0.1f), modifier = Modifier.size(36.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.TrendingDown, null, tint = DeleteRed, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Έξοδα", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${String.format("%.2f", expense)} €",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = DeleteRed
                    )
                }
            }
        }
    }
}

@Composable
fun FieldProfitAccordion(title: String, transactions: List<FarmTransaction>, onDeleteTx: (FarmTransaction) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val income = transactions.filter { it.type == "Έσοδο" }.sumOf { it.amount }
    val expense = transactions.filter { it.type == "Έξοδο" }.sumOf { it.amount }
    val profit = income - expense
    val isProfit = profit >= 0
    val profitColor = if (isProfit) SuccessGreen else DeleteRed

    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Grass,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "${transactions.size} συναλλαγές",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${if (profit > 0) "+" else ""}${String.format("%.2f", profit)} €",
                    fontWeight = FontWeight.Bold,
                    color = profitColor,
                    style = MaterialTheme.typography.titleSmall
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    if (transactions.isEmpty()) {
                        Text(
                            "Δεν υπάρχουν κινήσεις.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(8.dp)
                        )
                    } else {
                        transactions.forEach { tx -> TransactionRow(tx) { onDeleteTx(tx) } }
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionRow(transaction: FarmTransaction, onDeleteClick: () -> Unit) {
    val isIncome = transaction.type == "Έσοδο"
    val color = if (isIncome) SuccessGreen else DeleteRed

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(color = color.copy(0.1f), shape = CircleShape, modifier = Modifier.size(32.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(imageVector = if (isIncome) Icons.AutoMirrored.Filled.TrendingUp else Icons.AutoMirrored.Filled.TrendingDown, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(transaction.description, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(transaction.date, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
        Text("${if (isIncome) "+" else "-"}${transaction.amount} €", fontWeight = FontWeight.Bold, color = color)
        IconButton(onClick = onDeleteClick, modifier = Modifier.size(32.dp).padding(start = 8.dp)) {
            Icon(Icons.Default.Delete, null, tint = Color.LightGray, modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RevenueFilterDropdown(selectedText: String, options: List<String>, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = true,
            onClick = { expanded = true },
            label = { Text(selectedText) },
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
            colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.primary.copy(0.1f), labelColor = MaterialTheme.colorScheme.primary)
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelected(option); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionSmartDialog(
    cultivationsOfYear: List<Cultivation>,
    fields: List<FarmField>,
    selectedYear: String,
    onDismiss: () -> Unit,
    onConfirm: (FarmTransaction) -> Unit
) {
    var type by remember { mutableStateOf("Έσοδο") }
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var selectedCultivation by remember { mutableStateOf<Cultivation?>(null) }
    var expandedField by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Νέα Συναλλαγή", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    FilterChip(selected = type == "Έσοδο", onClick = { type = "Έσοδο" }, label = { Text("Έσοδο") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SuccessGreen.copy(0.2f), selectedLabelColor = SuccessGreen))
                    FilterChip(selected = type == "Έξοδο", onClick = { type = "Έξοδο" }, label = { Text("Έξοδο") }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = DeleteRed.copy(0.2f), selectedLabelColor = DeleteRed))
                }

                ExposedDropdownMenuBox(expanded = expandedField, onExpandedChange = { expandedField = !expandedField }) {
                    val displayText = selectedCultivation?.let { cult ->
                        val fName = fields.find { it.id == cult.fieldId }?.name ?: ""
                        "$fName - ${cult.cropType}"
                    } ?: "Γενικό (Επιχείρηση)"

                    OutlinedTextField(
                        value = displayText,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Αφορά Σπορά:") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedField) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    ExposedDropdownMenu(expanded = expandedField, onDismissRequest = { expandedField = false }) {
                        DropdownMenuItem(text = { Text("Γενικό (Επιχείρηση)") }, onClick = { selectedCultivation = null; expandedField = false })
                        cultivationsOfYear.forEach { cult ->
                            val fName = fields.find { it.id == cult.fieldId }?.name ?: ""
                            DropdownMenuItem(
                                text = { Text("$fName - ${cult.cropType} (${cult.season})") },
                                onClick = { selectedCultivation = cult; expandedField = false }
                            )
                        }
                    }
                }
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Περιγραφή") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Ποσό (€)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                DatePickerField(value = date, onValueChange = { date = it }, label = "Ημερομηνία")
            }
        },
        confirmButton = { Button(onClick = {
            val parsedAmount = amount.replace(",", ".").toDoubleOrNull() ?: 0.0
            val cult = selectedCultivation
            val parentField = cult?.let { c -> fields.find { it.id == c.fieldId } }
            val newTx = FarmTransaction(
                userId = "",
                cultivationId = cult?.id,
                fieldId = cult?.fieldId,
                fieldName = parentField?.name ?: "Γενικό",
                year = cult?.year ?: selectedYear,
                season = cult?.season ?: "",
                type = type,
                description = description,
                amount = parsedAmount,
                date = date
            )
            onConfirm(newTx)
        }) { Text("Αποθήκευση") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Ακύρωση") } }
    )
}