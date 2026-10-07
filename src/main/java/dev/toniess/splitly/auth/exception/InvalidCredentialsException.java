package dev.toniess.splitly.auth.exception;

import dev.toniess.splitly.common.exception.UnauthorizedException;


public class InvalidCredentialsException extends UnauthorizedException {
    public InvalidCredentialsException() {
        super("Неверный email или пароль");
    }
}