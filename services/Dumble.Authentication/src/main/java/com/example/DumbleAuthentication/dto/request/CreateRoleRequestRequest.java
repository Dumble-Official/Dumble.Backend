package com.example.DumbleAuthentication.dto.request;

import com.example.DumbleAuthentication.domain.RequestableRole;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Body for submitting (or editing) a role-promotion request. Documents are
 * Cloudinary URLs the client uploaded the cert / business licence to.
 */
public class CreateRoleRequestRequest {

    @NotNull(message = "requestedRole is required (TRAINER or GYM_OWNER)")
    private RequestableRole requestedRole;

    @NotEmpty(message = "At least one supporting document URL is required")
    @Size(max = 10, message = "At most 10 documents")
    private List<@Size(max = 512) String> documentUrls;

    @Size(max = 1000)
    private String note;

    public RequestableRole getRequestedRole() { return requestedRole; }
    public void setRequestedRole(RequestableRole requestedRole) { this.requestedRole = requestedRole; }

    public List<String> getDocumentUrls() { return documentUrls; }
    public void setDocumentUrls(List<String> documentUrls) { this.documentUrls = documentUrls; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
