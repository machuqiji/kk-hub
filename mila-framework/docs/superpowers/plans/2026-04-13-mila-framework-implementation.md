# mila-framework Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建基于 Spring Boot 4.0 的开发脚手架，面向开源场景

**Architecture:** Maven 多模块结构，版本统一锁定在 dependencies 模块，各 starter 独立功能按需引入

**Tech Stack:** Spring Boot 4.0, Spring Framework 6.x, Java 17+, PostgreSQL, Redis, MyBatisFlex (注解), Hutools 5.x, Sa-Token (完整功能)

---

## 模块结构概览

```
mila-framework/
├── mila-framework-parent/           # 父POM，管理生命周期
├── mila-framework-dependencies/    # 依赖版本锁定
├── mila-framework-starter-common/   # 公共组件
├── mila-framework-starter-redis/   # Redis starter
├── mila-framework-starter-mybatis/  # MyBatisFlex starter
├── mila-framework-starter-satoken/  # Sa-Token starter
└── mila-framework-starter-web/      # Web starter (REST规范)
```

---

## Task 1: 初始化 Maven 多模块结构

**Files:**
- Create: `mila-framework/pom.xml`
- Create: `mila-framework/mila-framework-parent/pom.xml`
- Create: `mila-framework/mila-framework-dependencies/pom.xml`
- Create: `mila-framework/mila-framework-starter-common/pom.xml`
- Create: `mila-framework/mila-framework-starter-redis/pom.xml`
- Create: `mila-framework/mila-framework-starter-mybatis/pom.xml`
- Create: `mila-framework/mila-framework-starter-satoken/pom.xml`
- Create: `mila-framework/mila-framework-starter-web/pom.xml`

- [ ] **Step 1: 创建根 POM**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.kk.mila</groupId>
    <artifactId>mila-framework</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>
    <name>mila-framework</name>
    <description>Spring Boot 4.0 开发脚手架</description>

    <modules>
        <module>mila-framework-dependencies</module>
        <module>mila-framework-starter-common</module>
        <module>mila-framework-starter-redis</module>
        <module>mila-framework-starter-mybatis</module>
        <module>mila-framework-starter-satoken</module>
        <module>mila-framework-starter-web</module>
    </modules>
</project>
```

- [ ] **Step 2: 创建 mila-framework-dependencies/pom.xml（版本锁定）**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.kk.mila</groupId>
        <artifactId>mila-framework-parent</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>
    <artifactId>mila-framework-dependencies</artifactId>
    <packaging>pom</packaging>

    <properties>
        <java.version>17</java.version>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <spring-boot.version>4.0.0</spring-boot.version>
        <mybatis-flex.version>1.9.0</mybatis-flex.version>
        <hutools.version>5.8.26</hutools.version>
        <satoken.version>1.37.0</satoken.version>
        <logback-json.version>4.0.0</logback-json.version>
    </properties>

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

            <!-- MyBatisFlex -->
            <dependency>
                <groupId>com.mybatis-flex</groupId>
                <artifactId>mybatis-flex-spring-boot-starter</artifactId>
                <version>${mybatis-flex.version}</version>
            </dependency>

            <!-- Hutools -->
            <dependency>
                <groupId>cn.hutool</groupId>
                <artifactId>hutool-all</artifactId>
                <version>${hutools.version}</version>
            </dependency>

            <!-- Sa-Token -->
            <dependency>
                <groupId>cn.dev33</groupId>
                <artifactId>sa-token-spring-boot-starter</artifactId>
                <version>${satoken.version}</version>
            </dependency>

            <!-- Logback JSON -->
            <dependency>
                <groupId>net.logstash.logback</groupId>
                <artifactId>logstash-logback-encoder</artifactId>
                <version>${logback-json.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

- [ ] **Step 3: 创建 mila-framework-parent/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.0.0</version>
        <relativePath/>
    </parent>

    <groupId>com.kk.mila</groupId>
    <artifactId>mila-framework-parent</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>
    <name>mila-framework-parent</name>

    <properties>
        <java.version>17</java.version>
    </properties>

    <modules>
        <module>../mila-framework-dependencies</module>
        <module>../mila-framework-starter-common</module>
        <module>../mila-framework-starter-redis</module>
        <module>../mila-framework-starter-mybatis</module>
        <module>../mila-framework-starter-satoken</module>
        <module>../mila-framework-starter-web</module>
    </modules>
</project>
```

