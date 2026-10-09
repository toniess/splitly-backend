package dev.toniess.splitly.common.idempotency.entity;

import dev.toniess.splitly.user.entity.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyRecord {
    @Id
    @GeneratedValue
    private UUID id;

    private String idempotencyKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    private byte[] requestHash;

    private Integer responseStatus;

    private String responseBody;

    @CreationTimestamp
    private Instant createdAt;

    protected IdempotencyRecord() {}

    public IdempotencyRecord(
            User user,
            String idempotencyKey,
            byte[] requestHash
    ) {
        this.user = user;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
    }

    public void complete(int responseStatus, String responseBody) {
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
    }

    public byte[] getRequestHash() {
        return requestHash;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }
}
