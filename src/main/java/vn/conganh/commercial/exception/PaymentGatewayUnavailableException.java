package vn.conganh.commercial.exception;

import org.springframework.http.HttpStatus;

public class PaymentGatewayUnavailableException extends AppException {

    public PaymentGatewayUnavailableException(String message) {
        super(message, HttpStatus.SERVICE_UNAVAILABLE);
    }
}
