package com.example.agromanage.view_model

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.agromanage.data.local.AppDatabase
import com.example.agromanage.data.local.Cultivation
import com.example.agromanage.data.local.FarmField
import com.example.agromanage.data.local.FarmTask
import com.example.agromanage.data.local.FarmTransaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FarmViewModel(application: Application) : AndroidViewModel(application) {

    private val dbLocal = AppDatabase.getDatabase(application)
    private val farmDao = dbLocal.farmDao()
    private val taskDao = dbLocal.taskDao()
    private val transactionDao = dbLocal.transactionDao()
    private val firestore = FirebaseFirestore.getInstance()

    val currentUserId: String
        get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    private val _userRole = MutableStateFlow<String>("")
    val userRole: StateFlow<String> = _userRole.asStateFlow()

    private val rolePrefs = application.getSharedPreferences("AgroPrefs", android.content.Context.MODE_PRIVATE)
    private fun roleCacheKey(uid: String) = "user_role_$uid"

    // Λίστες Δεδομένων (StateFlows) για το UI
    private val _allFields = MutableStateFlow<List<FarmField>>(emptyList())
    val allFields: StateFlow<List<FarmField>> = _allFields.asStateFlow()

    private val _currentCultivations = MutableStateFlow<List<Cultivation>>(emptyList())
    val currentCultivations: StateFlow<List<Cultivation>> = _currentCultivations.asStateFlow()

    private val _allTasks = MutableStateFlow<List<FarmTask>>(emptyList())
    val allTasks: StateFlow<List<FarmTask>> = _allTasks.asStateFlow()

    private val _allTransactions = MutableStateFlow<List<FarmTransaction>>(emptyList())
    val allTransactions: StateFlow<List<FarmTransaction>> = _allTransactions.asStateFlow()

    init {
        refreshFields()
        refreshTransactions()
    }

    // --- ΡΟΛΟΣ ΧΡΗΣΤΗ ---
    fun fetchUserRole() {
        val uid = currentUserId
        if (uid.isEmpty()) return

        // 1. Πρώτα διάβασε από local cache για άμεσο UI (αποφεύγει "flash" λάθος ρόλου σε αργό δίκτυο)
        val cached = rolePrefs.getString(roleCacheKey(uid), null)
        if (cached != null && cached.isNotEmpty()) {
            _userRole.value = cached
            Log.d("FarmRole", "Loaded role from cache: $cached")
        }

        // 2. Μετά ρώτα Firestore για να ενημερώσεις (αν επιτύχει, γράφει το cache)
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val cloudRole = document.getString("role")
                    if (!cloudRole.isNullOrEmpty()) {
                        _userRole.value = cloudRole
                        rolePrefs.edit().putString(roleCacheKey(uid), cloudRole).apply()
                        Log.d("FarmRole", "Loaded role from Firestore: $cloudRole")
                    } else if (cached == null) {
                        // Document υπάρχει αλλά χωρίς role πεδίο — μόνο αν δεν έχουμε cache, default Αγρότης
                        _userRole.value = "Αγρότης"
                    }
                } else if (cached == null) {
                    Log.w("FarmRole", "User document not found in Firestore for uid=$uid — defaulting to Αγρότης")
                    _userRole.value = "Αγρότης"
                }
            }
            .addOnFailureListener { e ->
                Log.e("FarmRole", "Firestore fetchUserRole failed", e)
                // Αν έχουμε cache, το κρατάμε. Αν όχι, fallback Αγρότης για να μη μένει stuck στο loading.
                if (cached == null) _userRole.value = "Αγρότης"
            }
    }

    /** Αποθηκεύει τον ρόλο στο τοπικό cache άμεσα — καλείται κατά την εγγραφή. */
    fun cacheUserRole(role: String) {
        val uid = currentUserId
        if (uid.isNotEmpty()) {
            rolePrefs.edit().putString(roleCacheKey(uid), role).apply()
            _userRole.value = role
        }
    }

    // ==========================================
    // 1. ΧΩΡΑΦΙΑ (FarmFields)
    // ==========================================
    fun refreshFields() {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = currentUserId
            _allFields.value = if (uid.isNotEmpty()) farmDao.getAllFieldsForUser(uid) else emptyList()
        }
    }

    fun addField(field: FarmField) {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = currentUserId
            if (uid.isNotEmpty()) {
                val docRef = firestore.collection("users").document(uid).collection("fields").document()
                val fieldWithIds = field.copy(userId = uid, cloudId = docRef.id)

                farmDao.insertField(fieldWithIds)
                refreshFields()

                try { docRef.set(fieldWithIds) } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    fun deleteField(field: FarmField) {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = currentUserId
            if (uid.isNotEmpty() && field.cloudId.isNotEmpty()) {
                val userDoc = firestore.collection("users").document(uid)

                val cultivationsOfField = farmDao.getCultivationsForField(field.id)
                for (cult in cultivationsOfField) {
                    val tasksOfCult = taskDao.getTasksForCultivation(cult.id)
                    for (task in tasksOfCult) {
                        taskDao.deleteTask(task)
                        if (task.cloudId.isNotEmpty()) {
                            try { userDoc.collection("tasks").document(task.cloudId).delete() }
                            catch (e: Exception) { e.printStackTrace() }
                        }
                    }
                    val txsOfCult = transactionDao.getTransactionsForCultivation(cult.id)
                    for (tx in txsOfCult) {
                        transactionDao.deleteTransaction(tx)
                        if (tx.cloudId.isNotEmpty()) {
                            try { userDoc.collection("transactions").document(tx.cloudId).delete() }
                            catch (e: Exception) { e.printStackTrace() }
                        }
                    }
                    farmDao.deleteCultivation(cult)
                    if (cult.cloudId.isNotEmpty()) {
                        try { userDoc.collection("cultivations").document(cult.cloudId).delete() }
                        catch (e: Exception) { e.printStackTrace() }
                    }
                }

                farmDao.deleteField(field)
                refreshFields()
                refreshTransactions()
                try { userDoc.collection("fields").document(field.cloudId).delete() }
                catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    fun syncFieldsFromCloud() {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = currentUserId
            if (uid.isNotEmpty()) {
                try {
                    syncFieldsInternal(uid)
                    refreshFields()
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    /**
     * Κεντρικό sync που τρέχει σε σωστή σειρά εξάρτησης (fields → cultivations → tasks → transactions)
     * και κάνει re-mapping των τοπικών foreign-key IDs βάσει των cloudIds.
     * Καλείται όταν θέλεις να φέρεις ΟΛΑ τα δεδομένα από το cloud (π.χ. login σε νέα συσκευή).
     */
    fun syncAllFromCloud() {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = currentUserId
            if (uid.isEmpty()) {
                Log.w("FarmSync", "syncAllFromCloud: uid is empty, aborting")
                return@launch
            }
            Log.d("FarmSync", "===== syncAllFromCloud START (uid=$uid) =====")
            try {
                syncFieldsInternal(uid)
                syncCultivationsInternal(uid)
                syncTasksInternal(uid)
                syncTransactionsInternal(uid)
                refreshFields()
                refreshTransactions()
                Log.d("FarmSync", "===== syncAllFromCloud DONE =====")
            } catch (e: Exception) {
                Log.e("FarmSync", "syncAllFromCloud FAILED", e)
            }
        }
    }

    private suspend fun syncFieldsInternal(uid: String) {
        val snapshot = firestore.collection("users").document(uid).collection("fields").get().await()
        Log.d("FarmSync", "Fields: cloud has ${snapshot.size()} documents")
        var saved = 0
        for (document in snapshot.documents) {
            val cloudField = document.toObject(FarmField::class.java)?.copy(cloudId = document.id)
            if (cloudField == null) {
                Log.w("FarmSync", "Field DESERIALIZATION FAILED for doc ${document.id}, data=${document.data}")
                continue
            }
            val existing = farmDao.getFieldByCloudId(cloudField.cloudId)
            val toSave = if (existing != null) cloudField.copy(id = existing.id) else cloudField
            val newId = farmDao.insertField(toSave)
            Log.d("FarmSync", "  Field saved: cloudId=${toSave.cloudId} localId=${if (existing != null) existing.id else newId.toInt()} name=${toSave.name}")
            saved++
        }
        Log.d("FarmSync", "Fields saved: $saved")
    }

    private suspend fun syncCultivationsInternal(uid: String) {
        val snapshot = firestore.collection("users").document(uid).collection("cultivations").get().await()
        Log.d("FarmSync", "Cultivations: cloud has ${snapshot.size()} documents")
        var saved = 0
        var orphans = 0
        for (document in snapshot.documents) {
            val cloudCult = document.toObject(Cultivation::class.java)?.copy(cloudId = document.id)
            if (cloudCult == null) {
                Log.w("FarmSync", "Cultivation DESERIALIZATION FAILED for doc ${document.id}, data=${document.data}")
                continue
            }
            // Re-map fieldId βάσει του cloudId του parent field
            val parentField = if (cloudCult.fieldCloudId.isNotEmpty()) farmDao.getFieldByCloudId(cloudCult.fieldCloudId) else null
            val resolvedFieldId = parentField?.id ?: cloudCult.fieldId
            if (parentField == null) {
                orphans++
                Log.w("FarmSync", "  Cultivation ORPHAN: cloudId=${cloudCult.cloudId} cropType=${cloudCult.cropType} fieldCloudId='${cloudCult.fieldCloudId}' (parent field not found locally; using fallback fieldId=${cloudCult.fieldId})")
            }
            val existing = farmDao.getCultivationByCloudId(cloudCult.cloudId)
            val toSave = if (existing != null) {
                cloudCult.copy(id = existing.id, fieldId = resolvedFieldId)
            } else {
                cloudCult.copy(fieldId = resolvedFieldId)
            }
            farmDao.insertCultivation(toSave)
            Log.d("FarmSync", "  Cultivation saved: cloudId=${toSave.cloudId} cropType=${toSave.cropType} fieldId=${toSave.fieldId}")
            saved++
        }
        Log.d("FarmSync", "Cultivations saved: $saved (orphans: $orphans)")
    }

    private suspend fun syncTasksInternal(uid: String) {
        val snapshot = firestore.collection("users").document(uid).collection("tasks").get().await()
        Log.d("FarmSync", "Tasks: cloud has ${snapshot.size()} documents")
        var saved = 0
        var orphans = 0
        for (document in snapshot.documents) {
            val cloudTask = document.toObject(FarmTask::class.java)?.copy(cloudId = document.id)
            if (cloudTask == null) {
                Log.w("FarmSync", "Task DESERIALIZATION FAILED for doc ${document.id}, data=${document.data}")
                continue
            }
            val parentCult = if (cloudTask.cultivationCloudId.isNotEmpty()) farmDao.getCultivationByCloudId(cloudTask.cultivationCloudId) else null
            val resolvedCultId = parentCult?.id ?: cloudTask.cultivationId
            if (parentCult == null) {
                orphans++
                Log.w("FarmSync", "  Task ORPHAN: cloudId=${cloudTask.cloudId} title=${cloudTask.title} cultivationCloudId='${cloudTask.cultivationCloudId}'")
            }
            val existing = taskDao.getTaskByCloudId(cloudTask.cloudId)
            val toSave = if (existing != null) {
                cloudTask.copy(id = existing.id, cultivationId = resolvedCultId)
            } else {
                cloudTask.copy(cultivationId = resolvedCultId)
            }
            taskDao.insertTask(toSave)
            saved++
        }
        Log.d("FarmSync", "Tasks saved: $saved (orphans: $orphans)")
    }

    private suspend fun syncTransactionsInternal(uid: String) {
        val snapshot = firestore.collection("users").document(uid).collection("transactions").get().await()
        Log.d("FarmSync", "Transactions: cloud has ${snapshot.size()} documents")
        var saved = 0
        for (document in snapshot.documents) {
            val cloudTx = document.toObject(FarmTransaction::class.java)?.copy(cloudId = document.id)
            if (cloudTx == null) {
                Log.w("FarmSync", "Transaction DESERIALIZATION FAILED for doc ${document.id}, data=${document.data}")
                continue
            }
            val resolvedCultId = cloudTx.cultivationCloudId?.takeIf { it.isNotEmpty() }
                ?.let { farmDao.getCultivationByCloudId(it)?.id }
                ?: cloudTx.cultivationId
            val resolvedFieldId = cloudTx.fieldCloudId?.takeIf { it.isNotEmpty() }
                ?.let { farmDao.getFieldByCloudId(it)?.id }
                ?: cloudTx.fieldId
            val existing = transactionDao.getTransactionByCloudId(cloudTx.cloudId)
            val toSave = if (existing != null) {
                cloudTx.copy(id = existing.id, cultivationId = resolvedCultId, fieldId = resolvedFieldId)
            } else {
                cloudTx.copy(cultivationId = resolvedCultId, fieldId = resolvedFieldId)
            }
            transactionDao.insertTransaction(toSave)
            saved++
        }
        Log.d("FarmSync", "Transactions saved: $saved")
    }

    // ==========================================
    // 2. ΚΑΛΛΙΕΡΓΕΙΕΣ (Cultivations)
    // ==========================================
    fun refreshCultivationsForField(fieldId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _currentCultivations.value = farmDao.getCultivationsForField(fieldId)
        }
    }

    fun addCultivation(cultivation: Cultivation) {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = currentUserId
            if (uid.isNotEmpty()) {
                val parentField = farmDao.getFieldById(cultivation.fieldId)
                val parentFieldCloudId = parentField?.cloudId ?: ""

                val docRef = firestore.collection("users").document(uid).collection("cultivations").document()
                val cultivationWithIds = cultivation.copy(
                    cloudId = docRef.id,
                    fieldCloudId = parentFieldCloudId
                )

                farmDao.insertCultivation(cultivationWithIds)
                refreshCultivationsForField(cultivationWithIds.fieldId)

                try { docRef.set(cultivationWithIds) } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    fun deleteCultivation(cultivation: Cultivation) {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = currentUserId
            if (uid.isNotEmpty() && cultivation.cloudId.isNotEmpty()) {
                val userDoc = firestore.collection("users").document(uid)

                val tasksOfCult = taskDao.getTasksForCultivation(cultivation.id)
                for (task in tasksOfCult) {
                    taskDao.deleteTask(task)
                    if (task.cloudId.isNotEmpty()) {
                        try { userDoc.collection("tasks").document(task.cloudId).delete() }
                        catch (e: Exception) { e.printStackTrace() }
                    }
                }
                val txsOfCult = transactionDao.getTransactionsForCultivation(cultivation.id)
                for (tx in txsOfCult) {
                    transactionDao.deleteTransaction(tx)
                    if (tx.cloudId.isNotEmpty()) {
                        try { userDoc.collection("transactions").document(tx.cloudId).delete() }
                        catch (e: Exception) { e.printStackTrace() }
                    }
                }

                farmDao.deleteCultivation(cultivation)
                refreshCultivationsForField(cultivation.fieldId)
                refreshTransactions()
                try { userDoc.collection("cultivations").document(cultivation.cloudId).delete() }
                catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    fun syncCultivationsFromCloud() {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = currentUserId
            if (uid.isNotEmpty()) {
                try { syncCultivationsInternal(uid) }
                catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    // ==========================================
    // 3. ΕΡΓΑΣΙΕΣ (Tasks)
    // ==========================================
    fun refreshTasksForCultivation(cultivationId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _allTasks.value = taskDao.getTasksForCultivation(cultivationId)
        }
    }

    fun refreshTasksForField(fieldId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _allTasks.value = taskDao.getAllTasksForField(fieldId)
        }
    }

    fun addTask(task: FarmTask, cultivationId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = currentUserId
            if (uid.isNotEmpty()) {
                val parentCult = farmDao.getCultivationById(cultivationId)
                val parentCultCloudId = parentCult?.cloudId ?: ""

                val docRef = firestore.collection("users").document(uid).collection("tasks").document()
                val taskWithIds = task.copy(
                    cultivationId = cultivationId,
                    cultivationCloudId = parentCultCloudId,
                    cloudId = docRef.id
                )

                taskDao.insertTask(taskWithIds)
                refreshTasksForCultivation(cultivationId)

                try { docRef.set(taskWithIds) } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    fun deleteTask(task: FarmTask) {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = currentUserId
            if (uid.isNotEmpty() && task.cloudId.isNotEmpty()) {
                taskDao.deleteTask(task)
                refreshTasksForCultivation(task.cultivationId)
                try {
                    firestore.collection("users").document(uid).collection("tasks").document(task.cloudId).delete()
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    fun syncTasksFromCloud() {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = currentUserId
            if (uid.isNotEmpty()) {
                try { syncTasksInternal(uid) }
                catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    // ==========================================
    // 4. ΟΙΚΟΝΟΜΙΚΑ (Transactions)
    // ==========================================
    fun refreshTransactions() {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = currentUserId
            _allTransactions.value = if (uid.isNotEmpty()) transactionDao.getAllTransactionsForUser(uid) else emptyList()
        }
    }

    fun addTransaction(transaction: FarmTransaction) {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = currentUserId
            if (uid.isNotEmpty()) {
                val parentCultCloudId = transaction.cultivationId?.let { cid ->
                    farmDao.getCultivationById(cid)?.cloudId
                }
                val parentFieldCloudId = transaction.fieldId?.let { fid ->
                    farmDao.getFieldById(fid)?.cloudId
                }

                val docRef = firestore.collection("users").document(uid).collection("transactions").document()
                val transactionWithIds = transaction.copy(
                    userId = uid,
                    cloudId = docRef.id,
                    cultivationCloudId = parentCultCloudId,
                    fieldCloudId = parentFieldCloudId
                )

                transactionDao.insertTransaction(transactionWithIds)
                refreshTransactions()

                try { docRef.set(transactionWithIds) } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    fun deleteTransaction(transaction: FarmTransaction) {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = currentUserId
            if (uid.isNotEmpty() && transaction.cloudId.isNotEmpty()) {
                transactionDao.deleteTransaction(transaction)
                refreshTransactions()
                try {
                    firestore.collection("users").document(uid).collection("transactions").document(transaction.cloudId).delete()
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    fun syncTransactionsFromCloud() {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = currentUserId
            if (uid.isNotEmpty()) {
                try {
                    syncTransactionsInternal(uid)
                    refreshTransactions()
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }
}