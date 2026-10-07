package dev.toniess.splitly.auth;

import dev.toniess.splitly.auth.dto.TokenPairResponse;
import dev.toniess.splitly.auth.dto.UserCreatedResponse;
import dev.toniess.splitly.auth.entity.RefreshToken;
import dev.toniess.splitly.auth.exception.EmailAlreadyTakenException;
import dev.toniess.splitly.auth.exception.InvalidCredentialsException;
import dev.toniess.splitly.auth.exception.InvalidRefreshTokenException;
import dev.toniess.splitly.user.UserRepository;
import dev.toniess.splitly.user.entity.User;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, RefreshTokenRepository refreshTokenRepository, RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public UserCreatedResponse createUser(String email, String password, String displayName) {
        final User user = new User(email.toLowerCase(Locale.ROOT), passwordEncoder.encode(password), displayName);

        try {
            userRepository.saveAndFlush(user);
            return UserCreatedResponse.fromUser(user);
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyTakenException(email);
        }
    }

    @Transactional
    public TokenPairResponse login(String email, String password) {
        final Instant now = Instant.now();
        final User user = userRepository.findByEmail(
            email.toLowerCase(Locale.ROOT)
        ).orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
           throw new InvalidCredentialsException();
        }

        return refreshTokenService.issueTokenPair(user, now);
    }


    @Transactional
    public TokenPairResponse refresh(String refresh) {
        final Instant now = Instant.now();
        final String tokenHash = refreshTokenService.hashToken(refresh);
        final RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(InvalidRefreshTokenException::new);

        if (refreshToken.isExpired(now)) {
            throw new InvalidRefreshTokenException();
        }

        if (refreshToken.isRevoked(now)) {
            refreshTokenService.revokeRefreshTokensForUser(refreshToken.getUser());
            throw new InvalidRefreshTokenException();
        }

        refreshToken.revoke();

        return refreshTokenService.issueTokenPair(refreshToken.getUser(), now);
    }
}
