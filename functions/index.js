const { setGlobalOptions } = require("firebase-functions");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const admin = require("firebase-admin");

admin.initializeApp();

const db = admin.firestore();

setGlobalOptions({
    maxInstances: 10
});


// ==========================================
// CREATE SUB-ADMIN
// ==========================================

exports.createSubAdmin = onCall(async (request) => {

    // ------------------------------------------
    // 1. Check whether user is logged in
    // ------------------------------------------

    if (!request.auth) {

        throw new HttpsError(
            "unauthenticated",
            "You must be logged in."
        );

    }


    // ------------------------------------------
    // 2. Get currently logged-in user's profile
    // ------------------------------------------

    const principalUid =
        request.auth.uid;

    const principalRef =
        db.collection("Users").doc(principalUid);

    const principalSnapshot =
        await principalRef.get();


    if (!principalSnapshot.exists) {

        throw new HttpsError(
            "permission-denied",
            "User profile was not found."
        );

    }


    const principalData =
        principalSnapshot.data();


    // ------------------------------------------
    // 3. Only Principal can create Sub-Admin
    // ------------------------------------------

    if (principalData.role !== "principal") {

        throw new HttpsError(
            "permission-denied",
            "Only the Principal can create Sub-Admin accounts."
        );

    }


    // ------------------------------------------
    // 4. Get data sent from website
    // ------------------------------------------

    const {
        name,
        email
    } = request.data;


    // ------------------------------------------
    // 5. Validate data
    // ------------------------------------------

    if (!name || !email) {

        throw new HttpsError(
            "invalid-argument",
            "Name and email are required."
        );

    }


    // ------------------------------------------
    // 6. Create Firebase Authentication account
    // ------------------------------------------

    let newUser;

    try {

        newUser =
            await admin.auth().createUser({
                email: email.trim(),
                emailVerified: false,
                disabled: false
            });

    } catch (error) {

        console.error(
            "Firebase Auth error:",
            error
        );

        throw new HttpsError(
            "already-exists",
            "Unable to create account. The email may already be registered."
        );

    }


    // ------------------------------------------
    // 7. Create Firestore user profile
    // ------------------------------------------

    await db
        .collection("Users")
        .doc(newUser.uid)
        .set({

            name: name.trim(),

            email: email.trim(),

            role: "sub_admin",

            status: "active",

            createdBy: principalUid,

            createdAt:
                admin.firestore.FieldValue.serverTimestamp()

        });


    // ------------------------------------------
    // 8. Return success
    // ------------------------------------------

    return {

        success: true,

        message:
            "Sub-Admin account created successfully.",

        uid:
            newUser.uid

    };

});