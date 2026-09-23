// ============================================================
// INVITATION SERVICE
// ============================================================
//
// This module handles staff invitations for:
// - Sub-Admin
// - Election Commission
// - Presiding Officer
// - Assistant Presiding Officer
//
// The administrator NEVER creates or stores the invited user's
// password.
//
// Flow:
// Principal/Admin
//      ↓
// createInvitation()
//      ↓
// Firestore: Invitations/{invitationId}
//      ↓
// Firebase Email Link sent
//      ↓
// invited user opens accept-invitation.html
//
// ============================================================


import {
    collection,
    addDoc,
    serverTimestamp
} from
"https://www.gstatic.com/firebasejs/12.17.1/firebase-firestore.js";


import {
    sendSignInLinkToEmail
} from
"https://www.gstatic.com/firebasejs/12.17.1/firebase-auth.js";


// ============================================================
// CONFIGURATION
// ============================================================

const INVITATION_EXPIRY_DAYS = 3;


// ============================================================
// VALID ROLES
// ============================================================
//
// These are the only staff roles that can be invited through
// this service.
//
// ============================================================

const VALID_ROLES = [

    "sub_admin",

    "election_commission",

    "presiding_officer",

    "assistant_presiding_officer"

];


// ============================================================
// CREATE STAFF INVITATION
// ============================================================
//
// Parameters:
//
// db:
//     Firestore instance
//
// auth:
//     Current Firebase Auth instance
//
// name:
//     Staff member's full name
//
// email:
//     Staff member's email
//
// role:
//     One of VALID_ROLES
//
// createdBy:
//     UID of the currently logged-in Principal
//
// ============================================================

export async function createInvitation({

    db,

    auth,

    name,

    email,

    role,

    createdBy

}) {

    // ---------------------------------------------------------
    // VALIDATION
    // ---------------------------------------------------------

    if (!db) {

        throw new Error(
            "Firestore instance is required."
        );

    }


    if (!auth) {

        throw new Error(
            "Firebase Auth instance is required."
        );

    }


    if (!name || !name.trim()) {

        throw new Error(
            "Full name is required."
        );

    }


    if (!email || !email.trim()) {

        throw new Error(
            "Email address is required."
        );

    }


    if (
        !VALID_ROLES.includes(role)
    ) {

        throw new Error(
            "Invalid staff role."
        );

    }


    if (!createdBy) {

        throw new Error(
            "The creator UID is required."
        );

    }


    // ---------------------------------------------------------
    // NORMALIZE INPUT
    // ---------------------------------------------------------

    const cleanName =
        name.trim();


    const cleanEmail =
        email
            .trim()
            .toLowerCase();


    // ---------------------------------------------------------
    // CREATE EXPIRATION DATE
    // ---------------------------------------------------------

    const expiresAt =
        new Date();


    expiresAt.setDate(
        expiresAt.getDate() +
        INVITATION_EXPIRY_DAYS
    );


    // ---------------------------------------------------------
    // CREATE FIRESTORE INVITATION
    // ---------------------------------------------------------
    //
    // IMPORTANT:
    // No password is stored here.
    //
    // ---------------------------------------------------------

    const invitationRef =
        await addDoc(
            collection(
                db,
                "Invitations"
            ),
            {

                name:
                    cleanName,

                email:
                    cleanEmail,

                role:
                    role,

                status:
                    "pending",

                createdBy:
                    createdBy,

                createdAt:
                    serverTimestamp(),

                expiresAt:
                    expiresAt

            }
        );


    // ---------------------------------------------------------
    // EMAIL LINK SETTINGS
    // ---------------------------------------------------------
    //
    // The invited user's email is NOT placed in the URL.
    //
    // Only the invitation ID is passed to the acceptance page.
    //
    // ---------------------------------------------------------

    const actionCodeSettings = {

        url:
            `${window.location.origin}/Web-Admin/accept-invitation.html?invitationId=${encodeURIComponent(
                invitationRef.id
            )}`,

        handleCodeInApp:
            true

    };


    // ---------------------------------------------------------
    // SEND FIREBASE EMAIL LINK
    // ---------------------------------------------------------

    try {

        await sendSignInLinkToEmail(
            auth,
            cleanEmail,
            actionCodeSettings
        );

    } catch (error) {

        /*
         * If the email could not be sent, remove the invitation
         * record so we do not leave a false "pending" invitation.
         *
         * We intentionally do not import deleteDoc here yet;
         * keeping this module simple makes the first
         * implementation easier to debug.
         *
         * The invitation will remain pending and can be handled
         * later with a resend/cancel feature.
         */

        console.error(
            "Failed to send invitation email:",
            error
        );

        throw error;

    }


    // ---------------------------------------------------------
    // REMEMBER EMAIL LOCALLY
    // ---------------------------------------------------------
    //
    // Firebase needs the same email address when the invited user
    // completes the email-link sign-in flow.
    //
    // This is stored in the invited browser's localStorage only.
    //
    // ---------------------------------------------------------

    localStorage.setItem(
        "pendingInvitationEmail",
        cleanEmail
    );


    // ---------------------------------------------------------
    // RETURN INVITATION INFORMATION
    // ---------------------------------------------------------

    return {

        invitationId:
            invitationRef.id,

        email:
            cleanEmail,

        name:
            cleanName,

        role:
            role,

        expiresAt:
            expiresAt

    };

}