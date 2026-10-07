package dev.toniess.splitly.auth.exception;

import dev.toniess.splitly.common.exception.UnauthorizedException;

public class InvalidRefreshTokenException extends UnauthorizedException {
    public InvalidRefreshTokenException() {
        super("Refresh token is invalid");
    }
}
