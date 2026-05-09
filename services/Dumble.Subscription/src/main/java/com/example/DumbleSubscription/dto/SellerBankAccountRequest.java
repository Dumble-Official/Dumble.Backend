package com.example.DumbleSubscription.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SellerBankAccountRequest {
    @NotBlank
    private String accountHolderName;

    @NotBlank
    private String destination;

    @NotBlank
    private String destinationType;     // BANK_ACCOUNT | VODAFONE_CASH | etc.
}
