package vn.conganh.commercial.exception;

import org.springframework.http.HttpStatus;

public class EmptyCartException extends AppException {

    public EmptyCartException() {
        super("Cart is empty, cannot proceed with checkout", HttpStatus.BAD_REQUEST);
    }
}
