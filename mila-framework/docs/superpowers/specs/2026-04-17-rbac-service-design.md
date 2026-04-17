# RBAC 基础服务设计文档

> 日期：2026-04-17
> 状态：已确认
> 基于：mila-framework 1.0.0-SNAPSHOT (Spring Boot 4.0)

## 1. 概述

在 mila-framework 基础上构建 RBAC（基于角色的访问控制）基础服务，支持：
- **双模式部署**：作为 jar 包 in-process 嵌入其他服务，或作为独立微服务提供 REST API
- **多租户架构**：共享数据库 + 行级隔离（tenant_id 列）
- **RBAC + 资源权限**：用户 → 角色 → 权限，权限关联资源（菜单/按钮/API）
- **数据范围权限**：支持 ALL / SELF / DEPT / DEPT_AND_SUB / CUSTOM 五种数据范围
- **混合租户上下文**：外部请求从 token 解析，内部调用从请求头传播

## 2. 模块结构

### 2.1 Maven 模块

采用双模块架构：

```
mila-framework/
├── mila-framework-starter-rbac/        ← 核心模块（jar）
│   └── com.kk.mila.rbac
│       ├── config/                     ← Auto-configuration + Properties
│       ├── context/                    ← 多租户上下文
│       ├── domain/                     ← 领域模型实体
│       ├── mapper/                     ← MyBatisFlex Mapper 接口
│       ├── service/                    ← 服务接口 + 实现
│       ├── interceptor/               ← 数据权限拦截器
│       └── controller/                ← REST Controller（条件注册）
│
└── mila-service-rbac/                  ← 独立微服务（thin wrapper）
    └── com.kk.mila.rbac.server
        └── RbacApplication.java       ← @SpringBootApplication
```

### 2.2 双模式部署

| 模式 | 引入方式 | API 暴露 | 配置 |
|------|----------|----------|------|
| In-process | 依赖 `starter-rbac` | `mila.rbac.api-enabled=true` 控制 | 内嵌数据源 |
| 独立微服务 | 启动 `mila-service-rbac` | Controller 默认注册 | 独立数据源 + 端口 |

### 2.3 依赖关系

```
mila-framework-starter-rbac 依赖：
  ├── mila-framework-starter-common     (ApiResponse, BizException)
  ├── mila-framework-starter-mybatis    (BaseMapper, MyBatisFlex)
  ├── mila-framework-starter-satoken    (Sa-Token 鉴权)
  ├── mila-framework-starter-web        (REST 基础, 仅 api-enabled=true 时需要)
  └── mila-framework-starter-redis      (可选, 权限缓存 L2)

mila-service-rbac 依赖：
  └── mila-framework-starter-rbac       (全部核心逻辑)
```

### 2.4 配置属性

```yaml
mila:
  rbac:
    enabled: true                    # 总开关
    api-enabled: false               # 是否暴露 REST API（in-process 默认 false，微服务默认 true）
    tenant:
      enabled: true                  # 多租户开关
      ignore-urls:                   # 租户 ID 校验白名单
        - /auth/**
        - /public/**
    data-scope:
      enabled: true                  # 数据权限开关
```

## 3. 领域模型与数据库

### 3.1 实体关系

```
Tenant ────────────────────────────────────────── (租户)
  │
  ├── User ──┐                                  (用户)
  │           │
  │     UserRole ── Role ──┐                    (用户-角色关联 / 角色)
  │                         │
  │                   RolePermission ── Permission ── Resource  (角色-权限关联 / 权限 / 资源)
  │
  └── DataScopeRule                              (数据范围规则)
```

### 3.2 数据库表设计

表前缀统一为 `sys_`。

