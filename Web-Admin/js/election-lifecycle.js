export const ELECTION_STATUS = {
    DRAFT: "draft",
    FINALIZED: "finalized",
    VOTING_ACTIVE: "voting_active",
    VOTING_STOPPED: "voting_stopped",
    CLOSED: "closed"
};

export const ELECTION_STATUS_LABELS = {
    draft: "Draft",
    finalized: "Finalized",
    voting_active: "Voting Active",
    voting_stopped: "Voting Stopped",
    closed: "Closed"
};

export function getElectionStatusLabel(status) {
    return ELECTION_STATUS_LABELS[status] || "Unknown";
}

export function isVotingActive(status) {
    return status === ELECTION_STATUS.VOTING_ACTIVE;
}

export function isElectionConfigurationLocked(status) {
    return (
        status === ELECTION_STATUS.FINALIZED ||
        status === ELECTION_STATUS.VOTING_ACTIVE ||
        status === ELECTION_STATUS.VOTING_STOPPED ||
        status === ELECTION_STATUS.CLOSED
    );
}

export function isElectionFinished(status) {
    return status === ELECTION_STATUS.CLOSED;
}