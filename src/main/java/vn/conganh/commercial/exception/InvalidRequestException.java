package vn.conganh.commercial.exception;

import org.springframework.http.HttpStatus;

public class InvalidRequestException extends AppException {

    public InvalidRequestException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
