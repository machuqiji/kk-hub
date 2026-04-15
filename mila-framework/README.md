# mila-framework

Spring Boot 4.0 开发脚手架，面向开源场景。

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
mila-framework/
├── mila-framework-parent/           # 父POM，管理生命周期
├── mila-framework-dependencies/    # 依赖版本锁定
├── mila-framework-starter-common/   # 公共组件
├── mila-framework-starter-web/     # Web starter（REST规范）
├── mila-framework-starter-redis/   # Redis starter
├── mila-framework-starter-mybatis/ # MyBatisFlex starter
└── mila-framework-starter-satoken/ # Sa-Token starter
```

## 快速开始

### 1. 引入依赖

在项目的 `pom.xml` 中添加所需模块依赖：

```xml
<dependencies>
    <!-- Web starter（必需，包含REST规范、异常处理、日志）-->
    <dependency>
        <groupId>com.kk.mila</groupId>
        <artifactId>mila-framework-starter-web</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>

    <!-- MyBatisFlex starter（数据库访问）-->
    <dependency>
        <groupId>com.kk.mila</groupId>
        <artifactId>mila-framework-starter-mybatis</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>

    <!-- Redis starter（缓存 + Session共享）-->
    <dependency>
        <groupId>com.kk.mila</groupId>
        <artifactId>mila-framework-starter-redis</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>

    <!-- Sa-Token starter（认证授权）-->
    <dependency>
        <groupId>com.kk.mila</groupId>
        <artifactId>mila-framework-starter-satoken</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
</dependencies>
```

### 2. 配置文件

在 `application.yml` 中添加配置：

```yaml
server:
  port: 8080

# Web配置
mila:
  web:
    base-package: com.example.api

# 数据库配置
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/yourdb
    username: postgres
    password: yourpassword
    driver-class-name: org.postgresql.Driver

# MyBatis配置
  mybatis:
    type-aliases-package: com.example.entity

# Redis配置
  data:
    redis:
      host: localhost
      port: 6379

# Sa-Token配置（可选）
# mila:
#   satoken:
#     token-name: Authorization
#     timeout: 7200
#     secret-key: your-secret-key
```

### 3. 开发接口

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

## 模块详解

### mila-framework-starter-common

公共组件模块，提供基础工具类。

#### ApiResponse - 统一响应封装

```java
// 成功响应
ApiResponse.ok()
ApiResponse.ok(data)
ApiResponse.ok(data, "操作成功")

// 失败响应
ApiResponse.error(500, "系统异常")
ApiResponse.error(BizCodeEnum.DATA_NOT_FOUND)
```

响应示例：
```json
{
    "code": 0,
    "message": "success",
    "data": { ... },
    "timestamp": 1713206400000
}
```

#### BizException - 业务异常

```java
// 抛出业务异常
throw new BizException(1001, "参数无效");
throw new BizException(ExceptionCode.PARAM_INVALID);
```

#### IBizCode - 业务码接口

```java
public enum UserCodeEnum implements IBizCode {
    USER_NOT_FOUND(2001, "用户不存在"),
    USER_EXIST(2002, "用户已存在");

    private final int code;
    private final String message;

    UserCodeEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() { return code; }

    @Override
    public String getMessage() { return message; }
}
```

#### JsonUtil - JSON工具

```java
String json = JsonUtil.toJson(obj);
User user = JsonUtil.fromJson(json, User.class);
```

#### PageUtil - 分页工具

```java
// 分页结果转换
PageResult<UserDTO> dtoPage = PageUtil.convert(userPage, user -> {
    return new UserDTO(user.getId(), user.getName());
});
```

---

### mila-framework-starter-web

Web增强模块，包含REST规范、全局异常处理、JSON日志。

#### 全自动异常处理

无需手动处理异常，所有异常自动转换为统一响应格式：

| 异常类型 | 响应码 | 说明 |
|---------|--------|------|
| `BizException` | 异常指定的code | 业务异常 |
| `MethodArgumentNotValidException` | 1001 | 参数校验失败 |
| `BindException` | 1001 | 参数绑定失败 |
| `Exception` | 500 | 系统异常 |

#### TraceId请求追踪

请求自动生成TraceId，可在日志中追踪完整请求链路：

```yaml
# 请求头中传入
Authorization: Bearer xxx
traceId: abc123  # 可选，不传则自动生成
```

#### JSON结构化日志

生产环境自动输出JSON格式日志，便于日志收集与分析：

```json
{
    "timestamp": "2024-04-15 10:30:00.000",
    "level": "INFO",
    "logger": "c.k.m.w.filter.TraceIdFilter",
    "message": "request completed",
    "traceId": "abc123",
    "userId": "1001",
    "requestUri": "/api/user/1",
    "method": "GET",
    "costTime": 25
}
```

---

### mila-framework-starter-mybatis

MyBatisFlex ORM增强模块。

#### BaseMapper通用CRUD

继承 `BaseMapper<T>` 即可获得通用CRUD方法：

```java
@Mapper
public interface UserMapper extends BaseMapper<User> {
}

@Service
public class UserService {
    @Autowired
    private UserMapper userMapper;

    public User getById(Long id) {
        return userMapper.selectById(id);
    }

