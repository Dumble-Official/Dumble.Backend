package com.example.DumbleAuthentication.dto.response;

import com.example.DumbleAuthentication.domain.RequestableRole;
import com.example.DumbleAuthentication.domain.RoleRequest;
import com.example.DumbleAuthentication.domain.RoleRequestStatus;

import java.time.Instant;
import java.util.UUID;

public class RoleRequestResponse {

    private UUID id;
    private UUID userId;
    private RequestableRole requestedRole;
    private RoleRequestStatus status;
    private String certificateUrl;
    private String applicantNote;
    private String adminMessage;
    private Instant createdAt;
    private Instant updatedAt;

    public static RoleRequestResponse from(RoleRequest r) {
        RoleRequestResponse dto = new RoleRequestResponse();
        dto.id = r.getId();
        dto.userId = r.getUserId();
        dto.requestedRole = r.getRequestedRole();
        dto.status = r.getStatus();
        dto.certificateUrl = r.getCertificateUrl();
        dto.applicantNote = r.getApplicantNote();
        dto.adminMessage = r.getAdminMessage();
        dto.createdAt = r.getCreatedAt();
        dto.updatedAt = r.getUpdatedAt();
        return dto;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public RequestableRole getRequestedRole() { return requestedRole; }
    public void setRequestedRole(RequestableRole requestedRole) { this.requestedRole = requestedRole; }
    public RoleRequestStatus getStatus() { return status; }
    public void setStatus(RoleRequestStatus status) { this.status = status; }
    public String getCertificateUrl() { return certificateUrl; }
    public void setCertificateUrl(String certificateUrl) { this.certificateUrl = certificateUrl; }
    public String getApplicantNote() { return applicantNote; }
    public void setApplicantNote(String applicantNote) { this.applicantNote = applicantNote; }
    public String getAdminMessage() { return adminMessage; }
    public void setAdminMessage(String adminMessage) { this.adminMessage = adminMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
