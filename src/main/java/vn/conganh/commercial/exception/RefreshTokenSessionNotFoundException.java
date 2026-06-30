package vn.conganh.commercial.exception;

public class RefreshTokenSessionNotFoundException extends UnauthorizedException {

    public RefreshTokenSessionNotFoundException(String message) {
        super(message);
    }
}
