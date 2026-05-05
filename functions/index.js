const { onDocumentUpdated } = require("firebase-functions/v2/firestore");
const { logger } = require("firebase-functions"); // Για σωστά logs
const admin = require("firebase-admin");

admin.initializeApp();

exports.notifyOnNewInterest = onDocumentUpdated("market/{postId}", async (event) => {
    // Στη v2, τα δεδομένα είναι στο event.data
    const newData = event.data.after.data();
    const oldData = event.data.before.data();

    // Ασφαλής έλεγχος για τις λίστες εμπόρων
    const newMerchants = newData.interestedMerchants || [];
    const oldMerchants = oldData.interestedMerchants || [];

    // Έλεγχος αν προστέθηκε νέος έμπορος
    if (newMerchants.length > oldMerchants.length) {
        const farmerId = newData.farmerId;
        const cropType = newData.cropType;
        const postId = event.params.postId; // Το ID της αγγελίας από το URL

        logger.log(`Ανιχνεύθηκε νέο ενδιαφέρον για την αγγελία ${postId} του αγρότη ${farmerId}`);

        // Data-only payload ώστε το AgroFirebaseMessagingService να καλείται και
        // σε background — αλλιώς το Android θα έδειχνε auto το system notification
        // και το in-app ιστορικό δεν θα ενημερωνόταν ποτέ.
        const message = {
            data: {
                title: 'Νέο Ενδιαφέρον! 🔔',
                body: `Ένας έμπορος ενδιαφέρεται για το προϊόν: ${cropType}`,
                postId: postId,
                type: "MARKET_INTEREST"
            },
            android: {
                priority: "high"
            },
            topic: `user_${farmerId}`
        };

        try {
            // Αποστολή με τη νέα μέθοδο
            const response = await admin.messaging().send(message);
            logger.log("Η ειδοποίηση στάλθηκε επιτυχώς:", response);
            return response;
        } catch (error) {
            logger.error("Σφάλμα κατά την αποστολή της ειδοποίησης:", error);
            return null;
        }
    }

    return null;
});