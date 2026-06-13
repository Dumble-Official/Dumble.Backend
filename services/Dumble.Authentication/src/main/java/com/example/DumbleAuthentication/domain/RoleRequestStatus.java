package com.example.DumbleAuthentication.domain;

/**
 * Lifecycle of a role-promotion request.
 *
 * PENDING            — submitted, waiting for an admin to review.
 * CHANGES_REQUESTED  — admin sent it back with a message; the applicant edits
 *                      the same request and resubmits (it returns to PENDING,
 *                      keeping its id so the review history is traceable).
 * APPROVED           — admin accepted it; the user's userType has been flipped.
 * REJECTED           — admin declined it; the row is kept (with the reason) for
 *                      the record, and the user may submit a fresh request.
 */
public enum RoleRequestStatus {
    PENDING,
    CHANGES_REQUESTED,
    APPROVED,
    REJECTED
}
