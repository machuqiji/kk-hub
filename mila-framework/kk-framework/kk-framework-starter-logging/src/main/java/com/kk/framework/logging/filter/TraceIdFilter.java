package com.kk.framework.logging.filter;

import com.kk.framework.common.core.constant.CommonConstants;
import com.kk.framework.logging.config.LoggingProperties;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "kk.logging.enabled", havingValue = "true", matchIfMissing = true)
public class TraceIdFilter implements Filter {

    private final LoggingProperties properties;

    public TraceIdFilter(LoggingProperties properties) {
        this.properties = properties;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String traceId = req.getHeader(properties.getTraceIdHeader());
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        }

        MDC.put(CommonConstants.DEFAULT_TRACE_ID, traceId);
        resp.setHeader(properties.getTraceIdHeader(), traceId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CommonConstants.DEFAULT_TRACE_ID);
        }
    }
}