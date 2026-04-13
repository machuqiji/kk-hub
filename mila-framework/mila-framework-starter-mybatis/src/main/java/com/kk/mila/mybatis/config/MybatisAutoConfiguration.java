package com.kk.mila.mybatis.config;

import com.mybatisflex.spring.boot.ConfigurationCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MybatisProperties.class)
@ConditionalOnClass(com.mybatisflex.spring.boot.MybatisFlexAutoConfiguration.class)
public class MybatisAutoConfiguration {

    @Bean
    public ConfigurationCustomizer mybatisFlexConfigurationCustomizer(MybatisProperties properties) {
        return configuration -> {
            configuration.setCacheEnabled(properties.isCacheEnabled());
        };
    }
}