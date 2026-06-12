package com.example.DumbleAuthentication.dto.request;

import com.example.DumbleAuthentication.domain.RequestableRole;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body for submitting (or editing) a TRAINER role request. The certificate is a
 * Cloudinary URL the client uploaded the training certificate to.
 */
public class CreateRoleRequestRequest {

    @NotNull(message = "requestedRole is required (TRAINER)")
    private RequestableRole requestedRole;

    @NotBlank(message = "certificateUrl is required")
    @Size(max = 512)
    private String certificateUrl;

    @Size(max = 1000)
    private String note;

    public RequestableRole getRequestedRole() { return requestedRole; }
    public void setRequestedRole(RequestableRole requestedRole) { this.requestedRole = requestedRole; }

    public String getCertificateUrl() { return certificateUrl; }
    public void setCertificateUrl(String certificateUrl) { this.certificateUrl = certificateUrl; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
