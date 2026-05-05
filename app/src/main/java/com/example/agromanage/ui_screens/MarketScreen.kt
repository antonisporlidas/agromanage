package com.example.agromanage.ui_screens

import android.content.Context
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.agromanage.data.local.MarketPost
import com.example.agromanage.view_model.FarmViewModel
import com.example.agromanage.view_model.MarketViewModel
import com.example.agromanage.ui.theme.* import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.lazy.rememberLazyListState

val marketVegetablesList = listOf(
    "Αγγούρια", "Αγγούρια Κνωσσού", "Άνηθος-Μαϊντανός", "Αντίδια", "Βασιλικός-Δυόσμος",
    "Καρότα", "Κολοκυθάκια", "Κουνουπίδια", "Κρεμμυδάκια", "Κρεμμύδια", "Λάχανα",
    "Μελιτζάνες μακρύκαρπες", "Μελιτζάνες φλάσκες", "Μαρούλια", "Μπρόκολα", "Πατάτες",
    "Παντζάρια", "Πιπεριές μακρύκαρπες", "Πιπεριές χονδρόκαρπες", "Πιπεριές φλωρίνης",
    "Πιπεριές τσούσκες", "Πράσα", "Ρόκα", "Σαλάτες", "Σέλινο", "Σκόρδα", "Σπανάκι",
    "Τομάτες", "Τοματίνια"
)