| 表名 | 说明 | 关键字段 |
|------|------|----------|
| `sys_tenant` | 租户 | `id`, `code`(唯一), `name`, `status`, `expired_at` |
| `sys_user` | 用户 | `id`, `tenant_id`, `username`, `password`, `status` |
| `sys_role` | 角色 | `id`, `tenant_id`, `code`, `name`, `type`(SYSTEM/CUSTOM) |
| `sys_permission` | 权限 | `id`, `tenant_id`, `code`, `name` |
| `sys_resource` | 资源 | `id`, `tenant_id`, `code`, `name`, `type`(MENU/BUTTON/API), `parent_id` |
| `sys_user_role` | 用户-角色关联 | `id`, `tenant_id`, `user_id`, `role_id` |
| `sys_role_permission` | 角色-权限关联 | `id`, `tenant_id`, `role_id`, `permission_id` |
| `sys_permission_resource` | 权限-资源关联 | `id`, `tenant_id`, `permission_id`, `resource_id` |
| `sys_data_scope_rule` | 数据范围规则 | `id`, `tenant_id`, `role_id`, `scope_type`, `scope_config`(JSON) |

### 3.3 设计约束

- 所有业务表都有 `tenant_id` 字段，用于行级隔离
- `sys_tenant` 自身无 `tenant_id`，通过独立管理通道访问
- `scope_config` 为 JSON 类型，存储自定义数据范围的部门 ID 列表等
- `role.type` 区分系统内置角色（不可删除/修改）和租户自定义角色
- `resource.type` 支持 MENU / BUTTON / API 三种资源类型

## 4. 多租户基础设施

### 4.1 租户上下文传播链

```
外部请求 → Sa-Token 解析 loginId → 从 loginId 获取 tenantId → TenantContext.set(tenantId)
内部调用 → 请求头 X-Tenant-ID → TenantContext.set(tenantId)
                                                    ↓
                                          MyBatisFlex TenantInterceptor
                                          自动在 SQL 中追加 tenant_id 条件
```

### 4.2 核心组件

| 组件 | 位置 | 职责 |
|------|------|------|
| `TenantContext` | `context/` | ThreadLocal 持有当前 tenantId，提供 `get()/set()/clear()` |
| `TenantFilter` | `context/` | Servlet Filter，从 token 或请求头解析 tenantId |
| `TenantInterceptor` | `context/` | MyBatisFlex 拦截器，自动注入 `tenant_id = ?` 条件 |
| `TenantManager` | `service/` | 租户 CRUD + 状态管理（启用/禁用/过期检查） |

### 4.3 混合模式解析优先级

1. 请求头 `X-Tenant-ID` 存在 → 直接使用（内部服务调用）
2. Sa-Token 已登录 → 从用户信息中获取 tenantId（外部请求）
3. 两者都不存在 → 白名单 URL 放行，否则拒绝

### 4.4 MyBatisFlex 租户拦截器

- 利用 MyBatisFlex 的 `Interceptor` 机制，在 SQL 执行前追加 `AND tenant_id = ?`
- 忽略 `sys_tenant` 表（租户表本身不需要租户过滤）
- 支持 `@TenantIgnore` 注解标记不需要租户隔离的 Mapper 方法

## 5. 权限校验与数据权限

### 5.1 功能权限校验流程

```
请求 → Sa-Token 拦截器 → StpUtil.checkLogin()
                          → 检查用户角色 → 检查角色权限 → 检查权限对应的资源
                          → 匹配当前请求的 API 资源 → 通过/拒绝
```

### 5.2 核心组件

| 组件 | 职责 |
|------|------|
| `RbacService` | 权限校验核心：`hasPermission(userId, resourceCode)`, `getPermissions(userId)`, `getRoles(userId)` |
| `ResourceRegistry` | 资源注册表：扫描 `@RequirePermission` 注解自动注册 API 资源 |
| `@RequirePermission` | 注解：标记 Controller 方法所需权限，如 `@RequirePermission("user:delete")` |
| `RbacInterceptor` | Sa-Token 路由拦截器：校验当前用户是否拥有请求所需权限 |

### 5.3 权限缓存策略

- **L1**：进程内 Caffeine 缓存（TTL 5min）
- **L2**：Redis 缓存（可选，依赖 starter-redis）
- 缓存 key：`rbac:perm:{tenantId}:{userId}` → `Set<permissionCode>`
- 角色/权限变更时主动清除缓存

### 5.4 数据权限（DataScope）

