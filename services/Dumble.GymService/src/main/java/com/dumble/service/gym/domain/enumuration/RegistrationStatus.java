package com.dumble.service.gym.domain.enumuration;

/**
 * Lifecycle of a gym-owner registration.
 *
 * PENDING            — submitted, waiting for an admin to review the documents.
 * CHANGES_REQUESTED  — admin sent it back with a message; the applicant edits
 *                      the same registration and resubmits (back to PENDING,
 *                      keeping its id so the review history is traceable).
 * APPROVED           — admin accepted it; the applicant became GYM_OWNER and the
 *                      gym page (each branch) was created ACTIVE/verified.
 * REJECTED           — admin declined it; the row is kept with the reason.
 */
public enum RegistrationStatus {
    PENDING,
    CHANGES_REQUESTED,
    APPROVED,
    REJECTED
}
