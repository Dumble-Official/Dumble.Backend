package com.example.DumbleAuthentication.dto.response;

import com.example.DumbleAuthentication.domain.User;
import com.example.DumbleAuthentication.domain.UserType;

import java.util.UUID;

/**
 * Lightweight, public-safe user summary for people search / discovery cards.
 * Deliberately excludes PII (email, date of birth, weight, height, …) that the
 * admin {@link UserResponse} exposes — any authenticated user can read this, so
 * it carries only what a "follow" card renders: identity, handle, avatar, role,
 * and a bio snippet. Built directly by a JPQL constructor projection so the
 * search query never hydrates full entities.
 */
public class UserSummaryResponse {

    private final UUID id;
    private final String displayName;
    private final String userName;
    private final String pfp;
    private final UserType userType;
    private final String bio;

    public UserSummaryResponse(UUID id, String displayName, String userName,
            String pfp, UserType userType, String bio) {
        this.id = id;
        this.displayName = displayName;
        this.userName = userName;
        this.pfp = pfp;
        this.userType = userType;
        this.bio = bio;
    }

    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(
                user.getId(), user.getEffectiveDisplayName(), user.getUserName(),
                user.getPfp(), user.getUserType(), user.getBio());
    }

    public UUID getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getUserName() { return userName; }
    public String getPfp() { return pfp; }
    public UserType getUserType() { return userType; }
    public String getBio() { return bio; }
}
