package com.example.DumbleGateway.exception;

import com.example.DumbleGateway.dto.ErrorResponse;

import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), ex.getMessage()));
    }

    /**
     * Unrouted paths (no matching gateway route) fall through to Spring's
     * static-resource handler, which throws NoResourceFoundException when no
     * file matches. Without this explicit mapping it would be caught by the
     * generic Exception handler below and surface as a misleading 500.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(HttpStatus.NOT_FOUND.value(), "Not found"));
    }

    /**
     * The client (mobile app) closed the connection before we finished writing
     * the response — a broken pipe / connection reset, usually a network blip,
     * a request the app timed out and cancelled, or the app being backgrounded.
     * There is nothing to send back to a closed socket and it is not a server
     * fault, so log it quietly and return no body. Handling it here also stops
     * it from reaching handleGeneral, where trying to render an ErrorResponse on
     * the dead connection threw a secondary "no converter" error.
     */
    @ExceptionHandler({ClientAbortException.class, AsyncRequestNotUsableException.class})
    public void handleClientDisconnect(Exception ex) {
        log.debug("Client disconnected before the response was sent: {}", ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception ex) {
        log.error("Unexpected gateway error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                // Pin the content type so the JSON converter is always selected, even
                // when the request carried no Accept header and negotiation would
                // otherwise fall back to octet-stream/text-plain (which has no
                // converter for ErrorResponse and threw HttpMessageNotWritableException).
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "Gateway error: An unexpected error occurred"));
    }
}
