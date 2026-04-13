# kk-framework Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Spring Boot 4.0 multi-module scaffold with 5 starters: common, logging, web, data, security

**Architecture:** Multi-module Maven project with strict layer dependency (common → logging → web, common → data → security). Each starter is independently usable via Spring Boot auto-configuration.

**Tech Stack:** Spring Boot 3.4.x, MyBatis-Flex 1.9.x, Sa-Token 1.37.x, Hutools 5.8.x, Lombok, PostgreSQL Driver 42.7.x, Lettuce (Redis)

---

## File Structure

```
kk-framework/
├── pom.xml                                          # 父 POM，modules 声明
├── kk-framework-parent/
│   └── pom.xml                                      # pluginManagement + shared configs
├── kk-framework-dependencies/
│   └── pom.xml                                      # BOM，dependencyManagement
├── kk-framework-starter-common/
│   ├── pom.xml
│   └── src/main/java/com/kk/framework/common/
│       ├── ApiResponse.java                         # 统一响应结构
│       ├── core/
│       │   ├── response/
│       │   │   └── ApiResponse.java
│       │   ├── exception/
│       │   │   ├── BizException.java
│       │   │   ├── IBizCode.java
│       │   │   ├── ExceptionCode.java               # 注解
│       │   │   └── GlobalExceptionHandler.java      # 由 web starter 实现
│       │   ├── constant/
│       │   │   └── CommonConstants.java
│       │   └── enums/
│       │       └── ResultCode.java                  # 系统级错误码
│       ├── util/
│       │   ├── JsonUtil.java
│       │   └── PageUtil.java
│       └── extension/
│           └── KkExtension.java                     # SPI 扩展点标记
├── kk-framework-starter-logging/
│   ├── pom.xml
│   └── src/main/java/com/kk/framework/logging/
│       ├── KkLoggingAutoConfiguration.java
│       ├── config/
│       │   └── LoggingProperties.java                # 配置绑定
│       ├── filter/
│       │   └── TraceIdFilter.java                   # 过滤器
│       └── layout/
│           └── JsonLogLayout.java                   # JSON 日志格式
├── kk-framework-starter-web/
│   ├── pom.xml
│   └── src/main/java/com/kk/framework/web/
│       ├── KkWebAutoConfiguration.java
│       ├── config/
│       │   └── WebProperties.java
│       ├── handler/
│       │   └── GlobalExceptionHandler.java           # 全局异常处理
│       ├── response/
│       │   └── PagedApiResponse.java                # 分页响应
│       └── interceptor/
│           └── SlowOperationInterceptor.java         # 慢操作拦截
├── kk-framework-starter-data/
│   ├── pom.xml
│   └── src/main/java/com/kk/framework/data/
│       ├── KkDataAutoConfiguration.java
│       ├── config/
│       │   └── DataProperties.java
│       ├── mybatis/
│       │   └── CommonFieldInterceptor.java           # 公共字段填充
│       └── redis/
│           └── RedisConfig.java
├── kk-framework-starter-security/
│   ├── pom.xml
│   └── src/main/java/com/kk/framework/security/
│       ├── KkSecurityAutoConfiguration.java
│       ├── config/
│       │   └── SecurityProperties.java
│       ├── annotation/
│       │   └── KkSecurityAnnotations.java           # 安全注解汇总
│       └── sql/
│           └── sys_permission.sql                    # 权限模型 DDL
```

---

## Task 1: Project Skeleton & Parent POMs

**Files:**
- Create: `kk-framework/pom.xml`
- Create: `kk-framework-parent/pom.xml`
- Create: `kk-framework-dependencies/pom.xml`
- Create: `kk-framework-starter-common/pom.xml`
- Create: `kk-framework-starter-logging/pom.xml`
- Create: `kk-framework-starter-web/pom.xml`
- Create: `kk-framework-starter-data/pom.xml`
- Create: `kk-framework-starter-security/pom.xml`

