# mila-framework 设计文档

## 概述

基于 Spring Boot 4.0 的开发脚手架，面向外部开源场景。

## 技术栈

| 组件 | 技术选型 | 版本 |
|------|---------|------|
| 基础框架 | Spring Boot | 4.0.x |
| Java版本 | Java | 17+ |
| 数据库 | PostgreSQL | - |
| 缓存 | Redis | - |
| ORM | MyBatisFlex | 注解方式 |
| 工具类 | Hutools | 5.x |
| 安全框架 | Sa-Token | 完整功能 |
| 日志 | Logback + JSON格式 | 结构化日志 |

## 模块结构

```
mila-framework-parent/
├── mila-framework-dependencies/      # 依赖管理，版本仲裁
├── mila-framework-starter-common/   # 公共组件
├── mila-framework-starter-redis/    # Redis starter
├── mila-framework-starter-mybatis/ # MyBatisFlex starter
├── mila-framework-starter-satoken/ # Sa-Token starter
└── mila-framework-starter-web/      # Web starter（REST规范）
```

## 版本策略

所有依赖版本在 `mila-framework-dependencies/pom.xml` 中统一锁定管理，子模块通过 `<dependencyManagement>` 引入。

## REST接口规范

### 统一响应格式

```java
public class ApiResponse<T> {
    private int code;       // 业务码，0=成功
    private String message; // 信息
    private T data;         // 数据
    private long timestamp; // 时间戳
}
```

### 异常处理（全自动化）

- `@RestControllerAdvice` 统一拦截所有异常
- 业务异常 `BizException(code, message)` → `ApiResponse.error(code, message)`
- 系统异常（数据库、网络等）→ `ApiResponse.error(500, "系统异常")`
- 自定义业务错误码枚举 `BizCodeEnum`

## 日志处理

- JSON结构化日志（Logback + JsonLogLayout）
- 统一日志字段：traceId, userId, requestUri, method, costTime, params, result
- 请求拦截器自动生成 traceId 并灌入 MDC

## 核心组件

### mila-framework-starter-common

- `ApiResponse<T>` - 统一响应封装
- `BizException` - 业务异常
- `IBizCode` - 业务码接口
- `ResultCode` - 结果码枚举基类
- `JsonUtil` - JSON工具（基于Hutools）
- `PageUtil` - 分页工具

### mila-framework-starter-redis

- `RedisProperties` - 配置绑定
- `RedisTemplate` 封装
- 分布式Session共享配置

### mila-framework-starter-mybatis

- 配置类（SqlSessionFactory等）
- 通用BaseMapper基类
- 分页插件集成

### mila-framework-starter-satoken

- 登录认证（JWT/Token）
- 权限认证（路由拦截）
- 二级认证
- `SaTokenProperties` 配置绑定

## 使用方式

### 引入依赖

```xml
<dependencies>
    <dependency>
        <groupId>com.kk.mila</groupId>
        <artifactId>mila-framework-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>com.kk.mila</groupId>
        <artifactId>mila-framework-starter-mybatis</artifactId>
    </dependency>
    <dependency>
        <groupId>com.kk.mila</groupId>
        <artifactId>mila-framework-starter-redis</artifactId>
    </dependency>
    <dependency>
        <groupId>com.kk.mila</groupId>
        <artifactId>mila-framework-starter-satoken</artifactId>
    </dependency>
</dependencies>
```

### 配置示例

```yaml
mila:
  web:
    base-package: com.example.api
  mybatis:
    type-aliases-package: com.example.entity
  redis:
    host: localhost
    port: 6379
  satoken:
    token-name: Authorization
    timeout: 7200
```

### 接口开发示例

```java
@RestController
@RequestMapping("/user")
public class UserController {

    @GetMapping("/{id}")
    public ApiResponse<User> getUser(@PathVariable Long id) {
        User user = userService.getById(id);
        return ApiResponse.ok(user);
    }

    @PostMapping
    public ApiResponse<Long> create(@RequestBody @Valid UserCreateDTO dto) {
        Long id = userService.create(dto);
        return ApiResponse.ok(id);
    }
}
```

## 暂不集成

- 数据库版本管理（Flyway/Liquibase）- 团队自行选择
