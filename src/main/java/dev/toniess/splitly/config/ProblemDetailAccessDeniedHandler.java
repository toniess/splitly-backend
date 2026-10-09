package dev.toniess.splitly.config;

import dev.toniess.splitly.common.ProblemDetails;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Отказ в доступе на уровне Spring Security — аутентификация прошла, но правил доступа
 * оказалось недостаточно.
 *
 * <p>Без этого обработчика Spring Security отдаёт 403 с пустым телом, и клиент не получает
 * ни {@code traceId}, ни объяснения — в отличие от всех остальных ошибок приложения.
 * Отказы, приходящие из сервисов ({@code ForbiddenException}), сюда не попадают:
 * их обрабатывает {@code GlobalExceptionHandler}.
 */
@Component
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemDetails problemDetails;
    private final ObjectMapper objectMapper;

    public ProblemDetailAccessDeniedHandler(ProblemDetails problemDetails, ObjectMapper objectMapper) {
        this.problemDetails = problemDetails;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException, ServletException {
        ProblemDetail problem = problemDetails.of(HttpStatus.FORBIDDEN,
                "Недостаточно прав для этого действия", "Доступ к ресурсу запрещён", "forbidden");
        problem.setInstance(URI.create(request.getRequestURI()));

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
