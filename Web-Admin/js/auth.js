import {
    onAuthStateChanged,
    signOut
} from "https://www.gstatic.com/firebasejs/12.17.1/firebase-auth.js";

import {
    doc,
    getDoc
} from "https://www.gstatic.com/firebasejs/12.17.1/firebase-firestore.js";

import {
    auth,
    db
} from "./firebase.js";


export function waitForAuth() {
    return new Promise((resolve) => {
        const unsubscribe =
            onAuthStateChanged(
                auth,
                (user) => {
                    unsubscribe();
                    resolve(user || null);
                }
            );
    });
}


export async function getCurrentUserProfile() {
    const user = await waitForAuth();

    if (!user) {
        return {
            user: null,
            data: null
        };
    }

    const userRef =
        doc(
            db,
            "Users",
            user.uid
        );

    const snapshot =
        await getDoc(
            userRef
        );

    if (!snapshot.exists()) {
        return {
            user,
            data: null
        };
    }

    return {
        user,
        data: snapshot.data()
    };
}


export async function requireAuth(
    redirectPage = "login.html"
) {
    const result =
        await getCurrentUserProfile();

    if (!result.user) {
        window.location.href =
            redirectPage;

        return null;
    }

    return result;
}


export async function requireRole(
    requiredRole,
    redirectPage = "login.html"
) {
    const result =
        await requireAuth(
            redirectPage
        );

    if (!result) {
        return null;
    }

    const userData =
        result.data;

    if (!userData) {
        await signOut(auth);

        window.location.href =
            redirectPage;

        return null;
    }

    if (
        userData.status ===
        "disabled"
    ) {
        await signOut(auth);

        window.location.href =
            redirectPage;

        return null;
    }

    if (
        userData.role !==
        requiredRole
    ) {
        alert(
            "You are not authorized to access this page."
        );

        window.location.href =
            redirectPage;

        return null;
    }

    return result;
}


export async function requirePermission(
    permission,
    redirectPage = "login.html"
) {
    const result =
        await requireAuth(
            redirectPage
        );

    if (!result) {
        return null;
    }

    const userData =
        result.data;

    if (!userData) {
        await signOut(auth);

        window.location.href =
            redirectPage;

        return null;
    }

    if (
        userData.status ===
        "disabled"
    ) {
        await signOut(auth);

        window.location.href =
            redirectPage;

        return null;
    }


    /* ==========================================
       PRINCIPAL
       ========================================== */

    if (
        userData.role ===
        "principal"
    ) {
        return result;
    }


    /* ==========================================
       SUB-ADMIN
       ========================================== */

    if (
        userData.role ===
        "sub_admin"
    ) {

        const subAdminRef =
            doc(
                db,
                "SubAdmins",
                result.user.uid
            );

        const subAdminSnapshot =
            await getDoc(
                subAdminRef
            );

        if (
            !subAdminSnapshot.exists()
        ) {
            alert(
                "Your Sub-Admin profile could not be found."
            );

            await signOut(auth);

            window.location.href =
                redirectPage;

            return null;
        }

        const subAdminData =
            subAdminSnapshot.data();


        if (
            subAdminData.status ===
            "disabled"
        ) {
            alert(
                "Your Sub-Admin account is disabled."
            );

            await signOut(auth);

            window.location.href =
                redirectPage;

            return null;
        }


        const duties =
            Array.isArray(
                subAdminData.duties
            )
                ? subAdminData.duties
                : [];


        if (
            !duties.includes(
                permission
            )
        ) {
            alert(
                "You are not authorized to perform this action."
            );

            window.location.href =
                redirectPage;

            return null;
        }


        return {
            ...result,
            subAdminData
        };
    }


    /* ==========================================
       OTHER ROLES
       ========================================== */

    alert(
        "You are not authorized to perform this action."
    );

    window.location.href =
        redirectPage;

    return null;
}