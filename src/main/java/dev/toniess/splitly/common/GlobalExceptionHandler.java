package dev.toniess.splitly.common;

import dev.toniess.splitly.common.dto.ValidationError;
import dev.toniess.splitly.common.exception.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ProblemDetails problemDetails;

    public GlobalExceptionHandler(ProblemDetails problemDetails) {
        this.problemDetails = problemDetails;
    }

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        return problemDetails.of(HttpStatus.NOT_FOUND, "Объект не найден", ex.getMessage(), "not-found");
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        return problemDetails.of(HttpStatus.CONFLICT, "Конфликт состояния", ex.getMessage(), "conflict");
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ProblemDetail handleUnauthorized(UnauthorizedException ex) {
        return problemDetails.of(HttpStatus.UNAUTHORIZED, "Требуется аутентификация", ex.getMessage(), "unauthorized");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        List<ValidationError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new ValidationError(fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();

        ProblemDetail problem = problemDetails.of(HttpStatus.BAD_REQUEST, "Некорректный запрос",
                "Тело запроса не прошло валидацию", "validation");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail handleBadRequest(BadRequestException ex) {
        return problemDetails.of(HttpStatus.BAD_REQUEST, "Некорректный запрос", ex.getMessage(), "bad-request");
    }


    /**
     * Путь или параметр запроса не приводится к нужному типу: {@code /expenses/123} вместо UUID,
     * {@code ?limit=abc} вместо числа. Ошибка клиента, а не сбой сервера.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String expectedType = ex.getRequiredType() == null ? "другой тип" : ex.getRequiredType().getSimpleName();

        ProblemDetail problem = problemDetails.of(HttpStatus.BAD_REQUEST, "Некорректный запрос",
                "Значение параметра '%s' не соответствует типу %s".formatted(ex.getName(), expectedType),
                "bad-request");
        problem.setProperty("errors",
                List.of(new ValidationError(ex.getName(), "ожидался " + expectedType)));
        return problem;
    }

    /** Тело запроса не разобралось: битый JSON, не тот формат числа или даты. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException ex) {
        return problemDetails.of(HttpStatus.BAD_REQUEST, "Некорректный запрос",
                "Тело запроса не удалось разобрать", "bad-request");
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail handleMissingHeader(MissingRequestHeaderException ex) {
        ProblemDetail problem = problemDetails.of(HttpStatus.BAD_REQUEST, "Некорректный запрос",
                "Не передан обязательный заголовок '%s'".formatted(ex.getHeaderName()), "bad-request");
        problem.setProperty("errors",
                List.of(new ValidationError(ex.getHeaderName(), "заголовок обязателен")));
        return problem;
    }

    @ExceptionHandler(UnprocessableException.class)
    public ProblemDetail handleUnprocessable(UnprocessableException ex) {
        return problemDetails.of(HttpStatus.UNPROCESSABLE_CONTENT, "Нарушение бизнес-правила", ex.getMessage(), "unprocessable");
    }

    @ExceptionHandler(ForbiddenException.class)
    public ProblemDetail handleForbidden(ForbiddenException ex) {
        return problemDetails.of(HttpStatus.FORBIDDEN, "Недостаточно прав для этого действия", ex.getMessage(), "forbidden");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLocking(OptimisticLockingFailureException ex) {
        return problemDetails.of(HttpStatus.CONFLICT, "Конфликт состояния",
                "Объект изменился, пока вы с ним работали. Перечитайте его и повторите.", "conflict");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        ProblemDetail problem = problemDetails.of(HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка",
                "Произошла непредвиденная ошибка", "internal");
        if (problem.getProperties() != null) {
            log.error("Unhandled exception, traceId={}", problem.getProperties().get("traceId"), ex);
        }
        return problem;
    }
}
