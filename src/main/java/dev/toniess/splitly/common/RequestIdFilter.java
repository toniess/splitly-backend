package dev.toniess.splitly.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(RequestIdFilter.ORDER)
public class RequestIdFilter extends OncePerRequestFilter {

    /**
     * Самый первый в цепочке: traceId нужен всем, кто пишет в лог после него,
     * включая отказы аутентификации.
     *
     * <p>Константа публична, чтобы соседние фильтры объявляли свой порядок
     * относительно неё, а не магическим числом.
     */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE;

    public static final String TRACE_ID = "traceId";
    private static final String HEADER = "X-Request-Id";

    /** Идентификатор приходит снаружи, поэтому принимаем только безобидный набор символов. */
    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9_.-]{1,64}");


    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = request.getHeader(HEADER);
        if (requestId == null || !ALLOWED.matcher(requestId).matches()) {
            requestId = UUID.randomUUID().toString();
        }

        MDC.put(TRACE_ID, requestId);
        response.addHeader(HEADER, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID);
        }
    }
}
