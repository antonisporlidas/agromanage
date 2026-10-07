# 🌾 AgroManage — Smart Farm Management & Marketplace

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-purple.svg?logo=kotlin)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Platform-Android_12+-green.svg?logo=android)](https://developer.android.com)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack_Compose-4285F4.svg?logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![Material Design 3](https://img.shields.io/badge/Design-Material_You_(M3)-795548.svg)](https://m3.material.io)
[![Architecture](https://img.shields.io/badge/Architecture-MVVM-orange.svg)](#αρχιτεκτονική--τεχνολογίες)

Μια σύγχρονη native εφαρμογή Android για την ψηφιακή οργάνωση αγροτικών εκμεταλλεύσεων, την άμεση διασύνδεση παραγωγών με εμπόρους και την προστασία φυτών μέσω On-Device Τεχνητής Νοημοσύνης.

---

## 📋 Πίνακας Περιεχομένων
- [Επισκόπηση](#-επισκόπηση)
- [Βασικά Χαρακτηριστικά](#-βασικά-χαρακτηριστικά)
- [Αρχιτεκτονική & Τεχνολογίες](#-αρχιτεκτονική--τεχνολογίες)
- [Δομή Δεδομένων & Queries](#-δομή-δεδομένων--queries)
- [Δομή Project](#-δομή-project)
- [Ροή & Σύστημα Ειδοποιήσεων](#-σύστημα-ειδοποιήσεων--background-tasks)
- [Οδηγός Εγκατάστασης](#-οδηγός-εγκατάστασης)

---

## 📖 Επισκόπηση

Η εφαρμογή **AgroManage** γεφυρώνει το χάσμα μεταξύ της χειρόγραφης διαχείρισης μιας αγροτικής παραγωγής και των σύγχρονων ψηφιακών εργαλείων. Υποστηρίζει **διπλό ρόλο χρήστη** (Αγρότης / Έμπορος) με πλήρως διαφοροποιημένο User Interface ανά ρόλο:

- **Αγρότης:** Διαχείριση οικοπέδων/χωραφιών, καταγραφή σπορών και αγροτικών επεμβάσεων, οικονομικός έλεγχος (τζίρος/έσοδα/έξοδα), διάγνωση ασθενειών με AI και δημοσίευση αγγελιών παραγωγής.
- **Έμπορος:** Προβολή διαθέσιμης παραγωγής (Marketplace), φιλτράρισμα προϊόντων, real-time εκδήλωση ενδιαφέροντος και άμεση αποκάλυψη στοιχείων επικοινωνίας παραγωγού.

---

## ✨ Βασικά Χαρακτηριστικά

### 1. Διαχείριση Χωραφιών & Χαρτογράφηση
- Προσθήκη, επεξεργασία και φιλτράρισμα χωραφιών (Δενδροκαλλιέργειες, Θερμοκήπια, Εξωχώραφα).
- **Διαδραστικός Χάρτης (OpenStreetMap / osmdroid):** Επιλογή ακριβούς τοποθεσίας με tap και αυτόματος εντοπισμός GPS (`MyLocationNewOverlay`).
- **Live Καιρός:** Αυτόματη άντληση καιρικών συνθηκών για κάθε χωράφι μέσω **OpenWeatherMap API**.
- **Χειρονομίες Swipe:** Swipe δεξιά για προσθήκη σποράς, swipe αριστερά για διαγραφή (`SwipeToDismissBox`).

### 2. Ημερολόγιο Εργασιών & Εξαγωγή PDF
- Ιεραρχική οργάνωση: *Χωράφι → Καλλιέργεια/Σπορά → Εργασία*.
- Κατηγοριοποίηση σε: **Λίπανση/Πότισμα**, **Ράντισμα**, **Συγκομιδή**.
- Μαζική επιλογή και διαγραφή (Contextual Action Bar UX).
- **Εξαγωγή σε PDF:** Αυτόματη παραγωγή και εξαγωγή αρχείου A4 PDF στον φάκελο `Downloads` μέσω του Android `PdfDocument API`.

### 3. Ψηφιακός Γεωπόνος (AI Scanner)
- **On-Device Machine Learning:** Ανάλυση φωτογραφιών φύλλων από τη συλλογή.
- Αυτόματη ταυτοποίηση ασθενειών, ποσοστό ακρίβειας/βεβαιότητας και προτεινόμενη μέθοδος αντιμετώπισης.
- Ενδιάμεσες καταστάσεις διαχείρισης (`Idle`, `Loading`, `Success`, `Error`).

### 4. Οικονομική Παρακολούθηση (Τζίρος) & Adaptive Layout
- Καταγραφή εσόδων και εξόδων ανά καλλιέργεια ή ως γενικά έξοδα επιχείρησης.
- Αναλυτικά ακορντεόν κερδοφορίας (`FieldProfitAccordion`) με `AnimatedVisibility`.
- **Adaptive Split-Screen (Landscape):** Αυτόματη μετατροπή διάταξης σε split-screen layout κατά την οριζόντια περιστροφή (`LocalConfiguration`), κρατώντας τη σύνοψη και τη λίστα ταυτόχρονα ορατές.

### 5. Marketplace & Αγγελίες
- Δημοσίευση σοδειάς με φωτογραφίες (ασύγχρονη φόρτωση Coil & φιλοξενία στο ImgBB).
- **Infinite Scrolling Pagination:** Δυναμική φόρτωση 10-10 αγγελιών με `derivedStateOf`.
- **Firestore Transactions:** Ατομική ενημέρωση εκδήλωσης ενδιαφέροντος εμπόρου (`runTransaction`) και άμεσο ξεκλείδωμα στοιχείων επικοινωνίας.

---

## 🛠 Αρχιτεκτονική & Τεχνολογίες

Η εφαρμογή ακολουθεί αυστηρά τις προδιαγραφές **Modern Android Development (MAD)**:

| Τομέας | Τεχνολογία / Βιβλιοθήκη |
| :--- | :--- |
| **Γλώσσα** | Kotlin (Coroutines, Flow, StateFlow, Extension Functions) |
| **Αρχιτεκτονική** | MVVM (Model - View - ViewModel) + State Hoisting |
| **UI Toolkit** | Jetpack Compose (Declarative UI) |
| **Σχεδιασμός** | Material Design 3 (Material You Dynamic Theming) |
| **Τοπική Βάση** | Room Database (4 πίνακες, SQLite, Foreign Keys) |
| **Cloud Backend** | Firebase Authentication, Cloud Firestore, Cloud Messaging (FCM) |
| **Χαρτογράφηση** | OpenStreetMap (osmdroid) μέσω `AndroidView` |
| **Εικόνες & PDF** | Coil AsyncImage, Android Native PdfDocument API |
| **Background Tasks** | WorkManager API (`WeatherWorker`) |

---

## 🗄 Δομή Δεδομένων & Queries

### Τοπική Βάση (Room)
Η βάση αποτελείται από 4 οντότητες με foreign key constraints:
1. `FarmField`: Στοιχεία οικοπέδου, τύπος, τοποθεσία, συντεταγμένες GPS.
2. `Cultivation`: Σπορές ανά χωράφι (έτος, εποχή).
3. `FarmTask`: Εργασίες ανά σπορά (πότισμα, λίπανση, ράντισμα, συγκομιδή).
4. `FarmTransaction`: Οικονομικές εγγραφές (έσοδα/έξοδα).

**Ενδεικτικά Room Queries (DAOs):**
```sql
-- 1. WHERE με ORDER BY
SELECT * FROM fields_table WHERE userId = :uid ORDER BY name ASC

-- 2. Σύνθετη συνθήκη με AND
SELECT * FROM fields_table WHERE location = :loc AND userId = :uid

-- 3. INNER JOIN πινάκων
SELECT c.* FROM cultivations c INNER JOIN fields_table f ON c.fieldId = f.id WHERE f.userId = :uid

-- 4. Πολλαπλό ORDER BY
SELECT * FROM cultivations WHERE fieldId = :id ORDER BY year DESC, season DESC
```

### Απομακρυσμένη Βάση (Firestore)
- **Users Collection:** `users/{uid}` με ρόλο χρήστη (`farmer` ή `merchant`).
- **Subcollections:** Συγχρονισμός σε πραγματικό χρόνο για `fields`, `cultivations`, `tasks`, `transactions`.
- **Market Collection:** Δημόσιες αγγελίες με queries όπως:
  - `whereEqualTo("farmerId", uid)`
  - `orderBy(sortBy, DESCENDING).limit(postLimit)`
  - Real-time snapshots με `addSnapshotListener`

---

## 📂 Δομή Project

```text
app/src/main/java/com/agromanage/
│
├── data/
│   └── local/              # Room Database, Entities & DAOs (FarmDao, TaskDao, TransactionDao)
│
├── view_model/             # ViewModels (Farm, Market, Weather, Notification, Scanner)
│
├── ui_screens/             # Composable Screens & Subcomponents
│   ├── LoginScreen.kt      # Αυθεντικοποίηση & Επιλογέας Ρόλου
│   ├── FieldsScreen.kt     # Λίστα χωραφιών & AddFieldDialog (OpenStreetMap)
│   ├── TaskScreen.kt       # Ημερολόγιο εργασιών & PDF Export
│   ├── ScannerScreen.kt    # AI Plant Disease Detection
│   ├── RevenueScreen.kt    # Τζίρος & Adaptive Landscape Layout
│   └── MarketScreen.kt     # Αγορά, Φίλτρα, Pagination & BottomSheet
│
└── ui/theme/               # Material 3 Styling (Color.kt, Theme.kt, Type.kt)
```

---

## 🔔 Σύστημα Ειδοποιήσεων & Background Tasks

1. **System Tray Notifications:** Native Android Channels (`NotificationCompat.Builder`).
2. **In-App Notifications:** Αποθήκευση ιστορικού σε `SharedPreferences` (JSON) και προβολή σε Bottom Sheet με δυνατότητα swipe-to-delete.
3. **Cloud Push Notifications:** Στοχευμένη αποστολή μέσω **Firebase Cloud Messaging (FCM)** σε topics τύπου `user_{uid}`.
4. **Περιοδικός Έλεγχος Καιρού (WorkManager):** 
   - Εκτέλεση του `WeatherWorker` στο παρασκήνιο κάθε 6 ώρες (ακόμα και με κλειστή εφαρμογή).
   - Ανάλυση πρόγνωσης για κάθε χωράφι και αποστολή προειδοποίησης σε περίπτωση δυσμενών φαινομένων (έντονη βροχή, καταιγίδα, χιόνι).

---

## 🚀 Οδηγός Εγκατάστασης

1. **Κλωνοποίηση του αποθετηρίου:**
   ```bash
   git clone https://github.com/your-username/AgroManage.git
   cd AgroManage
   ```
2. **Ρύθμιση Firebase:**
   - Δημιουργήστε ένα νέο project στο [Firebase Console](https://console.firebase.google.com/).
   - Ενεργοποιήστε το **Firebase Authentication** (Email/Password) και το **Cloud Firestore**.
   - Κατεβάστε το αρχείο `google-services.json` και τοποθετήστε το στον φάκελο `app/`.
3. **API Keys:**
   - Προσθέστε το API key του OpenWeatherMap στο αρχείο `local.properties`:
     ```properties
     OPENWEATHER_API_KEY="your_api_key_here"
     ```
4. **Build & Run:**
   - Ανοίξτε το project στο **Android Studio (Ladybug / Meerkat ή νεότερο)**.
   - Συγχρονίστε τα Gradle dependencies και εκτελέστε σε συσκευή/εξομοιωτή με Android 12+ (API 31+ για πλήρη εμπειρία Material You).