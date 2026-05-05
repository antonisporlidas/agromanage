package com.example.agromanage.view_model

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.agromanage.data.local.MarketPost
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class MarketViewModel(application: Application) : AndroidViewModel(application) {

    private val firestore = FirebaseFirestore.getInstance()

    private val currentUserId: String
        get() = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    // ==========================================
    // --- STATE FLOWS & ΜΕΤΑΒΛΗΤΕΣ ΑΓΟΡΑΣ ---
    // ==========================================
    private val _marketPosts = MutableStateFlow<List<MarketPost>>(emptyList())
    val marketPosts: StateFlow<List<MarketPost>> = _marketPosts.asStateFlow()

    private var marketListener: ListenerRegistration? = null
    private var myPostsListener: ListenerRegistration? = null // ξεχωριστός listener για ντιν
    private val pageSize = 10L
    private var lastVisibleDoc: DocumentSnapshot? = null
    private var isLoadingMore = false

    private val _hasMorePosts = MutableStateFlow(true)
    val hasMorePosts: StateFlow<Boolean> = _hasMorePosts.asStateFlow()

    // True όταν εφαρμόζεται ταξινόμηση/φίλτρο που έχει ήδη σωστή σειρά,
    // ώστε η UI να ΜΗΝ κάνει reverse τη λίστα.
    private val _sortedMode = MutableStateFlow(false)
    val sortedMode: StateFlow<Boolean> = _sortedMode.asStateFlow()

    // Παρακολουθούμε τον uid του τρέχοντος listener — ώστε ο AuthStateListener να επανεκκινεί
    // ΜΟΝΟ όταν αλλάζει ο χρήστης, όχι σε κάθε εγγραφή του listener (που πυροδοτεί immediate fire).
    private var listenerUid: String? = null

    // In-memory flag: true στο πρώτο fire του listener μετά από κάθε attach. Δεν αποθηκεύεται
    // σε prefs ώστε να μην επηρεάζεται από Auto Backup σε reinstall/άλλη συσκευή.
    private var isFirstMyPostsFire = true
    private val authStateListener = FirebaseAuth.AuthStateListener {
        val newUid = currentUserId
        if (newUid != listenerUid) {
            android.util.Log.d("MarketVM", "Auth changed: $listenerUid -> $newUid, restarting listener")
            myPostsListener?.remove()
            myPostsListener = null
            listenerUid = newUid
            if (newUid.isNotEmpty()) {
                startMyPostsNotificationsListener()
            }
        }
    }

    init {
        fetchMarketPosts()
        // Αρχικό setup μόνο αν είναι ήδη συνδεδεμένος χρήστης
        if (currentUserId.isNotEmpty()) {
            listenerUid = currentUserId
            startMyPostsNotificationsListener()
        }
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener)
    }

    override fun onCleared() {
        super.onCleared()
        FirebaseAuth.getInstance().removeAuthStateListener(authStateListener)
        marketListener?.remove()
        marketListener = null
        myPostsListener?.remove()
        myPostsListener = null
    }

    // ==========================================
    // --- LISTENER ΕΙΔΟΠΟΙΗΣΕΩΝ ΓΙΑ ΟΛΕΣ ΤΙΣ ΔΙΚΕΣ ΣΟΥ ΑΓΓΕΛΙΕΣ ---
    // (ανεξάρτητα από pagination ή φίλτρα)
    // ==========================================
    private fun startMyPostsNotificationsListener() {
        myPostsListener?.remove()
        isFirstMyPostsFire = true
        val uid = currentUserId
        if (uid.isEmpty()) return

        myPostsListener = firestore.collection("market")
            .whereEqualTo("farmerId", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                val prefs = getApplication<Application>()
                    .getSharedPreferences("AgroPrefs", Context.MODE_PRIVATE)
                val myPosts = snapshot.toObjects(MarketPost::class.java)

                // Πρώτο fire μετά το attach (νέο install / νέα συσκευή / αλλαγή χρήστη):
                // απλά καταγραφή των τρεχόντων counts ΧΩΡΙΣ ειδοποιήσεις.
                // Έτσι αποτρέπεται flood από stale prefs που μπορεί να επανέφερε το Auto Backup.
                if (isFirstMyPostsFire) {
                    val editor = prefs.edit()
                    for (myPost in myPosts) {
                        editor.putInt("post_${myPost.id}_count", myPost.interestedMerchants.size)
                    }
                    editor.apply()
                    isFirstMyPostsFire = false
                    return@addSnapshotListener
                }

                // Οι ειδοποιήσεις market interest παραδίδονται αποκλειστικά μέσω FCM
                // (AgroFirebaseMessagingService). Εδώ διατηρούμε μόνο το count tracking
                // ώστε να μη μείνουν stale prefs σε reinstall/restore.
                for (myPost in myPosts) {
                    val prefsKey = "post_${myPost.id}_count"
                    prefs.edit().putInt(prefsKey, myPost.interestedMerchants.size).apply()
                }
            }
    }

    // ==========================================
    // --- ΛΕΙΤΟΥΡΓΙΕΣ FEED & ΣΕΛΙΔΟΠΟΙΗΣΗΣ ---
    // ==========================================
    fun fetchMarketPosts() {
        marketListener?.remove()
        lastVisibleDoc = null
        _hasMorePosts.value = true
        _sortedMode.value = false // επιστροφή στην default κατάσταση
        _marketPosts.value = emptyList() // καθαρό ξεκίνημα — η μέρτζε λογική παρακάτω χτίζει τη λίστα

        marketListener = firestore.collection("market")
            .limit(pageSize)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null) {
                    val newPosts = snapshot.toObjects(MarketPost::class.java)
                    lastVisibleDoc = snapshot.documents.lastOrNull()
                    _hasMorePosts.value = newPosts.size.toLong() >= pageSize

                    // ΣΗΜΕΙΩΣΗ: Η λογική ειδοποιήσεων μετακινήθηκε στον
                    // startMyPostsNotificationsListener() για να καλύπτει ΟΛΕΣ τις
                    // αγγελίες του χρήστη, ανεξάρτητα από pagination/φίλτρα.

                    // ΣΗΜΑΝΤΙΚΟ: αντί να αντικαταστήσουμε όλη τη λίστα, κρατάμε τις
                    // αγγελίες που έχουν ήδη φορτωθεί μέσω loadMorePosts (paginated extras).
                    // Αλλιώς ο listener θα έσβηνε όσα έχει κατεβάσει η σελιδοποίηση.
                    val newIds = newPosts.map { it.id }.toSet()
                    val extras = _marketPosts.value.filter { it.id !in newIds }
                    _marketPosts.value = newPosts + extras
                }
            }
    }

    fun loadMorePosts() {
        if (!_hasMorePosts.value || isLoadingMore) return
        val cursor = lastVisibleDoc ?: return

        isLoadingMore = true
        firestore.collection("market")
            .startAfter(cursor)
            .limit(pageSize)
            .get()
            .addOnSuccessListener { snapshot ->
                val morePosts = snapshot.toObjects(MarketPost::class.java)
                if (morePosts.isNotEmpty()) {
                    lastVisibleDoc = snapshot.documents.last()
                    _marketPosts.value = _marketPosts.value + morePosts
                }
                if (morePosts.size.toLong() < pageSize) {
                    _hasMorePosts.value = false
                }
                isLoadingMore = false
            }
            .addOnFailureListener {
                it.printStackTrace()
                isLoadingMore = false
            }
    }

    // ==========================================
    // --- ΔΙΑΧΕΙΡΙΣΗ ΑΓΓΕΛΙΩΝ & ΕΜΠΟΡΩΝ ---
    // ==========================================
    fun publishPost(post: MarketPost, imageUri: Uri?) {
        viewModelScope.launch(Dispatchers.IO) {
            var finalImageUrl = ""

            if (imageUri != null) {
                finalImageUrl = uploadImageToImgBB(imageUri, com.example.agromanage.BuildConfig.IMGBB_API_KEY) ?: ""
            }

            val uid = currentUserId
            if (uid.isNotEmpty()) {
                val docRef = firestore.collection("market").document()
                val finalPost = post.copy(id = docRef.id, farmerId = uid, imageUrl = finalImageUrl)
                try {
                    docRef.set(finalPost).await()
                    // Optimistic προσθήκη ΜΟΝΟ σε sortedMode όπου ο live listener είναι σβηστός.
                    // Σε default mode ο listener θα προσθέσει την αγγελία μόνος του από το local cache
                    // (αν την προσθέταμε κι εμείς εδώ, θα εμφανιζόταν διπλή).
                    if (_sortedMode.value) {
                        _marketPosts.value = listOf(finalPost) + _marketPosts.value
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun deleteMarketPost(postId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val uid = currentUserId
            if (uid.isNotEmpty()) {
                try {
                    firestore.collection("market").document(postId).delete().await()
                    // Άμεση ενημέρωση της τοπικής λίστας ώστε η UI να ανανεωθεί
                    // ακόμα κι όταν ο live listener δεν είναι ενεργός (sorted/filter mode).
                    _marketPosts.value = _marketPosts.value.filterNot { it.id == postId }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }


    // Ενοποιημένη λογική: συνδυάζει whereEqualTo (δικές μου) με orderBy (ταξινόμηση)
    //   - priceMin   -> ASCENDING (φθηνότερες πρώτα)
    //   - date       -> dateTimestamp DESCENDING (πιο πρόσφατες πρώτα)
    // Όταν είναι ενεργά και τα δύο, το Firestore χρειάζεται composite index
    // (farmerId ASC + sortField). Στο πρώτο τρέξιμο θα δείξει σύνδεσμο στα logs.
    fun applyFilters(showOnlyMine: Boolean, sortBy: String) {
        val sortField = if (sortBy == "date") "dateTimestamp" else sortBy
        val direction = if (sortBy == "priceMin")
            com.google.firebase.firestore.Query.Direction.ASCENDING
        else
            com.google.firebase.firestore.Query.Direction.DESCENDING

        // Σταματάμε τον live listener ώστε να μην ξανα-γράψει πάνω από τα φιλτραρισμένα
        marketListener?.remove()
        _sortedMode.value = true
        _hasMorePosts.value = false

        var query: com.google.firebase.firestore.Query = firestore.collection("market")

        if (showOnlyMine) {
            val uid = currentUserId
            if (uid.isEmpty()) return
            query = query.whereEqualTo("farmerId", uid)
        }

        query = query.orderBy(sortField, direction).limit(20)

        query.get()
            .addOnSuccessListener { snapshot ->
                if (snapshot != null) {
                    _marketPosts.value = snapshot.toObjects(MarketPost::class.java)
                }
            }
            .addOnFailureListener { it.printStackTrace() }
    }

    fun expressInterest(postId: String) {
        val uid = currentUserId
        if (uid.isNotEmpty()) {
            val postRef = firestore.collection("market").document(postId)

            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(postRef)
                val interestedList = snapshot.get("interestedMerchants") as? MutableList<String> ?: mutableListOf()

                if (!interestedList.contains(uid)) {
                    interestedList.add(uid)
                    transaction.update(postRef, "interestedMerchants", interestedList)
                }
            }.addOnSuccessListener {
                // Άμεση ενημέρωση μετρητή στη UI ακόμα κι όταν ο live listener δεν τρέχει
                _marketPosts.value = _marketPosts.value.map { post ->
                    if (post.id == postId && uid !in post.interestedMerchants) {
                        post.copy(interestedMerchants = post.interestedMerchants + uid)
                    } else {
                        post
                    }
                }
            }.addOnFailureListener { e ->
                e.printStackTrace()
            }
        }
    }


    // ==========================================
    // --- ΕΠΕΞΕΡΓΑΣΙΑ & ΑΝΕΒΑΣΜΑ ΕΙΚΟΝΑΣ ---
    // ==========================================
    private fun compressImage(imageUri: Uri): ByteArray? {
        return try {
            val inputStream = getApplication<Application>().contentResolver.openInputStream(imageUri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            val outputStream = ByteArrayOutputStream()
            bitmap?.compress(Bitmap.CompressFormat.JPEG, 40, outputStream)
            outputStream.toByteArray()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun uploadImageToImgBB(imageUri: Uri, apiKey: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val bytes = compressImage(imageUri) ?: return@withContext null

                val requestBody = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                val multipartBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("image", "upload.jpg", requestBody)
                    .build()

                val request = Request.Builder()
                    .url("https://api.imgbb.com/1/upload?key=$apiKey")
                    .post(multipartBody)
                    .build()

                val client = OkHttpClient()
                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    val jsonObject = JSONObject(responseBody ?: "")
                    return@withContext jsonObject.getJSONObject("data").getString("url")
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return@withContext null
        }
    }
}