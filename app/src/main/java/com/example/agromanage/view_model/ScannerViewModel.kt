package com.example.agromanage.view_model

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.agromanage.ml.Model
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp // <-- Προστέθηκε για το κόψιμο
import org.tensorflow.lite.support.common.ops.NormalizeOp
import java.io.InputStream

sealed class ScanState {
    object Idle : ScanState()
    object Loading : ScanState()
    data class Success(val diseaseName: String, val confidence: Int, val treatment: String) : ScanState()
    data class Error(val message: String) : ScanState()
}

class ScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    fun analyzePlant(imageUri: Uri) {
        _scanState.value = ScanState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()

                val inputStream: InputStream? = context.contentResolver.openInputStream(imageUri)
                val bitmap = BitmapFactory.decodeStream(inputStream)

                // Κλείνουμε το stream για ασφάλεια μνήμης
                inputStream?.close()

                // Βεβαιωνόμαστε ότι το bitmap φορτώθηκε
                if (bitmap == null) {
                    throw Exception("Η φωτογραφία δεν μπόρεσε να διαβαστεί.")
                }

                val labels = try {
                    context.assets.open("labels.txt").bufferedReader().useLines { it.toList() }
                } catch (e: Exception) {
                    throw Exception("Το αρχείο labels.txt δεν βρέθηκε!")
                }

                val model = Model.newInstance(context)

                // 1. Βρίσκουμε τη μικρότερη πλευρά της φωτογραφίας για να κόψουμε ένα τέλειο τετράγωνο (Crop)
                val cropSize = Math.min(bitmap.width, bitmap.height)

                // --- ΟΙ ΔΙΟΡΘΩΣΕΙΣ ΣΤΗΝ ΕΙΚΟΝΑ ΓΙΑ ΝΑ ΜΗΝ ΜΠΕΡΔΕΥΕΤΑΙ ΤΟ AI ---
                val imageProcessor = ImageProcessor.Builder()
                    .add(ResizeWithCropOrPadOp(cropSize, cropSize)) // Κόβει τη φωτογραφία σε τετράγωνο στο κέντρο
                    .add(ResizeOp(224, 224, ResizeOp.ResizeMethod.BILINEAR)) // Τη μικραίνει στα μέτρα του μοντέλου
                    .add(NormalizeOp(127.5f, 127.5f)) // Κανονικοποίηση ειδικά για MobileNet μοντέλα (-1 έως 1)
                    .build()

                var tensorImage = TensorImage(org.tensorflow.lite.DataType.FLOAT32)
                tensorImage.load(bitmap)
                tensorImage = imageProcessor.process(tensorImage)

                val outputs = model.process(tensorImage.tensorBuffer)

                val outputArray = outputs.outputFeature0AsTensorBuffer.floatArray
                val maxIndex = outputArray.indices.maxByOrNull { outputArray[it] } ?: -1
                val confidenceScore = if (maxIndex != -1) outputArray[maxIndex] else 0f