- [ ] **Step 1: Create root pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.kk.framework</groupId>
    <artifactId>kk-framework</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <modules>
        <module>kk-framework-parent</module>
        <module>kk-framework-dependencies</module>
        <module>kk-framework-starter-common</module>
        <module>kk-framework-starter-logging</module>
        <module>kk-framework-starter-web</module>
        <module>kk-framework-starter-data</module>
        <module>kk-framework-starter-security</module>
    </modules>
</project>
```

- [ ] **Step 2: Create kk-framework-dependencies/pom.xml (BOM)**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.kk.framework</groupId>
        <artifactId>kk-framework-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>kk-framework-dependencies</artifactId>
    <packaging>pom</packaging>

    <dependencyManagement>
        <dependencies>
            <!-- Spring Boot -->
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>

            <!-- MyBatis-Flex -->
            <dependency>
                <groupId>com.mybatis-flex</groupId>
                <artifactId>mybatis-flex-spring-boot3-starter</artifactId>
                <version>1.9.5</version>
            </dependency>

            <!-- Sa-Token -->
            <dependency>
                <groupId>cn.dev33</groupId>
                <artifactId>sa-token-spring-boot3-starter</artifactId>
                <version>1.37.0</version>
            </dependency>
            <dependency>
                <groupId>cn.dev33</groupId>
                <artifactId>sa-token-redis-jackson</artifactId>
                <version>1.37.0</version>
            </dependency>

            <!-- Hutools -->
            <dependency>
                <groupId>cn.hutool</groupId>
                <artifactId>hutool-all</artifactId>
                <version>5.8.30</version>
            </dependency>

            <!-- PostgreSQL -->
            <dependency>
                <groupId>org.postgresql</groupId>
                <artifactId>postgresql</artifactId>
                <version>42.7.3</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

- [ ] **Step 3: Create kk-framework-parent/pom.xml (plugin management)**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.kk.framework</groupId>
        <artifactId>kk-framework</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>kk-framework-parent</artifactId>
    <packaging>pom</packaging>

    <properties>
        <java.version>17</java.version>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <spring-boot.version>3.4.4</spring-boot.version>
        <lombok.version>1.18.36</lombok.version>
        <mybatis-flex.version>1.9.5</mybatis-flex.version>
        <sa-token.version>1.37.0</sa-token.version>
        <hutools.version>5.8.30</hutools.version>
    </properties>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.springframework.boot</groupId>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                    <version>${spring-boot.version}</version>
                    <configuration>
                        <excludes>
                            <exclude>
                                <groupId>org.projectlombok</groupId>
                                <artifactId>lombok</artifactId>
                            </exclude>
                        </excludes>
                    </configuration>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.13.0</version>
                    <configuration>
                        <source>${java.version}</source>
                        <target>${java.version}</target>
                        <annotationProcessorPaths>
                            <path>
                                <groupId>org.projectlombok</groupId>
                                <artifactId>lombok</artifactId>
                                <version>${lombok.version}</version>
                            </path>
                        </annotationProcessorPaths>
                    </configuration>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
```

- [ ] **Step 4: Create all 5 starter pom.xml files**

Each starter pom inherits from `kk-framework-parent` and declares dependencies:

**kk-framework-starter-common/pom.xml:**
```xml
<artifactId>kk-framework-starter-common</artifactId>
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>cn.hutool</groupId>
        <artifactId>hutool-all</artifactId>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

**kk-framework-starter-logging/pom.xml:**
```xml
<dependencies>
    <dependency>
        <groupId>com.kk.framework</groupId>
        <artifactId>kk-framework-starter-common</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>
</dependencies>
```

**kk-framework-starter-web/pom.xml:**
```xml
<dependencies>
    <dependency>
        <groupId>com.kk.framework</groupId>
        <artifactId>kk-framework-starter-common</artifactId>
    </dependency>
    <dependency>
        <groupId>com.kk.framework</groupId>
        <artifactId>kk-framework-starter-logging</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
</dependencies>
```

**kk-framework-starter-data/pom.xml:**
```xml
<dependencies>
    <dependency>
        <groupId>com.kk.framework</groupId>
        <artifactId>kk-framework-starter-common</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    <dependency>
        <groupId>com.mybatis-flex</groupId>
        <artifactId>mybatis-flex-spring-boot3-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
    </dependency>
