package vn.conganh.commercial.feature.auth.email;

import vn.conganh.commercial.exception.ServiceUnavailableException;

public class EmailDeliveryException extends ServiceUnavailableException {

    private final boolean retryable;

    public EmailDeliveryException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
