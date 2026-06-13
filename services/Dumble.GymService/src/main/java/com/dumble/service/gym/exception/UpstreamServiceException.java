package com.dumble.service.gym.exception;

/**
 * A dependency this service calls (currently the auth service, to promote an
 * approved applicant to GYM_OWNER) failed or is misconfigured. Surfaced as a
 * clear 502 instead of a generic 500 so the cause is obvious in logs/clients.
 */
public class UpstreamServiceException extends RuntimeException {
    public UpstreamServiceException(String message) {
        super(message);
    }

    public UpstreamServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