- [ ] **Step 4: 创建各 starter 模块的 pom.xml（模板）**

每个 starter 继承 mila-framework-dependencies，标准格式：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.kk.mila</groupId>
        <artifactId>mila-framework-dependencies</artifactId>
        <version>1.0.0-SNAPSHOT</version>
        <relativePath>../mila-framework-dependencies/pom.xml</relativePath>
    </parent>

    <artifactId>mila-framework-starter-xxx</artifactId>
    <name>mila-framework-starter-xxx</name>

    <dependencies>
        <!-- starter 自己需要的依赖 -->
    </dependencies>
</project>
```

创建以下模块：
- mila-framework-starter-common/pom.xml
- mila-framework-starter-redis/pom.xml
- mila-framework-starter-mybatis/pom.xml
- mila-framework-starter-satoken/pom.xml
- mila-framework-starter-web/pom.xml

- [ ] **Step 5: 验证编译**

Run: `mvn clean compile -DskipTests -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat: scaffold mila-framework multi-module structure"
```

---

## Task 2: mila-framework-starter-common 公共组件

**Files:**
- Create: `mila-framework-starter-common/src/main/java/com/kk/mila/common/ApiResponse.java`
- Create: `mila-framework-starter-common/src/main/java/com/kk/mila/common/core/exception/BizException.java`
- Create: `mila-framework-starter-common/src/main/java/com/kk/mila/common/core/exception/IBizCode.java`
- Create: `mila-framework-starter-common/src/main/java/com/kk/mila/common/core/exception/ResultCode.java`
- Create: `mila-framework-starter-common/src/main/java/com/kk/mila/common/core/exception/ExceptionCode.java`
- Create: `mila-framework-starter-common/src/main/java/com/kk/mila/common/util/JsonUtil.java`
- Create: `mila-framework-starter-common/src/main/java/com/kk/mila/common/util/PageUtil.java`
- Create: `mila-framework-starter-common/src/main/java/com/kk/mila/common/config/CommonAutoConfiguration.java`
- Create: `mila-framework-starter-common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Modify: `mila-framework-starter-common/pom.xml`

- [ ] **Step 1: 添加 common 模块依赖到 pom.xml**

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>cn.hutool</groupId>
        <artifactId>hutool-all</artifactId>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
</dependencies>
```

- [ ] **Step 2: 创建 ApiResponse.java**

```java
package com.kk.mila.common;

public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;
    private long timestamp;

    public ApiResponse() {
        this.timestamp = System.currentTimeMillis();
    }

    public ApiResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(0, "success", null);
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "success", data);
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(0, message, data);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    public static <T> ApiResponse<T> error(IBizCode bizCode) {
        return new ApiResponse<>(bizCode.getCode(), bizCode.getMessage(), null);
    }

    // getters and setters
}
```

- [ ] **Step 3: 创建 IBizCode.java**

```java
package com.kk.mila.common.core.exception;

public interface IBizCode {
    int getCode();
    String getMessage();
}
```

- [ ] **Step 4: 创建 ResultCode.java**

```java
package com.kk.mila.common.core.exception;

public enum ResultCode implements IBizCode {
    SUCCESS(0, "success"),
    FAIL(-1, "操作失败"),
    ERROR(500, "系统异常"),
    ;

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
```

- [ ] **Step 5: 创建 BizException.java**

```java
package com.kk.mila.common.core.exception;

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
}
```

- [ ] **Step 6: 创建 ExceptionCode.java（扩展错误码枚举）**

```java
package com.kk.mila.common.core.exception;

