package com.example.DumbleAuthentication.domain;

/**
 * The role a PARTICIPANT may request through the auth role-request flow.
 *
 * Only TRAINER lives here. Becoming a GYM_OWNER goes through the gym service's
 * gym-registration flow instead (business + branch documents, which are gym
 * domain data); that flow promotes the user to GYM_OWNER on approval. ADMIN is
 * granted by an existing admin, GYM pages are minted by the gym flow, and
 * MODERATOR is granted by a gym owner — none of those go through here.
 */
public enum RequestableRole {
    TRAINER;

    /** Maps to the account-level {@link UserType} set on approval. */
    public UserType toUserType() {
        return UserType.valueOf(name());
    }
}
