package dev.toniess.splitly.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.CannotSerializeTransactionException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Отображение ошибок параллельного доступа в ответ клиенту.
 *
 * <p>Проверяется именно иерархия исключений: обработчик объявлен на родителе,
 * и важно, что конкретные подклассы в него попадают.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(new ProblemDetails());

    @Test
    @DisplayName("Взаимоблокировка в базе превращается в 409, а не во внутреннюю ошибку")
    void mapsDeadlockToConflict() {
        ProblemDetail problem = handler.handleLockFailure(
                new CannotAcquireLockException("deadlock detected"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getType()).hasToString("https://splitly.dev/errors/conflict");
    }

    @Test
    @DisplayName("Сорванная сериализация транзакции — тоже 409")
    void mapsSerializationFailureToConflict() {
        ProblemDetail problem = handler.handleLockFailure(
                new CannotSerializeTransactionException("could not serialize access"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
    }

    @Test
    @DisplayName("Оптимистический конфликт и блокировка отвечают одинаковым статусом")
    void bothConcurrencyFailuresLookTheSameToClient() {
        ProblemDetail optimistic = handler.handleOptimisticLocking(
                new OptimisticLockingFailureException("stale version"));
        ProblemDetail lock = handler.handleLockFailure(
                new CannotAcquireLockException("deadlock detected"));

        assertThat(lock.getStatus()).isEqualTo(optimistic.getStatus());
        assertThat(lock.getType()).isEqualTo(optimistic.getType());
    }

    @Test
    @DisplayName("В ответе есть traceId, по которому запись ищется в логах")
    void includesTraceId() {
        ProblemDetail problem = handler.handleLockFailure(
                new CannotAcquireLockException("deadlock detected"));

        assertThat(problem.getProperties()).containsKey("traceId");
        assertThat(problem.getProperties().get("traceId")).asString().isNotBlank();
    }
}
