package vn.conganh.commercial.exception;

import java.util.Map;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class CodedBusinessException extends AppException {

    private final String code;
    private final Map<String, Object> details;

    public CodedBusinessException(String code, String message, HttpStatus status) {
        this(code, message, status, Map.of());
    }

    public CodedBusinessException(
            String code,
            String message,
            HttpStatus status,
            Map<String, Object> details) {
        super(message, status);
        this.code = code;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }
}
