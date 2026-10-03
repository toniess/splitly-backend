package dev.toniess.splitly.common;

import dev.toniess.splitly.common.dto.ValidationError;
import dev.toniess.splitly.common.exception.*;
import dev.toniess.splitly.expense.exception.ExpenseVersionConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TYPE_PREFIX = "https://splitly.dev/errors/";

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Объект не найден", ex.getMessage(), "not-found");
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        return problem(HttpStatus.CONFLICT, "Конфликт состояния", ex.getMessage(), "conflict");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        List<ValidationError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new ValidationError(fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Некорректный запрос",
                "Тело запроса не прошло валидацию", "validation");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail handleBadRequest(BadRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Некорректный запрос", ex.getMessage(), "bad-request");
    }


    @ExceptionHandler(UnprocessableException.class)
    public ProblemDetail handleUnprocessable(UnprocessableException ex) {
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "Нарушение бизнес-правила", ex.getMessage(), "unprocessable");
    }

    @ExceptionHandler(ForbiddenException.class)
    public ProblemDetail handleForbidden(ForbiddenException ex) {
        return problem(HttpStatus.FORBIDDEN, "Недостаточно прав для этого действия", ex.getMessage(), "forbidden");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail rethrowOptimisticLockingAsConflict(OptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "Конфликт состояния", "...", "conflict");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        ProblemDetail problem = problem(HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка",
                "Произошла непредвиденная ошибка", "internal");
        if (problem.getProperties() != null) {
            log.error("Unhandled exception, traceId={}", problem.getProperties().get("traceId"), ex);
        }
        return problem;
    }


    private ProblemDetail problem(HttpStatusCode status, String title, String detail, String type) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(TYPE_PREFIX + type));
        problem.setProperty("traceId", UUID.randomUUID().toString());
        return problem;
    }
}
