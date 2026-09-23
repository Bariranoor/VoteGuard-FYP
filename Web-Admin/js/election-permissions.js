import {
    ELECTION_STATUS
} from "./election-lifecycle.js";


export function canEditElectionInformation(status) {

    return (
        status === ELECTION_STATUS.DRAFT
    );

}


export function canEditPositions(status) {

    return (
        status === ELECTION_STATUS.DRAFT
    );

}


export function canOpenRegistration(status) {

    return (
        status === ELECTION_STATUS.FINALIZED
    );

}


export function canCloseRegistration(status) {

    return (
        status === ELECTION_STATUS.FINALIZED
    );

}


export function canStartCandidateVerification(status) {

    return (
        status === ELECTION_STATUS.FINALIZED
    );

}


export function canFinalizeCandidates(status) {

    return (
        status === ELECTION_STATUS.FINALIZED
    );

}


export function canStartVoterPreparation(status) {

    return (
        status === ELECTION_STATUS.FINALIZED
    );

}


export function canStartBoothPreparation(status) {

    return (
        status === ELECTION_STATUS.FINALIZED
    );

}


export function canMarkElectionReady(status) {

    return (
        status === ELECTION_STATUS.FINALIZED
    );

}


export function canStartVoting(status) {

    return (
        status === ELECTION_STATUS.FINALIZED ||
        status === ELECTION_STATUS.VOTING_STOPPED
    );

}


export function canCloseVoting(status) {

    return (
        status === ELECTION_STATUS.VOTING_ACTIVE
    );

}


export function canReviewResults(status) {

    return (
        status === ELECTION_STATUS.VOTING_STOPPED ||
        status === ELECTION_STATUS.CLOSED
    );

}


export function canPublishResults(status) {

    return (
        status === ELECTION_STATUS.CLOSED
    );

}