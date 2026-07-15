package vn.conganh.commercial.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

public class EnglishContentGenerationException extends CodedBusinessException {

    public EnglishContentGenerationException(String code, String message, HttpStatus status) {
        super(code, message, status);
    }

    public EnglishContentGenerationException(
            String code,
            String message,
            HttpStatus status,
            Map<String, Object> details) {
        super(code, message, status, details);
    }
}