</dependencies>
```

**kk-framework-starter-security/pom.xml:**
```xml
<dependencies>
    <dependency>
        <groupId>com.kk.framework</groupId>
        <artifactId>kk-framework-starter-data</artifactId>
    </dependency>
    <dependency>
        <groupId>com.kk.framework</groupId>
        <artifactId>kk-framework-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>cn.dev33</groupId>
        <artifactId>sa-token-spring-boot3-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>cn.dev33</groupId>
        <artifactId>sa-token-redis-jackson</artifactId>
    </dependency>
</dependencies>
```

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat: scaffold skeleton with 5 starter modules and parent poms"
```

---

## Task 2: kk-framework-starter-common — Core Classes

**Files:**
- Create: `kk-framework-starter-common/src/main/java/com/kk/framework/common/ApiResponse.java`
- Create: `kk-framework-starter-common/src/main/java/com/kk/framework/common/core/exception/BizException.java`
- Create: `kk-framework-starter-common/src/main/java/com/kk/framework/common/core/exception/IBizCode.java`
- Create: `kk-framework-starter-common/src/main/java/com/kk/framework/common/core/exception/ExceptionCode.java`
- Create: `kk-framework-starter-common/src/main/java/com/kk/framework/common/core/exception/ResultCode.java`
- Create: `kk-framework-starter-common/src/main/java/com/kk/framework/common/core/constant/CommonConstants.java`
- Create: `kk-framework-starter-common/src/main/java/com/kk/framework/common/util/JsonUtil.java`
- Create: `kk-framework-starter-common/src/main/java/com/kk/framework/common/util/PageUtil.java`
- Create: `kk-framework-starter-common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

- [ ] **Step 1: Write ApiResponse.java**

```java
package com.kk.framework.common;

import lombok.Data;
import java.io.Serializable;

@Data
public class ApiResponse<T> implements Serializable {
    private int code;
    private String message;
    private T data;
    private long timestamp;
    private String traceId;
    private String path;

    public static <T> ApiResponse<T> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> ok(T data) {
        return ok("success", data);
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        ApiResponse<T> resp = new ApiResponse<>();
        resp.setCode(0);
        resp.setMessage(message);
        resp.setData(data);
        resp.setTimestamp(System.currentTimeMillis());
        return resp;
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        ApiResponse<T> resp = new ApiResponse<>();
        resp.setCode(code);
        resp.setMessage(message);
        resp.setTimestamp(System.currentTimeMillis());
        return resp;
    }

    public static <T> ApiResponse<T> error(IBizCode bizCode) {
        return error(bizCode.getCode(), bizCode.getMessage());
    }

    public ApiResponse<T> traceId(String traceId) {
        this.setTraceId(traceId);
        return this;
    }

    public ApiResponse<T> path(String path) {
        this.setPath(path);
        return this;
    }
}
```

- [ ] **Step 2: Write BizException.java**

```java
package com.kk.framework.common.core.exception;

public class BizException extends RuntimeException {
    private final int code;

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(IBizCode bizCode) {
        super(bizCode.getMessage());
        this.code = bizCode.getCode();
    }

    public int getCode() {
        return code;
    }

    public static BizException of(IBizCode bizCode) {
        return new BizException(bizCode.getCode(), bizCode.getMessage());
    }

    public static BizException of(IBizCode bizCode, String message) {
        return new BizException(bizCode.getCode(), message);
    }

    public static BizException of(int code, String message) {
        return new BizException(code, message);
    }
}
```

- [ ] **Step 3: Write IBizCode.java**

```java
package com.kk.framework.common.core.exception;

public interface IBizCode {
    int getCode();
    String getMessage();
}
```

- [ ] **Step 4: Write ExceptionCode.java**

```java
package com.kk.framework.common.core.exception;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ExceptionCode {
    int code();
    String message();
}
```

- [ ] **Step 5: Write ResultCode.java (system-level codes)**

```java
package com.kk.framework.common.core.exception;

