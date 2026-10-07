package dev.toniess.splitly.auth.entity;

import dev.toniess.splitly.user.entity.User;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.web.bind.annotation.Mapping;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @NotNull
    private String tokenHash;

    @NotNull
    private Instant expiresAt;

    @CreationTimestamp
    private Instant createdAt;

    private Instant revokedAt;

    public boolean isActive(Instant when) {
        return !isRevoked(when) && !isExpired(when);
    }

    public boolean isExpired(Instant when) {
        return !expiresAt.isAfter(when);
    }

    public boolean isRevoked(Instant when) {
        return revokedAt != null && !revokedAt.isAfter(when);
    }

    public void revoke() {
        if (revokedAt == null)
            this.revokedAt = Instant.now();
    }

    protected RefreshToken() {}

    public RefreshToken(
            User user,
            String tokenHash,
            Instant expiresAt
    ) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public User getUser() {
        return user;
    }
}
