package com.example.DumbleAuthentication.dto.response;

import com.example.DumbleAuthentication.domain.User;
import com.example.DumbleAuthentication.domain.UserType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Another user's profile as seen by a viewer. Identity (name, handle, avatar,
 * role) is always present; the rest of the fields are nulled out when the owner
 * has marked them private via {@code hiddenFields}. The owner's own profile is
 * served by {@link UserResponse} (which never filters), so they always see all
 * of their data regardless of these flags.
 */
public class PublicProfileResponse {

    /** Profile fields a user is allowed to hide from other viewers. */
    public static final Set<String> CONTROLLABLE_FIELDS = Set.of(
            "bio", "dateOfBirth", "gender", "weight", "height", "fitnessGoals", "injuries");

    private UUID id;
    private String displayName;
    private String userName;
    private String pfp;
    private UserType userType;
    private String bio;
    private LocalDate dateOfBirth;
    private String gender;
    private BigDecimal weight;
    private BigDecimal height;
    private List<String> fitnessGoals;
    private String injuries;

    public static PublicProfileResponse from(User user) {
        List<String> hidden = user.getHiddenFields();
        boolean hidesBio = isHidden(hidden, "bio");
        boolean hidesDob = isHidden(hidden, "dateOfBirth");
        boolean hidesGender = isHidden(hidden, "gender");
        boolean hidesWeight = isHidden(hidden, "weight");
        boolean hidesHeight = isHidden(hidden, "height");
        boolean hidesGoals = isHidden(hidden, "fitnessGoals");
        boolean hidesInjuries = isHidden(hidden, "injuries");

        PublicProfileResponse r = new PublicProfileResponse();
        // Always public.
        r.id = user.getId();
        r.displayName = user.getDisplayName();
        r.userName = user.getUserName();
        r.pfp = user.getPfp();
        r.userType = user.getUserType();
        // Privacy-controlled.
        if (!hidesBio) r.bio = user.getBio();
        if (!hidesDob) r.dateOfBirth = user.getDateOfBirth();
        if (!hidesGender) r.gender = user.getGender() != null ? user.getGender().name() : null;
        if (!hidesWeight) r.weight = user.getWeight();
        if (!hidesHeight) r.height = user.getHeight();
        if (!hidesGoals) r.fitnessGoals = user.getFitnessGoals();
        if (!hidesInjuries) r.injuries = user.getInjuries();
        return r;
    }

    private static boolean isHidden(List<String> hidden, String key) {
        return hidden != null && hidden.contains(key);
    }

    public UUID getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getUserName() { return userName; }
    public String getPfp() { return pfp; }
    public UserType getUserType() { return userType; }
    public String getBio() { return bio; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public String getGender() { return gender; }
    public BigDecimal getWeight() { return weight; }
    public BigDecimal getHeight() { return height; }
    public List<String> getFitnessGoals() { return fitnessGoals; }
    public String getInjuries() { return injuries; }
}
