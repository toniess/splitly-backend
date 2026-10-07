package dev.toniess.splitly.auth;

import dev.toniess.splitly.auth.dto.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }


    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirements
    public UserCreatedResponse register(
        @RequestBody @Valid RegisterRequest request
    ) {
        return authService.createUser(request.email(), request.password(), request.displayName());
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    @SecurityRequirements
    public TokenPairResponse login(
            @RequestBody @Valid LoginRequest request
    ) {
        return authService.login(request.email(), request.password());
    }

    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.OK)
    @SecurityRequirements
    public TokenPairResponse refresh(
            @RequestBody @Valid RefreshRequest request
    ) {
        return authService.refresh(request.refresh());
    }
}
