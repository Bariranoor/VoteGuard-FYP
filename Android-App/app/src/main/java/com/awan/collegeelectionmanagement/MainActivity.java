package com.awan.collegeelectionmanagement;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "VotingDevice";
    private static final long HEARTBEAT_INTERVAL_MS = 60_000L;
    private static final long SUCCESS_SCREEN_MS = 3_000L;

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;

    private LinearLayout pairingPanel;
    private LinearLayout connectedPanel;
    private LinearLayout ballotPanel;
    private LinearLayout successPanel;
    private LinearLayout ballotContainer;

    private EditText etPairingCode;
    private Button btnConnectDevice;
    private Button btnSubmitBallot;

    private TextView tvAuthStatus;
    private TextView tvDeviceUid;
    private TextView tvInstitutionName;
    private TextView tvBoothName;
    private TextView tvElectionName;
    private TextView tvWaitingStatus;
    private TextView tvBallotElectionName;
    private TextView tvBallotBoothName;
    private TextView tvBallotMessage;
    private TextView tvBallotProgress;

    private FirebaseUser currentDeviceUser;

    private String currentElectionId = "";
    private String currentBoothId = "";
    private String currentElectionName = "";
    private String currentBoothDisplay = "";
    private String currentElectionStatus = "";

    private String currentSessionId = "";
    private String currentVoterId = "";

    private boolean ballotOpen = false;
    private boolean preparingBallot = false;
    private boolean submissionInProgress = false;

    private ListenerRegistration authorizationListener;
    private ListenerRegistration electionListener;
    private ListenerRegistration activeSessionListener;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Handler heartbeatHandler = new Handler(Looper.getMainLooper());

    private final List<PositionItem> ballotPositions = new ArrayList<>();
    private final Map<String, LinkedHashSet<String>> selectedCandidates = new HashMap<>();
    private final Map<String, List<RadioButton>> candidateRadioButtons = new HashMap<>();

    private final Runnable heartbeatRunnable = new Runnable() {
        @Override
        public void run() {
            sendHeartbeat();
            heartbeatHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        initializeViews();

        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        authenticateVotingDevice();
    }

    @Override
    protected void onDestroy() {
        stopAllListeners();
        stopHeartbeat();
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void initializeViews() {
        pairingPanel = findViewById(R.id.pairingPanel);
        connectedPanel = findViewById(R.id.connectedPanel);
        ballotPanel = findViewById(R.id.ballotPanel);
        successPanel = findViewById(R.id.successPanel);
        ballotContainer = findViewById(R.id.ballotContainer);

        etPairingCode = findViewById(R.id.etPairingCode);
        btnConnectDevice = findViewById(R.id.btnConnectDevice);
        btnSubmitBallot = findViewById(R.id.btnSubmitBallot);

        tvAuthStatus = findViewById(R.id.tvAuthStatus);
        tvDeviceUid = findViewById(R.id.tvDeviceUid);
        tvInstitutionName = findViewById(R.id.tvInstitutionName);
        tvBoothName = findViewById(R.id.tvBoothName);
        tvElectionName = findViewById(R.id.tvElectionName);
        tvWaitingStatus = findViewById(R.id.tvWaitingStatus);
        tvBallotElectionName = findViewById(R.id.tvBallotElectionName);
        tvBallotBoothName = findViewById(R.id.tvBallotBoothName);
        tvBallotMessage = findViewById(R.id.tvBallotMessage);
        tvBallotProgress = findViewById(R.id.tvBallotProgress);

        btnConnectDevice.setEnabled(false);
        btnSubmitBallot.setEnabled(false);

        btnConnectDevice.setOnClickListener(view -> attemptDevicePairing());
        btnSubmitBallot.setOnClickListener(view -> confirmBallotSubmission());
    }

    /* =====================================================
       DEVICE AUTHENTICATION / BINDING
       ===================================================== */

    private void authenticateVotingDevice() {
        stopAllListeners();
        stopHeartbeat();
        resetBallotState();

        tvAuthStatus.setText("Checking device identity...");
        tvDeviceUid.setText("");

        FirebaseUser existingUser = firebaseAuth.getCurrentUser();

        if (existingUser != null) {
            showAuthenticatedDevice(existingUser);
            return;
        }

        firebaseAuth.signInAnonymously()
                .addOnCompleteListener(this, task -> {
                    if (!task.isSuccessful()) {
                        Log.e(TAG, "Anonymous authentication failed", task.getException());
                        showAuthenticationError("Unable to authenticate this voting device.");
                        return;
                    }

                    FirebaseUser user = firebaseAuth.getCurrentUser();
                    if (user == null) {
                        showAuthenticationError("Firebase user was not available.");
                        return;
                    }

                    showAuthenticatedDevice(user);
                });
    }

    private void showAuthenticatedDevice(FirebaseUser user) {
        currentDeviceUser = user;

        tvDeviceUid.setText("");
        tvAuthStatus.setText("Checking device connection...");

        loadInstitutionName();
        checkExistingDeviceBinding();
    }

    private void loadInstitutionName() {
        tvInstitutionName.setText("Loading institution...");

        firestore.collection("Institutions")
                .document("main")
                .get()
                .addOnSuccessListener(snapshot -> {
                    String institutionName = snapshot.exists()
                            ? safeString(snapshot.getString("name"))
                            : "";
                    tvInstitutionName.setText(
                            institutionName.isEmpty()
                                    ? "Institution not configured"
                                    : institutionName
                    );
                })
                .addOnFailureListener(error -> {
                    Log.w(TAG, "Institution name could not be loaded", error);
                    tvInstitutionName.setText("Institution not configured");
                });
    }

    private void checkExistingDeviceBinding() {
        stopAllListeners();
        stopHeartbeat();
        resetBallotState();

        if (currentDeviceUser == null) {
            showAuthenticationError("Device identity is not available.");
            return;
        }

        String deviceUid = currentDeviceUser.getUid();

        firestore.collection("VotingDevices")
                .document(deviceUid)
                .get()
                .addOnSuccessListener(deviceSnapshot -> {
                    if (!deviceSnapshot.exists()) {
                        showPairingScreen();
                        return;
                    }

                    String status = safeString(deviceSnapshot.getString("status"));
                    String electionId = safeString(deviceSnapshot.getString("electionId"));
                    String boothId = safeString(deviceSnapshot.getString("boothId"));
                    String storedUid = safeString(deviceSnapshot.getString("deviceUid"));

                    if (!"active".equals(status)) {
                        showInactiveDeviceState(status);
                        return;
                    }

                    if (!storedUid.isEmpty() && !deviceUid.equals(storedUid)) {
                        showDeviceLoadError("Voting device identity is inconsistent.");
                        return;
                    }

                    if (electionId.isEmpty() || boothId.isEmpty()) {
                        showDeviceLoadError("Voting device assignment is incomplete.");
                        return;
                    }

                    currentElectionId = electionId;
                    currentBoothId = boothId;

                    startHeartbeat();
                    loadAssignedBooth();
                })
                .addOnFailureListener(error -> {
                    Log.e(TAG, "Voting device lookup failed", error);
                    showDeviceLoadError("Unable to verify this voting device.");
                });
    }

    private void loadAssignedBooth() {
        if (currentBoothId.isEmpty() || currentElectionId.isEmpty()) {
            showDeviceLoadError("Voting device assignment is incomplete.");
            return;
        }

        tvAuthStatus.setText("Loading assigned booth...");

        firestore.collection("Booths")
                .document(currentBoothId)
                .get()
                .addOnSuccessListener(boothSnapshot -> {
                    if (!boothSnapshot.exists()) {
                        showDeviceLoadError("Assigned booth was not found.");
                        return;
                    }

                    String boothElectionId = safeString(boothSnapshot.getString("electionId"));
                    if (!currentElectionId.equals(boothElectionId)) {
                        showDeviceLoadError("Voting device booth assignment is inconsistent.");
                        return;
                    }

                    String boothNumber = safeString(boothSnapshot.getString("boothNumber"));
                    String location = safeString(boothSnapshot.getString("location"));

                    if (boothNumber.isEmpty()) {
                        boothNumber = currentBoothId;
                    }

                    currentBoothDisplay = boothNumber;
                    if (!location.isEmpty()) {
                        currentBoothDisplay += " — " + location;
                    }

                    tvBoothName.setText(currentBoothDisplay);
                    tvBallotBoothName.setText(currentBoothDisplay);

                    startElectionListener();
                })
                .addOnFailureListener(error -> {
                    Log.e(TAG, "Booth loading failed", error);
                    showDeviceLoadError("Assigned booth could not be loaded.");
                });
    }

    /* =====================================================
       ELECTION STATE
       ===================================================== */

    private void startElectionListener() {
        stopElectionListener();

        if (currentElectionId.isEmpty()) {
            return;
        }

        electionListener = firestore.collection("Elections")
                .document(currentElectionId)
                .addSnapshotListener(this, (snapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Election listener failed", error);
                        tvAuthStatus.setText("Unable to monitor election state");
                        return;
                    }

                    if (snapshot == null || !snapshot.exists()) {
                        showDeviceLoadError("Assigned election was not found.");
                        return;
                    }

                    String electionName = safeString(snapshot.getString("electionName"));
                    String electionStatus = safeString(snapshot.getString("status"));

                    currentElectionName = electionName.isEmpty()
                            ? currentElectionId
                            : electionName;

                    currentElectionStatus = electionStatus;

                    tvElectionName.setText(currentElectionName);
                    tvBallotElectionName.setText(currentElectionName);

                    handleElectionStateChange();
                });
    }

    private void handleElectionStateChange() {
        if ("closed".equals(currentElectionStatus)) {
            stopAuthorizationListener();
            if (ballotOpen) {
                btnSubmitBallot.setEnabled(false);
                tvBallotMessage.setText("Election closed. This ballot can no longer be submitted.");
                tvAuthStatus.setText("Election closed");
            } else {
                showConnectedState("Election closed", "Voting device connected — election closed");
            }
            return;
        }

        if ("voting_stopped".equals(currentElectionStatus)) {
            stopAuthorizationListener();
            if (ballotOpen) {
                btnSubmitBallot.setEnabled(false);
                tvBallotMessage.setText("Voting is temporarily stopped. Your selections remain on this device until voting resumes.");
                tvAuthStatus.setText("Voting temporarily stopped");
            } else {
                showConnectedState("Voting is temporarily stopped", "Voting device connected");
            }
            return;
        }

        if ("finalized".equals(currentElectionStatus)) {
            stopAuthorizationListener();
            if (!ballotOpen) {
                showConnectedState("Waiting for voting to start", "Voting device connected");
            }
            return;
        }

        if (!"voting_active".equals(currentElectionStatus)) {
            stopAuthorizationListener();
            if (!ballotOpen) {
                showConnectedState("Voting is not available", "Voting device connected");
            }
            return;
        }

        if (ballotOpen) {
            tvBallotMessage.setText("Choose one candidate for each position that has candidates.");
            updateBallotProgress();
            return;
        }

        recoverExistingBoothSession();
    }

    /* =====================================================
       RECOVER ACTIVE SESSION AFTER APP RESTART
       ===================================================== */

    private void recoverExistingBoothSession() {
        stopAuthorizationListener();

        if (currentBoothId.isEmpty() || currentDeviceUser == null) {
            return;
        }

        tvAuthStatus.setText("Checking booth voting state...");

        firestore.collection("BoothVotingState")
                .document(currentBoothId)
                .get()
                .addOnSuccessListener(stateSnapshot -> {
                    if (!"voting_active".equals(currentElectionStatus) || ballotOpen) {
                        return;
                    }

                    if (!stateSnapshot.exists()) {
                        showConnectedState("Waiting for voter authorization...", "Voting device connected");
                        startAuthorizationListener();
                        return;
                    }

                    String stateStatus = safeString(stateSnapshot.getString("status"));
                    String stateElectionId = safeString(stateSnapshot.getString("electionId"));
                    String activeSessionId = safeString(stateSnapshot.getString("activeSessionId"));

                    if (!currentElectionId.equals(stateElectionId)
                            || !"active".equals(stateStatus)
                            || activeSessionId.isEmpty()) {
                        showConnectedState("Waiting for voter authorization...", "Voting device connected");
                        startAuthorizationListener();
                        return;
                    }

                    firestore.collection("VotingSessions")
                            .document(activeSessionId)
                            .get()
                            .addOnSuccessListener(sessionSnapshot -> {
                                if (!sessionSnapshot.exists()) {
                                    showConnectedState("Waiting for voter authorization...", "Voting device connected");
                                    startAuthorizationListener();
                                    return;
                                }

                                String status = safeString(sessionSnapshot.getString("status"));
                                String electionId = safeString(sessionSnapshot.getString("electionId"));
                                String boothId = safeString(sessionSnapshot.getString("boothId"));
                                String deviceId = safeString(sessionSnapshot.getString("deviceId"));

                                if (!currentElectionId.equals(electionId)
                                        || !currentBoothId.equals(boothId)) {
                                    showConnectedState("Waiting for voter authorization...", "Voting device connected");
                                    startAuthorizationListener();
                                    return;
                                }

                                if ("authorized".equals(status)) {
                                    processAuthorizedSession(sessionSnapshot);
                                    return;
                                }

                                if ("ballot_open".equals(status)) {
                                    if (currentDeviceUser.getUid().equals(deviceId)) {
                                        prepareExistingOpenSession(sessionSnapshot);
                                    } else {
                                        showConnectedState(
                                                "A ballot is already open on another paired device",
                                                "Presiding Officer action required"
                                        );
                                    }
                                    return;
                                }

                                showConnectedState("Waiting for voter authorization...", "Voting device connected");
                                startAuthorizationListener();
                            })
                            .addOnFailureListener(error -> {
                                Log.e(TAG, "Active-session recovery failed", error);
                                showConnectedState("Waiting for voter authorization...", "Voting device connected");
                                startAuthorizationListener();
                            });
                })
                .addOnFailureListener(error -> {
                    Log.e(TAG, "Booth-state recovery failed", error);
                    showConnectedState("Waiting for voter authorization...", "Voting device connected");
                    startAuthorizationListener();
                });
    }

    /* =====================================================
       AUTHORIZATION LISTENER
       ===================================================== */

    private void startAuthorizationListener() {
        stopAuthorizationListener();

        if (!"voting_active".equals(currentElectionStatus)
                || ballotOpen
                || preparingBallot
                || currentElectionId.isEmpty()
                || currentBoothId.isEmpty()) {
            return;
        }

        Query query = firestore.collection("VotingSessions")
                .whereEqualTo("electionId", currentElectionId)
                .whereEqualTo("boothId", currentBoothId)
                .whereEqualTo("status", "authorized")
                .whereEqualTo("deviceId", "");

        authorizationListener = query.addSnapshotListener(this, (snapshot, error) -> {
            if (error != null) {
                Log.e(TAG, "Authorization listener failed", error);
                showConnectedState("Unable to monitor voter authorization", "Voting device connection error");
                return;
            }

            handleAuthorizationSnapshot(snapshot);
        });
    }

    private void handleAuthorizationSnapshot(QuerySnapshot snapshot) {
        if (snapshot == null || preparingBallot || ballotOpen) {
            return;
        }

        long now = System.currentTimeMillis();
        List<DocumentSnapshot> validSessions = new ArrayList<>();

        for (QueryDocumentSnapshot document : snapshot) {
            String electionId = safeString(document.getString("electionId"));
            String boothId = safeString(document.getString("boothId"));
            String status = safeString(document.getString("status"));
            String deviceId = safeString(document.getString("deviceId"));
            Timestamp expiresAt = document.getTimestamp("expiresAt");

            if (!currentElectionId.equals(electionId)
                    || !currentBoothId.equals(boothId)
                    || !"authorized".equals(status)
                    || !deviceId.isEmpty()
                    || expiresAt == null
                    || expiresAt.toDate().getTime() <= now) {
                continue;
            }

            validSessions.add(document);
        }

        if (validSessions.isEmpty()) {
            showConnectedState("Waiting for voter authorization...", "Voting device connected");
            return;
        }

        if (validSessions.size() > 1) {
            showConnectedState("Multiple active voter sessions detected", "Presiding Officer action required");
            Log.e(TAG, "Multiple authorized sessions detected for booth " + currentBoothId);
            return;
        }

        processAuthorizedSession(validSessions.get(0));
    }

    private void processAuthorizedSession(DocumentSnapshot sessionSnapshot) {
        if (preparingBallot || ballotOpen || currentDeviceUser == null) {
            return;
        }

        String sessionId = sessionSnapshot.getId();
        String voterId = safeString(sessionSnapshot.getString("voterId"));
        Timestamp expiresAt = sessionSnapshot.getTimestamp("expiresAt");

        if (sessionId.isEmpty() || voterId.isEmpty() || expiresAt == null) {
            showConnectedState("Waiting for voter authorization...", "Voting device connected");
            startAuthorizationListener();
            return;
        }

        if (expiresAt.toDate().getTime() <= System.currentTimeMillis()) {
            showConnectedState("Waiting for voter authorization...", "Voting device connected");
            startAuthorizationListener();
            return;
        }

        preparingBallot = true;
        stopAuthorizationListener();

        currentSessionId = sessionId;
        currentVoterId = voterId;

        showConnectedState("Authorization received — loading ballot...", "Preparing secure ballot session...");

        loadOfficialBallot(() -> claimAuthorizedSession(sessionId));
    }

    private void prepareExistingOpenSession(DocumentSnapshot sessionSnapshot) {
        if (preparingBallot || ballotOpen) {
            return;
        }

        currentSessionId = sessionSnapshot.getId();
        currentVoterId = safeString(sessionSnapshot.getString("voterId"));

        if (currentSessionId.isEmpty() || currentVoterId.isEmpty()) {
            showConnectedState("Unable to recover open ballot", "Presiding Officer action required");
            return;
        }

        preparingBallot = true;
        showConnectedState("Recovering open ballot...", "Preparing secure ballot session...");

        loadOfficialBallot(() -> {
            preparingBallot = false;
            ballotOpen = true;
            showBallotScreen();
            startActiveSessionListener();
        });
    }

    /* =====================================================
       OFFICIAL BALLOT DATA
       ===================================================== */

    private void loadOfficialBallot(Runnable onReady) {
        ballotPositions.clear();
        selectedCandidates.clear();
        candidateRadioButtons.clear();
        ballotContainer.removeAllViews();

        firestore.collection("NominationForms")
                .document(currentElectionId)
                .get()
                .addOnSuccessListener(formSnapshot -> {
                    if (!formSnapshot.exists()) {
                        ballotPreparationFailed("Finalized nomination form was not found.");
                        return;
                    }

                    String formStatus = safeString(formSnapshot.getString("formStatus"));
                    String formElectionId = safeString(formSnapshot.getString("electionId"));
                    Object rawAllowed = formSnapshot.get("allowedPositionIds");

                    if (!("finalized".equals(formStatus) || "closed".equals(formStatus))
                            || !currentElectionId.equals(formElectionId)
                            || !(rawAllowed instanceof List<?>)) {
                        ballotPreparationFailed("Official ballot configuration is invalid.");
                        return;
                    }

                    List<String> allowedPositionIds = new ArrayList<>();
                    for (Object value : (List<?>) rawAllowed) {
                        if (value instanceof String && !((String) value).trim().isEmpty()) {
                            allowedPositionIds.add(((String) value).trim());
                        }
                    }

                    if (allowedPositionIds.isEmpty()) {
                        ballotPreparationFailed("No official ballot positions are configured.");
                        return;
                    }

                    loadPositionAtIndex(allowedPositionIds, 0, onReady);
                })
                .addOnFailureListener(error -> {
                    Log.e(TAG, "Nomination form load failed", error);
                    ballotPreparationFailed("Official ballot configuration could not be loaded.");
                });
    }

    private void loadPositionAtIndex(List<String> ids, int index, Runnable onReady) {
        if (index >= ids.size()) {
            loadCandidatesAtIndex(0, onReady);
            return;
        }

        String positionId = ids.get(index);

        firestore.collection("Positions")
                .document(positionId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        ballotPreparationFailed("An official ballot position is missing: " + positionId);
                        return;
                    }

                    String electionId = safeString(snapshot.getString("electionId"));
                    if (!currentElectionId.equals(electionId)) {
                        ballotPreparationFailed("A ballot position belongs to the wrong election.");
                        return;
                    }

                    String name = safeString(snapshot.getString("name"));
                    if (name.isEmpty()) {
                        name = safeString(snapshot.getString("positionName"));
                    }
                    if (name.isEmpty()) {
                        name = "Unnamed Position";
                    }

                    int seats = 1;
                    Long seatsValue = snapshot.getLong("seats");
                    if (seatsValue == null) {
                        seatsValue = snapshot.getLong("numberOfSeats");
                    }
                    if (seatsValue != null && seatsValue > 0 && seatsValue <= 20) {
                        seats = seatsValue.intValue();
                    }

                    if (seats != 1) {
                        ballotPreparationFailed(
                                "This election has a multi-seat position (" + name
                                        + "). This version supports one seat per position."
                        );
                        return;
                    }

                    ballotPositions.add(new PositionItem(positionId, name, seats));
                    loadPositionAtIndex(ids, index + 1, onReady);
                })
                .addOnFailureListener(error -> {
                    Log.e(TAG, "Position load failed: " + positionId, error);
                    ballotPreparationFailed("Official ballot positions could not be loaded.");
                });
    }

    private void loadCandidatesAtIndex(int index, Runnable onReady) {
        if (index >= ballotPositions.size()) {
            finalizeBallotData(onReady);
            return;
        }

        PositionItem position = ballotPositions.get(index);

        Query candidateQuery = firestore.collection("Candidates")
                .whereEqualTo("electionId", currentElectionId)
                .whereEqualTo("positionId", position.id)
                .whereEqualTo("status", "finalized");

        candidateQuery.get()
                .addOnSuccessListener(snapshot -> {
                    position.candidates.clear();

                    for (QueryDocumentSnapshot document : snapshot) {
                        String name = safeString(document.getString("applicantName"));
                        if (name.isEmpty()) {
                            name = "Unnamed Candidate";
                        }

                        position.candidates.add(new CandidateItem(
                                document.getId(),
                                position.id,
                                name,
                                safeString(document.getString("className")),
                                safeString(document.getString("section")),
                                safeString(document.getString("manifesto")),
                                safeString(document.getString("symbolBase64")),
                                safeString(document.getString("symbolMimeType"))
                        ));
                    }

                    Collections.sort(position.candidates,
                            Comparator.comparing(candidate -> candidate.name.toLowerCase(Locale.ROOT)));

                    loadCandidatesAtIndex(index + 1, onReady);
                })
                .addOnFailureListener(error -> {
                    Log.e(TAG, "Candidate load failed for position " + position.id, error);
                    ballotPreparationFailed(
                            "Finalized candidates could not be loaded. If Firebase asks for an index, create the suggested index and run again."
                    );
                });
    }

    private void finalizeBallotData(Runnable onReady) {
        if (ballotPositions.isEmpty()) {
            ballotPreparationFailed("No official positions are available on this ballot.");
            return;
        }

        boolean hasContestedPosition = false;
        for (PositionItem position : ballotPositions) {
            if (!position.candidates.isEmpty()) {
                hasContestedPosition = true;
                break;
            }
        }

        if (!hasContestedPosition) {
            ballotPreparationFailed("No finalized candidates are available for this election.");
            return;
        }

        onReady.run();
    }

    private void ballotPreparationFailed(String message) {
        Log.e(TAG, "Ballot preparation failed: " + message);

        preparingBallot = false;
        ballotOpen = false;
        resetBallotCollectionsOnly();

        showConnectedState("Ballot could not be prepared", message);

        Toast.makeText(this, message, Toast.LENGTH_LONG).show();

        /*
         * Keep the listener stopped here. Re-listening immediately would
         * pick the same still-authorized session again and create a retry
         * loop when ballot configuration itself is invalid. The PO can
         * cancel/re-authorize after the configuration problem is corrected.
         */
    }

    /* =====================================================
       CLAIM SESSION: AUTHORIZED -> BALLOT_OPEN
       ===================================================== */

    private void claimAuthorizedSession(String sessionId) {
        if (currentDeviceUser == null || sessionId.isEmpty()) {
            ballotPreparationFailed("Device identity or voting session is unavailable.");
            return;
        }

        DocumentReference sessionRef = firestore.collection("VotingSessions").document(sessionId);

        firestore.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(sessionRef);

            if (!snapshot.exists()) {
                throw new IllegalStateException("Voting session no longer exists.");
            }

            String status = safeString(snapshot.getString("status"));
            String electionId = safeString(snapshot.getString("electionId"));
            String boothId = safeString(snapshot.getString("boothId"));
            String voterId = safeString(snapshot.getString("voterId"));
            String deviceId = safeString(snapshot.getString("deviceId"));
            Timestamp expiresAt = snapshot.getTimestamp("expiresAt");

            if ("ballot_open".equals(status)
                    && currentDeviceUser.getUid().equals(deviceId)) {
                currentVoterId = voterId;
                return null;
            }

            if (!"authorized".equals(status)
                    || !deviceId.isEmpty()
                    || expiresAt == null
                    || expiresAt.toDate().getTime() <= System.currentTimeMillis()
                    || !currentElectionId.equals(electionId)
                    || !currentBoothId.equals(boothId)
                    || voterId.isEmpty()) {
                throw new IllegalStateException("Voting authorization is no longer valid.");
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("status", "ballot_open");
            updates.put("deviceId", currentDeviceUser.getUid());
            updates.put("ballotOpenedAt", FieldValue.serverTimestamp());
            updates.put("updatedAt", FieldValue.serverTimestamp());

            transaction.update(sessionRef, updates);
            currentVoterId = voterId;
            return null;
        }).addOnSuccessListener(unused -> {
            preparingBallot = false;
            ballotOpen = true;
            showBallotScreen();
            startActiveSessionListener();
        }).addOnFailureListener(error -> {
            Log.e(TAG, "Session claim failed", error);
            ballotPreparationFailed(error.getMessage() == null
                    ? "Voting authorization could not be claimed."
                    : error.getMessage());
        });
    }

    /* =====================================================
       BALLOT RENDERING / SELECTION
       ===================================================== */

    private void showBallotScreen() {
        pairingPanel.setVisibility(View.GONE);
        connectedPanel.setVisibility(View.GONE);
        successPanel.setVisibility(View.GONE);
        ballotPanel.setVisibility(View.VISIBLE);

        tvBallotElectionName.setText(currentElectionName);
        tvBallotBoothName.setText(currentBoothDisplay);
        tvBallotMessage.setText("Choose one candidate for each position that has candidates, then submit your ballot.");
        tvAuthStatus.setText("Secure ballot open");

        renderBallot();
        updateBallotProgress();
    }

    private void renderBallot() {
        ballotContainer.removeAllViews();
        selectedCandidates.clear();
        candidateRadioButtons.clear();

        int positionNumber = 1;
        for (PositionItem position : ballotPositions) {
            boolean hasCandidates = !position.candidates.isEmpty();
            if (hasCandidates) {
                selectedCandidates.put(position.id, new LinkedHashSet<>());
                candidateRadioButtons.put(position.id, new ArrayList<>());
            }

            LinearLayout positionCard = new LinearLayout(this);
            positionCard.setOrientation(LinearLayout.VERTICAL);
            positionCard.setPadding(dp(16), dp(18), dp(16), dp(18));
            positionCard.setBackground(createRoundedBackground(
                    Color.WHITE,
                    Color.parseColor("#D1D5DB"),
                    1,
                    16
            ));

            LinearLayout.LayoutParams positionParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            positionParams.bottomMargin = dp(18);
            positionCard.setLayoutParams(positionParams);

            TextView positionTitle = new TextView(this);
            positionTitle.setText(positionNumber + ". " + position.name);
            positionTitle.setTextColor(Color.parseColor("#172B4D"));
            positionTitle.setTextSize(21);
            positionTitle.setTypeface(
                    positionTitle.getTypeface(),
                    android.graphics.Typeface.BOLD
            );
            positionCard.addView(positionTitle);

            TextView instruction = new TextView(this);
            instruction.setText(
                    hasCandidates
                            ? "Choose one candidate for this position"
                            : "No candidate applied for this position. This seat is not included in voting."
            );
            instruction.setTextColor(Color.parseColor("#173A5E"));
            instruction.setTextSize(14);
            instruction.setTypeface(
                    instruction.getTypeface(),
                    android.graphics.Typeface.BOLD
            );
            instruction.setPadding(0, dp(5), 0, dp(14));
            positionCard.addView(instruction);

            if (hasCandidates) {
                for (CandidateItem candidate : position.candidates) {
                    positionCard.addView(createCandidateCard(position, candidate));
                }
            }

            ballotContainer.addView(positionCard);
            positionNumber++;
        }
    }

    private View createCandidateCard(PositionItem position, CandidateItem candidate) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackground(createCandidateCardBackground(false));
        card.setClickable(true);
        card.setFocusable(true);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.bottomMargin = dp(12);
        card.setLayoutParams(cardParams);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView imageView = new ImageView(this);
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dp(84), dp(84));
        imageParams.setMarginEnd(dp(14));
        imageView.setLayoutParams(imageParams);
        imageView.setPadding(dp(6), dp(6), dp(6), dp(6));
        imageView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        imageView.setBackground(createRoundedBackground(
                Color.parseColor("#F3F4F6"),
                Color.parseColor("#E5E7EB"),
                1,
                12
        ));

        Bitmap symbol = decodeBase64Image(candidate.symbolBase64);
        if (symbol != null) {
            imageView.setImageBitmap(symbol);
        } else {
            imageView.setImageResource(android.R.drawable.ic_menu_gallery);
        }
        header.addView(imageView);

        LinearLayout identityColumn = new LinearLayout(this);
        identityColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams identityParams = new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        );
        identityColumn.setLayoutParams(identityParams);

        TextView name = new TextView(this);
        name.setText(candidate.name);
        name.setTextColor(Color.parseColor("#172B4D"));
        name.setTextSize(18);
        name.setTypeface(name.getTypeface(), android.graphics.Typeface.BOLD);
        identityColumn.addView(name);

        TextView positionText = new TextView(this);
        positionText.setText("Candidate for " + position.name);
        positionText.setTextColor(Color.parseColor("#173A5E"));
        positionText.setTextSize(12);
        positionText.setTypeface(
                positionText.getTypeface(),
                android.graphics.Typeface.BOLD
        );
        positionText.setPadding(0, dp(3), 0, 0);
        identityColumn.addView(positionText);

        String detail = buildCandidateDetail(candidate);
        if (!detail.isEmpty()) {
            TextView details = new TextView(this);
            details.setText(detail);
            details.setTextColor(Color.parseColor("#6B7280"));
            details.setTextSize(13);
            details.setPadding(0, dp(5), 0, 0);
            identityColumn.addView(details);
        }

        header.addView(identityColumn);

        RadioButton radioButton = new RadioButton(this);
        radioButton.setText("Select");
        radioButton.setTextColor(Color.parseColor("#173A5E"));
        radioButton.setTextSize(12);
        radioButton.setTypeface(radioButton.getTypeface(), android.graphics.Typeface.BOLD);
        radioButton.setButtonTintList(new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{
                        Color.parseColor("#173A5E"),
                        Color.parseColor("#667085")
                }
        ));
        radioButton.setTag(candidate.id);
        radioButton.setContentDescription("Select " + candidate.name + " for " + position.name);
        LinearLayout.LayoutParams radioParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        radioParams.setMarginStart(dp(8));
        radioButton.setLayoutParams(radioParams);
        header.addView(radioButton);

        card.addView(header);

        View divider = new View(this);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
        );
        dividerParams.topMargin = dp(13);
        dividerParams.bottomMargin = dp(12);
        divider.setLayoutParams(dividerParams);
        divider.setBackgroundColor(Color.parseColor("#E5E7EB"));
        card.addView(divider);

        TextView manifestoLabel = new TextView(this);
        manifestoLabel.setText("Candidate statement");
        manifestoLabel.setTextColor(Color.parseColor("#374151"));
        manifestoLabel.setTextSize(12);
        manifestoLabel.setTypeface(
                manifestoLabel.getTypeface(),
                android.graphics.Typeface.BOLD
        );
        card.addView(manifestoLabel);

        TextView manifestoText = new TextView(this);
        manifestoText.setText(
                candidate.manifesto.isEmpty()
                        ? "No manifesto provided."
                        : candidate.manifesto
        );
        manifestoText.setTextColor(Color.parseColor("#374151"));
        manifestoText.setTextSize(14);
        manifestoText.setLineSpacing(0, 1.12f);
        manifestoText.setPadding(0, dp(5), 0, 0);
        card.addView(manifestoText);

        TextView profileLink = new TextView(this);
        profileLink.setText("View candidate details");
        profileLink.setTextColor(Color.parseColor("#173A5E"));
        profileLink.setTextSize(12);
        profileLink.setTypeface(
                profileLink.getTypeface(),
                android.graphics.Typeface.BOLD
        );
        profileLink.setPadding(0, dp(12), 0, dp(2));
        profileLink.setOnClickListener(view -> showCandidateProfile(position, candidate));
        card.addView(profileLink);

        candidateRadioButtons.get(position.id).add(radioButton);

        card.setOnClickListener(view -> {
            if (!radioButton.isChecked()) {
                radioButton.setChecked(true);
            }
        });

        imageView.setOnClickListener(view -> showCandidateProfile(position, candidate));
        name.setOnClickListener(view -> showCandidateProfile(position, candidate));

        radioButton.setOnCheckedChangeListener((buttonView, isChecked) -> {
            LinkedHashSet<String> selected = selectedCandidates.get(position.id);
            if (selected == null) {
                return;
            }

            if (isChecked) {
                List<RadioButton> buttons = candidateRadioButtons.get(position.id);
                if (buttons != null) {
                    for (RadioButton other : buttons) {
                        if (other != radioButton && other.isChecked()) {
                            other.setChecked(false);
                        }
                    }
                }

                selected.clear();
                selected.add(candidate.id);
                card.setBackground(createCandidateCardBackground(true));
            } else {
                selected.remove(candidate.id);
                card.setBackground(createCandidateCardBackground(false));
            }

            updateBallotProgress();
        });

        return card;
    }

    private String buildCandidateDetail(CandidateItem candidate) {
        String detail = candidate.className;

        if (!candidate.section.isEmpty()) {
            detail = detail.isEmpty()
                    ? "Section " + candidate.section
                    : detail + " • Section " + candidate.section;
        }

        return detail;
    }

    private GradientDrawable createCandidateCardBackground(boolean selected) {
        return createRoundedBackground(
                selected ? Color.parseColor("#EAF1F8") : Color.WHITE,
                selected ? Color.parseColor("#173A5E") : Color.parseColor("#D5DDE6"),
                selected ? 2 : 1,
                14
        );
    }

    private GradientDrawable createRoundedBackground(
            int fillColor,
            int strokeColor,
            int strokeWidthDp,
            int radiusDp
    ) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(strokeWidthDp), strokeColor);
        return drawable;
    }

    private void showCandidateProfile(PositionItem position, CandidateItem candidate) {
        ScrollView scrollView = new ScrollView(this);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(18), dp(22), dp(18));
        scrollView.addView(content);

        ImageView symbolView = new ImageView(this);
        LinearLayout.LayoutParams symbolParams = new LinearLayout.LayoutParams(dp(150), dp(150));
        symbolParams.gravity = Gravity.CENTER_HORIZONTAL;
        symbolView.setLayoutParams(symbolParams);
        symbolView.setPadding(dp(8), dp(8), dp(8), dp(8));
        symbolView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        symbolView.setBackground(createRoundedBackground(
                Color.parseColor("#F3F4F6"),
                Color.parseColor("#E5E7EB"),
                1,
                14
        ));

        Bitmap symbol = decodeBase64Image(candidate.symbolBase64);
        if (symbol != null) {
            symbolView.setImageBitmap(symbol);
        } else {
            symbolView.setImageResource(android.R.drawable.ic_menu_gallery);
        }
        content.addView(symbolView);

        TextView name = new TextView(this);
        name.setText(candidate.name);
        name.setTextColor(Color.parseColor("#172B4D"));
        name.setTextSize(23);
        name.setGravity(Gravity.CENTER);
        name.setTypeface(name.getTypeface(), android.graphics.Typeface.BOLD);
        name.setPadding(0, dp(16), 0, 0);
        content.addView(name);

        TextView positionText = new TextView(this);
        positionText.setText(position.name);
        positionText.setTextColor(Color.parseColor("#173A5E"));
        positionText.setTextSize(15);
        positionText.setGravity(Gravity.CENTER);
        positionText.setTypeface(
                positionText.getTypeface(),
                android.graphics.Typeface.BOLD
        );
        positionText.setPadding(0, dp(4), 0, 0);
        content.addView(positionText);

        String detail = buildCandidateDetail(candidate);
        if (!detail.isEmpty()) {
            TextView detailText = new TextView(this);
            detailText.setText(detail);
            detailText.setTextColor(Color.parseColor("#6B7280"));
            detailText.setTextSize(14);
            detailText.setGravity(Gravity.CENTER);
            detailText.setPadding(0, dp(5), 0, 0);
            content.addView(detailText);
        }

        TextView manifestoLabel = new TextView(this);
        manifestoLabel.setText("Candidate statement");
        manifestoLabel.setTextColor(Color.parseColor("#172B4D"));
        manifestoLabel.setTextSize(13);
        manifestoLabel.setTypeface(
                manifestoLabel.getTypeface(),
                android.graphics.Typeface.BOLD
        );
        manifestoLabel.setPadding(0, dp(22), 0, 0);
        content.addView(manifestoLabel);

        TextView manifesto = new TextView(this);
        manifesto.setText(
                candidate.manifesto.isEmpty()
                        ? "No manifesto provided."
                        : candidate.manifesto
        );
        manifesto.setTextColor(Color.parseColor("#374151"));
        manifesto.setTextSize(15);
        manifesto.setLineSpacing(0, 1.15f);
        manifesto.setPadding(0, dp(7), 0, dp(6));
        content.addView(manifesto);

        new AlertDialog.Builder(this)
                .setTitle("Candidate Profile")
                .setView(scrollView)
                .setNegativeButton("Close", null)
                .setPositiveButton("Select Candidate", (dialog, which) -> {
                    List<RadioButton> buttons = candidateRadioButtons.get(position.id);
                    if (buttons == null) {
                        return;
                    }

                    for (RadioButton button : buttons) {
                        if (candidate.id.equals(String.valueOf(button.getTag()))) {
                            button.setChecked(true);
                            break;
                        }
                    }
                })
                .show();
    }

    private void updateBallotProgress() {
        int completed = 0;

        int contestedPositionCount = 0;

        for (PositionItem position : ballotPositions) {
            if (position.candidates.isEmpty()) {
                continue;
            }

            contestedPositionCount++;
            Set<String> selected = selectedCandidates.get(position.id);
            if (selected != null && selected.size() == 1) {
                completed++;
            }
        }

        tvBallotProgress.setText(
                "Completed: " + completed + " of " + contestedPositionCount + " positions"
        );

        boolean complete = contestedPositionCount > 0 && completed == contestedPositionCount;
        boolean canSubmit = complete
                && ballotOpen
                && !submissionInProgress
                && "voting_active".equals(currentElectionStatus);

        btnSubmitBallot.setEnabled(canSubmit);
    }

    /* =====================================================
       FINAL BALLOT SUBMISSION
       ===================================================== */

    private void confirmBallotSubmission() {
        if (!isBallotComplete()) {
            Toast.makeText(this, "Select one candidate for every position that has candidates.", Toast.LENGTH_LONG).show();
            return;
        }

        if (!"voting_active".equals(currentElectionStatus)) {
            Toast.makeText(this, "Voting is not currently active.", Toast.LENGTH_LONG).show();
            return;
        }

        showVoteConfirmationDialog();
    }

    private void showVoteConfirmationDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_submit_vote);
        dialog.setCanceledOnTouchOutside(false);

        Button goBackButton = dialog.findViewById(R.id.btnReviewVote);
        Button submitVoteButton = dialog.findViewById(R.id.btnConfirmVote);

        goBackButton.setOnClickListener(view -> dialog.dismiss());
        submitVoteButton.setOnClickListener(view -> {
            dialog.dismiss();
            submitBallot();
        });

        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT
            );
            window.setWindowAnimations(0);
        }
    }

    private boolean isBallotComplete() {
        if (ballotPositions.isEmpty()) {
            return false;
        }

        boolean hasContestedPosition = false;

        for (PositionItem position : ballotPositions) {
            if (position.candidates.isEmpty()) {
                continue;
            }

            hasContestedPosition = true;
            Set<String> selected = selectedCandidates.get(position.id);
            if (selected == null || selected.size() != 1) {
                return false;
            }
        }

        return hasContestedPosition;
    }

    private void submitBallot() {
        if (submissionInProgress || !ballotOpen || currentDeviceUser == null) {
            return;
        }

        if (!isBallotComplete()) {
            Toast.makeText(this, "Complete the ballot before submitting.", Toast.LENGTH_LONG).show();
            return;
        }

        if (currentSessionId.isEmpty() || currentVoterId.isEmpty()) {
            Toast.makeText(this, "Voting session is unavailable.", Toast.LENGTH_LONG).show();
            return;
        }

        int voteWriteCount = 0;
        for (Set<String> selected : selectedCandidates.values()) {
            voteWriteCount += selected.size();
        }

        if (voteWriteCount < 1 || voteWriteCount > 450) {
            Toast.makeText(this, "Ballot size is not supported.", Toast.LENGTH_LONG).show();
            return;
        }

        submissionInProgress = true;
        btnSubmitBallot.setEnabled(false);
        tvBallotMessage.setText("Submitting your vote...");
        tvAuthStatus.setText("Submitting vote...");

        DocumentReference sessionRef = firestore.collection("VotingSessions").document(currentSessionId);
        DocumentReference voterRef = firestore.collection("Voters").document(currentVoterId);
        DocumentReference boothStateRef = firestore.collection("BoothVotingState").document(currentBoothId);

        WriteBatch batch = firestore.batch();

        for (PositionItem position : ballotPositions) {
            Set<String> selected = selectedCandidates.get(position.id);
            if (selected == null) {
                continue;
            }

            for (String candidateId : selected) {
                DocumentReference voteRef = firestore.collection("Votes").document();

                Map<String, Object> voteData = new HashMap<>();
                voteData.put("electionId", currentElectionId);
                voteData.put("positionId", position.id);
                voteData.put("candidateId", candidateId);
                voteData.put("castAt", FieldValue.serverTimestamp());

                batch.set(voteRef, voteData);
            }
        }

        Map<String, Object> sessionUpdates = new HashMap<>();
        sessionUpdates.put("status", "submitted");
        sessionUpdates.put("submittedAt", FieldValue.serverTimestamp());
        sessionUpdates.put("updatedAt", FieldValue.serverTimestamp());
        batch.update(sessionRef, sessionUpdates);

        Map<String, Object> voterUpdates = new HashMap<>();
        voterUpdates.put("participationStatus", "voted");
        voterUpdates.put("updatedAt", FieldValue.serverTimestamp());
        batch.update(voterRef, voterUpdates);

        Map<String, Object> boothStateUpdates = new HashMap<>();
        boothStateUpdates.put("status", "idle");
        boothStateUpdates.put("activeSessionId", "");
        boothStateUpdates.put("activeVoterId", "");
        boothStateUpdates.put("releasedBy", currentDeviceUser.getUid());
        boothStateUpdates.put("releasedAt", FieldValue.serverTimestamp());
        boothStateUpdates.put("updatedAt", FieldValue.serverTimestamp());
        batch.update(boothStateRef, boothStateUpdates);

        batch.commit()
                .addOnSuccessListener(unused -> handleSuccessfulSubmission())
                .addOnFailureListener(error -> {
                    submissionInProgress = false;
                    Log.e(TAG, "Ballot submission failed", error);
                    tvBallotMessage.setText(
                            "Ballot was not submitted. Your selections are still on this device. Try again."
                    );
                    tvAuthStatus.setText("Ballot submission failed");
                    updateBallotProgress();
                    Toast.makeText(
                            MainActivity.this,
                            error.getMessage() == null
                                    ? "Ballot submission failed."
                                    : error.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();
                });
    }

    private void handleSuccessfulSubmission() {
        stopActiveSessionListener();
        stopAuthorizationListener();

        submissionInProgress = false;
        ballotOpen = false;
        preparingBallot = false;

        pairingPanel.setVisibility(View.GONE);
        connectedPanel.setVisibility(View.GONE);
        ballotPanel.setVisibility(View.GONE);
        successPanel.setVisibility(View.VISIBLE);

        tvAuthStatus.setText("Vote submitted");

        resetBallotCollectionsOnly();
        currentSessionId = "";
        currentVoterId = "";

        mainHandler.postDelayed(() -> {
            successPanel.setVisibility(View.GONE);

            if ("voting_active".equals(currentElectionStatus)) {
                showConnectedState("Waiting for voter authorization...", "Voting device connected");
                recoverExistingBoothSession();
            } else {
                handleElectionStateChange();
            }
        }, SUCCESS_SCREEN_MS);
    }

    /* =====================================================
       ACTIVE SESSION LISTENER
       ===================================================== */

    private void startActiveSessionListener() {
        stopActiveSessionListener();

        if (currentSessionId.isEmpty() || currentDeviceUser == null) {
            return;
        }

        activeSessionListener = firestore.collection("VotingSessions")
                .document(currentSessionId)
                .addSnapshotListener(this, (snapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Active-session listener failed", error);
                        return;
                    }

                    if (snapshot == null || !snapshot.exists()) {
                        return;
                    }

                    String status = safeString(snapshot.getString("status"));
                    String deviceId = safeString(snapshot.getString("deviceId"));

                    if ("cancelled".equals(status)) {
                        handleSessionCancelled();
                        return;
                    }

                    if ("ballot_open".equals(status)
                            && !currentDeviceUser.getUid().equals(deviceId)) {
                        handleSessionCancelled();
                    }
                });
    }

    private void handleSessionCancelled() {
        stopActiveSessionListener();

        submissionInProgress = false;
        ballotOpen = false;
        preparingBallot = false;

        resetBallotCollectionsOnly();
        currentSessionId = "";
        currentVoterId = "";

        Toast.makeText(this, "This voting session was cancelled. Return the device to the Presiding Officer.", Toast.LENGTH_LONG).show();

        if ("voting_active".equals(currentElectionStatus)) {
            showConnectedState("Waiting for voter authorization...", "Voting device connected");
            recoverExistingBoothSession();
        } else {
            handleElectionStateChange();
        }
    }

    /* =====================================================
       WAITING / ERROR UI
       ===================================================== */

    private void showConnectedState(String mainStatus, String deviceStatus) {
        if (ballotOpen) {
            return;
        }

        pairingPanel.setVisibility(View.GONE);
        ballotPanel.setVisibility(View.GONE);
        successPanel.setVisibility(View.GONE);
        connectedPanel.setVisibility(View.VISIBLE);

        tvBoothName.setText(currentBoothDisplay.isEmpty() ? currentBoothId : currentBoothDisplay);
        tvElectionName.setText(currentElectionName.isEmpty() ? currentElectionId : currentElectionName);
        tvWaitingStatus.setText(mainStatus);
        tvAuthStatus.setText(deviceStatus);
    }

    private void showPairingScreen() {
        stopAllListeners();
        stopHeartbeat();
        resetBallotState();

        currentElectionId = "";
        currentBoothId = "";
        currentElectionName = "";
        currentBoothDisplay = "";
        currentElectionStatus = "";

        pairingPanel.setVisibility(View.VISIBLE);
        connectedPanel.setVisibility(View.GONE);
        ballotPanel.setVisibility(View.GONE);
        successPanel.setVisibility(View.GONE);

        tvAuthStatus.setText("Device identity ready");
        btnConnectDevice.setText("CONNECT DEVICE");
        btnConnectDevice.setEnabled(true);
        etPairingCode.setEnabled(true);
    }

    private void showInactiveDeviceState(String status) {
        stopAllListeners();
        stopHeartbeat();
        resetBallotState();

        pairingPanel.setVisibility(View.GONE);
        connectedPanel.setVisibility(View.GONE);
        ballotPanel.setVisibility(View.GONE);
        successPanel.setVisibility(View.GONE);

        tvAuthStatus.setText("Voting device is not active");
        btnConnectDevice.setEnabled(false);
        etPairingCode.setEnabled(false);

        Toast.makeText(
                this,
                "This device binding is " + (status.isEmpty() ? "inactive" : status) + ". Contact the Presiding Officer.",
                Toast.LENGTH_LONG
        ).show();
    }

    private void showDeviceLoadError(String message) {
        stopAllListeners();
        stopHeartbeat();
        resetBallotState();

        pairingPanel.setVisibility(View.GONE);
        connectedPanel.setVisibility(View.GONE);
        ballotPanel.setVisibility(View.GONE);
        successPanel.setVisibility(View.GONE);

        tvAuthStatus.setText("Voting device could not be loaded");
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void showAuthenticationError(String message) {
        stopAllListeners();
        stopHeartbeat();
        resetBallotState();

        currentDeviceUser = null;
        currentElectionId = "";
        currentBoothId = "";

        pairingPanel.setVisibility(View.GONE);
        connectedPanel.setVisibility(View.GONE);
        ballotPanel.setVisibility(View.GONE);
        successPanel.setVisibility(View.GONE);

        tvAuthStatus.setText("Device authentication failed");
        tvDeviceUid.setText("");
        btnConnectDevice.setEnabled(false);

        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    /* =====================================================
       DEVICE PAIRING
       ===================================================== */

    private void attemptDevicePairing() {
        if (currentDeviceUser == null) {
            Toast.makeText(this, "Device identity is not ready.", Toast.LENGTH_SHORT).show();
            return;
        }

        String pairingCode = etPairingCode.getText().toString().trim();

        if (pairingCode.length() < 8 || pairingCode.length() > 20) {
            etPairingCode.setError("Enter a valid pairing code.");
            etPairingCode.requestFocus();
            return;
        }

        setPairingLoadingState();

        String deviceUid = currentDeviceUser.getUid();
        DocumentReference pairingCodeRef = firestore.collection("DevicePairingCodes").document(pairingCode);

        pairingCodeRef.get()
                .addOnSuccessListener(snapshot -> validateAndClaimPairingCode(
                        pairingCode,
                        deviceUid,
                        pairingCodeRef,
                        snapshot
                ))
                .addOnFailureListener(error -> {
                    Log.e(TAG, "Pairing-code read failed", error);
                    showPairingError("Pairing code is invalid, expired, or unavailable.");
                });
    }

    private void validateAndClaimPairingCode(
            String pairingCode,
            String deviceUid,
            DocumentReference pairingCodeRef,
            DocumentSnapshot pairingSnapshot
    ) {
        if (!pairingSnapshot.exists()) {
            showPairingError("Pairing code was not found.");
            return;
        }

        String status = safeString(pairingSnapshot.getString("status"));
        String electionId = safeString(pairingSnapshot.getString("electionId"));
        String boothId = safeString(pairingSnapshot.getString("boothId"));
        String createdBy = safeString(pairingSnapshot.getString("createdBy"));
        Timestamp expiresAt = pairingSnapshot.getTimestamp("expiresAt");

        if (!"pending".equals(status)
                || electionId.isEmpty()
                || boothId.isEmpty()
                || createdBy.isEmpty()
                || expiresAt == null
                || expiresAt.toDate().getTime() <= System.currentTimeMillis()) {
            showPairingError("Pairing code is not valid or has expired.");
            return;
        }

        claimPairingCode(
                pairingCode,
                deviceUid,
                electionId,
                boothId,
                createdBy,
                pairingCodeRef
        );
    }

    private void claimPairingCode(
            String pairingCode,
            String deviceUid,
            String electionId,
            String boothId,
            String pairedBy,
            DocumentReference pairingCodeRef
    ) {
        DocumentReference deviceRef = firestore.collection("VotingDevices").document(deviceUid);
        WriteBatch batch = firestore.batch();

        Map<String, Object> pairingUpdates = new HashMap<>();
        pairingUpdates.put("status", "claimed");
        pairingUpdates.put("claimedBy", deviceUid);
        pairingUpdates.put("claimedAt", FieldValue.serverTimestamp());
        batch.update(pairingCodeRef, pairingUpdates);

        Map<String, Object> deviceData = new HashMap<>();
        deviceData.put("deviceUid", deviceUid);
        deviceData.put("electionId", electionId);
        deviceData.put("boothId", boothId);
        deviceData.put("status", "active");
        deviceData.put("pairedAt", FieldValue.serverTimestamp());
        deviceData.put("pairedBy", pairedBy);
        deviceData.put("pairingCode", pairingCode);
        deviceData.put("lastSeenAt", FieldValue.serverTimestamp());
        deviceData.put("revokedAt", null);
        deviceData.put("revokedBy", "");
        batch.set(deviceRef, deviceData);

        batch.commit()
                .addOnSuccessListener(unused -> {
                    Toast.makeText(
                            MainActivity.this,
                            "Device connected to booth successfully.",
                            Toast.LENGTH_LONG
                    ).show();
                    checkExistingDeviceBinding();
                })
                .addOnFailureListener(error -> {
                    Log.e(TAG, "Device pairing failed", error);
                    showPairingError("Device pairing failed. Generate a new pairing code and try again.");
                });
    }

    private void setPairingLoadingState() {
        btnConnectDevice.setEnabled(false);
        etPairingCode.setEnabled(false);
        btnConnectDevice.setText("CONNECTING...");
        tvAuthStatus.setText("Connecting voting device...");
    }

    private void showPairingError(String message) {
        pairingPanel.setVisibility(View.VISIBLE);
        connectedPanel.setVisibility(View.GONE);
        ballotPanel.setVisibility(View.GONE);
        successPanel.setVisibility(View.GONE);

        tvAuthStatus.setText("Device identity ready");
        btnConnectDevice.setText("CONNECT DEVICE");
        btnConnectDevice.setEnabled(true);
        etPairingCode.setEnabled(true);

        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    /* =====================================================
       HEARTBEAT
       ===================================================== */

    private void startHeartbeat() {
        stopHeartbeat();
        heartbeatHandler.post(heartbeatRunnable);
    }

    private void stopHeartbeat() {
        heartbeatHandler.removeCallbacks(heartbeatRunnable);
    }

    private void sendHeartbeat() {
        if (currentDeviceUser == null || currentElectionId.isEmpty() || currentBoothId.isEmpty()) {
            return;
        }

        firestore.collection("VotingDevices")
                .document(currentDeviceUser.getUid())
                .update("lastSeenAt", FieldValue.serverTimestamp())
                .addOnFailureListener(error ->
                        Log.w(TAG, "Heartbeat update failed", error));
    }

    /* =====================================================
       LISTENER / STATE CLEANUP
       ===================================================== */

    private void stopAuthorizationListener() {
        if (authorizationListener != null) {
            authorizationListener.remove();
            authorizationListener = null;
        }
    }

    private void stopElectionListener() {
        if (electionListener != null) {
            electionListener.remove();
            electionListener = null;
        }
    }

    private void stopActiveSessionListener() {
        if (activeSessionListener != null) {
            activeSessionListener.remove();
            activeSessionListener = null;
        }
    }

    private void stopAllListeners() {
        stopAuthorizationListener();
        stopElectionListener();
        stopActiveSessionListener();
    }

    private void resetBallotState() {
        preparingBallot = false;
        ballotOpen = false;
        submissionInProgress = false;
        currentSessionId = "";
        currentVoterId = "";
        resetBallotCollectionsOnly();
    }

    private void resetBallotCollectionsOnly() {
        ballotPositions.clear();
        selectedCandidates.clear();
        candidateRadioButtons.clear();
        if (ballotContainer != null) {
            ballotContainer.removeAllViews();
        }
        if (btnSubmitBallot != null) {
            btnSubmitBallot.setEnabled(false);
        }
    }

    /* =====================================================
       UTILITIES
       ===================================================== */

    private String safeString(String value) {
        return value == null ? "" : value.trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private Bitmap decodeBase64Image(String source) {
        if (source == null || source.trim().isEmpty()) {
            return null;
        }

        try {
            String data = source.trim();
            int commaIndex = data.indexOf(',');
            if (data.startsWith("data:") && commaIndex >= 0) {
                data = data.substring(commaIndex + 1);
            }

            byte[] decoded = Base64.decode(data, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
        } catch (Exception error) {
            Log.w(TAG, "Candidate symbol could not be decoded", error);
            return null;
        }
    }

    /* =====================================================
       SIMPLE BALLOT MODELS
       ===================================================== */

    private static class PositionItem {
        final String id;
        final String name;
        final int seats;
        final List<CandidateItem> candidates = new ArrayList<>();

        PositionItem(String id, String name, int seats) {
            this.id = id;
            this.name = name;
            this.seats = Math.max(1, seats);
        }
    }

    private static class CandidateItem {
        final String id;
        final String positionId;
        final String name;
        final String className;
        final String section;
        final String manifesto;
        final String symbolBase64;
        final String symbolMimeType;

        CandidateItem(
                String id,
                String positionId,
                String name,
                String className,
                String section,
                String manifesto,
                String symbolBase64,
                String symbolMimeType
        ) {
            this.id = id;
            this.positionId = positionId;
            this.name = name;
            this.className = className;
            this.section = section;
            this.manifesto = manifesto;
            this.symbolBase64 = symbolBase64;
            this.symbolMimeType = symbolMimeType;
        }
    }
}