                withContext(Dispatchers.Main) {
                    // 2. ΑΝΕΒΑΣΑΜΕ ΤΟΝ ΠΗΧΗ (Σιγουριά > 60%)
                    if (maxIndex != -1 && maxIndex < labels.size && confidenceScore > 0.60f) {

                        val finalDiseaseName = labels[maxIndex]

                        val treatmentInfo = getTreatmentForDisease(finalDiseaseName)

                        _scanState.value = ScanState.Success(
                            diseaseName = finalDiseaseName,
                            confidence = (confidenceScore * 100).toInt(),
                            treatment = treatmentInfo
                        )
                    } else {
                        // Αν δεν είναι τουλάχιστον 60% σίγουρο, του λέμε να βγάλει καλύτερη φώτο!
                        _scanState.value = ScanState.Error("Η ανάλυση δεν είναι ξεκάθαρη (${(confidenceScore * 100).toInt()}%). Προσπαθήστε να βγάλετε μια πιο κοντινή και καθαρή φωτογραφία του φύλλου.")
                    }
                }
                model.close()

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _scanState.value = ScanState.Error("Σφάλμα: ${e.message}")
                }
            }
        }
    }

    fun resetScanner() {
        _scanState.value = ScanState.Idle
    }

    private fun getTreatmentForDisease(disease: String): String {
        val treatments = mapOf(
            // --- ΜΗΛΑ (Apple) ---
            "Apple - Apple Scab" to "Ψεκάστε με κατάλληλα μυκητοκτόνα (π.χ. χαλκούχα). Αφαιρέστε και καταστρέψτε τα πεσμένα φύλλα για να μειώσετε το μόλυσμα.",
            "Apple - Black Rot" to "Αφαιρέστε και κάψτε τα νεκρά κλαδιά και τους προσβεβλημένους καρπούς. Ψεκάστε με μυκητοκτόνα κατά τη διάρκεια της βλαστικής περιόδου.",
            "Apple - Cedar Apple Rust" to "Αφαιρέστε τα δέντρα κέδρου από την περιοχή αν είναι δυνατόν. Εφαρμόστε προληπτικά μυκητοκτόνα την άνοιξη.",
            "Apple - Healthy" to "Το δέντρο σας είναι υγιέστατο! Συνεχίστε την καλή φροντίδα.",

            // --- ΜΥΡΤΙΛΑ (Blueberry) ---
            "Blueberry - Healthy" to "Ο θάμνος σας είναι υγιέστατος! Συνεχίστε την καλή φροντίδα.",

            // --- ΚΕΡΑΣΙΑ (Cherry) ---
            "Cherry - Powdery Mildew" to "Εφαρμόστε μυκητοκτόνα με βάση το θείο. Εξασφαλίστε καλό αερισμό στο φύλλωμα με σωστό κλάδεμα.",
            "Cherry - Healthy" to "Το δέντρο σας είναι υγιέστατο! Συνεχίστε την καλή φροντίδα.",

            // --- ΚΑΛΑΜΠΟΚΙ (Corn) ---
            "Corn - Cercospora Leaf Spot" to "Χρησιμοποιήστε ανθεκτικές ποικιλίες και εφαρμόστε αμειψισπορά. Ψεκάστε με μυκητοκτόνα αν η προσβολή είναι σοβαρή.",
            "Corn - Common Rust" to "Χρησιμοποιήστε ανθεκτικές ποικιλίες. Ψεκάστε με κατάλληλο μυκητοκτόνο αν η προσβολή είναι έντονη νωρίς στη σεζόν.",
            "Corn - Northern Leaf Blight" to "Εφαρμόστε αμειψισπορά και όργωμα για ενσωμάτωση των υπολειμμάτων στο έδαφος. Προτιμήστε ανθεκτικά υβρίδια.",
            "Corn - Healthy" to "Το φυτό σας είναι υγιέστατο! Συνεχίστε την καλή φροντίδα.",

            // --- ΑΜΠΕΛΙ (Grape) ---
            "Grape - Black Rot" to "Καταστρέψτε τις 'μούμιες' (ξερούς καρπούς). Ψεκάστε προληπτικά με μυκητοκτόνα από την άνοιξη έως το δέσιμο των καρπών.",
            "Grape - Esca (Black Measles)" to "Κλαδέψτε και κάψτε τα έντονα προσβεβλημένα τμήματα. Απολυμαίνετε πάντα τα εργαλεία κλαδέματος.",
            "Grape - Leaf Blight" to "Βελτιώστε την κυκλοφορία του αέρα στο αμπέλι (ξεφύλλισμα) και ψεκάστε με κατάλληλα μυκητοκτόνα.",
            "Grape - Healthy" to "Το αμπέλι σας είναι υγιέστατο! Συνεχίστε την καλή φροντίδα.",

            // --- ΠΟΡΤΟΚΑΛΙΑ (Orange) ---
            "Orange - Citrus Greening" to "Δεν υπάρχει θεραπεία. Αφαιρέστε και καταστρέψτε το δέντρο για να αποτρέψετε την εξάπλωση. Καταπολεμήστε το έντομο-φορέα (ψύλλα).",

            // --- ΡΟΔΑΚΙΝΙΑ (Peach) ---
            "Peach - Bacterial Spot" to "Χρησιμοποιήστε χαλκούχα σκευάσματα το φθινόπωρο και νωρίς την άνοιξη. Φυτέψτε ανθεκτικές ποικιλίες.",
            "Peach - Healthy" to "Το δέντρο σας είναι υγιέστατο! Συνεχίστε την καλή φροντίδα.",

            // --- ΠΙΠΕΡΙΑ (Pepper) ---
            "Pepper - Bacterial Spot" to "Χρησιμοποιήστε υγιή σπόρο, εφαρμόστε αμειψισπορά και ψεκάστε προληπτικά με χαλκούχα σκευάσματα.",
            "Pepper - Healthy" to "Το φυτό σας είναι υγιέστατο! Συνεχίστε την καλή φροντίδα.",

            // --- ΠΑΤΑΤΑ (Potato) ---
            "Potato - Early Blight" to "Εφαρμόστε προληπτικούς ψεκασμούς με μυκητοκτόνα. Κάντε συχνή αμειψισπορά.",
            "Potato - Late Blight" to "Ψεκάστε με χαλκούχα μυκητοκτόνα. Αφαιρέστε και καταστρέψτε τα προσβεβλημένα φύλλα. Αποφύγετε το βρέξιμο των φύλλων.",
            "Potato - Healthy" to "Το φυτό σας είναι υγιέστατο! Συνεχίστε την καλή φροντίδα.",

            // --- ΣΜΕΟΥΡΑ (Raspberry) ---
            "Raspberry - Healthy" to "Ο θάμνος σας είναι υγιέστατος! Συνεχίστε την καλή φροντίδα.",

            // --- ΣΟΓΙΑ (Soybean) ---
            "Soybean - Healthy" to "Το φυτό σας είναι υγιέστατο! Συνεχίστε την καλή φροντίδα.",

            // --- ΚΟΛΟΚΥΘΙ (Squash) ---
            "Squash - Powdery Mildew" to "Ψεκάστε με θειάφι ή άλλα εγκεκριμένα ωίδιοκτόνα. Αποφύγετε την υπερβολική υγρασία στο φύλλωμα.",

            // --- ΦΡΑΟΥΛΑ (Strawberry) ---
            "Strawberry - Leaf Scorch" to "Αφαιρέστε τα προσβεβλημένα φύλλα και βελτιώστε την κυκλοφορία του αέρα. Εφαρμόστε μυκητοκτόνα αν χρειαστεί.",
            "Strawberry - Healthy" to "Το φυτό σας είναι υγιέστατο! Συνεχίστε την καλή φροντίδα.",

            // --- ΝΤΟΜΑΤΑ (Tomato) ---
            "Tomato - Bacterial Spot" to "Χρησιμοποιήστε χαλκούχα σκευάσματα, αποφύγετε το βρέξιμο των φύλλων κατά το πότισμα και εφαρμόστε αμειψισπορά.",
            "Tomato - Early Blight" to "Αφαιρέστε τα χαμηλά προσβεβλημένα φύλλα. Ψεκάστε με εγκεκριμένα μυκητοκτόνα.",
            "Tomato - Late Blight" to "Ψεκάστε με χαλκούχα. Αφαιρέστε και καταστρέψτε τα προσβεβλημένα φύλλα. Ποτίζετε με σταγόνα, όχι με τεχνητή βροχή.",
            "Tomato - Leaf Mold" to "Βελτιώστε τον αερισμό (ειδικά στα θερμοκήπια). Μειώστε την υγρασία και χρησιμοποιήστε μυκητοκτόνα.",
            "Tomato - Septoria Leaf Spot" to "Αφαιρέστε τα μολυσμένα φύλλα, αποφύγετε το πότισμα από πάνω και χρησιμοποιήστε μυκητοκτόνα.",
            "Tomato - Spider Mites" to "Πρόκειται για Τετράνυχο. Χρησιμοποιήστε ειδικά ακαρεοκτόνα, θερινό πολτό ή φυσικούς εχθρούς. Μην αφήνετε τα φυτά να διψάσουν.",
            "Tomato - Target Spot" to "Εξασφαλίστε καλό αερισμό, αφαιρέστε τα κάτω φύλλα και εφαρμόστε κατάλληλα μυκητοκτόνα.",
            "Tomato - Yellow Leaf Curl Virus" to "Ελέγξτε τον πληθυσμό του αλευρώδη (έντομο φορέας). Καταστρέψτε τα μολυσμένα φυτά και χρησιμοποιήστε ανθεκτικές ποικιλίες.",
            "Tomato - Mosaic Virus" to "Ιός Μωσαϊκού: Δεν υπάρχει θεραπεία. Αφαιρέστε τα μολυσμένα φυτά άμεσα. Πλένετε καλά τα χέρια και τα εργαλεία σας γιατί μεταδίδεται με την αφή.",
            "Tomato - Healthy" to "Το φυτό σας είναι υγιέστατο! Συνεχίστε την καλή φροντίδα."
        )

        return treatments[disease] ?: "Συμβουλευτείτε τον τοπικό γεωπόνο για την κατάλληλη αντιμετώπιση και το σωστό φάρμακο."
    }
}