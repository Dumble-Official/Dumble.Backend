package com.example.DumbleAuthentication.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public class UpdateProfileRequest {

    @DecimalMin("20")
    @DecimalMax("300")
    private BigDecimal weight;

    @Size(max = 500)
    private String injuries;

    @Size(max = 20, message = "At most 20 fitness goals")
    private List<@Size(max = 50) String> fitnessGoals;

    @Size(max = 150)
    private String displayName;

    @Size(max = 512)
    private String pfp;

    @Size(max = 1000)
    private String bio;

    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }
    public String getInjuries() { return injuries; }
    public void setInjuries(String injuries) { this.injuries = injuries; }
    public List<String> getFitnessGoals() { return fitnessGoals; }
    public void setFitnessGoals(List<String> fitnessGoals) { this.fitnessGoals = fitnessGoals; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getPfp() { return pfp; }
    public void setPfp(String pfp) { this.pfp = pfp; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
}
