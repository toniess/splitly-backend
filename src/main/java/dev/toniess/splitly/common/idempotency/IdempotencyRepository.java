package dev.toniess.splitly.common.idempotency;

import dev.toniess.splitly.common.idempotency.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, UUID> {
    Optional<IdempotencyRecord> findByUserIdAndIdempotencyKey(UUID userId, String key);
}
