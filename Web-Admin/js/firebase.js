// js/firebase.js

import { initializeApp } from "https://www.gstatic.com/firebasejs/12.17.1/firebase-app.js";
import { getAuth } from "https://www.gstatic.com/firebasejs/12.17.1/firebase-auth.js";
import { getFirestore } from "https://www.gstatic.com/firebasejs/12.17.1/firebase-firestore.js";

const sharedStylesheetUrl = new URL(
    "../assets/css/style.css?v=20260913-ec-navigation",
    import.meta.url
).href;

if (!document.querySelector(`link[href="${sharedStylesheetUrl}"]`)) {
    const stylesheet = document.createElement("link");
    stylesheet.rel = "stylesheet";
    stylesheet.href = sharedStylesheetUrl;
    document.head.appendChild(stylesheet);
}

function applyVoteGuardIdentity() {
    if (!document.title.startsWith("VoteGuard")) {
        document.title = `VoteGuard | ${document.title}`;
    }

    const pageHeading = document.querySelector(
        ".header-title h1, .header h1, header h1"
    );

    if (!pageHeading) return;

    const context = pageHeading.textContent.trim();

    if (context && context !== "VoteGuard" && !context.startsWith("VoteGuard")) {
        pageHeading.textContent = `VoteGuard — ${context}`;
    }
}

if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", applyVoteGuardIdentity, { once: true });
} else {
    applyVoteGuardIdentity();
}

const firebaseConfig = {
  apiKey: "AIzaSyDdYImwHeNlLbfJ-S1N4Tv3kDbaOtZVd9s",
  authDomain: "voteguard-a6de3.firebaseapp.com",
  projectId: "voteguard-a6de3",
  storageBucket: "voteguard-a6de3.firebasestorage.app",
  messagingSenderId: "97235064913",
  appId: "1:97235064913:web:df10c7faf8149d53b88070"
};

// Initialize Firebase
const app = initializeApp(firebaseConfig);
// Firebase Authentication
const auth = getAuth(app);

// Firestore Database
const db = getFirestore(app);

// Export Firebase services
export {
    app,
    auth,
    db
};
