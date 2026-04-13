package com.kk.framework.logging.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "kk.logging")
public class LoggingProperties {
    private boolean enabled = true;
    private boolean jsonEnabled = true;
    private String traceIdHeader = "X-TraceId";
    private int slowThresholdMs = 1000;
    private boolean paramLogEnabled = true;
    private boolean responseLogEnabled = false;
}