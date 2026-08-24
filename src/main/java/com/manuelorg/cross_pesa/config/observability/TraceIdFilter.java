package com.manuelorg.cross_pesa.config.observability;

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

/**
 * Ensures every HTTP request carries a trace id, exposed via MDC and the
 * {@code X-Trace-Id} response header so all log lines for the request can be
 * correlated.
 *
 * <p>Inbound propagation is honoured: an upstream {@code X-Trace-Id} or W3C
 * {@code traceparent} header is reused so traces can span services. MDC is
 * always cleared in a {@code finally} block to prevent trace id leakage
 * across thread-pool reuse.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String MDC_TRACE_ID = "traceId";
    public static final String HEADER_TRACE_ID = "X-Trace-Id";
    private static final String HEADER_TRACEPARENT = "traceparent";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolveOrGenerateTraceId(request);
        MDC.put(MDC_TRACE_ID, traceId);
        response.setHeader(HEADER_TRACE_ID, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_TRACE_ID);
        }
    }

    private String resolveOrGenerateTraceId(HttpServletRequest request) {
        // Prefer our own header, then the W3C traceparent id portion
        String incoming = request.getHeader(HEADER_TRACE_ID);
        if (incoming != null && incoming.matches("[A-Za-z0-9_-]{1,64}")) {
            return sanitize(incoming);
        }

        String traceparent = request.getHeader(HEADER_TRACEPARENT);
        if (traceparent != null && traceparent.length() >= 55 && traceparent.charAt(0) == '0'
                && traceparent.matches("00-[a-f0-9]{32}-[a-f0-9]{16}-[a-f0-9]{2}")) {
            return traceparent.substring(3, 35); // 32-hex trace-id portion
        }

        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private String sanitize(String value) {
        // Keep it short and log-safe
        return value.length() > 8 ? value.substring(0, 8) : value;
    }
}
