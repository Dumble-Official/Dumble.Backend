package com.example.DumbleAuthentication.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "app_user", indexes = {
        // People-search filters on these columns (LOWER(...) LIKE). The btree
        // indexes serve equality/prefix lookups; for fast substring ('%q%')
        // matching at scale, add pg_trgm GIN indexes via SQL (see deploy notes).
        @Index(name = "idx_app_user_display_name", columnList = "display_name"),
        @Index(name = "idx_app_user_user_name", columnList = "user_name"),
        @Index(name = "idx_app_user_first_name", columnList = "first_name"),
        @Index(name = "idx_app_user_last_name", columnList = "last_name")
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "first_name", length = 100, nullable = false)
    private String firstName;

    @Column(name = "last_name", length = 100, nullable = false)
    private String lastName;

    @Column(unique = true, nullable = false, length = 255)
    @Email(message = "Invalid email address")
    private String email;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 20)
    private AuthProvider authProvider = AuthProvider.LOCAL;

    @Column(name = "provider_id", length = 255)
    private String providerId;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private Gender gender;

    @Column(precision = 5, scale = 2)
    private BigDecimal weight;

    @Column(precision = 5, scale = 2)
    private BigDecimal height;

    // Multiple goals (e.g. ["MUSCLE_GAIN","ENDURANCE"]) — persisted to the same
    // fitness_goals VARCHAR column as a comma-joined string by StringListConverter.
    @Column(name = "fitness_goals", length = 500)
    @Convert(converter = StringListConverter.class)
    private List<String> fitnessGoals;

    @Column(length = 512)
    private String pfp;

    @Column(name = "display_name", length = 150)
    private String displayName;

    @Column(unique = true, name = "user_name", length = 150)
    private String userName;

    // Profile fields the user has marked private — hidden from other viewers
    // (the owner always sees everything). Stored comma-joined like fitnessGoals.
    // Empty/null = nothing hidden (every field is public), so existing rows keep
    // showing their data. Allowed keys: bio, dateOfBirth, gender, weight, height,
    // fitnessGoals, injuries.
    @Column(name = "hidden_fields", length = 500)
    @Convert(converter = StringListConverter.class)
    private List<String> hiddenFields;

    @Column(length = 1000)
    private String bio;

    @Column(length = 500)
    private String injuries;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false)
    private UserType userType = UserType.PARTICIPANT;

    @Column(nullable = false)
    private boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    /**
     * Name to show in the UI: the user's chosen displayName, or — when they
     * never set one — their first + last name (the same source the generated
     * avatar uses). Avoids clients falling back to "User 1a2b3c".
     */
    public String getEffectiveDisplayName() {
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        String full = ((firstName == null ? "" : firstName) + " "
                + (lastName == null ? "" : lastName)).trim();
        return full.isEmpty() ? null : full;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public BigDecimal getWeight() {
        return weight;
    }

    public void setWeight(BigDecimal weight) {
        this.weight = weight;
    }

    public BigDecimal getHeight() {
        return height;
    }

    public void setHeight(BigDecimal height) {
        this.height = height;
    }

    public List<String> getFitnessGoals() {
        return fitnessGoals;
    }

    public void setFitnessGoals(List<String> fitnessGoals) {
        this.fitnessGoals = fitnessGoals;
    }

    public String getPfp() {
        return pfp;
    }

    public void setPfp(String pfp) {
        this.pfp = pfp;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public List<String> getHiddenFields() {
        return hiddenFields;
    }

    public void setHiddenFields(List<String> hiddenFields) {
        this.hiddenFields = hiddenFields;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getInjuries() {
        return injuries;
    }

    public void setInjuries(String injuries) {
        this.injuries = injuries;
    }

    public UserType getUserType() {
        return userType;
    }

    public void setUserType(UserType userType) {
        this.userType = userType;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean isActive) {
        this.isActive = isActive;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public AuthProvider getAuthProvider() {
        return authProvider;
    }

    public void setAuthProvider(AuthProvider authProvider) {
        this.authProvider = authProvider;
    }

    public String getProviderId() {
        return providerId;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }
}