public enum ResultCode implements IBizCode {
    SUCCESS(0, "success"),
    VALIDATION_ERROR(40001, "参数校验失败"),
    UNAUTHORIZED(40101, "未登录"),
    FORBIDDEN(40301, "无权限"),
    NOT_FOUND(40401, "资源不存在"),
    INTERNAL_ERROR(50001, "系统异常");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() { return code; }
    @Override
    public String getMessage() { return message; }
}
```

- [ ] **Step 6: Write CommonConstants.java**

```java
package com.kk.framework.common.core.constant;

public class CommonConstants {
    public static final String TRACE_ID_HEADER = "X-TraceId";
    public static final String DEFAULT_TRACE_ID = "traceId";
}
```

- [ ] **Step 7: Write JsonUtil.java (wrapper around Hutools)**

```java
package com.kk.framework.common.util;

import cn.hutool.json.JSONUtil;

public class JsonUtil {
    public static String toJson(Object obj) {
        return JSONUtil.toJsonStr(obj);
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        return JSONUtil.toBean(json, clazz);
    }

    public static boolean isJson(String str) {
        return JSONUtil.isJson(str);
    }
}
```

- [ ] **Step 8: Write PageUtil.java (wrapper around Hutools)**

```java
package com.kk.framework.common.util;

import cn.hutool.core.page.PageUtil;

public class PageUtil extends cn.hutool.core.page.PageUtil {
    // Re-export Hutools PageUtil for framework internal use
    // Business code uses it directly via Hutools
}
```

- [ ] **Step 9: Create auto-configuration imports file**

Create directory `kk-framework-starter-common/src/main/resources/META-INF/spring/` and file `org.springframework.boot.autoconfigure.AutoConfiguration.imports` containing:
```
com.kk.framework.common.config.CommonAutoConfiguration
```

Create `CommonAutoConfiguration.java`:
```java
package com.kk.framework.common.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class CommonAutoConfiguration {
    // Common auto-config, mostly empty since common has no auto-config needs
    // This file exists to declare the spring.factories / imports entry
}
```

- [ ] **Step 10: Commit**

```bash
git add kk-framework-starter-common/
git commit -m "feat(common): add ApiResponse, BizException, IBizCode, ExceptionCode, ResultCode"
```

---

## Task 3: kk-framework-starter-logging

**Files:**
- Create: `kk-framework-starter-logging/src/main/java/com/kk/framework/logging/config/LoggingProperties.java`
- Create: `kk-framework-starter-logging/src/main/java/com/kk/framework/logging/filter/TraceIdFilter.java`
- Create: `kk-framework-starter-logging/src/main/java/com/kk/framework/logging/layout/JsonLogLayout.java`
- Create: `kk-framework-starter-logging/src/main/java/com/kk/framework/logging/KkLoggingAutoConfiguration.java`
- Create: `kk-framework-starter-logging/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

- [ ] **Step 1: Write LoggingProperties.java**

```java
package com.kk.framework.logging.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "kk.logging")
public class LoggingProperties {
    private boolean jsonEnabled = true;
    private String traceIdHeader = "X-TraceId";
    private int slowThresholdMs = 1000;
    private boolean paramLogEnabled = true;
    private boolean responseLogEnabled = false;
}
```

- [ ] **Step 2: Write TraceIdFilter.java**

```java
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
```

- [ ] **Step 3: Write JsonLogLayout.java (logback JSON encoder)**

```java
package com.kk.framework.logging.layout;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementUtil;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class JsonLogLayout extends LayoutWrappingEncoder<ILoggingEvent> {

    private final ObjectMapper mapper = new ObjectMapper();
    private static final String TRACE_ID_KEY = "traceId";

    @Override
    public void doEncode(ILoggingEvent event, OutputStream out) throws IOException {
        Map<String, String> mdc = event.getMDCPropertyMap();
        String traceId = mdc.getOrDefault(TRACE_ID_KEY, null);

        JsonGenerator jg = mapper.getFactory().createGenerator(out);
        jg.writeStartObject();
        jg.writeStringField("timestamp", event.getInstant().toString());
        jg.writeStringField("level", event.getLevel().toString());
        jg.writeStringField("logger", event.getLoggerName());
        jg.writeStringField("message", event.getFormattedMessage());

        if (traceId != null) {
            jg.writeStringField(TRACE_ID_KEY, traceId);
        }

        IThrowableProxy throwable = event.getThrowableProxy();
        if (throwable != null) {
            jg.writeStringField("error", throwable.getMessage());
        } else {
            jg.writeNullField("error");
        }

        jg.writeEndObject();
        jg.writeRaw('\n');
        jg.flush();
    }

    @Override
    public byte[] headerBytes() {
        return new byte[0];
    }
}
```

