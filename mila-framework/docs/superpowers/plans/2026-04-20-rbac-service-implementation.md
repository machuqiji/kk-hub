# RBAC 基础服务 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 mila-framework 上构建 RBAC 基础服务，支持多租户、角色权限、数据范围权限，可 in-process 嵌入或独立微服务部署

**Architecture:** 双模块架构 — `mila-framework-starter-rbac`（核心 jar）+ `mila-service-rbac`（thin wrapper 独立微服务）。多租户通过 MyBatisFlex 内置 TenantManager + TenantFactory 实现；权限校验基于 Sa-Token + 自定义 RbacInterceptor；数据权限通过 MyBatisFlex AOP 拦截器追加 SQL 条件

**Tech Stack:** Spring Boot 4.0, Java 17, MyBatisFlex 1.11.6 (TenantManager/TenantFactory), Sa-Token 1.37.0, Caffeine 3.1.8 (L1 cache), Redis (L2 cache, optional), PostgreSQL

---

## File Structure

```
mila-framework-starter-rbac/
├── pom.xml
└── src/main/java/com/kk/mila/rbac/
    ├── config/
    │   ├── RbacAutoConfiguration.java      ← Auto-configuration 入口
    │   └── RbacProperties.java             ← 配置属性
    ├── context/
    │   ├── TenantContext.java               ← ThreadLocal 租户上下文
    │   ├── TenantFilter.java               ← Servlet Filter 解析 tenantId
    │   └── TenantFactoryImpl.java          ← MyBatisFlex TenantFactory 实现
    ├── domain/
    │   ├── Tenant.java                     ← 租户实体
    │   ├── User.java                       ← 用户实体
    │   ├── Role.java                       ← 角色实体
    │   ├── Permission.java                 ← 权限实体
    │   ├── Resource.java                   ← 资源实体
    │   ├── UserRole.java                   ← 用户-角色关联
    │   ├── RolePermission.java             ← 角色-权限关联
    │   ├── PermissionResource.java         ← 权限-资源关联
    │   └── DataScopeRule.java              ← 数据范围规则
    ├── mapper/
    │   ├── TenantMapper.java
    │   ├── UserMapper.java
    │   ├── RoleMapper.java
    │   ├── PermissionMapper.java
    │   ├── ResourceMapper.java
    │   ├── UserRoleMapper.java
    │   ├── RolePermissionMapper.java
    │   ├── PermissionResourceMapper.java
    │   └── DataScopeRuleMapper.java
    ├── service/
    │   ├── TenantManager.java              ← 租户 CRUD + 状态管理
    │   ├── RbacService.java                ← 权限校验核心
    │   └── PermissionCache.java            ← Caffeine + Redis 二级缓存
    ├── interceptor/
    │   ├── RbacInterceptor.java            ← Sa-Token 路由拦截器
    │   ├── DataScopeInterceptor.java       ← 数据权限 AOP 拦截器
    │   └── TenantIgnoreAspect.java         ← @TenantIgnore AOP 切面
    ├── annotation/
    │   ├── TenantIgnore.java               ← 忽略租户过滤
    │   ├── RequirePermission.java          ← 声明所需权限
    │   └── DataScope.java                  ← 声明数据范围
    └── controller/
        ├── TenantController.java
        ├── UserController.java
        ├── RoleController.java
        ├── PermissionController.java
        ├── ResourceController.java
        └── AuthCheckController.java
└── src/main/resources/
    ├── META-INF/spring/
    │   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
    └── db/migration/
        └── V1__rbac_init.sql               ← DDL 建表脚本

mila-service-rbac/
├── pom.xml
└── src/main/java/com/kk/mila/rbac/server/
    └── RbacApplication.java
└── src/main/resources/
    └── application.yml
```

---

## Task 1: Maven 模块脚手架

**Files:**
- Modify: `pom.xml` (root aggregator, add 2 modules)
- Modify: `mila-framework-dependencies/pom.xml` (add caffeine version if missing)
- Create: `mila-framework-starter-rbac/pom.xml`
- Create: `mila-service-rbac/pom.xml`

- [ ] **Step 1: 在根 pom.xml 添加两个模块**

在 `<modules>` 列表末尾追加：

```xml
<module>mila-framework-starter-rbac</module>
<module>mila-service-rbac</module>
```

- [ ] **Step 2: 在 mila-framework-dependencies/pom.xml 添加 caffeine 版本管理**

在 `<properties>` 中追加：

```xml
<caffeine.version>3.1.8</caffeine.version>
```

在 `<dependencyManagement><dependencies>` 中追加：

```xml
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
    <version>${caffeine.version}</version>
</dependency>
```

- [ ] **Step 3: 创建 mila-framework-starter-rbac/pom.xml**

> **注意**：parent 为 `mila-framework-dependencies`（与现有 starter 一致），不是 `mila-framework-parent`。

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

    <artifactId>mila-framework-starter-rbac</artifactId>
    <name>mila-framework-starter-rbac</name>
    <description>RBAC 基础服务 - 多租户、角色权限、数据范围</description>

    <dependencies>
        <dependency>
            <groupId>com.kk.mila</groupId>
            <artifactId>mila-framework-starter-common</artifactId>
        </dependency>
        <dependency>
            <groupId>com.kk.mila</groupId>
            <artifactId>mila-framework-starter-mybatis</artifactId>
        </dependency>
        <dependency>
            <groupId>com.kk.mila</groupId>
            <artifactId>mila-framework-starter-satoken</artifactId>
        </dependency>
        <dependency>
            <groupId>com.kk.mila</groupId>
            <artifactId>mila-framework-starter-web</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>com.kk.mila</groupId>
            <artifactId>mila-framework-starter-redis</artifactId>
            <optional>true</optional>
        </dependency>
        <dependency>
            <groupId>com.github.ben-manes.caffeine</groupId>
            <artifactId>caffeine</artifactId>
        </dependency>
        <dependency>
            <groupId>com.mybatis-flex</groupId>
            <artifactId>mybatis-flex-processor</artifactId>
            <version>1.11.6</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 4: 创建 mila-service-rbac/pom.xml**

> **注意**：parent 为 `mila-framework-dependencies`（与现有 starter 一致）。

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

    <artifactId>mila-service-rbac</artifactId>
    <name>mila-service-rbac</name>
    <description>RBAC 独立微服务</description>

    <dependencies>
        <dependency>
            <groupId>com.kk.mila</groupId>
            <artifactId>mila-framework-starter-rbac</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 5: 在 mila-framework-parent/pom.xml 添加两个模块**

> **注意**：`mila-framework-parent/pom.xml` 也有 `<modules>` 列表，需同步添加。

在 `mila-framework-parent/pom.xml` 的 `<modules>` 列表末尾追加：

```xml
<module>mila-framework-starter-rbac</module>
<module>mila-service-rbac</module>
```

- [ ] **Step 6: 验证编译**

Run: `mvn compile -pl mila-framework-starter-rbac,mila-service-rbac -am`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add pom.xml mila-framework-dependencies/pom.xml mila-framework-parent/pom.xml mila-framework-starter-rbac/ mila-service-rbac/
git commit -m "feat(rbac): add maven module scaffolding for starter-rbac and service-rbac"
```

---

## Task 2: 配置属性与异常码

**Files:**
- Create: `mila-framework-starter-rbac/src/main/java/com/kk/mila/rbac/config/RbacProperties.java`
- Create: `mila-framework-starter-rbac/src/main/java/com/kk/mila/rbac/config/RbacExceptionCode.java`

- [ ] **Step 1: 创建 RbacProperties**

```java
package com.kk.mila.rbac.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "mila.rbac")
public class RbacProperties {

    private boolean enabled = true;
    private boolean apiEnabled = false;

