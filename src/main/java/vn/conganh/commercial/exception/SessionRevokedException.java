package vn.conganh.commercial.exception;

import org.springframework.http.HttpStatus;

public class SessionRevokedException extends CodedBusinessException {

    public SessionRevokedException() {
        super(
                "SESSION_REVOKED",
                "Your session has been revoked. Please sign in again.",
                HttpStatus.UNAUTHORIZED);
    }
}
