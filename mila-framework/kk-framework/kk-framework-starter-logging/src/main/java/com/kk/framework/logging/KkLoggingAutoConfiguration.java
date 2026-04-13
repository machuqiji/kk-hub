package com.kk.framework.logging;

import com.kk.framework.logging.config.LoggingProperties;
import com.kk.framework.logging.filter.TraceIdFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LoggingProperties.class)
public class KkLoggingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TraceIdFilter traceIdFilter(LoggingProperties properties) {
        return new TraceIdFilter(properties);
    }
}