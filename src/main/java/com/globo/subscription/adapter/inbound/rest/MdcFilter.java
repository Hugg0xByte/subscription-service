package com.globo.subscription.adapter.inbound.rest;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Servlet filter that populates MDC with traceId, userId, and subscriptionId
 * for structured logging context propagation.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcFilter implements Filter {

    public static final String TRACE_ID = "traceId";
    public static final String USER_ID = "userId";
    public static final String SUBSCRIPTION_ID = "subscriptionId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            HttpServletRequest httpRequest = (HttpServletRequest) request;

            // Generate or propagate traceId
            String traceId = httpRequest.getHeader("X-Trace-Id");
            if (traceId == null || traceId.isBlank()) {
                traceId = UUID.randomUUID().toString();
            }
            MDC.put(TRACE_ID, traceId);

            // Extract userId from request parameter if available
            String userId = httpRequest.getParameter("userId");
            if (userId != null && !userId.isBlank()) {
                MDC.put(USER_ID, userId);
            }

            // Extract subscriptionId from path if available
            String path = httpRequest.getRequestURI();
            if (path != null && path.contains("/subscriptions/")) {
                String[] parts = path.split("/subscriptions/");
                if (parts.length > 1) {
                    String subscriptionIdPart = parts[1].split("/")[0];
                    try {
                        UUID.fromString(subscriptionIdPart);
                        MDC.put(SUBSCRIPTION_ID, subscriptionIdPart);
                    } catch (IllegalArgumentException ignored) {
                        // Not a valid UUID, skip
                    }
                }
            }

            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
