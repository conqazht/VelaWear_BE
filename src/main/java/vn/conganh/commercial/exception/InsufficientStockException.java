package vn.conganh.commercial.exception;

import org.springframework.http.HttpStatus;

public class InsufficientStockException extends AppException {

    public InsufficientStockException(String variantInfo) {
        super("Insufficient stock for variant: " + variantInfo, HttpStatus.CONFLICT);
    }
}
