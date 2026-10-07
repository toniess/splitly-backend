package dev.toniess.splitly.auth;

import dev.toniess.splitly.auth.dto.TokenPairResponse;
import dev.toniess.splitly.auth.entity.RefreshToken;
import dev.toniess.splitly.common.RefreshTokenGenerator;
import dev.toniess.splitly.config.JwtProperties;
import dev.toniess.splitly.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import static org.springframework.transaction.annotation.Propagation.REQUIRES_NEW;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, RefreshTokenGenerator refreshTokenGenerator, JwtService jwtService, JwtProperties jwtProperties) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    @Transactional(propagation = REQUIRES_NEW)
    public void revokeRefreshTokensForUser(User user) {
        final List<RefreshToken> refreshTokens = refreshTokenRepository
                .findAllByUserIdAndRevokedAtIsNull(user.getId());
        refreshTokens.forEach(RefreshToken::revoke);
    }

    public TokenPairResponse issueTokenPair(User user, Instant now) {
        final String refresh = refreshTokenGenerator.generate();

        refreshTokenRepository.saveAndFlush(new RefreshToken(
                user,
                sha256(refresh),
                now.plus(jwtProperties.refreshTtl())
        ));

        return new TokenPairResponse(
                jwtService.issueAccessToken(user.getId()),
                refresh,
                "Bearer",
                jwtProperties.accessTtl().toSeconds()
        );
    }

    public String hashToken(String token) {
        return sha256(token);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 обязан поддерживаться любой JVM", e);
        }
    }
}
