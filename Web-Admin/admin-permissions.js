import {
    doc,
    getDoc
} from "https://www.gstatic.com/firebasejs/12.17.1/firebase-firestore.js";


export async function checkAdminPermission(
    db,
    user,
    requiredDuty  
) {

    if (!user) {

        return {
            allowed: false,
            reason: "not-logged-in"
        };

    }


    // ==========================================
    // GET USER PROFILE
    // ==========================================

    const userRef =
        doc(
            db,
            "Users",
            user.uid
        );


    const userSnapshot =
        await getDoc(
            userRef
        );


    if (!userSnapshot.exists()) {

        return {
            allowed: false,
            reason: "profile-not-found"
        };

    }


    const userData =
        userSnapshot.data();


    // ==========================================
    // ACCOUNT STATUS
    // ==========================================

    if (userData.status !== "active") {

        return {
            allowed: false,
            reason: "disabled"
        };

    }


    // ==========================================
    // PRINCIPAL = FULL ACCESS
    // ==========================================

    if (
        userData.role ===
        "principal"
    ) {

        return {
            allowed: true,
            role: "principal",
            duties: []
        };

    }


    // ==========================================
    // SUB-ADMIN
    // ==========================================

    if (
        userData.role !==
        "sub_admin"
    ) {

        return {
            allowed: false,
            reason: "invalid-role"
        };

    }


    // ==========================================
    // GET SUB-ADMIN RECORD
    // ==========================================

    const subAdminRef =
        doc(
            db,
            "SubAdmins",
            user.uid
        );


    const subAdminSnapshot =
        await getDoc(
            subAdminRef
        );


    if (
        !subAdminSnapshot.exists()
    ) {

        return {
            allowed: false,
            reason: "subadmin-record-not-found"
        };

    }


    const subAdminData =
        subAdminSnapshot.data();


    // ==========================================
    // SUB-ADMIN STATUS
    // ==========================================

    if (subAdminData.status !== "active") {

        return {
            allowed: false,
            reason: "disabled"
        };

    }


    // ==========================================
    // GET DUTIES
    // ==========================================

    const duties =
        Array.isArray(
            subAdminData.duties
        )
            ? subAdminData.duties
            : [];


    // ==========================================
    // CHECK REQUIRED DUTY
    // ==========================================

    if (
        !duties.includes(
            requiredDuty
        )
    ) {

        return {
            allowed: false,
            reason: "missing-duty",
            duties: duties
        };

    }


    // ==========================================
    // AUTHORIZED
    // ==========================================

    return {
        allowed: true,
        role: "sub_admin",
        duties: duties
    };

}