- [ ] **Step 4: Write KkLoggingAutoConfiguration.java**

```java
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
```

- [ ] **Step 5: Create imports file**

```
com.kk.framework.logging.KkLoggingAutoConfiguration
```

- [ ] **Step 6: Create logback XML config**

Create `kk-framework-starter-logging/src/main/resources/logback-spring.xml` with JSON appender configuration (student fills in actual appender ref). Include console appender with JsonLogLayout for the JSON format.

- [ ] **Step 7: Commit**

```bash
git add kk-framework-starter-logging/
git commit -m "feat(logging): add TraceIdFilter, JsonLogLayout, LoggingProperties"
```

---

## Task 4: kk-framework-starter-web

**Files:**
- Create: `kk-framework-starter-web/src/main/java/com/kk/framework/web/config/WebProperties.java`
- Create: `kk-framework-starter-web/src/main/java/com/kk/framework/web/handler/GlobalExceptionHandler.java`
- Create: `kk-framework-starter-web/src/main/java/com/kk/framework/web/response/PagedApiResponse.java`
- Create: `kk-framework-starter-web/src/main/java/com/kk/framework/web/interceptor/SlowOperationInterceptor.java`
- Create: `kk-framework-starter-web/src/main/java/com/kk/framework/web/KkWebAutoConfiguration.java`
- Create: `kk-framework-starter-web/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

- [ ] **Step 1: Write WebProperties.java**

```java
package com.kk.framework.web.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "kk.web")
public class WebProperties {
    private boolean slowEnabled = true;
    private int slowThresholdMs = 1000;
    private boolean paramLogEnabled = true;
    private boolean responseLogEnabled = false;
}
```

- [ ] **Step 2: Write GlobalExceptionHandler.java**

```java
package com.kk.framework.web.handler;

import com.kk.framework.common.ApiResponse;
import com.kk.framework.common.core.exception.BizException;
import com.kk.framework.common.core.exception.ExceptionCode;
import com.kk.framework.common.core.exception.IBizCode;
import com.kk.framework.common.core.exception.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> handleBizException(BizException e) {
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .findFirst()
                .orElse("参数校验失败");
        return ApiResponse.error(ResultCode.VALIDATION_ERROR.getCode(), message);
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.OK)
    public ApiResponse<Void> handleBindException(BindException e) {
        return ApiResponse.error(ResultCode.VALIDATION_ERROR.getCode(), e.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNotFound(NoResourceFoundException e) {
        return ApiResponse.error(ResultCode.NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ApiResponse<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return ApiResponse.error(ResultCode.NOT_FOUND);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return ApiResponse.error(ResultCode.INTERNAL_ERROR);
    }
}
```

- [ ] **Step 3: Write PagedApiResponse.java**

```java
package com.kk.framework.web.response;

import com.kk.framework.common.ApiResponse;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class PagedApiResponse<T> extends ApiResponse<List<T>> {
    private long total;
    private int page;
    private int pageSize;
    private int pages;

    public static <T> PagedApiResponse<T> of(List<T> data, long total, int page, int pageSize) {
        PagedApiResponse<T> resp = new PagedApiResponse<>();
        resp.setCode(0);
        resp.setMessage("success");
        resp.setData(data);
        resp.setTotal(total);
        resp.setPage(page);
        resp.setPageSize(pageSize);
        resp.setPages((int) Math.ceil((double) total / pageSize));
        resp.setTimestamp(System.currentTimeMillis());
        return resp;
    }
}
```

- [ ] **Step 4: Write SlowOperationInterceptor.java**

```java
package com.kk.framework.web.interceptor;

import com.kk.framework.web.config.WebProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;

@Configuration
public class SlowOperationInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(SlowOperationInterceptor.class);
    private static final String START_TIME = "slowInterceptor_startTime";

    private final WebProperties properties;

    public SlowOperationInterceptor(WebProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME, System.currentTimeMillis());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                 Object handler, Exception ex) {
        if (!properties.isSlowEnabled()) return;

        Long startTime = (Long) request.getAttribute(START_TIME);
        if (startTime == null) return;

        long duration = System.currentTimeMillis() - startTime;
        if (duration > properties.getSlowThresholdMs()) {
            log.warn("[SLOW] method={} path={} duration={}ms",
                    request.getMethod(), request.getRequestURI(), duration);
        }
    }
}
```

- [ ] **Step 5: Write KkWebAutoConfiguration.java**

```java
package com.kk.framework.web;

