package com.kk.mila.satoken.config;

import cn.dev33.satoken.context.SaTokenContext;
import cn.dev33.satoken.filter.SaServletFilter;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.util.SaResult;
import com.kk.mila.common.ApiResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(SaTokenProperties.class)
@ConditionalOnProperty(prefix = "mila.satoken", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SaTokenAutoConfiguration {

    @Bean
    public SaServletFilter saServletFilter(SaTokenProperties properties) {
        return new SaServletFilter()
                .setAuth(object -> {
                    throw new com.kk.mila.common.core.exception.BizException(401, "未登录或登录已过期");
                })
                .setError(e -> {
                    return ApiResponse.error(401, "未登录或登录已过期");
                });
    }

    @Bean
    public WebMvcConfigurer saTokenWebMvcConfigurer(SaTokenProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new SaInterceptor())
                        .addPathPatterns("/**")
                        .excludePathPatterns(
                                "/auth/login",
                                "/auth/register",
                                "/error"
                        );
            }
        };
    }
}