    public List<User> findByStatus(Integer status) {
        return userMapper.selectList(
            QueryWrapper.create()
                .eq(User::getStatus, status)
        );
    }
}
```

#### 配置说明

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `mila.mybatis.type-aliases-package` | 实体类包路径 | - |
| `mila.mybatis.mapper-locations` | Mapper XML位置 | `classpath*:/mapper/**/*.xml` |
| `mila.mybatis.cache-enabled` | 是否启用二级缓存 | `true` |

---

### mila-framework-starter-redis

Redis缓存与Session共享模块。

#### RedisTemplate

自动配置，直接注入使用：

```java
@Service
public class CacheService {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value, Duration.ofHours(1));
    }

    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }
}
```

#### Session共享

自动配置分布式Session，多节点部署时session自动同步：

```yaml
# 无需额外配置，自动生效
spring:
  session:
    store-type: redis
```

#### 配置说明

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `mila.redis.host` | Redis主机 | `localhost` |
| `mila.redis.port` | Redis端口 | `6379` |
| `mila.redis.password` | 密码 | - |
| `mila.redis.database` | 数据库编号 | `0` |
| `mila.redis.timeout` | 连接超时(ms) | `3000` |

---

### mila-framework-starter-satoken

Sa-Token认证授权模块，提供登录认证、权限验证等功能。

#### 登录/登出

```java
@RestController
@RequestMapping("/auth")
public class AuthController {

    @PostMapping("/login")
    public ApiResponse<String> login(@RequestBody LoginDTO dto) {
        // 验证用户密码
        User user = userService.login(dto.getUsername(), dto.getPassword());
        // 登录，生成Token
        StpUtil.login(user.getId());
        return ApiResponse.ok(StpUtil.getTokenValue());
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        StpUtil.logout();
        return ApiResponse.ok();
    }

    @GetMapping("/info")
    public ApiResponse<User> getUserInfo() {
        long userId = StpUtil.getLoginIdAsLong();
        return ApiResponse.ok(userService.getById(userId));
    }
}
```

#### 权限验证

```java
// 登录验证（自动拦截未登录请求）
@SaCheckLogin
@GetMapping("/user/info")
public ApiResponse<User> getUserInfo() { ... }

// 权限验证
@SaCheckPermission("user:delete")
@DeleteMapping("/user/{id}")
public ApiResponse<Void> deleteUser(@PathVariable Long id) { ... }

// 二级认证验证
@SaCheckSafeValue("update-password")
@PostMapping("/user/password")
public ApiResponse<Void> updatePassword() { ... }
```

#### 配置说明

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `mila.satoken.token-name` | Token名称 | `Authorization` |
| `mila.satoken.timeout` | Token有效期(s) | `7200` |
| `mila.satoken.secret-key` | 加密密钥 | - |
| `mila.satoken.is-concurrent` | 是否允许并发登录 | `true` |
| `mila.satoken.is-share` | 是否共享登录 | `true` |
| `mila.satoken.token-style` | Token生成风格 | `uuid` |

---

## 接口规范

### 请求格式

| Content-Type | 说明 |
|--------------|------|
| `application/json` | JSON请求体 |
| `application/x-www-form-urlencoded` | 表单提交 |
| `multipart/form-data` | 文件上传 |

### 响应格式

```json
{
    "code": 0,
    "message": "success",
    "data": { },
    "timestamp": 1713206400000
}
```

| code | 说明 |
|------|------|
| 0 | 成功 |
| -1 | 操作失败 |
| > 0 | 业务错误码 |
| 500 | 系统异常 |

### 分页响应

```json
{
    "code": 0,
    "message": "success",
    "data": {
        "records": [ ],
        "total": 100
    },
    "timestamp": 1713206400000
}
```

---

## 最佳实践

### 1. 定义业务错误码

```java
public enum BizCodeEnum implements IBizCode {
    // 通用错误码 (1001-1999)
    PARAM_INVALID(1001, "参数无效"),
    DATA_NOT_FOUND(1002, "数据不存在"),
    DATA_DUPLICATE(1003, "数据重复"),

    // 业务错误码 (2000+)
    USER_NOT_FOUND(2001, "用户不存在"),
    USER_EXIST(2002, "用户已存在"),
    PASSWORD_ERROR(2003, "密码错误");

    private final int code;
    private final String message;

    BizCodeEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() { return code; }

    @Override
    public String getMessage() { return message; }
}
```

### 2. Service层异常处理

```java
@Service
public class UserService {

    public User getById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(BizCodeEnum.USER_NOT_FOUND);
        }
        return user;
    }
}
```

### 3. 事务管理

```java
@Service
public class UserService {

    @Transactional(rollbackFor = Exception.class)
    public Long create(UserCreateDTO dto) {
        // 验证
        if (userMapper.selectCount(
            QueryWrapper.create().eq(User::getUsername, dto.getUsername())
        ) > 0) {
            throw new BizException(BizCodeEnum.USER_EXIST);
        }
        // 创建
        User user = new User();
        BeanUtil.copyProperties(dto, user);
        userMapper.insert(user);
        return user.getId();
    }
}
```

---

## 构建与安装

```bash
# 编译所有模块
mvn clean compile

# 安装到本地仓库
mvn clean install

# 跳过测试
mvn clean install -DskipTests
```

---

## 注意事项

1. **Java版本**：必须使用 Java 17+
2. **数据库驱动**：默认集成 PostgreSQL 驱动，如需其他数据库请自行添加
3. **版本锁定**：所有依赖版本在 `mila-framework-dependencies` 中统一管理，请勿在子模块中指定版本号
4. **Session共享**：Redis starter启用后自动开启分布式Session，无需额外配置