import com.kk.framework.web.config.WebProperties;
import com.kk.framework.web.interceptor.SlowOperationInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(WebProperties.class)
public class KkWebAutoConfiguration implements WebMvcConfigurer {

    private final WebProperties webProperties;

    public KkWebAutoConfiguration(WebProperties webProperties) {
        this.webProperties = webProperties;
    }

    @Bean
    @ConditionalOnMissingBean
    public SlowOperationInterceptor slowOperationInterceptor() {
        return new SlowOperationInterceptor(webProperties);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(slowOperationInterceptor())
                .addPathPatterns("/**");
    }
}
```

- [ ] **Step 6: Create imports file**

```
com.kk.framework.web.KkWebAutoConfiguration
```

- [ ] **Step 7: Commit**

```bash
git add kk-framework-starter-web/
git commit -m "feat(web): add GlobalExceptionHandler, PagedApiResponse, SlowOperationInterceptor"
```

---

## Task 5: kk-framework-starter-data

**Files:**
- Create: `kk-framework-starter-data/src/main/java/com/kk/framework/data/config/DataProperties.java`
- Create: `kk-framework-starter-data/src/main/java/com/kk/framework/data/config/MybatisFlexProperties.java`
- Create: `kk-framework-starter-data/src/main/java/com/kk/framework/data/mybatis/CommonFieldInterceptor.java`
- Create: `kk-framework-starter-data/src/main/java/com/kk/framework/data/redis/RedisConfig.java`
- Create: `kk-framework-starter-data/src/main/java/com/kk/framework/data/KkDataAutoConfiguration.java`
- Create: `kk-framework-starter-data/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

- [ ] **Step 1: Write DataProperties.java**

```java
package com.kk.framework.data.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "kk.data")
public class DataProperties {
    private DatabaseProperties database = new DatabaseProperties();
    private RedisProperties redis = new RedisProperties();
    private MybatisFlexProperties mybatisFlex = new MybatisFlexProperties();

    @Data
    public static class DatabaseProperties {
        private String url;
        private String username;
        private String password;
        private String driverClassName = "org.postgresql.Driver";
    }

    @Data
    public static class RedisProperties {
        private String host = "localhost";
        private int port = 6379;
        private String password;
        private int database = 0;
    }

    @Data
    public static class MybatisFlexProperties {
        private String mapperLocations = "classpath*:/mapper/**/*.xml";
        private String typeAliasesPackage;
        private boolean mapUnderscoreToCamelCase = true;
    }
}
```

- [ ] **Step 2: Write CommonFieldInterceptor.java (auto-fill createdAt/updatedAt/createdBy/updatedBy)**

```java
package com.kk.framework.data.mybatis;

import cn.dev33.satoken.stp.StpUtil;
import com.mybatis-flex.core.SqlSession;
import com.mybatis-flex.core.handler.FlexHandler;
import com.mybatis-flex.interceptor.IAutoFill;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
public class CommonFieldInterceptor implements IAutoFill {

    @Override
    public void onInsert(SqlSession sqlSession, Object entity, FlexHandler flexHandler) {
        setFieldValue(entity, "createdAt", LocalDateTime.now());
        setFieldValue(entity, "updatedAt", LocalDateTime.now());
        try {
            Object userId = StpUtil.getLoginId();
            setFieldValue(entity, "createdBy", userId);
            setFieldValue(entity, "updatedBy", userId);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onUpdate(SqlSession sqlSession, Object entity, FlexHandler flexHandler) {
        setFieldValue(entity, "updatedAt", LocalDateTime.now());
        try {
            Object userId = StpUtil.getLoginId();
            setFieldValue(entity, "updatedBy", userId);
        } catch (Exception ignored) {
        }
    }

    private void setFieldValue(Object entity, String fieldName, Object value) {
        try {
            var field = entity.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            if (field.get(entity) == null) {
                field.set(entity, value);
            }
        } catch (Exception ignored) {
        }
    }
}
```

