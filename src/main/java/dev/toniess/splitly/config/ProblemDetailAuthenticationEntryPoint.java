package dev.toniess.splitly.config;

import dev.toniess.splitly.common.ProblemDetails;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

@Component
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemDetails problemDetails;
    private final ObjectMapper objectMapper;

    public ProblemDetailAuthenticationEntryPoint(ProblemDetails problemDetails, ObjectMapper objectMapper) {
        this.problemDetails = problemDetails;
        this.objectMapper = objectMapper;
    }


    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            org.springframework.security.core.AuthenticationException authException
    ) throws IOException, ServletException {
        ProblemDetail problem = problemDetails.of(HttpStatus.UNAUTHORIZED,
                "Требуется аутентификация", "Токен отсутствует или недействителен", "unauthorized");
        problem.setInstance(URI.create(request.getRequestURI()));

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
