# kk-framework 设计规格

## 1. 项目概述

基于 Spring Boot 4.0 的多模块开发脚手架，采用多 starter 架构分离关注点，支持 PostgreSQL + Redis，业务层以 MyBatis-Flex 为 ORM，Sa-Token 为安全框架，Hutools 为工具库。

## 2. 模块结构

```
kk-framework/                                # 父项目
├── kk-framework-parent/                     # Parent，定义插件、公共配置
├── kk-framework-dependencies/               # BOM，统一第三方依赖版本
├── kk-framework-starter-common/             # 基础工具、响应结构、异常基类
├── kk-framework-starter-logging/            # JSON日志、TraceId 过滤器
├── kk-framework-starter-web/                # REST 规范、全局异常处理
├── kk-framework-starter-data/               # MyBatis-Flex、PG、Redis 集成
└── kk-framework-starter-security/           # Sa-Token、权限模型 SQL
```

### 模块依赖关系

```
common
 ├── logging ← common
 ├── web ← common, logging
 ├── data ← common
 └── security ← data, web
```

## 3. 核心依赖版本

| 组件 | 版本 |
|------|------|
| Spring Boot | 3.4.x |
| MyBatis-Flex | 1.9.x |
| Sa-Token | 1.37.x |
| Hutools | 5.8.x |
| Lombok | 1.18.x |
| PostgreSQL Driver | 42.7.x |

## 4. kk-framework-starter-common

### 4.1 包结构

```
com.kk.framework.common
├── core/
│   ├── response/       # ApiResponse、响应构建器
│   ├── exception/       # BizException、IBizCode、ExceptionCode 注解
│   ├── constant/        # 通用常量
│   └── enums/           # 通用枚举
├── util/                # Hutools 封装（轻量 wrapper）
└── extension/           # SPI 扩展点
```

### 4.2 统一响应结构

```java
public class ApiResponse<T> {
    private int code;          // 0=成功，非0=业务错误
    private String message;    // 描述信息
    private T data;            // 响应数据
    private long timestamp;    // 时间戳
    private String traceId;    // 请求追踪ID
    private String path;        // 请求路径
}
```

HTTP 状态码统一返回 200，业务错误码在 body 中体现。仅系统异常返回非 200。

### 4.3 异常体系

**基础业务异常：**

```java
public class BizException extends RuntimeException {
    private final int code;
    private final String message;
}
```

**异常码接口：**

```java
public interface IBizCode {
    int getCode();
    String getMessage();
}
```

**注解驱动异常（支持动态消息）：**

```java
@ExceptionCode(code = 10001, message = "用户不存在")
public class UserNotFoundException extends BizException { }
```

**异常处理映射：**

| 异常类型 | HTTP Status | 业务 code |
|----------|-------------|-----------|
| BizException | 200 | 异常中的 code |
| ValidationException | 200 | 40001 |
| UnauthorizedException | 401 | 40101 |
| AccessDeniedException | 403 | 40301 |
| ResourceNotFoundException | 404 | 40401 |
| InternalServerError | 500 | 50001 |

### 4.4 响应构建器

```java
ApiResponse.ok()
ApiResponse.ok(data)
ApiResponse.ok(message, data)
ApiResponse.error(IBizCode)
ApiResponse.error(code, message)
```

## 5. kk-framework-starter-logging

### 5.1 JSON 日志格式

```json
{
  "timestamp": "2026-04-13T10:30:00.000+08:00",
  "level": "INFO",
  "traceId": "f4a12b8c",
  "userId": "10001",
  "operation": "createOrder",
  "duration": 125,
  "success": true,
  "method": "POST",
  "path": "/api/order",
  "params": {},
  "error": null,
  "ip": "192.168.1.100"
}
```

### 5.2 TraceId 处理

- `TraceIdFilter` 拦截所有请求
- 从 Header `X-TraceId` 获取，无则生成 UUID
- 存入 MDC 供日志打印，出响应头传递

### 5.3 配置项

```yaml
kk:
  logging:
    json-enabled: true
    trace-id-header: X-TraceId
    slow-threshold-ms: 1000
    param-log-enabled: true
    response-log-enabled: false
```

## 6. kk-framework-starter-web

### 6.1 全局异常处理器

`GlobalExceptionHandler` 统一处理所有异常，输出标准化 `ApiResponse`。

### 6.2 请求拦截

- TraceId 生成与传递
- 慢操作日志记录

### 6.3 REST 规范

- 统一响应包装
- 分页响应结构（继承 ApiResponse，附加 total/pages 等字段）

## 7. kk-framework-starter-data

### 7.1 配置结构

```yaml
kk:
  data:
    database:
      url: jdbc:postgresql://localhost:5432/mydb
      username: postgres
      password: xxx
    redis:
      host: localhost
      port: 6379
      password: xxx
      database: 0
    mybatis-flex:
      mapper-locations: classpath*:/mapper/**/*.xml
      type-aliases-package: com.kk.**.domain.entity
      configuration:
        map-underscore-to-camel-case: true
```

### 7.2 公共字段自动填充

通过 MyBatis-Flex 拦截器自动填充 `createdAt`、`updatedAt`、`createdBy`、`updatedBy`。

### 7.3 工具封装

基于 Hutools 封装 `JsonUtil`、`PageUtil`，不重复造轮子。

## 8. kk-framework-starter-security

### 8.1 权限数据模型

```sql
sys_user        (id, username, password, nickname, status, create_time)
sys_role        (id, code, name, status, create_time)
sys_menu        (id, code, name, path, method, type, status)
sys_user_role   (user_id, role_id)
sys_role_menu   (role_id, menu_id)
```

提供完整的 SQL 初始化脚本，开箱即用。

### 8.2 配置项

```yaml
kk:
  security:
    enabled: true
    excludes: ["/api/public/**", "/api/auth/login"]
    token-name: Authorization
    timeout: 7200
    active-timeout: 1800
```

### 8.3 核心注解

```java
@SaCheckLogin              // 需登录
@SaCheckRole("admin")      // 需角色
@SaCheckPermission("user:add")  // 需权限码
```

### 8.4 Sa-Token 集成范围

- 登录、登出、Session 管理
- Redis Session 共享（分布式）
- 路由拦截器（路径级权限控制）
- 角色/权限注解鉴权

## 9. Spring Boot 自动配置

每个 starter 提供 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 文件，声明自动配置类。业务方只需引入对应 starter 依赖，配置即可生效。

## 10. 待确认事项

- [ ] 数据库连接池选择（Hikari / Druid）
- [ ] 是否需要 API 版本管理（/api/v1/ vs /api/）
- [ ] 是否需要请求限流组件
- [ ] 测试策略（单元测试 / 集成测试）
