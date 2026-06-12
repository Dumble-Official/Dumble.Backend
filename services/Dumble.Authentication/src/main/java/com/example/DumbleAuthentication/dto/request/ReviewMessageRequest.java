package com.example.DumbleAuthentication.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Admin's message when sending a request back for changes or rejecting it —
 * tells the applicant what to fix or why it was declined.
 */
public class ReviewMessageRequest {

    @NotBlank(message = "A message is required")
    @Size(max = 1000)
    private String message;

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