val marketFruitsList = listOf(
    "Ακτινίδια", "Αβοκάντο", "Ανανάς", "Αχλάδια αμπατε", "Αχλάδια κρυστάλια",
    "Γκρέιπ φρούτ", "Λεμόνια", "Μανταρίνια", "Μήλα λοιπές", "Μήλα στάρκιν",
    "Μπανάνες", "Πορτοκάλια", "Φράουλες"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketScreen(viewModelFarm: FarmViewModel = viewModel(), viewModelMarket: MarketViewModel = viewModel()) {
    val posts by viewModelMarket.marketPosts.collectAsState()
    val hasMorePosts by viewModelMarket.hasMorePosts.collectAsState()
    val sortedMode by viewModelMarket.sortedMode.collectAsState()
    val userRole by viewModelFarm.userRole.collectAsState()
    val currentUserId = viewModelFarm.currentUserId

    var showAddDialog by remember { mutableStateOf(false) }
    var postToDelete by remember { mutableStateOf<MarketPost?>(null) }

    // --- ΜΕΤΑΒΛΗΤΕΣ ΓΙΑ ΤΑ ΦΙΛΤΡΑ & ΤΟ BOTTOM SHEET ---
    var showFilterSheet by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf("date") }
    var showOnlyMine by remember { mutableStateOf(false) }

    var selectedCategoryFilter by remember { mutableStateOf("Όλες οι Κατηγορίες") }
    var selectedCropFilter by remember { mutableStateOf("Όλα τα Είδη") }

    // Τοπικό φιλτράρισμα βάσει Κατηγορίας/Είδους
    val filteredPosts = posts.filter { post ->
        val matchesCategory = when (selectedCategoryFilter) {
            "Λαχανικά" -> marketVegetablesList.contains(post.cropType)
            "Φρούτα" -> marketFruitsList.contains(post.cropType)
            else -> true
        }
        val matchesCrop = if (selectedCropFilter == "Όλα τα Είδη") true else post.cropType == selectedCropFilter
        matchesCategory && matchesCrop
    }

    val listState = rememberLazyListState()
    val isAtBottom by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleItem >= totalItems - 1 && totalItems > 0
        }
    }

    // Φορτώνουμε κι άλλες ΜΟΝΟ αν:
    //  α) ο χρήστης έφτασε στο τέλος της λίστας
    //  β) υπάρχουν πραγματικά κι άλλες αγγελίες στη βάση (hasMorePosts)
    //  γ) δεν τρέχει ενεργό φίλτρο που κρύβει στοιχεία (αλλιώς θα ζητούσαμε άσκοπα)
    val noFilterActive = filteredPosts.size == posts.size
    LaunchedEffect(isAtBottom, hasMorePosts, noFilterActive) {
        if (isAtBottom && hasMorePosts && noFilterActive) {
            viewModelMarket.loadMorePosts()
        }
    }

    Scaffold(
        floatingActionButton = {
            if (userRole == "Αγρότης") {
                ExtendedFloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Νέα Αγγελία") }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (userRole == "Έμπορος") "Διαθέσιμη Παραγωγή" else "Πίνακας Αγγελιών",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (userRole == "Έμπορος") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                )
                FilledTonalButton(
                    onClick = { showFilterSheet = true },
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.FilterList, contentDescription = "Φίλτρα", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Φίλτρα", style = MaterialTheme.typography.labelLarge)
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = "${filteredPosts.size} αποτελέσματα",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (filteredPosts.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Storefront,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Δεν βρέθηκαν αγγελίες.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Στο sortedMode τα δεδομένα έρχονται ήδη στη σωστή σειρά από το Firestore.
                    // Στο default (live listener) τα αναποδογυρίζουμε για να φαίνονται οι πιο νέες πρώτες.
                    val displayPosts = if (sortedMode) filteredPosts else filteredPosts.reversed()
                    items(displayPosts) { post ->
                        MarketPostCard(
                            post = post,
                            userRole = userRole,
                            currentUserId = currentUserId,
                            onInterestClick = { viewModelMarket.expressInterest(post.id) },
                            onDeleteClick = { postToDelete = post }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }

        // ========================================================
        // ΤΟ ΣΥΡΤΑΡΙ ΦΙΛΤΡΩΝ & ΤΑΞΙΝΟΜΗΣΗΣ (BOTTOM SHEET)
        // ========================================================
        if (showFilterSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

            ModalBottomSheet(
                onDismissRequest = { showFilterSheet = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("Φίλτρα & Ταξινόμηση", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // 1. ΤΑΞΙΝΟΜΗΣΗ (Firestore orderBy)
                    Text("Ταξινόμηση βάσει:", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = sortBy == "date",
                            onClick = { sortBy = "date" },
                            label = { Text("Πιο Πρόσφατα") }
                        )
                        FilterChip(
                            selected = sortBy == "priceMin",
                            onClick = { sortBy = "priceMin" },
                            label = { Text("Χαμηλότερη Τιμή") }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 2. ΚΑΤΗΓΟΡΙΑ & ΕΙΔΟΣ (Τοπικά Φίλτρα)
                    Text("Κατηγορία Προϊόντος:", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MarketFilterDropdown(
                            label = selectedCategoryFilter,
                            options = listOf("Όλες οι Κατηγορίες", "Λαχανικά", "Φρούτα"),
                            onSelected = {
                                selectedCategoryFilter = it
                                selectedCropFilter = "Όλα τα Είδη"
                            }
                        )

                        val cropOptions = mutableListOf("Όλα τα Είδη")
                        if (selectedCategoryFilter == "Λαχανικά") cropOptions.addAll(marketVegetablesList)
                        else if (selectedCategoryFilter == "Φρούτα") cropOptions.addAll(marketFruitsList)
                        else {
                            cropOptions.addAll(marketVegetablesList)
                            cropOptions.addAll(marketFruitsList)
                        }

                        MarketFilterDropdown(
                            label = selectedCropFilter,
                            options = cropOptions,
                            onSelected = { selectedCropFilter = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 3. ΕΠΙΛΟΓΗ ΑΓΡΟΤΗ (Firestore whereEqualTo)
                    if (userRole == "Αγρότης") {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Checkbox(checked = showOnlyMine, onCheckedChange = { showOnlyMine = it })
                            Text("Προβολή μόνο των δικών μου αγγελιών", fontWeight = FontWeight.Medium)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // 4. ΚΟΥΜΠΙ ΕΦΑΡΜΟΓΗΣ
                    Button(
                        onClick = {
                            showFilterSheet = false
                            viewModelMarket.applyFilters(showOnlyMine, sortBy)
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Εφαρμογή Φίλτρων", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        if (showAddDialog) {
            AddMarketPostDialog(
                onDismiss = { showAddDialog = false },
                onConfirm = { newPost, imageUri ->
                    viewModelMarket.publishPost(newPost, imageUri)
                    showAddDialog = false
                }
            )
        }

        if (postToDelete != null) {
            AlertDialog(
                onDismissRequest = { postToDelete = null },
                icon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Διαγραφή Αγγελίας") },
                text = { Text("Είστε σίγουροι ότι θέλετε να διαγράψετε την αγγελία για: ${postToDelete?.cropType};") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModelMarket.deleteMarketPost(postToDelete!!.id)
                            postToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) { Text("Διαγραφή") }
                },
                dismissButton = {
                    TextButton(onClick = { postToDelete = null }) { Text("Ακύρωση") }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketFilterDropdown(label: String, options: List<String>, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = label != "Όλες οι Κατηγορίες" && label != "Όλα τα Είδη",
            onClick = { expanded = true },
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelected(option); expanded = false })
            }
        }
    }
}

@Composable
fun MarketPostCard(
    post: MarketPost,
    userRole: String,
    currentUserId: String,
    onInterestClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val isMyPost = post.farmerId == currentUserId
    val iAmInterested = post.interestedMerchants.contains(currentUserId)

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {

            // Header: crop name + date + delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        post.cropType,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        post.date,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (userRole == "Αγρότης" && isMyPost) {
                    IconButton(onClick = onDeleteClick) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Διαγραφή",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Person,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    "Από: ${post.farmerName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (post.imageUrl.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                AsyncImage(
                    model = post.imageUrl,
                    contentDescription = "Εικόνα Προϊόντος",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(MaterialTheme.shapes.medium),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quantity + Price row
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "Διαθέσιμη Ποσότητα",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            post.quantity,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "Τιμή (Εκτίμηση)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "${post.priceMin}€ – ${post.priceMax}€ / κιλό",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (post.description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "\"${post.description}\"",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(10.dp))

            if (userRole == "Έμπορος") {
                if (iAmInterested) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Εκδηλώσατε ενδιαφέρον!",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Email,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    post.farmerEmail,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (post.farmerPhone.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Phone,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        post.farmerPhone,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Button(
                        onClick = onInterestClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        )
                    ) {
                        Icon(Icons.Default.Handshake, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ενδιαφέρομαι", style = MaterialTheme.typography.labelLarge)
                    }
                }
            } else if (userRole == "Αγρότης" && isMyPost) {
                val count = post.interestedMerchants.size
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (count > 0) MaterialTheme.colorScheme.tertiaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            if (count > 0) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                            null,
                            tint = if (count > 0) MaterialTheme.colorScheme.onTertiaryContainer
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (count > 0) "Ενδιαφέρονται $count έμποροι!" else "Αναμονή για ενδιαφέρον...",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = if (count > 0) MaterialTheme.colorScheme.onTertiaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMarketPostDialog(onDismiss: () -> Unit, onConfirm: (MarketPost, Uri?) -> Unit) {
    var farmerName by remember { mutableStateOf("") }
    var farmerPhone by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var priceMin by remember { mutableStateOf("") }
    var priceMax by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Λαχανικά") }
    var cropExpanded by remember { mutableStateOf(false) }
    var cropType by remember { mutableStateOf(marketVegetablesList[0]) }

    val context = LocalContext.current
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> if (uri != null) selectedImageUri = uri }
    )

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success -> if (success) selectedImageUri = tempCameraUri }
    )

    val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email ?: ""

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Δημοσίευση Παραγωγής") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { tempCameraUri = createImageUri(context); cameraLauncher.launch(tempCameraUri!!) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null); Spacer(modifier = Modifier.width(4.dp)); Text("Κάμερα", fontSize = 12.sp)
                    }
                    OutlinedButton(onClick = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null); Spacer(modifier = Modifier.width(4.dp)); Text("Συλλογή", fontSize = 12.sp)
                    }
                }
                if (selectedImageUri != null) {
                    Text("Η εικόνα φορτώθηκε! ✔️", color = SuccessGreen, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                OutlinedTextField(value = farmerName, onValueChange = { farmerName = it }, label = { Text("Όνομα Παραγωγού") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = farmerPhone, onValueChange = { farmerPhone = it }, label = { Text("Τηλέφωνο Επικοινωνίας") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())

                Spacer(modifier = Modifier.height(4.dp))
                Text("Κατηγορία Προϊόντος", style = MaterialTheme.typography.labelMedium)

                val categories = listOf("Λαχανικά", "Φρούτα")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    categories.forEachIndexed { index, label ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = categories.size),
                            onClick = {
                                selectedCategory = label
                                cropType = if (label == "Λαχανικά") marketVegetablesList[0] else marketFruitsList[0]
                            },
                            selected = selectedCategory == label
                        ) { Text(label, fontSize = 13.sp) }
                    }
                }
                val currentList = if (selectedCategory == "Λαχανικά") marketVegetablesList else marketFruitsList
                ExposedDropdownMenuBox(expanded = cropExpanded, onExpandedChange = { cropExpanded = !cropExpanded }) {
                    OutlinedTextField(value = cropType, onValueChange = {}, readOnly = true, label = { Text("Επιλογή Είδους") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cropExpanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                    ExposedDropdownMenu(expanded = cropExpanded, onDismissRequest = { cropExpanded = false }) {
                        currentList.forEach { item -> DropdownMenuItem(text = { Text(item) }, onClick = { cropType = item; cropExpanded = false }) }
                    }
                }
                OutlinedTextField(value = quantity, onValueChange = { quantity = it }, label = { Text("Ποσότητα (π.χ. 1 τόνος)") }, modifier = Modifier.fillMaxWidth())
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = priceMin, onValueChange = { priceMin = it }, label = { Text("Ελάχ. Τιμή") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = priceMax, onValueChange = { priceMax = it }, label = { Text("Μέγ. Τιμή") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Σχόλια (Προαιρετικό)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = {
            Button(onClick = {
                val pMin = priceMin.replace(",", ".").toDoubleOrNull() ?: 0.0
                val pMax = priceMax.replace(",", ".").toDoubleOrNull() ?: 0.0
                val currentDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
                val newPost = MarketPost(
                    farmerName = farmerName.ifEmpty { "Ανώνυμος" },
                    farmerEmail = currentUserEmail,
                    farmerPhone = farmerPhone,
                    cropType = cropType,
                    quantity = quantity,
                    priceMin = pMin,
                    priceMax = pMax,
                    description = description,
                    date = currentDate,
                    dateTimestamp = com.google.firebase.Timestamp.now() // για χρονολογική ταξινόμηση
                )
                onConfirm(newPost, selectedImageUri)
            }) { Text("Δημοσίευση") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Ακύρωση") } }
    )
}

fun createImageUri(context: Context): Uri {
    val file = File(context.cacheDir, "camera_image_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, context.packageName + ".provider", file)
}