    private Tenant tenant = new Tenant();
    private DataScope dataScope = new DataScope();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isApiEnabled() { return apiEnabled; }
    public void setApiEnabled(boolean apiEnabled) { this.apiEnabled = apiEnabled; }

    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }

    public DataScope getDataScope() { return dataScope; }
    public void setDataScope(DataScope dataScope) { this.dataScope = dataScope; }

    public static class Tenant {
        private boolean enabled = true;
        private List<String> ignoreUrls = new ArrayList<>(List.of("/auth/**", "/public/**"));

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public List<String> getIgnoreUrls() { return ignoreUrls; }
        public void setIgnoreUrls(List<String> ignoreUrls) { this.ignoreUrls = ignoreUrls; }
    }

    public static class DataScope {
        private boolean enabled = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
```

- [ ] **Step 2: 创建 RbacExceptionCode**

```java
package com.kk.mila.rbac.config;

import com.kk.mila.common.core.exception.IBizCode;

public enum RbacExceptionCode implements IBizCode {

    TENANT_NOT_FOUND(2001, "租户不存在"),
    TENANT_DISABLED(2002, "租户已禁用"),
    TENANT_EXPIRED(2003, "租户已过期"),
    TENANT_ID_MISSING(2004, "租户ID缺失"),
    USER_NOT_FOUND(2010, "用户不存在"),
    ROLE_NOT_FOUND(2020, "角色不存在"),
    ROLE_IS_SYSTEM(2021, "系统角色不可修改/删除"),
    PERMISSION_DENIED(2030, "权限不足"),
    PERMISSION_NOT_FOUND(2031, "权限不存在"),
    RESOURCE_NOT_FOUND(2040, "资源不存在");

    private final int code;
    private final String message;

    RbacExceptionCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public int getCode() { return code; }

    @Override
    public String getMessage() { return message; }
}
```

- [ ] **Step 3: 验证编译**

Run: `mvn compile -pl mila-framework-starter-rbac`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add mila-framework-starter-rbac/src/
git commit -m "feat(rbac): add RbacProperties and RbacExceptionCode"
```

---

## Task 3: 领域实体

**Files:**
- Create: `domain/Tenant.java`, `domain/User.java`, `domain/Role.java`, `domain/Permission.java`, `domain/Resource.java`, `domain/UserRole.java`, `domain/RolePermission.java`, `domain/PermissionResource.java`, `domain/DataScopeRule.java`

所有实体放在 `com.kk.mila.rbac.domain` 包下，使用 MyBatisFlex 注解。

- [ ] **Step 1: 创建 Tenant 实体**

```java
package com.kk.mila.rbac.domain;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import java.time.LocalDateTime;

@Table(value = "sys_tenant")
public class Tenant {

    @Id(keyType = KeyType.Auto)
    private Long id;
    private String code;
    private String name;
    private Integer status;
    private LocalDateTime expiredAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getExpiredAt() { return expiredAt; }
    public void setExpiredAt(LocalDateTime expiredAt) { this.expiredAt = expiredAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 2: 创建 User 实体**

```java
package com.kk.mila.rbac.domain;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import java.time.LocalDateTime;

@Table(value = "sys_user")
public class User {

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column(tenantId = true)
    private Long tenantId;

    private String username;
    private String password;
    private String nickname;
    private Long deptId;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public Long getDeptId() { return deptId; }
    public void setDeptId(Long deptId) { this.deptId = deptId; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 3: 创建 Role 实体**

```java
package com.kk.mila.rbac.domain;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import java.time.LocalDateTime;

@Table(value = "sys_role")
public class Role {

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column(tenantId = true)
    private Long tenantId;

    private String code;
    private String name;
    private String type;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 4: 创建 Permission 实体**

```java
package com.kk.mila.rbac.domain;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import java.time.LocalDateTime;

@Table(value = "sys_permission")
public class Permission {

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column(tenantId = true)
    private Long tenantId;

    private String code;
    private String name;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 5: 创建 Resource 实体**

```java
package com.kk.mila.rbac.domain;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import java.time.LocalDateTime;
import java.util.List;

@Table(value = "sys_resource")
public class Resource {

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column(tenantId = true)
    private Long tenantId;

    private String code;
    private String name;
    private String type;
    private Long parentId;
    private Integer sort;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 树结构返回用，不映射数据库
    @Column(ignore = true)
    private List<Resource> children;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public List<Resource> getChildren() { return children; }
    public void setChildren(List<Resource> children) { this.children = children; }
}
```

- [ ] **Step 6: 创建关联实体 UserRole, RolePermission, PermissionResource**

```java
package com.kk.mila.rbac.domain;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

@Table(value = "sys_user_role")
public class UserRole {

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column(tenantId = true)
    private Long tenantId;

    private Long userId;
    private Long roleId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
}
```

```java
package com.kk.mila.rbac.domain;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

@Table(value = "sys_role_permission")
public class RolePermission {

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column(tenantId = true)
    private Long tenantId;

    private Long roleId;
    private Long permissionId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
    public Long getPermissionId() { return permissionId; }
    public void setPermissionId(Long permissionId) { this.permissionId = permissionId; }
}
```

```java
package com.kk.mila.rbac.domain;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

@Table(value = "sys_permission_resource")
public class PermissionResource {

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column(tenantId = true)
    private Long tenantId;

    private Long permissionId;
    private Long resourceId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getPermissionId() { return permissionId; }
    public void setPermissionId(Long permissionId) { this.permissionId = permissionId; }
    public Long getResourceId() { return resourceId; }
    public void setResourceId(Long resourceId) { this.resourceId = resourceId; }
}
```

- [ ] **Step 7: 创建 DataScopeRule 实体**

```java
package com.kk.mila.rbac.domain;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import java.time.LocalDateTime;

@Table(value = "sys_data_scope_rule")
public class DataScopeRule {

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column(tenantId = true)
    private Long tenantId;

    private Long roleId;
    private String scopeType;
    private String scopeConfig;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getRoleId() { return roleId; }
    public void setRoleId(Long roleId) { this.roleId = roleId; }
    public String getScopeType() { return scopeType; }
    public void setScopeType(String scopeType) { this.scopeType = scopeType; }
    public String getScopeConfig() { return scopeConfig; }
    public void setScopeConfig(String scopeConfig) { this.scopeConfig = scopeConfig; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
```

- [ ] **Step 8: 验证编译**

Run: `mvn compile -pl mila-framework-starter-rbac`
Expected: BUILD SUCCESS

- [ ] **Step 9: Commit**

```bash
git add mila-framework-starter-rbac/src/main/java/com/kk/mila/rbac/domain/
git commit -m "feat(rbac): add domain entities with MyBatisFlex annotations"
```

---

## Task 4: Mapper 接口

**Files:**
- Create: 9 个 Mapper 接口在 `com.kk.mila.rbac.mapper` 包下

- [ ] **Step 1: 创建所有 Mapper 接口**

```java
package com.kk.mila.rbac.mapper;

import com.kk.mila.rbac.domain.Tenant;
import com.mybatisflex.core.BaseMapper;

public interface TenantMapper extends BaseMapper<Tenant> {}
```

```java
package com.kk.mila.rbac.mapper;

import com.kk.mila.rbac.domain.User;
import com.mybatisflex.core.BaseMapper;

public interface UserMapper extends BaseMapper<User> {}
```

```java
package com.kk.mila.rbac.mapper;

import com.kk.mila.rbac.domain.Role;
import com.mybatisflex.core.BaseMapper;

public interface RoleMapper extends BaseMapper<Role> {}
```

```java
package com.kk.mila.rbac.mapper;

import com.kk.mila.rbac.domain.Permission;
import com.mybatisflex.core.BaseMapper;

public interface PermissionMapper extends BaseMapper<Permission> {}
```

```java
package com.kk.mila.rbac.mapper;

import com.kk.mila.rbac.domain.Resource;
import com.mybatisflex.core.BaseMapper;

public interface ResourceMapper extends BaseMapper<Resource> {}
```

```java
package com.kk.mila.rbac.mapper;

import com.kk.mila.rbac.domain.UserRole;
import com.mybatisflex.core.BaseMapper;

public interface UserRoleMapper extends BaseMapper<UserRole> {}
```

```java
package com.kk.mila.rbac.mapper;

import com.kk.mila.rbac.domain.RolePermission;
import com.mybatisflex.core.BaseMapper;

public interface RolePermissionMapper extends BaseMapper<RolePermission> {}
```

```java
package com.kk.mila.rbac.mapper;

import com.kk.mila.rbac.domain.PermissionResource;
import com.mybatisflex.core.BaseMapper;

public interface PermissionResourceMapper extends BaseMapper<PermissionResource> {}
```

```java
package com.kk.mila.rbac.mapper;

import com.kk.mila.rbac.domain.DataScopeRule;
import com.mybatisflex.core.BaseMapper;

public interface DataScopeRuleMapper extends BaseMapper<DataScopeRule> {}
```

- [ ] **Step 2: 验证编译**

Run: `mvn compile -pl mila-framework-starter-rbac`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add mila-framework-starter-rbac/src/main/java/com/kk/mila/rbac/mapper/
git commit -m "feat(rbac): add mapper interfaces for all domain entities"
```

---

## Task 5: 多租户上下文基础设施

**Files:**
- Create: `context/TenantContext.java`
- Create: `context/TenantFilter.java`
- Create: `context/TenantFactoryImpl.java`

- [ ] **Step 1: 创建 TenantContext**

```java
package com.kk.mila.rbac.context;

public class TenantContext {

    private static final ThreadLocal<Long> TENANT_ID = new ThreadLocal<>();

    public static void set(Long tenantId) {
        TENANT_ID.set(tenantId);
    }

    public static Long get() {
        return TENANT_ID.get();
    }

    public static void clear() {
        TENANT_ID.remove();
    }
}
```

- [ ] **Step 2: 创建 TenantFilter**

从请求头或 Sa-Token session 解析 tenantId，设置到 TenantContext 和 RequestAttribute。

> **注意**：`RequestContextHolder.getRequestAttributes()` 可能为 null（异步请求等），需做 null 检查。

```java
package com.kk.mila.rbac.context;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.io.IOException;

public class TenantFilter implements Filter {

    private static final String TENANT_HEADER = "X-Tenant-ID";
    private static final String TENANT_ATTR = "tenantId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        Long tenantId = resolveTenantId(httpRequest);

        if (tenantId != null) {
            TenantContext.set(tenantId);
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                attributes.setAttribute(TENANT_ATTR, tenantId, RequestAttributes.SCOPE_REQUEST);
            }
        }

        try {
            chain.doFilter(httpRequest, response);
        } finally {
            TenantContext.clear();
        }
    }

    private Long resolveTenantId(HttpServletRequest request) {
        // 优先级1: 请求头 X-Tenant-ID（内部服务调用）
        String headerTenantId = request.getHeader(TENANT_HEADER);
        if (headerTenantId != null && !headerTenantId.isEmpty()) {
            return Long.parseLong(headerTenantId);
        }

        // 优先级2: Sa-Token session（外部请求）
        try {
            if (StpUtil.isLogin()) {
                Object tenantIdObj = StpUtil.getSession().get(TENANT_ATTR);
                if (tenantIdObj instanceof Long) {
                    return (Long) tenantIdObj;
                }
            }
        } catch (Exception ignored) {
            // Sa-Token 未登录或 session 不可用，忽略
        }

        return null;
    }
}
```

- [ ] **Step 3: 创建 TenantFactoryImpl**

MyBatisFlex 的 `TenantFactory` 实现，从 RequestAttribute 获取 tenantId。

> **注意**：覆写 `getTenantIds(String tableName)`（推荐 API），而非已废弃的无参 `getTenantIds()`。

```java
package com.kk.mila.rbac.context;

import com.mybatisflex.core.tenant.TenantFactory;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

public class TenantFactoryImpl implements TenantFactory {

    private static final String TENANT_ATTR = "tenantId";

    @Override
    public Object[] getTenantIds(String tableName) {
        // 优先从 TenantContext (ThreadLocal) 获取，支持非 Web 场景
        Long tenantId = TenantContext.get();
        if (tenantId != null) {
            return new Object[]{tenantId};
        }

        // 回退到 RequestAttribute
        try {
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                Object attrTenantId = attributes.getAttribute(TENANT_ATTR, RequestAttributes.SCOPE_REQUEST);
                if (attrTenantId instanceof Long) {
                    return new Object[]{attrTenantId};
                }
            }
        } catch (Exception ignored) {
        }

        return new Object[]{};
    }
}
```

- [ ] **Step 4: 验证编译**

Run: `mvn compile -pl mila-framework-starter-rbac`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add mila-framework-starter-rbac/src/main/java/com/kk/mila/rbac/context/
git commit -m "feat(rbac): add TenantContext, TenantFilter, and TenantFactoryImpl"
```

---

## Task 6: 注解定义

**Files:**
- Create: `annotation/TenantIgnore.java`
- Create: `annotation/RequirePermission.java`
- Create: `annotation/DataScope.java`

- [ ] **Step 1: 创建 TenantIgnore 注解**

标记不需要租户隔离的 Mapper 方法或 Service 方法。

```java
package com.kk.mila.rbac.annotation;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface TenantIgnore {
}
```

- [ ] **Step 2: 创建 RequirePermission 注解**

标记 Controller 方法所需权限。

```java
package com.kk.mila.rbac.annotation;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequirePermission {
    String value();
}
```

- [ ] **Step 3: 创建 DataScope 注解**

声明方法的数据范围类型。

```java
package com.kk.mila.rbac.annotation;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataScope {

    ScopeType value() default ScopeType.DEPT_AND_SUB;

    enum ScopeType {
        ALL,
        SELF,
        DEPT,
        DEPT_AND_SUB,
        CUSTOM
    }
}
```

- [ ] **Step 4: 创建 TenantIgnoreAspect**

AOP 切面，拦截 `@TenantIgnore` 注解的方法，用 `TenantManager.ignoreTenantCondition()` 包裹。

```java
package com.kk.mila.rbac.interceptor;

import com.kk.mila.rbac.annotation.TenantIgnore;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

@Aspect
public class TenantIgnoreAspect {

    @Around("@annotation(tenantIgnore)")
    public Object around(ProceedingJoinPoint joinPoint, TenantIgnore tenantIgnore) throws Throwable {
        com.mybatisflex.core.tenant.TenantManager.ignoreTenantCondition();
        try {
            return joinPoint.proceed();
        } finally {
            com.mybatisflex.core.tenant.TenantManager.restoreTenantCondition();
        }
    }
}
```

- [ ] **Step 5: 验证编译**

Run: `mvn compile -pl mila-framework-starter-rbac`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add mila-framework-starter-rbac/src/main/java/com/kk/mila/rbac/annotation/
git add mila-framework-starter-rbac/src/main/java/com/kk/mila/rbac/interceptor/TenantIgnoreAspect.java
git commit -m "feat(rbac): add TenantIgnore, RequirePermission, DataScope annotations and TenantIgnoreAspect"
```

---

## Task 7: 服务层 — TenantManager

**Files:**
- Create: `service/TenantManager.java`

- [ ] **Step 1: 创建 TenantManager**

租户 CRUD + 状态管理（启用/禁用/过期检查）。使用 `TenantManager.ignoreTenantCondition()` 访问 sys_tenant 表。

```java
package com.kk.mila.rbac.service;

import com.kk.mila.common.core.exception.BizException;
import com.kk.mila.rbac.config.RbacExceptionCode;
import com.kk.mila.rbac.domain.Tenant;
import com.kk.mila.rbac.mapper.TenantMapper;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.tenant.TenantManager;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static com.kk.mila.rbac.domain.table.TenantTableDef.TENANT;

@Service
public class TenantManager {

    private final TenantMapper tenantMapper;

    public TenantManager(TenantMapper tenantMapper) {
        this.tenantMapper = tenantMapper;
    }

    public Tenant create(Tenant tenant) {
        tenant.setStatus(1);
        tenant.setCreatedAt(LocalDateTime.now());
        tenant.setUpdatedAt(LocalDateTime.now());
        tenantMapper.insert(tenant);
        return tenant;
    }

    public Tenant getById(Long id) {
        return TenantManager.withoutTenantCondition(() -> tenantMapper.selectOneById(id));
    }

    public Tenant update(Tenant tenant) {
        tenant.setUpdatedAt(LocalDateTime.now());
        TenantManager.withoutTenantCondition(() -> {
            tenantMapper.update(tenant);
            return null;
        });
        return tenant;
    }

    public void enable(Long id) {
        updateStatus(id, 1);
    }

    public void disable(Long id) {
        updateStatus(id, 0);
    }

    public void validateTenant(Long tenantId) {
        Tenant tenant = TenantManager.withoutTenantCondition(() -> tenantMapper.selectOneById(tenantId));
        if (tenant == null) {
            throw new BizException(RbacExceptionCode.TENANT_NOT_FOUND);
        }
        if (tenant.getStatus() == null || tenant.getStatus() == 0) {
            throw new BizException(RbacExceptionCode.TENANT_DISABLED);
        }
        if (tenant.getExpiredAt() != null && tenant.getExpiredAt().isBefore(LocalDateTime.now())) {
            throw new BizException(RbacExceptionCode.TENANT_EXPIRED);
        }
    }

    private void updateStatus(Long id, int status) {
        TenantManager.withoutTenantCondition(() -> {
            Tenant tenant = new Tenant();
            tenant.setId(id);
            tenant.setStatus(status);
            tenant.setUpdatedAt(LocalDateTime.now());
            tenantMapper.update(tenant);
            return null;
        });
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `mvn compile -pl mila-framework-starter-rbac`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add mila-framework-starter-rbac/src/main/java/com/kk/mila/rbac/service/TenantManager.java
git commit -m "feat(rbac): add TenantManager service with CRUD and status management"
```

---

## Task 8: 服务层 — PermissionCache

**Files:**
- Create: `service/PermissionCache.java`

- [ ] **Step 1: 创建 PermissionCache**

Caffeine L1 + Redis L2 二级缓存。缓存 key 格式：`rbac:perm:{tenantId}:{userId}` → `Set<String>`

> **注意**：使用 `RedisTemplate<String, Object>`（与 Redis starter 注册的 bean 一致），而非 `StringRedisTemplate`（Redis starter 未注册）。Redis L2 使用 Jackson JSON 序列化存储 `Set<String>`。

```java
package com.kk.mila.rbac.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class PermissionCache {

    private static final String KEY_PREFIX = "rbac:perm:";
    private static final long L1_TTL_MINUTES = 5;

    private final Cache<String, Set<String>> l1Cache;
    private final RedisTemplate<String, Object> redisTemplate;

    public PermissionCache(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.l1Cache = Caffeine.newBuilder()
                .expireAfterWrite(L1_TTL_MINUTES, TimeUnit.MINUTES)
                .maximumSize(1000)
                .build();
    }

    public PermissionCache() {
        this.redisTemplate = null;
        this.l1Cache = Caffeine.newBuilder()
                .expireAfterWrite(L1_TTL_MINUTES, TimeUnit.MINUTES)
                .maximumSize(1000)
                .build();
    }

    public Set<String> getPermissions(Long tenantId, Long userId) {
        String key = cacheKey(tenantId, userId);

        // L1
        Set<String> cached = l1Cache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }

        // L2
        if (redisTemplate != null) {
            try {
                Object value = redisTemplate.opsForValue().get(key);
                if (value instanceof Set<?> set) {
                    @SuppressWarnings("unchecked")
                    Set<String> redisSet = (Set<String>) set;
                    l1Cache.put(key, redisSet);
                    return redisSet;
                }
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    public void putPermissions(Long tenantId, Long userId, Set<String> permissions) {
        String key = cacheKey(tenantId, userId);
        l1Cache.put(key, permissions);

        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(key, permissions);
            } catch (Exception ignored) {
            }
        }
    }

    public void evict(Long tenantId, Long userId) {
        String key = cacheKey(tenantId, userId);
        l1Cache.invalidate(key);
        if (redisTemplate != null) {
            try {
                redisTemplate.delete(key);
            } catch (Exception ignored) {
            }
        }
    }

    public void evictAll(Long tenantId) {
        // L1: 仅清除该租户前缀的 key
        l1Cache.asMap().keySet().removeIf(k -> k.startsWith(KEY_PREFIX + tenantId + ":"));
        if (redisTemplate != null) {
            try {
                var keys = redisTemplate.keys(KEY_PREFIX + tenantId + ":*");
                if (keys != null && !keys.isEmpty()) {
                    redisTemplate.delete(keys);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private String cacheKey(Long tenantId, Long userId) {
        return KEY_PREFIX + tenantId + ":" + userId;
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `mvn compile -pl mila-framework-starter-rbac`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add mila-framework-starter-rbac/src/main/java/com/kk/mila/rbac/service/PermissionCache.java
git commit -m "feat(rbac): add PermissionCache with Caffeine L1 and Redis L2"
```

---

## Task 9: 服务层 — RbacService

**Files:**
- Create: `service/RbacService.java`

- [ ] **Step 1: 创建 RbacService**

权限校验核心：`hasPermission`, `getPermissions`, `getRoles`, `assignRoles`, `assignPermissions`。

```java
package com.kk.mila.rbac.service;

import com.kk.mila.common.core.exception.BizException;
import com.kk.mila.rbac.config.RbacExceptionCode;
import com.kk.mila.rbac.domain.*;
import com.kk.mila.rbac.mapper.*;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static com.kk.mila.rbac.domain.table.UserRoleTableDef.USER_ROLE;
import static com.kk.mila.rbac.domain.table.RolePermissionTableDef.ROLE_PERMISSION;
import static com.kk.mila.rbac.domain.table.PermissionResourceTableDef.PERMISSION_RESOURCE;
import static com.kk.mila.rbac.domain.table.RoleTableDef.ROLE;
import static com.kk.mila.rbac.domain.table.PermissionTableDef.PERMISSION;

@Service
public class RbacService {

    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionMapper permissionMapper;
    private final PermissionResourceMapper permissionResourceMapper;
    private final DataScopeRuleMapper dataScopeRuleMapper;
    private final PermissionCache permissionCache;

    public RbacService(UserRoleMapper userRoleMapper,
                       RoleMapper roleMapper,
                       RolePermissionMapper rolePermissionMapper,
                       PermissionMapper permissionMapper,
                       PermissionResourceMapper permissionResourceMapper,
                       DataScopeRuleMapper dataScopeRuleMapper,
                       PermissionCache permissionCache) {
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.permissionMapper = permissionMapper;
        this.permissionResourceMapper = permissionResourceMapper;
        this.dataScopeRuleMapper = dataScopeRuleMapper;
        this.permissionCache = permissionCache;
    }

    public Set<String> getPermissionCodes(Long userId) {
        Long tenantId = com.kk.mila.rbac.context.TenantContext.get();
        if (tenantId == null) {
            return Collections.emptySet();
        }

        // 查缓存
        Set<String> cached = permissionCache.getPermissions(tenantId, userId);
        if (cached != null) {
            return cached;
        }

        // 查数据库
        Set<Long> roleIds = getRoleIds(userId);
        if (roleIds.isEmpty()) {
            permissionCache.putPermissions(tenantId, userId, Collections.emptySet());
            return Collections.emptySet();
        }

        Set<Long> permissionIds = getPermissionIds(roleIds);
        if (permissionIds.isEmpty()) {
            permissionCache.putPermissions(tenantId, userId, Collections.emptySet());
            return Collections.emptySet();
        }

        List<Permission> permissions = permissionMapper.selectListByIds(permissionIds);
        Set<String> codes = permissions.stream()
                .map(Permission::getCode)
                .collect(Collectors.toSet());

        permissionCache.putPermissions(tenantId, userId, codes);
        return codes;
    }

    public boolean hasPermission(Long userId, String permissionCode) {
        return getPermissionCodes(userId).contains(permissionCode);
    }

    public void checkPermission(Long userId, String permissionCode) {
        if (!hasPermission(userId, permissionCode)) {
            throw new BizException(RbacExceptionCode.PERMISSION_DENIED);
        }
    }

    public List<Role> getRoles(Long userId) {
        Set<Long> roleIds = getRoleIds(userId);
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }
        return roleMapper.selectListByIds(roleIds);
    }

    public void assignRoles(Long userId, Set<Long> roleIds) {
        // 删除旧关联
        userRoleMapper.deleteByQuery(QueryWrapper.create()
                .where(USER_ROLE.USER_ID.eq(userId)));

        // 插入新关联
        Long tenantId = com.kk.mila.rbac.context.TenantContext.get();
        for (Long roleId : roleIds) {
            UserRole ur = new UserRole();
            ur.setTenantId(tenantId);
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            userRoleMapper.insert(ur);
        }

        // 清缓存
        if (tenantId != null) {
            permissionCache.evict(tenantId, userId);
        }
    }

    public void assignPermissions(Long roleId, Set<Long> permissionIds) {
        rolePermissionMapper.deleteByQuery(QueryWrapper.create()
                .where(ROLE_PERMISSION.ROLE_ID.eq(roleId)));

        Long tenantId = com.kk.mila.rbac.context.TenantContext.get();
        for (Long permId : permissionIds) {
            RolePermission rp = new RolePermission();
            rp.setTenantId(tenantId);
            rp.setRoleId(roleId);
            rp.setPermissionId(permId);
            rolePermissionMapper.insert(rp);
        }

        // 清该角色下所有用户的缓存
        if (tenantId != null) {
            permissionCache.evictAll(tenantId);
        }
    }

    public DataScopeRule getDataScopeRule(Long roleId) {
        return dataScopeRuleMapper.selectOneByQuery(QueryWrapper.create()
                .where(com.kk.mila.rbac.domain.table.DataScopeRuleTableDef.DATA_SCOPE_RULE.ROLE_ID.eq(roleId)));
    }

    public void setDataScope(Long roleId, String scopeType, String scopeConfig) {
        DataScopeRule existing = getDataScopeRule(roleId);
        if (existing != null) {
            existing.setScopeType(scopeType);
            existing.setScopeConfig(scopeConfig);
            existing.setUpdatedAt(java.time.LocalDateTime.now());
            dataScopeRuleMapper.update(existing);
        } else {
            DataScopeRule rule = new DataScopeRule();
            rule.setTenantId(com.kk.mila.rbac.context.TenantContext.get());
            rule.setRoleId(roleId);
            rule.setScopeType(scopeType);
            rule.setScopeConfig(scopeConfig);
            rule.setCreatedAt(java.time.LocalDateTime.now());
            rule.setUpdatedAt(java.time.LocalDateTime.now());
            dataScopeRuleMapper.insert(rule);
        }
    }

    private Set<Long> getRoleIds(Long userId) {
        List<UserRole> userRoles = userRoleMapper.selectListByQuery(
                QueryWrapper.create().where(USER_ROLE.USER_ID.eq(userId)));
        return userRoles.stream().map(UserRole::getRoleId).collect(Collectors.toSet());
    }

    private Set<Long> getPermissionIds(Set<Long> roleIds) {
        List<RolePermission> rolePermissions = rolePermissionMapper.selectListByQuery(
                QueryWrapper.create().where(ROLE_PERMISSION.ROLE_ID.in(roleIds)));
        return rolePermissions.stream().map(RolePermission::getPermissionId).collect(Collectors.toSet());
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `mvn compile -pl mila-framework-starter-rbac`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add mila-framework-starter-rbac/src/main/java/com/kk/mila/rbac/service/RbacService.java
git commit -m "feat(rbac): add RbacService with permission checking and role assignment"
```

---

## Task 10: 拦截器 — RbacInterceptor

**Files:**
- Create: `interceptor/RbacInterceptor.java`

- [ ] **Step 1: 创建 RbacInterceptor**

Sa-Token 路由拦截器，在 `StpUtil.checkLogin()` 之后校验当前用户是否拥有 `@RequirePermission` 声明的权限。

```java
package com.kk.mila.rbac.interceptor;

import cn.dev33.satoken.stp.StpUtil;
import com.kk.mila.rbac.annotation.RequirePermission;
import com.kk.mila.rbac.service.RbacService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

public class RbacInterceptor implements HandlerInterceptor {

    private final RbacService rbacService;

    public RbacInterceptor(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequirePermission methodAnnotation = handlerMethod.getMethodAnnotation(RequirePermission.class);
        RequirePermission classAnnotation = handlerMethod.getBeanType().getAnnotation(RequirePermission.class);

        String requiredPermission = null;
        if (methodAnnotation != null) {
            requiredPermission = methodAnnotation.value();
        } else if (classAnnotation != null) {
            requiredPermission = classAnnotation.value();
        }

        if (requiredPermission == null || requiredPermission.isEmpty()) {
            return true;
        }

        long userId = StpUtil.getLoginIdAsLong();
        rbacService.checkPermission(userId, requiredPermission);
        return true;
    }
}
```

- [ ] **Step 2: 验证编译**

Run: `mvn compile -pl mila-framework-starter-rbac`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add mila-framework-starter-rbac/src/main/java/com/kk/mila/rbac/interceptor/RbacInterceptor.java
git commit -m "feat(rbac): add RbacInterceptor for permission checking on @RequirePermission"
```

---

## Task 11: 拦截器 — DataScopeInterceptor

**Files:**
- Create: `interceptor/DataScopeInterceptor.java`

- [ ] **Step 1: 创建 DataScopeInterceptor**

AOP 拦截器，拦截 `@DataScope` 注解的方法，根据当前用户角色的 DataScopeRule 追加 SQL 条件。通过 MyBatisFlex 的 `QueryWrapper` 条件注入实现。

> **注意**：需先检查 `StpUtil.isLogin()`，未登录时跳过数据范围过滤（直接执行原方法）。

```java
package com.kk.mila.rbac.interceptor;

import cn.dev33.satoken.stp.StpUtil;
import com.kk.mila.rbac.annotation.DataScope;
import com.kk.mila.rbac.domain.DataScopeRule;
import com.kk.mila.rbac.service.RbacService;
import com.mybatisflex.core.query.QueryWrapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.util.List;

@Aspect
public class DataScopeInterceptor {

    private final RbacService rbacService;

    public DataScopeInterceptor(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    @Around("@annotation(dataScope)")
    public Object around(ProceedingJoinPoint joinPoint, DataScope dataScope) throws Throwable {
        // 未登录时跳过数据范围过滤
        if (!StpUtil.isLogin()) {
            return joinPoint.proceed();
        }

        DataScope.ScopeType annotationScope = dataScope.value();

        // 获取当前用户的角色数据范围
        long userId = StpUtil.getLoginIdAsLong();
        List<com.kk.mila.rbac.domain.Role> roles = rbacService.getRoles(userId);

        // 取最宽松的数据范围（角色间取并集）
        DataScope.ScopeType effectiveScope = resolveEffectiveScope(roles, annotationScope);

        // 将数据范围信息放入 ThreadLocal，供 SQL 拦截器使用
        DataScopeContext.set(effectiveScope, userId);

        try {
            return joinPoint.proceed();
        } finally {
            DataScopeContext.clear();
        }
    }

    private DataScope.ScopeType resolveEffectiveScope(List<com.kk.mila.rbac.domain.Role> roles,
                                                       DataScope.ScopeType annotationScope) {
        DataScope.ScopeType effective = DataScope.ScopeType.SELF;

        for (com.kk.mila.rbac.domain.Role role : roles) {
            DataScopeRule rule = rbacService.getDataScopeRule(role.getId());
            if (rule == null) {
                continue;
            }
            DataScope.ScopeType roleScope = DataScope.ScopeType.valueOf(rule.getScopeType());
            if (roleScope.ordinal() > effective.ordinal()) {
                effective = roleScope;
            }
        }

        // 取注解声明与角色配置的交集（取更严格的）
        if (annotationScope.ordinal() < effective.ordinal()) {
            effective = annotationScope;
        }

        return effective;
    }

    /**
     * ThreadLocal 持有当前数据范围上下文，供 MyBatisFlex 拦截器读取
     */
    public static class DataScopeContext {
        private static final ThreadLocal<DataScopeInfo> SCOPE = new ThreadLocal<>();

        public static void set(DataScope.ScopeType scopeType, Long userId) {
            SCOPE.set(new DataScopeInfo(scopeType, userId));
        }

        public static DataScopeInfo get() {
            return SCOPE.get();
        }

        public static void clear() {
            SCOPE.remove();
        }
    }

    public record DataScopeInfo(DataScope.ScopeType scopeType, Long userId) {}
}
```

- [ ] **Step 2: 验证编译**

Run: `mvn compile -pl mila-framework-starter-rbac`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add mila-framework-starter-rbac/src/main/java/com/kk/mila/rbac/interceptor/DataScopeInterceptor.java
git commit -m "feat(rbac): add DataScopeInterceptor AOP for data scope filtering"
```

---

## Task 12: REST Controllers

**Files:**
- Create: 6 个 Controller 在 `com.kk.mila.rbac.controller` 包下

所有 API 前缀 `/api/v1/rbac`，返回 `ApiResponse<T>`。

> **设计说明**：Controller 直接注入 Mapper 进行简单 CRUD，复杂业务逻辑（如权限校验、缓存清除）委托给 Service 层。后续如需添加业务逻辑（密码加密、唯一性校验等），可抽取 Service 而无需修改 Controller 接口。

- [ ] **Step 1: 创建 TenantController**

```java
package com.kk.mila.rbac.controller;

import com.kk.mila.common.ApiResponse;
import com.kk.mila.rbac.domain.Tenant;
import com.kk.mila.rbac.service.TenantManager;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rbac/tenants")
public class TenantController {

    private final TenantManager tenantManager;

    public TenantController(TenantManager tenantManager) {
        this.tenantManager = tenantManager;
    }

    @PostMapping
    public ApiResponse<Tenant> create(@RequestBody Tenant tenant) {
        return ApiResponse.ok(tenantManager.create(tenant));
    }

    @GetMapping("/{id}")
    public ApiResponse<Tenant> getById(@PathVariable Long id) {
        return ApiResponse.ok(tenantManager.getById(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<Tenant> update(@PathVariable Long id, @RequestBody Tenant tenant) {
        tenant.setId(id);
        return ApiResponse.ok(tenantManager.update(tenant));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        if (status == 1) {
            tenantManager.enable(id);
        } else {
            tenantManager.disable(id);
        }
        return ApiResponse.ok();
    }
}
```

- [ ] **Step 2: 创建 UserController**

```java
package com.kk.mila.rbac.controller;

import com.kk.mila.common.ApiResponse;
import com.kk.mila.rbac.domain.User;
import com.kk.mila.rbac.mapper.UserMapper;
import com.kk.mila.rbac.service.RbacService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.update.UpdateWrapper;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static com.kk.mila.rbac.domain.table.UserTableDef.USER;

@RestController
@RequestMapping("/api/v1/rbac/users")
public class UserController {

    private final UserMapper userMapper;
    private final RbacService rbacService;

    public UserController(UserMapper userMapper, RbacService rbacService) {
        this.userMapper = userMapper;
        this.rbacService = rbacService;
    }

    @PostMapping
    public ApiResponse<User> create(@RequestBody User user) {
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);
        return ApiResponse.ok(user);
    }

    @GetMapping("/{id}")
    public ApiResponse<User> getById(@PathVariable Long id) {
        return ApiResponse.ok(userMapper.selectOneById(id));
    }

    @GetMapping
    public ApiResponse<List<User>> list(QueryWrapper queryWrapper) {
        return ApiResponse.ok(userMapper.selectListByQuery(queryWrapper));
    }

    @PutMapping("/{id}")
    public ApiResponse<User> update(@PathVariable Long id, @RequestBody User user) {
        user.setId(id);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.update(user);
        return ApiResponse.ok(user);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        userMapper.deleteById(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/roles")
    public ApiResponse<Void> assignRoles(@PathVariable Long id, @RequestBody Set<Long> roleIds) {
        rbacService.assignRoles(id, roleIds);
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/permissions")
    public ApiResponse<Set<String>> getPermissions(@PathVariable Long id) {
        return ApiResponse.ok(rbacService.getPermissionCodes(id));
    }
}
```

- [ ] **Step 3: 创建 RoleController**

```java
package com.kk.mila.rbac.controller;

import com.kk.mila.common.ApiResponse;
import com.kk.mila.common.core.exception.BizException;
import com.kk.mila.rbac.config.RbacExceptionCode;
import com.kk.mila.rbac.domain.Role;
import com.kk.mila.rbac.mapper.RoleMapper;
import com.kk.mila.rbac.service.RbacService;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/rbac/roles")
public class RoleController {

    private final RoleMapper roleMapper;
    private final RbacService rbacService;

    public RoleController(RoleMapper roleMapper, RbacService rbacService) {
        this.roleMapper = roleMapper;
        this.rbacService = rbacService;
    }

    @PostMapping
    public ApiResponse<Role> create(@RequestBody Role role) {
        role.setType("CUSTOM");
        role.setCreatedAt(LocalDateTime.now());
        role.setUpdatedAt(LocalDateTime.now());
        roleMapper.insert(role);
        return ApiResponse.ok(role);
    }

    @GetMapping
    public ApiResponse<List<Role>> list(QueryWrapper queryWrapper) {
        return ApiResponse.ok(roleMapper.selectListByQuery(queryWrapper));
    }

    @PutMapping("/{id}")
    public ApiResponse<Role> update(@PathVariable Long id, @RequestBody Role role) {
        Role existing = roleMapper.selectOneById(id);
        if (existing != null && "SYSTEM".equals(existing.getType())) {
            throw new BizException(RbacExceptionCode.ROLE_IS_SYSTEM);
        }
        role.setId(id);
        role.setUpdatedAt(LocalDateTime.now());
        roleMapper.update(role);
        return ApiResponse.ok(role);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        Role existing = roleMapper.selectOneById(id);
        if (existing != null && "SYSTEM".equals(existing.getType())) {
            throw new BizException(RbacExceptionCode.ROLE_IS_SYSTEM);
        }
        roleMapper.deleteById(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/permissions")
    public ApiResponse<Void> assignPermissions(@PathVariable Long id, @RequestBody Set<Long> permissionIds) {
        rbacService.assignPermissions(id, permissionIds);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/data-scope")
    public ApiResponse<Void> setDataScope(@PathVariable Long id,
                                          @RequestParam String scopeType,
                                          @RequestParam(required = false) String scopeConfig) {
        rbacService.setDataScope(id, scopeType, scopeConfig);
        return ApiResponse.ok();
    }
}
```

- [ ] **Step 4: 创建 PermissionController**

```java
package com.kk.mila.rbac.controller;

import com.kk.mila.common.ApiResponse;
import com.kk.mila.rbac.domain.Permission;
import com.kk.mila.rbac.domain.PermissionResource;
import com.kk.mila.rbac.mapper.PermissionMapper;
import com.kk.mila.rbac.mapper.PermissionResourceMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static com.kk.mila.rbac.domain.table.PermissionResourceTableDef.PERMISSION_RESOURCE;

@RestController
@RequestMapping("/api/v1/rbac/permissions")
public class PermissionController {

    private final PermissionMapper permissionMapper;
    private final PermissionResourceMapper permissionResourceMapper;

    public PermissionController(PermissionMapper permissionMapper,
                                PermissionResourceMapper permissionResourceMapper) {
        this.permissionMapper = permissionMapper;
        this.permissionResourceMapper = permissionResourceMapper;
    }

    @PostMapping
    public ApiResponse<Permission> create(@RequestBody Permission permission) {
        permission.setCreatedAt(LocalDateTime.now());
        permission.setUpdatedAt(LocalDateTime.now());
        permissionMapper.insert(permission);
        return ApiResponse.ok(permission);
    }

    @GetMapping
    public ApiResponse<List<Permission>> list(QueryWrapper queryWrapper) {
        return ApiResponse.ok(permissionMapper.selectListByQuery(queryWrapper));
    }

    @PostMapping("/{id}/resources")
    public ApiResponse<Void> assignResources(@PathVariable Long id, @RequestBody Set<Long> resourceIds) {
        permissionResourceMapper.deleteByQuery(QueryWrapper.create()
                .where(PERMISSION_RESOURCE.PERMISSION_ID.eq(id)));

        Long tenantId = com.kk.mila.rbac.context.TenantContext.get();
        for (Long resourceId : resourceIds) {
            PermissionResource pr = new PermissionResource();
            pr.setTenantId(tenantId);
            pr.setPermissionId(id);
            pr.setResourceId(resourceId);
            permissionResourceMapper.insert(pr);
        }
        return ApiResponse.ok();
    }
}
```

- [ ] **Step 5: 创建 ResourceController**

```java
package com.kk.mila.rbac.controller;

import com.kk.mila.common.ApiResponse;
import com.kk.mila.rbac.domain.Resource;
import com.kk.mila.rbac.mapper.ResourceMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/rbac/resources")
public class ResourceController {

    private final ResourceMapper resourceMapper;

    public ResourceController(ResourceMapper resourceMapper) {
        this.resourceMapper = resourceMapper;
    }

    @PostMapping
    public ApiResponse<Resource> create(@RequestBody Resource resource) {
        resource.setCreatedAt(LocalDateTime.now());
        resource.setUpdatedAt(LocalDateTime.now());
        resourceMapper.insert(resource);
        return ApiResponse.ok(resource);
    }

    @GetMapping("/tree")
    public ApiResponse<List<Resource>> tree() {
        List<Resource> all = resourceMapper.selectAll();
        Map<Long, List<Resource>> childrenMap = all.stream()
                .filter(r -> r.getParentId() != null && r.getParentId() != 0)
                .collect(Collectors.groupingBy(Resource::getParentId));
        // 递归设置 children
        List<Resource> roots = all.stream()
                .filter(r -> r.getParentId() == null || r.getParentId() == 0)
                .collect(Collectors.toList());
        roots.forEach(r -> fillChildren(r, childrenMap));
        return ApiResponse.ok(roots);
    }

    private void fillChildren(Resource parent, Map<Long, List<Resource>> childrenMap) {
        List<Resource> children = childrenMap.get(parent.getId());
        if (children != null && !children.isEmpty()) {
            parent.setChildren(children);
            children.forEach(c -> fillChildren(c, childrenMap));
        }
    }
}
```

- [ ] **Step 6: 创建 AuthCheckController**

```java
package com.kk.mila.rbac.controller;

import com.kk.mila.common.ApiResponse;
import com.kk.mila.rbac.service.RbacService;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/rbac")
public class AuthCheckController {

    private final RbacService rbacService;

    public AuthCheckController(RbacService rbacService) {
        this.rbacService = rbacService;
    }

    @GetMapping("/check")
    public ApiResponse<Boolean> check(@RequestParam Long userId,
                                      @RequestParam String permission) {
        return ApiResponse.ok(rbacService.hasPermission(userId, permission));
    }

    @GetMapping("/users/{id}/permissions/codes")
    public ApiResponse<Set<String>> getPermissionCodes(@PathVariable Long id) {
        return ApiResponse.ok(rbacService.getPermissionCodes(id));
    }
}
```

- [ ] **Step 7: 验证编译**

Run: `mvn compile -pl mila-framework-starter-rbac`
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add mila-framework-starter-rbac/src/main/java/com/kk/mila/rbac/controller/
git commit -m "feat(rbac): add REST controllers for tenant, user, role, permission, resource, auth-check"
```

---

## Task 13: Auto-Configuration

**Files:**
- Create: `config/RbacAutoConfiguration.java`
- Create: `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

- [ ] **Step 1: 创建 RbacAutoConfiguration**

> **注意**：
> 1. Sa-Token starter 已注册 `SaInterceptor` 拦截 `/**`，`RbacInterceptor` 需在其之后执行（order 更大）。
> 2. 排除 URL 需合并 Sa-Token 的排除路径，避免对公开接口做权限检查。
> 3. Redis bean 类型为 `RedisTemplate<String, Object>`（与 Redis starter 一致）。

```java
package com.kk.mila.rbac.config;

import cn.dev33.satoken.stp.StpUtil;
import com.kk.mila.rbac.context.TenantFactoryImpl;
import com.kk.mila.rbac.context.TenantFilter;
import com.kk.mila.rbac.interceptor.DataScopeInterceptor;
import com.kk.mila.rbac.interceptor.RbacInterceptor;
import com.kk.mila.rbac.interceptor.TenantIgnoreAspect;
import com.kk.mila.rbac.service.PermissionCache;
import com.kk.mila.rbac.service.RbacService;
import com.kk.mila.rbac.service.TenantManager;
import com.mybatisflex.core.tenant.TenantManager;
import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableConfigurationProperties(RbacProperties.class)
@ConditionalOnProperty(prefix = "mila.rbac", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RbacAutoConfiguration {

    // ==================== 多租户基础设施 ====================

    @Bean
    @ConditionalOnProperty(prefix = "mila.rbac.tenant", name = "enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<TenantFilter> tenantFilter(RbacProperties properties) {
        FilterRegistrationBean<TenantFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TenantFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    @Bean
    @ConditionalOnProperty(prefix = "mila.rbac.tenant", name = "enabled", havingValue = "true", matchIfMissing = true)
    public TenantFactoryImpl tenantFactory() {
        TenantFactoryImpl factory = new TenantFactoryImpl();
        com.mybatisflex.core.tenant.TenantManager.setTenantFactory(factory);
        return factory;
    }

    @Bean
    @ConditionalOnProperty(prefix = "mila.rbac.tenant", name = "enabled", havingValue = "true", matchIfMissing = true)
    public TenantIgnoreAspect tenantIgnoreAspect() {
        return new TenantIgnoreAspect();
    }

    // ==================== 权限缓存 ====================

    @Bean
    public PermissionCache permissionCache(
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            RedisTemplate<String, Object> redisTemplate) {
        if (redisTemplate != null) {
            return new PermissionCache(redisTemplate);
        }
        return new PermissionCache();
    }

    // ==================== 权限拦截器 ====================

    @Bean
    public RbacInterceptor rbacInterceptor(RbacService rbacService) {
        return new RbacInterceptor(rbacService);
    }

    @Bean
    public WebMvcConfigurer rbacWebMvcConfigurer(RbacInterceptor rbacInterceptor,
                                                  RbacProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                // 合并 Sa-Token 排除路径 + 租户忽略路径
                List<String> excludeUrls = new ArrayList<>(List.of(
                        "/auth/login", "/auth/register", "/error"));
                excludeUrls.addAll(properties.getTenant().getIgnoreUrls());

                // RbacInterceptor 在 SaInterceptor 之后执行（order 更大）
                registry.addInterceptor(rbacInterceptor)
                        .addPathPatterns("/**")
                        .excludePathPatterns(excludeUrls)
                        .order(100);
            }
        };
    }

    // ==================== 数据权限 ====================

    @Bean
    @ConditionalOnProperty(prefix = "mila.rbac.data-scope", name = "enabled", havingValue = "true", matchIfMissing = true)
    public DataScopeInterceptor dataScopeInterceptor(RbacService rbacService) {
        return new DataScopeInterceptor(rbacService);
    }

    // ==================== REST API（条件注册） ====================

    @Bean
    @ConditionalOnProperty(prefix = "mila.rbac", name = "api-enabled", havingValue = "true")
    public TenantController tenantController(TenantManager tenantManager) {
        return new TenantController(tenantManager);
    }

    @Bean
    @ConditionalOnProperty(prefix = "mila.rbac", name = "api-enabled", havingValue = "true")
    public UserController userController(com.kk.mila.rbac.mapper.UserMapper userMapper,
                                          RbacService rbacService) {
        return new UserController(userMapper, rbacService);
    }

    @Bean
    @ConditionalOnProperty(prefix = "mila.rbac", name = "api-enabled", havingValue = "true")
    public RoleController roleController(com.kk.mila.rbac.mapper.RoleMapper roleMapper,
                                          RbacService rbacService) {
        return new RoleController(roleMapper, rbacService);
    }

    @Bean
    @ConditionalOnProperty(prefix = "mila.rbac", name = "api-enabled", havingValue = "true")
    public PermissionController permissionController(com.kk.mila.rbac.mapper.PermissionMapper permissionMapper,
                                                      com.kk.mila.rbac.mapper.PermissionResourceMapper permissionResourceMapper) {
        return new PermissionController(permissionMapper, permissionResourceMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "mila.rbac", name = "api-enabled", havingValue = "true")
    public ResourceController resourceController(com.kk.mila.rbac.mapper.ResourceMapper resourceMapper) {
        return new ResourceController(resourceMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "mila.rbac", name = "api-enabled", havingValue = "true")
    public AuthCheckController authCheckController(RbacService rbacService) {
        return new AuthCheckController(rbacService);
    }
}
```

- [ ] **Step 2: 创建 Auto-Configuration imports 文件**

文件路径：`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

内容（单行）：

```
com.kk.mila.rbac.config.RbacAutoConfiguration
```

- [ ] **Step 3: 验证编译**

Run: `mvn compile -pl mila-framework-starter-rbac`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add mila-framework-starter-rbac/src/main/java/com/kk/mila/rbac/config/RbacAutoConfiguration.java
git add mila-framework-starter-rbac/src/main/resources/
git commit -m "feat(rbac): add RbacAutoConfiguration with conditional bean registration"
```

---

## Task 14: 独立微服务 — mila-service-rbac

> **注意**：这是项目中首个 service 模块（之前仅有 starter/library 模块）。service 模块包含 `spring-boot-maven-plugin` 用于打包可执行 jar。

**Files:**
- Create: `mila-service-rbac/src/main/java/com/kk/mila/rbac/server/RbacApplication.java`
- Create: `mila-service-rbac/src/main/resources/application.yml`

- [ ] **Step 1: 创建 RbacApplication**

```java
package com.kk.mila.rbac.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class RbacApplication {

    public static void main(String[] args) {
        SpringApplication.run(RbacApplication.class, args);
    }
}
```

- [ ] **Step 2: 创建 application.yml**

```yaml
server:
  port: 8081

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mila_rbac
    username: postgres
    password: postgres
    driver-class-name: org.postgresql.Driver

mybatis-flex:
  mapper-locations: classpath*:/mapper/**/*.xml

mila:
  rbac:
    enabled: true
    api-enabled: true
    tenant:
      enabled: true
      ignore-urls:
        - /auth/**
        - /public/**
    data-scope:
      enabled: true

sa-token:
  token-name: Authorization
  timeout: 7200
  token-style: uuid
```

- [ ] **Step 3: 验证编译**

Run: `mvn compile -pl mila-service-rbac -am`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add mila-service-rbac/src/
git commit -m "feat(rbac): add mila-service-rbac standalone microservice"
```

---

## Task 15: SQL DDL 脚本

**Files:**
- Create: `mila-framework-starter-rbac/src/main/resources/db/migration/V1__rbac_init.sql`

- [ ] **Step 1: 创建建表脚本**

```sql
-- RBAC 基础服务 DDL
-- 表前缀: sys_

-- 租户表（自身无 tenant_id，是租户隔离的例外）
CREATE TABLE IF NOT EXISTS sys_tenant (
    id           BIGSERIAL    PRIMARY KEY,
    code         VARCHAR(64)  NOT NULL UNIQUE,       -- 租户编码，全局唯一
    name         VARCHAR(128) NOT NULL,               -- 租户名称
    status       INTEGER      NOT NULL DEFAULT 1,     -- 状态：1=启用 0=禁用
    expired_at   TIMESTAMP,                           -- 过期时间，NULL 表示永不过期
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE sys_tenant IS '租户表';
COMMENT ON COLUMN sys_tenant.code IS '租户编码，全局唯一';
COMMENT ON COLUMN sys_tenant.status IS '状态：1=启用 0=禁用';
COMMENT ON COLUMN sys_tenant.expired_at IS '过期时间，NULL 表示永不过期';

-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id           BIGSERIAL    PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL,               -- 租户ID
    username     VARCHAR(64)  NOT NULL,               -- 用户名
    password     VARCHAR(256) NOT NULL,               -- 密码（加密存储）
    nickname     VARCHAR(64),                         -- 昵称
    dept_id      BIGINT,                              -- 部门ID
    status       INTEGER      NOT NULL DEFAULT 1,     -- 状态：1=启用 0=禁用
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE sys_user IS '用户表';

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_tenant_username ON sys_user (tenant_id, username);
CREATE INDEX IF NOT EXISTS idx_user_dept ON sys_user (tenant_id, dept_id);

-- 角色表
CREATE TABLE IF NOT EXISTS sys_role (
    id           BIGSERIAL    PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL,               -- 租户ID
    code         VARCHAR(64)  NOT NULL,               -- 角色编码
    name         VARCHAR(128) NOT NULL,               -- 角色名称
    type         VARCHAR(16)  NOT NULL DEFAULT 'CUSTOM', -- 类型：SYSTEM=系统角色 CUSTOM=自定义角色
    status       INTEGER      NOT NULL DEFAULT 1,     -- 状态：1=启用 0=禁用
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE sys_role IS '角色表';
COMMENT ON COLUMN sys_role.type IS '类型：SYSTEM=系统角色(不可修改/删除) CUSTOM=自定义角色';

CREATE UNIQUE INDEX IF NOT EXISTS uk_role_tenant_code ON sys_role (tenant_id, code);

-- 权限表
CREATE TABLE IF NOT EXISTS sys_permission (
    id           BIGSERIAL    PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL,               -- 租户ID
    code         VARCHAR(128) NOT NULL,               -- 权限编码，如 user:create
    name         VARCHAR(128) NOT NULL,               -- 权限名称
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE sys_permission IS '权限表';
COMMENT ON COLUMN sys_permission.code IS '权限编码，如 user:create, role:delete';

CREATE UNIQUE INDEX IF NOT EXISTS uk_perm_tenant_code ON sys_permission (tenant_id, code);

-- 资源表
CREATE TABLE IF NOT EXISTS sys_resource (
    id           BIGSERIAL    PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL,               -- 租户ID
    code         VARCHAR(128) NOT NULL,               -- 资源编码
    name         VARCHAR(128) NOT NULL,               -- 资源名称
    type         VARCHAR(16)  NOT NULL,               -- 类型：MENU/BUTTON/API
    parent_id    BIGINT       DEFAULT 0,              -- 父资源ID，0=顶级
    sort         INTEGER      DEFAULT 0,              -- 排序号
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE sys_resource IS '资源表（菜单/按钮/API）';
COMMENT ON COLUMN sys_resource.type IS '类型：MENU=菜单 BUTTON=按钮 API=接口';

CREATE UNIQUE INDEX IF NOT EXISTS uk_res_tenant_code ON sys_resource (tenant_id, code);

-- 用户-角色关联表
CREATE TABLE IF NOT EXISTS sys_user_role (
    id           BIGSERIAL    PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL,               -- 租户ID
    user_id      BIGINT       NOT NULL,               -- 用户ID
    role_id      BIGINT       NOT NULL                -- 角色ID
);

COMMENT ON TABLE sys_user_role IS '用户-角色关联表';

CREATE UNIQUE INDEX IF NOT EXISTS uk_ur_tenant_user_role ON sys_user_role (tenant_id, user_id, role_id);
CREATE INDEX IF NOT EXISTS idx_ur_user ON sys_user_role (tenant_id, user_id);

-- 角色-权限关联表
CREATE TABLE IF NOT EXISTS sys_role_permission (
    id           BIGSERIAL    PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL,               -- 租户ID
    role_id      BIGINT       NOT NULL,               -- 角色ID
    permission_id BIGINT      NOT NULL                -- 权限ID
);

COMMENT ON TABLE sys_role_permission IS '角色-权限关联表';

CREATE UNIQUE INDEX IF NOT EXISTS uk_rp_tenant_role_perm ON sys_role_permission (tenant_id, role_id, permission_id);
CREATE INDEX IF NOT EXISTS idx_rp_role ON sys_role_permission (tenant_id, role_id);

-- 权限-资源关联表
CREATE TABLE IF NOT EXISTS sys_permission_resource (
    id           BIGSERIAL    PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL,               -- 租户ID
    permission_id BIGINT      NOT NULL,               -- 权限ID
    resource_id  BIGINT       NOT NULL                -- 资源ID
);

COMMENT ON TABLE sys_permission_resource IS '权限-资源关联表';

CREATE UNIQUE INDEX IF NOT EXISTS uk_pr_tenant_perm_res ON sys_permission_resource (tenant_id, permission_id, resource_id);

-- 数据范围规则表
CREATE TABLE IF NOT EXISTS sys_data_scope_rule (
    id           BIGSERIAL    PRIMARY KEY,
    tenant_id    BIGINT       NOT NULL,               -- 租户ID
    role_id      BIGINT       NOT NULL,               -- 角色ID
    scope_type   VARCHAR(32)  NOT NULL,               -- 范围类型：ALL/SELF/DEPT/DEPT_AND_SUB/CUSTOM
    scope_config TEXT,                                -- 范围配置（JSON，CUSTOM 类型时使用）
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE sys_data_scope_rule IS '数据范围规则表';
COMMENT ON COLUMN sys_data_scope_rule.scope_type IS '范围类型：ALL=全部 SELF=本人 DEPT=本部门 DEPT_AND_SUB=本部门及子部门 CUSTOM=自定义';
COMMENT ON COLUMN sys_data_scope_rule.scope_config IS '范围配置（JSON），CUSTOM 类型时指定自定义部门ID列表等';

CREATE UNIQUE INDEX IF NOT EXISTS uk_dsr_tenant_role ON sys_data_scope_rule (tenant_id, role_id);
```

- [ ] **Step 2: 验证全量编译**

Run: `mvn compile -pl mila-framework-starter-rbac,mila-service-rbac -am`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add mila-framework-starter-rbac/src/main/resources/db/
git commit -m "feat(rbac): add DDL migration script for all RBAC tables"
```

---

## Task 16: 最终全量编译验证

- [ ] **Step 1: 根目录全量编译**

Run: `mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 2: 确认所有模块在根 pom.xml 中注册**

检查 `pom.xml` 的 `<modules>` 包含：
- `mila-framework-starter-rbac`
- `mila-service-rbac`

- [ ] **Step 3: 最终 Commit（如有遗漏修复）**

```bash
git add -A
git commit -m "feat(rbac): RBAC service implementation complete - multi-tenant, role-permission, data-scope"
```
