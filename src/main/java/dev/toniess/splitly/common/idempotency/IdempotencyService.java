package dev.toniess.splitly.common.idempotency;

import dev.toniess.splitly.common.Hash;
import dev.toniess.splitly.common.idempotency.entity.IdempotencyRecord;
import dev.toniess.splitly.common.idempotency.exception.IdempotencyKeyReusedException;
import dev.toniess.splitly.common.idempotency.exception.RequestInProgressException;
import dev.toniess.splitly.user.UserRepository;
import dev.toniess.splitly.user.entity.User;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class IdempotencyService {

    private final IdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    public IdempotencyService(IdempotencyRepository idempotencyRepository, ObjectMapper objectMapper, UserRepository userRepository) {
        this.idempotencyRepository = idempotencyRepository;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
    }

    @Transactional
    public <T> T execute(
            UUID userId,
            String key,
            String scope,
            Object request,
            int successStatus,
            Class<T> responseType,
            Supplier<T> operation
    ) {
        final byte[] requestHash = Hash.sha256(
                objectMapper.writeValueAsString(scope) + objectMapper.writeValueAsString(request)
        );

        final Optional<IdempotencyRecord> idempotencyRecord = idempotencyRepository
                .findByUserIdAndIdempotencyKey(userId, key);

        if (idempotencyRecord.isPresent()) {
            final IdempotencyRecord record = idempotencyRecord.get();
            if (!Arrays.equals(record.getRequestHash(), requestHash)) {
                throw new IdempotencyKeyReusedException(key);
            }
            if (record.getResponseStatus() == null) {
                throw new RequestInProgressException(key);
            }

            return objectMapper.readValue(record.getResponseBody(), responseType);
        }

        final User user = userRepository.getReferenceById(userId);

        final IdempotencyRecord record = new IdempotencyRecord(user, key, requestHash);

        final IdempotencyRecord newRecord;
        try {
            newRecord = idempotencyRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException ex) {
            throw new RequestInProgressException(key);
        }

        final T responseBody = operation.get();
        newRecord.complete(successStatus, objectMapper.writeValueAsString(responseBody));
        return responseBody;


    }

}
