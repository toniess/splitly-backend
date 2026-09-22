package dev.toniess.splitly.group.entity;

import dev.toniess.splitly.user.entity.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "groups")
public class Group {
    @Id
    @GeneratedValue
    private UUID id;

    private String name;

    @Column(length = 3)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currency;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by")
    private User createdBy;

    private String inviteCode;

    @CreationTimestamp
    private Instant createdAt;

    public Group(String name, String currency, User createdBy, String inviteCode) {
        this.name = name;
        this.currency = currency;
        this.createdBy = createdBy;
        this.inviteCode = inviteCode;
    }

    protected Group() {}

    public UUID getId() { return id; }

    public String getName() { return name; }

    public String getCurrency() { return currency; }

    public User getCreatedBy() { return createdBy; }

    public String getInviteCode() { return inviteCode; }

    public Instant getCreatedAt() { return createdAt; }
}