public enum ExceptionCode implements IBizCode {
    // 通用错误码
    PARAM_INVALID(1001, "参数无效"),
    DATA_NOT_FOUND(1002, "数据不存在"),
    DATA_DUPLICATE(1003, "数据重复"),
    ;

    private final int code;
    private final String message;

    ExceptionCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
```

- [ ] **Step 7: 创建 JsonUtil.java**

```java
package com.kk.mila.common.util;

import cn.hutool.json.JSONUtil;

public class JsonUtil {
    public static String toJson(Object obj) {
        return JSONUtil.toJsonStr(obj);
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        return JSONUtil.toBean(json, clazz);
    }
}
```

- [ ] **Step 8: 创建 PageUtil.java**

```java
package com.kk.mila.common.util;

import cn.hutool.core.bean.BeanUtil;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PageUtil {
    public static <T, R> PageResult<R> convert(PageResult<T> page, Function<T, R> converter) {
        List<R> records = page.getRecords().stream()
                .map(converter)
                .collect(Collectors.toList());
        return new PageResult<>(records, page.getTotal());
    }

    public record PageResult<T>(List<T> records, long total) {}
}
```

- [ ] **Step 9: 创建 CommonAutoConfiguration.java**

```java
package com.kk.mila.common.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class CommonAutoConfiguration {
    // 后续会添加拦截器、异常处理器等Bean
}
```

- [ ] **Step 10: 创建 Spring Boot 自动配置文件**

`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

```
com.kk.mila.common.config.CommonAutoConfiguration
```

- [ ] **Step 11: 验证编译**

Run: `mvn clean compile -DskipTests -q -pl mila-framework-starter-common`
Expected: BUILD SUCCESS

- [ ] **Step 12: Commit**

```bash
git add -A && git commit -m "feat(common): add ApiResponse, BizException, IBizCode, ResultCode, ExceptionCode, JsonUtil, PageUtil"
```

---

## Task 3: mila-framework-starter-web（REST规范 + 全自动异常处理 + JSON日志）

**Files:**
- Create: `mila-framework-starter-web/src/main/java/com/kk/mila/web/config/WebProperties.java`
- Create: `mila-framework-starter-web/src/main/java/com/kk/mila/web/config/GlobalExceptionHandler.java`
- Create: `mila-framework-starter-web/src/main/java/com/kk/mila/web/filter/TraceIdFilter.java`
- Create: `mila-framework-starter-web/src/main/java/com/kk/mila/web/log/JsonLogLayout.java`
- Create: `mila-framework-starter-web/src/main/resources/logback-spring.xml`
- Create: `mila-framework-starter-web/src/main/java/com/kk/mila/web/config/WebAutoConfiguration.java`
- Create: `mila-framework-starter-web/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Modify: `mila-framework-starter-web/pom.xml`

- [ ] **Step 1: 添加 web 模块依赖到 pom.xml**

```xml
<dependencies>
    <dependency>
        <groupId>com.kk.mila</groupId>
        <artifactId>mila-framework-starter-common</artifactId>
        <version>${project.version}</version>
    </dependency>
    <dependency>
        <groupId>net.logstash.logback</groupId>
        <artifactId>logstash-logback-encoder</artifactId>
    </dependency>
</dependencies>
```

- [ ] **Step 2: 创建 WebProperties.java**

```java
package com.kk.mila.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mila.web")
public class WebProperties {
    private String basePackage = "";

    public String getBasePackage() {
        return basePackage;
    }