```java
@DataScope(scopeType = ScopeType.DEPT_AND_SUB)
public List<Order> listOrders() {
    // 拦截器自动追加: WHERE dept_id IN (当前用户部门及子部门ID)
}
```

| ScopeType | SQL 追加条件 | 说明 |
|-----------|-------------|------|
| `ALL` | 无限制 | 查看所有数据 |
| `SELF` | `AND creator_id = {currentUserId}` | 仅自己创建的 |
| `DEPT` | `AND dept_id = {currentUserDeptId}` | 本部门 |
| `DEPT_AND_SUB` | `AND dept_id IN ({deptId及子部门})` | 本部门及子部门 |
| `CUSTOM` | `AND dept_id IN ({自定义部门ID列表})` | 自定义范围 |

实现方式：MyBatisFlex Interceptor 在 SQL 执行前，根据当前用户角色的 `DataScopeRule` 追加过滤条件。`@DataScope` 注解声明期望的数据范围类型，拦截器取角色配置与注解声明的**交集**。

## 6. REST API 设计

所有 API 前缀：`/api/v1/rbac`

### 6.1 租户管理（超管权限，不受租户过滤）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/tenants` | 创建租户 |
| GET | `/tenants/{id}` | 查询租户 |
| PUT | `/tenants/{id}` | 更新租户 |
| PATCH | `/tenants/{id}/status` | 启用/禁用租户 |

### 6.2 用户管理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/users` | 创建用户 |
| GET | `/users/{id}` | 查询用户 |
| GET | `/users` | 分页查询用户列表 |
| PUT | `/users/{id}` | 更新用户 |
| DELETE | `/users/{id}` | 删除用户 |
| POST | `/users/{id}/roles` | 分配角色 |
| GET | `/users/{id}/permissions` | 查询用户所有权限 |

### 6.3 角色管理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/roles` | 创建角色 |
| GET | `/roles` | 查询角色列表 |
| PUT | `/roles/{id}` | 更新角色 |
| DELETE | `/roles/{id}` | 删除角色 |
| POST | `/roles/{id}/permissions` | 分配权限 |
| PUT | `/roles/{id}/data-scope` | 设置数据范围 |

### 6.4 权限与资源管理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/permissions` | 创建权限 |
| GET | `/permissions` | 查询权限列表 |
| POST | `/resources` | 创建资源 |
| GET | `/resources/tree` | 查询资源树 |
| POST | `/permissions/{id}/resources` | 关联权限与资源 |

### 6.5 鉴权内部接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/check?userId=&permission=` | 校验用户是否有某权限 |
| GET | `/users/{id}/permissions/codes` | 获取用户权限码列表 |

## 7. Auto-Configuration 与集成

### 7.1 RbacAutoConfiguration

```java
@Configuration
@EnableConfigurationProperties(RbacProperties.class)
@ConditionalOnProperty(prefix = "mila.rbac", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RbacAutoConfiguration {

    // 1. 多租户基础设施（mila.rbac.tenant.enabled=true）
    TenantFilter        // Servlet Filter
    TenantInterceptor   // MyBatisFlex SQL 拦截器

    // 2. 权限校验（Sa-Token 在 classpath）
    RbacService         // 权限校验核心
    RbacInterceptor     // Sa-Token 路由拦截器
    PermissionCache     // Caffeine + Redis 二级缓存

    // 3. 数据权限（mila.rbac.data-scope.enabled=true）
    DataScopeInterceptor // MyBatisFlex SQL 拦截器

    // 4. REST API（mila.rbac.api-enabled=true）
    TenantController, UserController, RoleController,
    PermissionController, ResourceController, AuthCheckController
}
```

### 7.2 与 Sa-Token 的集成

- Sa-Token 负责**认证**（登录/登出/token 管理）
- RBAC starter 负责**授权**（角色/权限/资源校验）
- `RbacInterceptor` 在 `StpUtil.checkLogin()` 之后执行权限校验
- 用户登录时，将 `tenantId` 存入 Sa-Token session

### 7.3 Auto-Configuration 注册

文件：`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

```
com.kk.mila.rbac.config.RbacAutoConfiguration
```
