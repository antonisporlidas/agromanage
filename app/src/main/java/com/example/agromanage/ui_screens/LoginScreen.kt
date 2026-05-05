package com.example.agromanage.ui_screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val firestore = FirebaseFirestore.getInstance()
    val context = androidx.compose.ui.platform.LocalContext.current

    fun cacheRoleLocally(uid: String, role: String) {
        context.getSharedPreferences("AgroPrefs", android.content.Context.MODE_PRIVATE)
            .edit()
            .putString("user_role_$uid", role)
            .apply()
    }

    var isLoginMode by remember { mutableStateOf(true) }
    var isFarmer by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    val roleColor =  MaterialTheme.colorScheme.primary
    val roleContainerColor = MaterialTheme.colorScheme.primaryContainer

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // --- App Icon ---
                Surface(
                    shape = CircleShape,
                    color = roleContainerColor,
                    modifier = Modifier.size(96.dp),
                    shadowElevation = 4.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isFarmer) Icons.Default.Agriculture else Icons.Default.Storefront,
                            contentDescription = null,
                            tint = roleColor,
                            modifier = Modifier.size(52.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "AgroManage",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(32.dp))

                // --- Form Card ---
                ElevatedCard(
                    shape = MaterialTheme.shapes.extraLarge,
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // --- Role Selector (μόνο σε εγγραφή — στο login ο ρόλος έρχεται από Firestore) ---
                        AnimatedVisibility(visible = !isLoginMode) {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(
                                    "Τύπος Λογαριασμού",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(24.dp))
                                            .background(if (isFarmer) roleColor else Color.Transparent)
                                            .clickable { isFarmer = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Agriculture,
                                                contentDescription = null,
                                                tint = if (isFarmer) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                "Αγρότης",
                                                color = if (isFarmer) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(24.dp))
                                            .background(if (!isFarmer) roleColor else Color.Transparent)
                                            .clickable { isFarmer = false },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Storefront,
                                                contentDescription = null,
                                                tint = if (!isFarmer) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                "Έμπορος",
                                                color = if (!isFarmer) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }

                        // --- Fields ---
                        AnimatedVisibility(visible = !isLoginMode) {
                            OutlinedTextField(
                                value = name,
                                onValueChange = { name = it },
                                label = { Text("Ονοματεπώνυμο") },
                                leadingIcon = {
                                    Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                singleLine = true
                            )
                        }

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email") },
                            leadingIcon = {
                                Icon(Icons.Default.Email, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Κωδικός") },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        if (isLoading) {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = roleColor)
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (email.isEmpty() || password.isEmpty() || (!isLoginMode && name.isEmpty())) {
                                        scope.launch { snackbarHostState.showSnackbar("Συμπληρώστε όλα τα πεδία") }
                                        return@Button
                                    }
                                    isLoading = true
                                    if (isLoginMode) {
                                        auth.signInWithEmailAndPassword(email, password)
                                            .addOnCompleteListener { task ->
                                                if (task.isSuccessful) {
                                                    val uid = task.result?.user?.uid
                                                    if (uid != null) {
                                                        // Φέρνουμε τον ρόλο από Firestore και τον κάνουμε cache πριν μπει στο app
                                                        // (καλύπτει και cross-device login: άλλη συσκευή, άδειο cache)
                                                        firestore.collection("users").document(uid).get()
                                                            .addOnSuccessListener { doc ->
                                                                val cloudRole = doc?.getString("role")
                                                                if (!cloudRole.isNullOrEmpty()) {
                                                                    cacheRoleLocally(uid, cloudRole)
                                                                }
                                                                onLoginSuccess()
                                                            }
                                                            .addOnFailureListener {
                                                                // Δεν φέραμε ρόλο — δεν πειράζει, το fetchUserRole στο MainActivity θα δοκιμάσει ξανά
                                                                onLoginSuccess()
                                                            }
                                                    } else {
                                                        onLoginSuccess()
                                                    }
                                                } else {
                                                    isLoading = false
                                                    scope.launch { snackbarHostState.showSnackbar("Λάθος στοιχεία ή ο λογαριασμός δεν υπάρχει") }
                                                }
                                            }
                                    } else {
                                        auth.createUserWithEmailAndPassword(email, password)
                                            .addOnCompleteListener { task ->
                                                if (task.isSuccessful) {
                                                    val uid = task.result?.user?.uid
                                                    if (uid != null) {
                                                        val chosenRole = if (isFarmer) "Αγρότης" else "Έμπορος"
                                                        // Cache τον ρόλο τοπικά ΑΜΕΣΩΣ — αν αποτύχει το Firestore, θα τον έχουμε
                                                        cacheRoleLocally(uid, chosenRole)

                                                        val userProfile = hashMapOf(
                                                            "name" to name,
                                                            "email" to email,
                                                            "role" to chosenRole
                                                        )
                                                        firestore.collection("users").document(uid).set(userProfile)
                                                            .addOnSuccessListener { onLoginSuccess() }
                                                            .addOnFailureListener {
                                                                // Παρότι απέτυχε το Firestore, ο ρόλος υπάρχει στο τοπικό cache
                                                                // Δίνουμε στον χρήστη ένα μήνυμα αλλά συνεχίζουμε
                                                                isLoading = false
                                                                scope.launch { snackbarHostState.showSnackbar("Επιτυχία (offline). Ο ρόλος αποθηκεύτηκε τοπικά.") }
                                                                onLoginSuccess()
                                                            }
                                                    }
                                                } else {
                                                    isLoading = false
                                                    scope.launch { snackbarHostState.showSnackbar("Η εγγραφή απέτυχε: ${task.exception?.message}") }
                                                }
                                            }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = MaterialTheme.shapes.medium,
                                colors = ButtonDefaults.buttonColors(containerColor = roleColor)
                            ) {
                                Text(
                                    if (isLoginMode) "Είσοδος" else "Εγγραφή",
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                TextButton(onClick = { isLoginMode = !isLoginMode }) {
                    Text(
                        if (isLoginMode) "Δεν έχετε λογαριασμό; Εγγραφή"
                        else "Έχετε λογαριασμό; Σύνδεση",
                        color = roleColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}