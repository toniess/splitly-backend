package dev.toniess.splitly.common;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.UUID;

@Component
public class ProblemDetails {

    private static final String TYPE_PREFIX = "https://splitly.dev/errors/";

    public ProblemDetail of(HttpStatusCode status, String title, String detail, String type) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(TYPE_PREFIX + type));
        problem.setProperty("traceId", UUID.randomUUID().toString());
        return problem;
    }
}
