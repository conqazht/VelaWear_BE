package vn.conganh.commercial.exception;

import org.springframework.http.HttpStatus;

public class InvalidOrderTransitionException extends AppException {

    public InvalidOrderTransitionException(String fromStatus, String toStatus) {
        super("Cannot transition order from %s to %s".formatted(fromStatus, toStatus),
                HttpStatus.CONFLICT);
    }
}
