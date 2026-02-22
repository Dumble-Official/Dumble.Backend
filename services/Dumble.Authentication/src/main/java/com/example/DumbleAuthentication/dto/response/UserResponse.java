package com.example.DumbleAuthentication.dto.response;

import com.example.DumbleAuthentication.domain.User;
import com.example.DumbleAuthentication.domain.UserType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public class UserResponse {

    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String displayName;
    private String pfp;
    private LocalDate dateOfBirth;
    private String gender;
    private BigDecimal weight;
    private BigDecimal height;
    private String fitnessGoals;
    private String bio;
    private String injuries;
    private UserType userType;
    private Instant createdAt;
    private Instant updatedAt;

    public static UserResponse from(User user) {
        UserResponse r = new UserResponse();
        r.setId(user.getId());
        r.setFirstName(user.getFirstName());
        r.setLastName(user.getLastName());
        r.setEmail(user.getEmail());
        r.setDisplayName(user.getDisplayName());
        r.setPfp(user.getPfp());
        r.setDateOfBirth(user.getDateOfBirth());
        r.setGender(user.getGender() != null ? user.getGender().name() : null);
        r.setWeight(user.getWeight());
        r.setHeight(user.getHeight());
        r.setFitnessGoals(user.getFitnessGoals());
        r.setBio(user.getBio());
        r.setInjuries(user.getInjuries());
        r.setUserType(user.getUserType());
        r.setCreatedAt(user.getCreatedAt());
        r.setUpdatedAt(user.getUpdatedAt());
        return r;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getPfp() { return pfp; }
    public void setPfp(String pfp) { this.pfp = pfp; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }
    public BigDecimal getHeight() { return height; }
    public void setHeight(BigDecimal height) { this.height = height; }
    public String getFitnessGoals() { return fitnessGoals; }
    public void setFitnessGoals(String fitnessGoals) { this.fitnessGoals = fitnessGoals; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public String getInjuries() { return injuries; }
    public void setInjuries(String injuries) { this.injuries = injuries; }
    public UserType getUserType() { return userType; }
    public void setUserType(UserType userType) { this.userType = userType; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