- [ ] **Step 3: Write RedisConfig.java**

```java
package com.kk.framework.data.redis;

import com.kk.framework.data.config.DataProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisConnectionFactory redisConnectionFactory(DataProperties properties) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(properties.getRedis().getHost());
        config.setPort(properties.getRedis().getPort());
        config.setDatabase(properties.getRedis().getDatabase());
        String password = properties.getRedis().getPassword();
        if (password != null && !password.isBlank()) {
            config.setPassword(password);
        }
        return new LettuceConnectionFactory(config);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
```

- [ ] **Step 4: Write KkDataAutoConfiguration.java**

```java
package com.kk.framework.data;

import com.kk.framework.data.config.DataProperties;
import com.mybatis-flex.spring.boot3.MybatisFlexAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@Configuration
@ConditionalOnClass(MybatisFlexAutoConfiguration.class)
@EnableConfigurationProperties(DataProperties.class)
public class KkDataAutoConfiguration {
    // DataSource and MyBatis-Flex auto-configuration is handled
    // by their own spring.factories / imports entries.
    // This class wires DataProperties and additional framework components.
}
```

- [ ] **Step 5: Create imports file**

```
com.kk.framework.data.KkDataAutoConfiguration
com.mybatis.flex.spring.boot3.MybatisFlexAutoConfiguration
org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
```

- [ ] **Step 6: Commit**

```bash
git add kk-framework-starter-data/
git commit -m "feat(data): add MyBatis-Flex, Redis config, CommonFieldInterceptor"
```

---

## Task 6: kk-framework-starter-security

**Files:**
- Create: `kk-framework-starter-security/src/main/java/com/kk/framework/security/config/SecurityProperties.java`
- Create: `kk-framework-starter-security/src/main/java/com/kk/framework/security/KkSecurityAutoConfiguration.java`
- Create: `kk-framework-starter-security/src/main/java/com/kk/framework/security/interceptor/KkSaTokenInterceptor.java`
- Create: `kk-framework-starter-security/src/main/resources/sql/sys_permission.sql`
- Create: `kk-framework-starter-security/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

- [ ] **Step 1: Write SecurityProperties.java**

```java
package com.kk.framework.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "kk.security")
public class SecurityProperties {
    private boolean enabled = true;
    private List<String> excludes = new ArrayList<>();
    private String tokenName = "Authorization";
    private int timeout = 7200;
    private int activeTimeout = 1800;
}
```

- [ ] **Step 2: Write KkSaTokenInterceptor.java**

```java
package com.kk.framework.security.interceptor;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.stp.StpUtil;
import com.kk.framework.common.ApiResponse;
import com.kk.framework.common.core.exception.ResultCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.List;

@Component
@ConditionalOnProperty(name = "kk.security.enabled", havingValue = "true", matchIfMissing = true)
public class KkSaTokenInterceptor implements Filter {

    private final SecurityProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KkSaTokenInterceptor(SecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String path = request.getRequestURI();
        if (isExcluded(path)) {
            chain.doFilter(request, response);
            return;
        }

        try {
            StpUtil.checkLogin();
            chain.doFilter(request, response);
        } catch (NotLoginException e) {
            writeResponse(response, ApiResponse.error(ResultCode.UNAUTHORIZED));
        } catch (NotRoleException | NotPermissionException e) {
            writeResponse(response, ApiResponse.error(ResultCode.FORBIDDEN));
        }
    }

    private boolean isExcluded(String path) {
        for (String pattern : properties.getExcludes()) {
            if (path.matches(pattern.replace("**", ".*").replace("*", "[^/]*"))) {
                return true;
            }
        }
        return false;
    }

