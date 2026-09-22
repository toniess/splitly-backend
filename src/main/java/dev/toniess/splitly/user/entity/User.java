package dev.toniess.splitly.user.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue
    private UUID id;

    private String email;

    private String passwordHash;

    private String displayName;

    @CreationTimestamp
    private Instant createdAt;


    public User(String email, String passwordHash, String displayName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
    }

    protected User() {}

    public UUID getId() { return id; }

    public String getEmail() { return email; }

    public String getPasswordHash() { return passwordHash; }

    public String getDisplayName() { return displayName; }

    public Instant getCreatedAt() { return createdAt; }
}