    public void setBasePackage(String basePackage) {
        this.basePackage = basePackage;
    }
}
```

- [ ] **Step 3: 创建 GlobalExceptionHandler.java（全自动异常处理）**

```java
package com.kk.mila.web.config;

import com.kk.mila.common.ApiResponse;
import com.kk.mila.common.core.exception.BizException;
import com.kk.mila.common.core.exception.IBizCode;
import com.kk.mila.common.core.exception.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> handleBizException(BizException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(IBizCode.class)
    public ApiResponse<Void> handleBizCode(IBizCode bizCode) {
        log.warn("业务码异常: code={}, message={}", bizCode.getCode(), bizCode.getMessage());
        return ApiResponse.error(bizCode.getCode(), bizCode.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldError() != null
                ? e.getBindingResult().getFieldError().getDefaultMessage()
                : "参数校验失败";
        log.warn("参数校验异常: {}", message);
        return ApiResponse.error(ResultCode.PARAM_INVALID.getCode(), message);
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleBindException(BindException e) {
        String message = e.getFieldError() != null
                ? e.getFieldError().getDefaultMessage()
                : "参数绑定失败";
        log.warn("参数绑定异常: {}", message);
        return ApiResponse.error(ResultCode.PARAM_INVALID.getCode(), message);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return ApiResponse.error(ResultCode.ERROR);
    }
}
```

- [ ] **Step 4: 创建 TraceIdFilter.java（请求拦截 + traceId灌入MDC）**

```java
package com.kk.mila.web.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter implements Filter {

    public static final String TRACE_ID = "traceId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String traceId = httpRequest.getHeader(TRACE_ID);
        if (traceId == null || traceId.isEmpty()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        MDC.put(TRACE_ID, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID);
        }
    }
}
```

- [ ] **Step 5: 创建 JsonLogLayout.java**

```java
package com.kk.mila.web.log;

import net.logstash.logback.composite.CompositeJsonProvider;
import net.logstash.logback.composite.JsonProvider;
import net.logstash.logback.composite.loggingevent.*;

import java.util.ArrayList;
import java.util.List;

public class JsonLogLayout extends net.logstash.logback.classic.LogstashLayout {

    public JsonLogLayout() {
        setCustomFields("{\"app\":\"mila-framework\"}");
        setIncludeMdcKeyNameList(getMdcFields());
        setIncludeContext(true);
        setIncludeTags(true);
        setIncludeLoggerName(true);
        setIncludeThreadName(true);
        setIncludeMessage(true);
        setIncludeException(true);
        setIncludeCallerData(true);
        setTimestampPattern("yyyy-MM-dd HH:mm:ss.SSS");
    }

    private static List<String> getMdcFields() {
        List<String> fields = new ArrayList<>();
        fields.add("traceId");
        fields.add("userId");
        fields.add("requestUri");
        fields.add("method");
        fields.add("costTime");
        return fields;
    }
}
```

- [ ] **Step 6: 创建 logback-spring.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <property name="LOG_PATTERN" value="%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"/>

    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <customFields>{"app":"mila-framework"}</customFields>
            <includeMdcKeyName>traceId</includeMdcKeyName>
            <includeMdcKeyName>userId</includeMdcKeyName>
            <includeMdcKeyName>requestUri</includeMdcKeyName>
            <includeMdcKeyName>method</includeMdcKeyName>
            <includeMdcKeyName>costTime</includeMdcKeyName>
        </encoder>
    </appender>

    <appender name="CONSOLE_PLAIN" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>${LOG_PATTERN}</pattern>
        </encoder>
    </appender>

    <springProfile name="dev,default">
        <root level="INFO">
            <appender-ref ref="CONSOLE_PLAIN"/>
        </root>
    </springProfile>

    <springProfile name="prod">
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
    </springProfile>
</configuration>
```

- [ ] **Step 7: 创建 WebAutoConfiguration.java**

```java
package com.kk.mila.web.config;

import com.kk.mila.web.filter.TraceIdFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(WebProperties.class)
public class WebAutoConfiguration {

    @Bean
    public TraceIdFilter traceIdFilter() {
        return new TraceIdFilter();
    }

    @Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }
}
```

- [ ] **Step 8: 创建 Spring Boot 自动配置文件**

`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

```
com.kk.mila.web.config.WebAutoConfiguration
```

- [ ] **Step 9: 验证编译**

Run: `mvn clean compile -DskipTests -q -pl mila-framework-starter-web -am`
Expected: BUILD SUCCESS

- [ ] **Step 10: Commit**

```bash
git add -A && git commit -m "feat(web): add REST unified response, global exception handler, traceId filter, JSON logging"
```

---

## Task 4: mila-framework-starter-redis

**Files:**
- Create: `mila-framework-starter-redis/src/main/java/com/kk/mila/redis/config/RedisProperties.java`
- Create: `mila-framework-starter-redis/src/main/java/com/kk/mila/redis/config/RedisAutoConfiguration.java`
- Create: `mila-framework-starter-redis/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Modify: `mila-framework-starter-redis/pom.xml`

- [ ] **Step 1: 添加 redis 模块依赖到 pom.xml**

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.session</groupId>
        <artifactId>spring-session-data-redis</artifactId>
    </dependency>
    <dependency>
        <groupId>com.kk.mila</groupId>
        <artifactId>mila-framework-starter-common</artifactId>
        <version>${project.version}</version>
    </dependency>
</dependencies>
```

- [ ] **Step 2: 创建 RedisProperties.java**

```java
package com.kk.mila.redis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mila.redis")
public class RedisProperties {
    private String host = "localhost";
    private int port = 6379;
    private String password;
    private int database = 0;
    private long timeout = 3000;

    // getters and setters
}
```

- [ ] **Step 3: 创建 RedisAutoConfiguration.java**

```java
package com.kk.mila.redis.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

@Configuration
@EnableConfigurationProperties(RedisProperties.class)
@EnableRedisHttpSession
@ConditionalOnClass(RedisTemplate.class)
@ConditionalOnProperty(prefix = "mila.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisAutoConfiguration {

    @Bean
    public RedisConnectionFactory redisConnectionFactory(RedisProperties properties) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
        config.setHostName(properties.getHost());
        config.setPort(properties.getPort());
        config.setDatabase(properties.getDatabase());
        if (properties.getPassword() != null && !properties.getPassword().isEmpty()) {
            config.setPassword(properties.getPassword());
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

- [ ] **Step 4: 创建 Spring Boot 自动配置文件**

`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

```
com.kk.mila.redis.config.RedisAutoConfiguration
```

- [ ] **Step 5: 验证编译**

Run: `mvn clean compile -DskipTests -q -pl mila-framework-starter-redis -am`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat(redis): add Redis starter with session sharing support"
```

---

## Task 5: mila-framework-starter-mybatis

**Files:**
- Create: `mila-framework-starter-mybatis/src/main/java/com/kk/mila/mybatis/config/MybatisProperties.java`
- Create: `mila-framework-starter-mybatis/src/main/java/com/kk/mila/mybatis/config/MybatisAutoConfiguration.java`
- Create: `mila-framework-starter-mybatis/src/main/java/com/kk/mila/mybatis/core/BaseMapper.java`
- Create: `mila-framework-starter-mybatis/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Modify: `mila-framework-starter-mybatis/pom.xml`

- [ ] **Step 1: 添加 mybatis 模块依赖到 pom.xml**

```xml
<dependencies>
    <dependency>
        <groupId>com.mybatis-flex</groupId>
        <artifactId>mybatis-flex-spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>com.kk.mila</groupId>
        <artifactId>mila-framework-starter-common</artifactId>
        <version>${project.version}</version>
    </dependency>
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
    </dependency>
</dependencies>
```

- [ ] **Step 2: 创建 MybatisProperties.java**

```java
package com.kk.mila.mybatis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mila.mybatis")
public class MybatisProperties {
    private String typeAliasesPackage;
    private String mapperLocations = "classpath*:/mapper/**/*.xml";
    private boolean cacheEnabled = true;

    // getters and setters
}
```

- [ ] **Step 3: 创建 MybatisAutoConfiguration.java**

```java
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
```

- [ ] **Step 4: 创建 BaseMapper.java**

```java
package com.kk.mila.mybatis.core;

import com.mybatisflex.core.BaseMapper as FlexBaseMapper;

public interface BaseMapper<T> extends FlexBaseMapper<T> {
    // 通用CRUD方法由MyBatisFlex BaseMapper提供
    // 子类只需继承此接口即可获得通用方法
}
```

- [ ] **Step 5: 创建 Spring Boot 自动配置文件**

`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

```
com.kk.mila.mybatis.config.MybatisAutoConfiguration
```

- [ ] **Step 6: 验证编译**

Run: `mvn clean compile -DskipTests -q -pl mila-framework-starter-mybatis -am`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat(mybatis): add MyBatisFlex starter with BaseMapper"
```

---

## Task 6: mila-framework-starter-satoken

**Files:**
- Create: `mila-framework-starter-satoken/src/main/java/com/kk/mila/satoken/config/SaTokenProperties.java`
- Create: `mila-framework-starter-satoken/src/main/java/com/kk/mila/satoken/config/SaTokenAutoConfiguration.java`
- Create: `mila-framework-starter-satoken/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Modify: `mila-framework-starter-satoken/pom.xml`

- [ ] **Step 1: 添加 satoken 模块依赖到 pom.xml**

```xml
<dependencies>
    <dependency>
        <groupId>cn.dev33</groupId>
        <artifactId>sa-token-spring-boot-starter</artifactId>
    </dependency>
    <dependency>
        <groupId>com.kk.mila</groupId>
        <artifactId>mila-framework-starter-common</artifactId>
        <version>${project.version}</version>
    </dependency>
</dependencies>
```

- [ ] **Step 2: 创建 SaTokenProperties.java**

```java
package com.kk.mila.satoken.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mila.satoken")
public class SaTokenProperties {
    private String tokenName = "Authorization";
    private long timeout = 7200;
    private String secretKey;
    private boolean isConcurrent = true;
    private boolean isShare = true;
    private String tokenStyle = "uuid";

    // getters and setters
}
```

- [ ] **Step 3: 创建 SaTokenAutoConfiguration.java**

```java
package com.kk.mila.satoken.config;

import cn.dev33.satoken.context.SaTokenContext;
import cn.dev33.satoken.filter.SaFilterAuthStrategy;
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
public class SaTokenAutoConfiguration {

    @Bean
    public SaServletFilter saServletFilter(SaTokenProperties properties) {
        return new SaServletFilter()
                .setAuth(object -> {
                    // 鉴权失败时返回统一响应
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
```

- [ ] **Step 4: 创建 Spring Boot 自动配置文件**

`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

```
com.kk.mila.satoken.config.SaTokenAutoConfiguration
```

- [ ] **Step 5: 验证编译**

Run: `mvn clean compile -DskipTests -q -pl mila-framework-starter-satoken -am`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat(satoken): add Sa-Token starter with full features"
```

---

## Task 7: 安装验证

**Files:**
- Create: `mvnw` (Maven Wrapper)
- Create: `.mvn/wrapper/maven-wrapper.properties`
- Modify: `pom.xml` (添加 wrapper 插件)

- [ ] **Step 1: 添加 Maven Wrapper 插件到根 pom.xml**

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-wrapper-plugin</artifactId>
    <version>3.2.0</version>
</plugin>
```

- [ ] **Step 2: 验证完整项目编译**

Run: `mvn clean install -DskipTests -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: 验证版本锁定**

Run: `mvn dependency:tree -pl mila-framework-starter-web -am | grep -E "mybatis-flex|satoken|hutool"`
Expected: 显示具体版本号

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "feat: add maven wrapper and verify full build"
```

---

## 设计覆盖检查

| 需求 | 实现位置 |
|------|----------|
| Spring Boot 4.0 | Task 1 |
| Maven多模块 + 版本锁定 | Task 1 |
| ApiResponse统一响应 | Task 2 |
| 全自动异常处理 | Task 3 |
| JSON结构化日志 + traceId | Task 3 |
| Redis缓存 + Session | Task 4 |
| MyBatisFlex注解方式 | Task 5 |
| Sa-Token完整功能 | Task 6 |
| PostgreSQL支持 | Task 5 |
| Hutools工具 | Task 2 |