    private void writeResponse(HttpServletResponse response, ApiResponse<?> apiResponse) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}
```

- [ ] **Step 3: Write KkSecurityAutoConfiguration.java**

```java
package com.kk.framework.security;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.kk.framework.security.config.SecurityProperties;
import com.kk.framework.security.interceptor.KkSaTokenInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SecurityProperties.class)
public class KkSecurityAutoConfiguration {

    private final SecurityProperties properties;

    public KkSecurityAutoConfiguration(SecurityProperties properties) {
        this.properties = properties;
    }

    @Bean
    @ConditionalOnMissingBean
    public KkSaTokenInterceptor kkSaTokenInterceptor() {
        return new KkSaTokenInterceptor(properties);
    }
}
```

- [ ] **Step 4: Write sys_permission.sql**

```sql
-- kk-framework 权限模型 DDL

CREATE TABLE IF NOT EXISTS sys_user (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(64)  NOT NULL UNIQUE,
    password        VARCHAR(255) NOT NULL,
    nickname        VARCHAR(64),
    status          SMALLINT     NOT NULL DEFAULT 1,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT,
    updated_by      BIGINT
);

CREATE TABLE IF NOT EXISTS sys_role (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(64)  NOT NULL UNIQUE,
    name            VARCHAR(128) NOT NULL,
    status          SMALLINT     NOT NULL DEFAULT 1,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_menu (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(128) NOT NULL UNIQUE,
    name            VARCHAR(128) NOT NULL,
    path            VARCHAR(255),
    method          VARCHAR(16),
    type            SMALLINT     NOT NULL DEFAULT 1 COMMENT '1=menu, 2=button',
    status          SMALLINT     NOT NULL DEFAULT 1,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id         BIGINT       NOT NULL,
    role_id         BIGINT       NOT NULL,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS sys_role_menu (
    role_id         BIGINT       NOT NULL,
    menu_id         BIGINT       NOT NULL,
    PRIMARY KEY (role_id, menu_id)
);

CREATE INDEX idx_sys_user_username ON sys_user(username);
CREATE INDEX idx_sys_user_status ON sys_user(status);
CREATE INDEX idx_sys_role_code ON sys_role(code);
CREATE INDEX idx_sys_menu_code ON sys_menu(code);
```

- [ ] **Step 5: Create imports file**

```
com.kk.framework.security.KkSecurityAutoConfiguration
```

- [ ] **Step 6: Commit**

```bash
git add kk-framework-starter-security/
git commit -m "feat(security): add Sa-Token interceptor, SecurityProperties, permission SQL"
```

---

## Task 7: Root POM — Enable Dependency Management

**Files:**
- Modify: `kk-framework/pom.xml` — enable `kk-framework-dependencies` import

- [ ] **Step 1: Add kk-framework-dependencies import to root pom**

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.kk.framework</groupId>
            <artifactId>kk-framework-dependencies</artifactId>
            <version>${project.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Also add `dependencyManagement` block to each starter pom referencing the BOM version. The root pom should manage the kk-framework group version.

- [ ] **Step 2: Commit**

```bash
git add kk-framework/pom.xml
git commit -m "chore: wire kk-framework-dependencies BOM to root pom"
```

---

## Spec Coverage Check

| Spec Section | Task |
|---|---|
| 2. 模块结构 | Task 1 |
| 3. 核心依赖版本 | Task 1 (BOM) |
| 4. common (ApiResponse, BizException, IBizCode, ExceptionCode, ResultCode) | Task 2 |
| 5. logging (JSON layout, TraceId, MDC) | Task 3 |
| 6. web (GlobalExceptionHandler, SlowOperation, PagedResponse) | Task 4 |
| 7. data (MyBatis-Flex, Redis, CommonFieldInterceptor) | Task 5 |
| 8. security (Sa-Token, SQL model) | Task 6 |
| 9. Spring Boot auto-configuration | All tasks |

All spec items are covered.

---

**Plan complete and saved to `docs/superpowers/plans/2026-04-13-kk-framework-implementation.md`.**

Two execution options:

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?
