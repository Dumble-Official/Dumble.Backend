package com.example.DumbleAuthentication.domain;

/**
 * The subset of {@link UserType} a PARTICIPANT may request to be promoted to.
 * Per the authentication design doc: TRAINER (after cert review) and GYM_OWNER
 * (after business-license review) are the only admin-promoted roles. ADMIN is
 * granted by an existing admin, GYM pages are minted by the gym flow, and
 * MODERATOR is granted by a gym owner — none of those go through this request.
 */
public enum RequestableRole {
    TRAINER,
    GYM_OWNER;

    /** Maps to the account-level {@link UserType} set on approval. */
    public UserType toUserType() {
        return UserType.valueOf(name());
    }
}
