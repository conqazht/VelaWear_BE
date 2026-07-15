package vn.conganh.commercial.dto;

import java.time.LocalDateTime;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.annotation.JsonInclude;
@JsonInclude(JsonInclude.Include.NON_NULL)

public record ApiResponse<T>(
        int statusCode,
        T data,
        String message,
        LocalDateTime timestamp,
        String code
) {

    public ApiResponse(int statusCode, T data, String message, LocalDateTime timestamp) {
        this(statusCode, data, message, timestamp, null);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(HttpStatus.OK.value(), data, "Success", LocalDateTime.now(), null);
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(HttpStatus.CREATED.value(), data, "Created", LocalDateTime.now(), null);
    }

    public static <T> ApiResponse<T> error(int statusCode, String message) {
        return new ApiResponse<>(statusCode, null, message, LocalDateTime.now(), null);
    }

    public static <T> ApiResponse<T> error(int statusCode, String code, String message, T data) {
        return new ApiResponse<>(statusCode, data, message, LocalDateTime.now(), code);
    }
}
