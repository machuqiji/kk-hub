# 无人机管理平台架构设计

## 概述

基于 mila-framework 脚手架构建的无人机管理平台，采用协议适配层（Protocol Adapter）+ 事件驱动架构，支持多厂商无人机接入（DJI Cloud API 优先，扩展运载机、Pilot 等厂商），通信协议以 MQTT 为主、HTTP 为辅。

## 核心需求

1. 多厂商无人机接入（不同 MQTT topic/payload 差异、部分厂商 HTTP 对接）
2. 实时遥测采集与历史存储
3. 命令下发与任务管理（巡检任务、航线执行）
4. 视频流接入 + AI 实时推理（山火检测等）+ 多模型交叉验证
5. 检测结果推送到不同业主平台
6. 告警引擎（电量低、偏航、失联等）
7. 多租户双层视图（运营方全局 + 业主隔离）
8. 混合部署（私有化 + 公有云）

## 架构方案：协议适配层 + 事件驱动

### 设计原则

- **Adapter 层屏蔽厂商差异**：新厂商接入只需实现一个 Adapter，不动核心逻辑
- **内部统一事件模型**：下游服务只消费标准事件，不关心厂商协议
- **可插拔**：流媒体、AI 推理均通过接口抽象，支持内置/第三方切换
- **以 DJI Cloud API 为基线**：内部事件模型参考 DJI thing model 设计

### 分层架构

```
┌──────────────────────────────────────────────┐
│              API Layer (REST)                │  ← mila-framework-starter-web
├──────────────────────────────────────────────┤
│           Application Services              │  ← 业务编排
├──────────┬──────────┬───────────┬───────────┤
│ Protocol │ Telemetry│ Command   │ Alert     │
│ Adapter  │ Pipeline │ Dispatch  │ Engine    │
│ Layer    │          │           │           │
├──────────┴──────────┴───────────┴───────────┤
│         Domain Model & Event Bus            │  ← 内部事件总线
├──────────────────────────────────────────────┤
│    Infrastructure (MQTT, HTTP, Redis, DB)   │  ← mila-framework starters
└──────────────────────────────────────────────┘
```

## 核心领域模型

| 概念 | 说明 |
|------|------|
| Vendor（厂商） | DJI、运载机厂商、Pilot 厂商等 |
| Adapter（协议适配器） | 每个厂商一个，双向：上行解析 + 下行转换 |
| Device（设备/无人机） | 一架无人机，归属某个 Vendor |
| Telemetry（遥测） | 位置、速度、姿态、电量等实时数据 |
| Command（命令） | 下发给无人机的指令（起飞、返航、任务等） |
| Alert（告警） | 异常事件触发（低电量、偏航、失联等） |
| Task（任务） | 巡检任务、航线执行等 |
| Detection（检测结果） | AI 推理结果（山火、异常等） |
| Tenant（租户/业主） | 检测结果推送的目标业主 |

## 协议适配层（Protocol Adapter Layer）

### 双向 Adapter

每个 VendorAdapter 包含：
- **InboundHandler**：解析厂商特定 topic/payload → 转为内部标准事件
- **OutboundHandler**：将内部命令消息 → 转为厂商特定 topic/payload → 发布到厂商 MQTT 或调用厂商 HTTP API
- **TopicMapper / ApiMapper**：厂商 topic/API ↔ 内部事件类型映射

```
┌─────────────────────────────────┐
│  DJI Cloud API Adapter          │
│  ├── InboundHandler             │  DJI topic → TelemetryEvent
│  │     sys/{sn}/thing/event/... │
│  ├── OutboundHandler            │  CommandMessage → DJI topic
│  │     sys/{sn}/thing/service/..│
│  └── TopicMapper                │  DJI topic ↔ 内部事件类型映射
├─────────────────────────────────┤
│  VendorB Adapter                │
│  ├── InboundHandler             │  厂商B topic → TelemetryEvent
│  ├── OutboundHandler            │  CommandMessage → 厂商B topic
│  └── TopicMapper                │
├─────────────────────────────────┤
│  VendorC HTTP Adapter           │
│  ├── InboundHandler             │  HTTP callback → TelemetryEvent
│  ├── OutboundHandler            │  CommandMessage → HTTP API call
│  └── ApiMapper                  │
└─────────────────────────────────┘
```

### Adapter 与 MQTT Starter 集成策略

现有 `mila-framework-starter-mqtt` 默认订阅 `devices/+/telemetry` 等简单 topic，与 DJI Cloud API 的 `sys/{sn}/thing/event/...` 格式差异很大。不同厂商可能连接不同 Broker、使用不同 topic 规则。

**策略：每个 Adapter 持有独立的 MQTT 连接**

```
┌──────────────────────────────────────────┐
│  DJI Cloud API Adapter                  │
│  ├── 独立 MqttPahoMessageDrivenAdapter  │  ← 连接 DJI EMQX
│  ├── 订阅 sys/+/thing/event/#           │
│  └── InboundHandler / OutboundHandler   │
├──────────────────────────────────────────┤
│  VendorB Adapter                        │
│  ├── 独立 MqttPahoMessageDrivenAdapter  │  ← 连接厂商B Broker
│  ├── 订阅 vendor_b/devices/+/data       │
│  └── InboundHandler / OutboundHandler   │
└──────────────────────────────────────────┘
```

- 每个 `VendorAdapter.initialize()` 创建自己的 `MqttPahoClientFactory` + `MqttPahoMessageDrivenChannelAdapter`
- `mila-framework-starter-mqtt` 的通用能力（消息转换、重连、SSL）复用，但 topic 订阅由 Adapter 自治
- HTTP 类厂商 Adapter 不创建 MQTT 连接，仅注册 HTTP callback endpoint

### Adapter 接口定义

```java
// 敏感字段用 char[] 而非 String，防止 toString/日志泄露
public record AdapterConfig(
    String vendorType,
    String brokerUrl,
    String clientId,
    String username,
    char[] password,           // char[] — 调用后可零化，toString 不泄露
    boolean sslEnabled,
    TlsConfig tls,
    List<String> subscribedTopics,
    Map<String, String> extra
) {
    @Override public String toString() {
        return "AdapterConfig[vendorType=%s, brokerUrl=%s, password=***]".formatted(vendorType, brokerUrl);
    }
}

public record TlsConfig(
    String keyStorePath,
    char[] keyStorePassword,   // char[] — 同上
    String keyStoreType,
    String trustStorePath,
    char[] trustStorePassword  // char[] — 同上
) {
    @Override public String toString() {
        return "TlsConfig[keyStorePath=%s, keyStoreType=%s]".formatted(keyStorePath, keyStoreType);
    }
}
```

```java
// VendorAdapter 作为 Spring Bean，配置通过 @ConfigurationProperties 注入
// initialize() 由 @PostConstruct 自动调用，不应手动调用
public interface VendorAdapter extends AutoCloseable {
    String getVendorType();
    void initialize();              // @PostConstruct 调用，内部读取注入的 AdapterConfig
    void onConnect(String deviceSn);
    void onDisconnect(String deviceSn);
    boolean isHealthy();            // K8s liveness 探针用
    @PreDestroy void close();       // 优雅关闭资源
}

public interface InboundHandler {
    Result<TelemetryEvent> handleTelemetry(String topic, MqttMessage message);
    Result<DeviceStatusEvent> handleStatus(String topic, MqttMessage message);
    Result<TaskProgressEvent> handleTaskProgress(String topic, MqttMessage message);
    Result<CommandResultEvent> handleCommandResult(String topic, MqttMessage message);
}

public interface OutboundHandler {
    void sendCommand(String deviceSn, CommandMessage command);
}

// 解析结果：成功或失败，不抛异常；Failure 不携带 Throwable（不可序列化，跨 JVM 传输失败）
public sealed interface Result<T> {
    record Success<T>(T data) implements Result<T> {}
    record Failure<T>(String vendorType, String error) implements Result<T> {}
}
```

## 内部标准事件模型

以 DJI Cloud API 的 thing model 为基线：

```java
// 遥测事件 — 所有厂商 Adapter 上行输出
public record TelemetryEvent(
    String deviceId,        // 平台统一设备ID
    String vendorType,      // DJI / VENDOR_B / VENDOR_C
    String deviceSn,        // 厂商原始序列号
    long timestamp,         // 事件时间戳
    Position position,      // 经纬度、高度
    Attitude attitude,      // 姿态角
    Velocity velocity,      // 速度
    Battery battery,        // 电量、电压
    Map<String, Object> extra  // 厂商特有字段扩展（构造时 Map.copyOf 冻结，不可变）
) {
    public TelemetryEvent {
        extra = Map.copyOf(extra);  // 防御性拷贝，确保不可变
    }
}

// 命令消息 — 所有厂商 Adapter 下行输入
public record CommandMessage(
    String commandId,       // 命令唯一ID，用于追踪确认
    String deviceId,
    String commandType,     // TAKEOFF / RTH / WAYLINE / CONFIG / LIVE_STREAM / ...
    Map<String, Object> params,
    long timeout,           // 超时时间（ms）
    int maxRetries          // 最大重试次数
) {
    public CommandMessage {
        params = Map.copyOf(params);
    }
}

// 命令确认事件 — 设备执行结果回传
public record CommandResultEvent(
    String commandId,       // 关联 CommandMessage.commandId
    String deviceId,
    CommandResult result,   // ACCEPTED / EXECUTING / SUCCESS / FAILED / TIMEOUT
    String message,         // 结果描述或错误信息
    long timestamp
) {}

// 设备状态事件
public record DeviceStatusEvent(
    String deviceId,
    DeviceState state,      // ONLINE / OFFLINE / FLIGHT / IDLE / ERROR
    String vendorType,
    long timestamp
) {}

// 告警事件
public record AlertEvent(
    String alertId,
    String deviceId,
    AlertLevel level,       // WARN / CRITICAL / EMERGENCY
    String ruleId,
    String metric,
    double value,
    double threshold,
    long timestamp
) {}

// 检测结果事件
public record DetectionEvent(
    String detectionId,
    String deviceId,
    String taskId,
    String providerId,      // AI 推理提供者
    String detectionType,   // FIRE / SMOKE / ...
    double confidence,
    Map<String, Object> detail,
    long timestamp
) {
    public DetectionEvent {
        detail = Map.copyOf(detail);
    }
}
```

## 事件总线

### 选型：Redis Pub/Sub（默认）

Spring `ApplicationEvent` 是同步且单 JVM 的，多实例部署时事件丢失。无人机平台从第一天起就可能水平扩展，因此默认采用 **Redis Pub/Sub** 作为事件总线（项目已引入 `mila-framework-starter-redis`）。

| 方案 | 适用场景 | 限制 |
|------|---------|------|
| Spring ApplicationEvent + @Async | 单实例开发/调试 | 不跨 JVM，发布者阻塞 |
| Redis Pub/Sub（默认） | 多实例，中规模 | 消息不持久，订阅者离线会丢；Redis 5.0+ 可用 Streams 替代 |
| MQTT 内部 topic | 需要消息持久化 + QoS | 增加 Broker 负载，与设备 MQTT 流量竞争 |

**升级路径：** 若 Redis Pub/Sub 的"订阅者离线丢消息"不可接受（如命令确认事件），切换到 Redis Streams（消费者组模式，支持持久化 + 重试）。

### 事件通道分级

不同事件对可靠性要求不同，应使用不同通道：

| 事件类型 | 通道 | 理由 |
|---------|------|------|
| TelemetryEvent | Redis Pub/Sub | 遥测丢了没关系（下一秒还有），优先吞吐 |
| DeviceStatusEvent | Redis Pub/Sub | 状态变化低频，Pub/Sub 足够 |
| CommandMessage / CommandResultEvent | **Redis Streams** | 命令不可丢，消费者组保证至少一次投递 + 持久化 |
| AlertEvent | Redis Pub/Sub | 告警由 AlertEngine 内部生成，不依赖外部投递 |
| DetectionEvent | Redis Pub/Sub | 检测结果丢了可重推，非关键路径 |

Redis Streams 命令通道：
```
CommandService ──XADD──→ stream:command ──XREADGROUP──→ Adapter(Outbound)
Adapter(Inbound) ──XADD──→ stream:command_result ──XREADGROUP──→ CommandService
```

事件流转：
```
Adapter(Inbound) ──publish──→ TelemetryEvent ──→ TelemetryProcessor
                                              ──→ AlertEngine
                                              ──→ HistoryStore

CommandService ──publish──→ CommandMessage ──→ Adapter(Outbound)

Adapter(Inbound) ──publish──→ CommandResultEvent ──→ CommandService（更新命令状态）
```

## 遥测处理管线

```
TelemetryEvent
  │
  ▼
┌─────────────┐
│  Dedup/     │  ← 去重：同设备短时间内的重复遥测
│  Throttle   │  ← 降频：高频遥测按策略降采样
└──────┬──────┘
       ▼
┌─────────────┐
│  Enrich     │  ← 补充：设备归属租户、任务关联等
└──────┬──────┘
       ▼
┌─────────────┐
│  Route      │  ← 分发：写时序库 + 推WebSocket + 触发告警
└──────┬──────┘
       ▼
  ┌────┴────┐
  ▼         ▼
TimeseriesDB  WebSocket(前端实时展示)
```

## 存储

| 数据类型 | 存储方案 | 理由 |
|---------|---------|------|
| 遥测时序数据 | PostgreSQL + TimescaleDB | 与主库同引擎，运维简单，中规模够用 |
| 设备/任务/告警 | PostgreSQL（MyBatisFlex） | 关系型数据，主库即可 |
| 设备实时状态 | Redis | 在线状态、最新位置、缓存 |
| 告警规则配置 | PostgreSQL | 持久化规则定义 |

### TimescaleDB 运维策略

- **Hypertable**：`create_hypertable('telemetry', 'timestamp')`，chunk interval = 1 day
- **数据保留**：原始遥测保留 90 天（`add_retention_policy('telemetry', INTERVAL '90 days')`）
- **降采样（Continuous Aggregate）**：
  - 1 分钟聚合：保留 180 天
  - 5 分钟聚合：保留 1 年
  - 1 小时聚合：永久保留
- 查询时根据时间范围自动路由到合适的聚合表（`time_bucket` + CAGG）

## 命令下发与任务管理

### 命令下发流程

```
前端/API ──→ CommandService
                │
                ├── 1. 校验设备在线状态（Redis）
                ├── 2. 构造 CommandMessage（含 commandId + timeout + maxRetries）
                ├── 3. 记录命令日志（DB，状态=PENDING）
                └── 4. 发布 CommandMessage 事件
                        │
                        ▼
                  Adapter(Outbound)
                        │
                ┌───────┼───────┐
                ▼       ▼       ▼
            DJI: MQTT  VENDOR_B: MQTT  VENDOR_C: HTTP API
```

### 命令确认流程

无人机命令（起飞、返航）必须确认执行结果，不能 fire-and-forget：

```
CommandMessage ──→ Adapter(Outbound) ──→ 设备
                                         │
设备执行结果 ──→ Adapter(Inbound) ──→ CommandResultEvent ──→ CommandService
                                                                         │
                                    ┌────────────────────────────────────┤
                                    ▼                                    ▼
                              更新命令状态(DB)                    超时未确认？
                              SUCCESS/FAILED                      ├── 重试（≤ maxRetries）
                                                                 └── 触发 AlertEvent(COMMAND_TIMEOUT)
```

- `CommandService` 启动定时器，`commandId` 超 `timeout` 未收到 `CommandResultEvent` 则重试或告警
- 命令状态机：`PENDING → SENT → ACCEPTED → EXECUTING → SUCCESS/FAILED/TIMEOUT`

### 命令超时 — 分布式实现

多实例部署时，命令可能由实例 A 发出但 `CommandResultEvent` 被实例 B 收到。不能用 `ScheduledExecutor` 做超时检测（实例崩溃后定时器丢失）。

**方案：Redis Key Expiry + Keyspace Notification**

```
1. CommandService 发命令时：
   SET cmd:{commandId} {commandPayload} EX {timeoutSeconds}

2. 收到 CommandResultEvent 时：
   DEL cmd:{commandId}   ← 取消超时

3. Key 过期触发 Redis keyspace notification：
   __keyevent@0__:expired cmd:{commandId}
   → 订阅该通知的实例执行重试或触发 AlertEvent(COMMAND_TIMEOUT)
```

- 优势：实例崩溃后 key 仍会过期，新实例能接收到通知（比 ScheduledExecutor 更可靠）
- Keyspace Notification 需在 Redis 配置 `notify-keyspace-events Ex`
- 重试时重新 `SET cmd:{commandId} EX {timeout}`，直到 `maxRetries` 耗尽

### 命令类型

| 命令 | 说明 | DJI Cloud API 对应 |
|------|------|-------------------|
| TAKEOFF | 起飞 | flighttask_prepare + flighttask_execute |
| RTH | 返航 | return_to_home |
| WAYLINE | 执行航线 | wayline_execute |
| CONFIG | 参数配置 | device_config |
| LIVE_STREAM | 开关直播 | live_start_push / live_stop_push |

### 任务模型

巡检任务 = 航线 + 无人机 + 时间窗口 + AI 检测配置

```
Task
├── waylineId        // 航线
├── deviceId         // 执行无人机
├── scheduledTime    // 计划时间
├── status           // PENDING / EXECUTING / COMPLETED / FAILED
├── detectionConfig  // AI 检测配置（哪些模型、灵敏度等）
└── resultPushTargets // 检测结果推送目标（业主平台）
```

## 视频流与 AI 推理

### 视频流架构

```
无人机 ──RTMP/RTSP──→ StreamServer ──→ AI Inference
                          │                    │
                          │                    ▼
                          │              DetectionEvent
                          │                    │
                          ▼                    ▼
                    前端播放(WebRTC/HLS)   结果推送(业主平台)
```

### 流媒体服务器 — 可插拔

```java
public interface StreamMediaProvider {
    String getProviderId();
    String getPushUrl(String deviceId, StreamType type);
    String getPullUrl(String deviceId, StreamType type);
    void startStream(String deviceId, StreamType type);
    void stopStream(String deviceId, StreamType type);
    // 录制与回放（巡检事后回看）
    String startRecording(String deviceId, StreamType type);
    void stopRecording(String deviceId);
    String getPlaybackUrl(String deviceId, long startTimestamp, long endTimestamp, PlaybackFormat format);  // HLS / DASH / FLV
}
```

- 内置实现：SRS（支持 RTMP/RTSP/WebRTC）
- 外部实现：阿里云直播、华为云视频等

### AI 推理 — 可插拔 + 多模型交叉验证

```java
public interface AiInferenceProvider {
    String getProviderId();
    DetectionResult infer(InferenceRequest request);
}

// 交叉验证编排
public class CrossValidationOrchestrator {
    List<AiInferenceProvider> providers;
    MergeStrategy mergeStrategy;  // ANY / MAJORITY / WEIGHTED
    Duration timeout;             // 单个 provider 超时
    PartialFailurePolicy partialFailurePolicy;  // ALL_MUST_SUCCEED / BEST_EFFORT
}

// 权重配置来源：DB 或配置中心，按 detectionType 差异化
public enum MergeStrategy {
    ANY,        // 任一 provider 检出即告警（高灵敏度）
    MAJORITY,   // 过半 provider 检出才告警（平衡）
    WEIGHTED    // 加权投票，权重从配置加载（高精度）
}
```

### 检测结果推送

- 按业主配置推送方式（HTTP callback / MQTT / Kafka）
- 失败重试 + 死信队列

## 告警引擎

简单规则引擎，不引入 Drools：

```java
public class AlertRule {
    String ruleId;
    String deviceIdPattern;   // 匹配的设备（支持通配符）
    String metric;            // battery / altitude / speed / ...
    ComparisonOperator op;    // LT / GT / EQ / ...
    double threshold;
    Duration window;          // 滑动窗口
    AlertLevel level;         // WARN / CRITICAL / EMERGENCY
}
```

告警触发流程：
```
TelemetryEvent → AlertEngine.evaluate() → 命中规则 → AlertEvent
  → 通知（WebSocket / 短信 / 邮件）
  → 持久化（DB）
```

### 滑动窗口实现

- **单实例**：Caffeine（项目已引入 3.1.8）+ `expireAfterWrite`，按 `deviceId + metric` 缓存窗口内遥测值，窗口过期自动清理
- **多实例**：每实例独立窗口（最终一致性），可能重复触发告警 → 由 `AlertEvent.alertId` 去重（相同 rule + device + 窗口内只产生一条）
- 若需严格全局窗口：改用 Redis Sorted Set（score=timestamp），按窗口范围 `ZRANGEBYSCORE` 查询

### 告警去重模式

| 模式 | 行为 | 适用场景 |
|------|------|---------|
| **单次触发 + 冷却期** | 首次命中后，N 分钟内相同 rule+device 不重复告警 | 电量低、信号弱（持续异常报一次即可） |
| **状态变化触发** | 正常→异常报一次，异常→正常恢复报一次 | 设备离线/上线、偏航/回正（需要恢复通知） |

- `AlertRule` 增加 `dedupMode`（SINGLE_WITH_COOLDOWN / STATE_CHANGE）和 `cooldownDuration` 字段
- 去重 key：`alert_dedup:{ruleId}:{deviceId}`，存入 Redis 并设 TTL

## 多租户（双层视图）

```
┌─────────────────────────────────────┐
│         运营方（Operator）           │
│  全局视角：所有无人机、所有任务、     │
│  所有告警、所有检测结果              │
├──────────────┬──────────────────────┤
│  业主A        │  业主B              │
│  自己的无人机  │  自己的无人机        │
│  自己的检测结果│  自己的检测结果      │
│  自己的告警    │  自己的告警          │
└──────────────┴──────────────────────┘
```

- 数据层：设备表关联 tenant_id，业主只能查自己的设备
- 运营方：tenant_id = NULL 或特殊角色，可查所有
- 检测结果推送：按设备的 tenant_id 确定推送目标
- **推送隔离校验**：推送配置必须 `target.tenantId == device.tenantId`，运行时强制校验，防止跨租户数据泄露
- AI 推理服务内部按 tenant 逻辑隔离（推理请求携带 tenantId，结果回写时校验）
- 权限：Sa-Token + RBAC，运营方角色 vs 业主角色

## MQTT Broker

选用 **EMQX**：
- 中规模 100-1000 架连接轻松
- 集群模式支持水平扩展
- 支持 MQTT 5.0
- Docker 部署简单，私有化/公有云都支持

## 部署架构

```
┌──────────────────────────────────────────────┐
│                 负载均衡 (Nginx)              │
├──────────┬──────────┬────────────────────────┤
│  App实例1 │ App实例2 │  (水平扩展)            │
├──────────┴──────────┴────────────────────────┤
│  EMQX Cluster (2-3节点)                      │
├──────────────────────────────────────────────┤
│  SRS (流媒体)                                │
├──────────────────────────────────────────────┤
│  PostgreSQL + TimescaleDB  │  Redis          │
├────────────────────────────┴─────────────────┤
│  AI 推理服务 (GPU节点，可独立部署)             │
└──────────────────────────────────────────────┘
```

AI 推理服务可独立部署在 GPU 节点上，通过 HTTP/gRPC 与主应用通信。

### 网络拓扑

```
┌─ 公网 ─────────────────────────────────────────────────────┐
│  无人机 ──MQTT──→ LB(1883/8883) ──→ EMQX Cluster          │
│  无人机 ──RTMP──→ LB(1935) ──→ SRS                        │
│  前端   ──HTTPS──→ Nginx ──→ App实例                      │
└────────────────────────────────────────────────────────────┘
┌─ 内网 ─────────────────────────────────────────────────────┐
│  App实例 ──MQTT──→ EMQX Cluster（内网直连，不经 LB）       │
│  App实例 ──Redis──→ Redis Cluster                          │
│  App实例 ──TCP──→ PostgreSQL + TimescaleDB                 │
│  App实例 ──HTTP/gRPC──→ AI 推理服务(GPU 节点)             │
└────────────────────────────────────────────────────────────┘
```

- 设备 MQTT 连接走公网 LB（需 TLS，EMQX 监听 8883）
- App → EMQX 走内网（Adapter 发命令不需要公网绕行）
- AI 推理服务与主应用同内网，gRPC 延迟可控

## 应用模块结构

```
drone-platform/
├── drone-platform-app/              # 主应用启动模块
├── drone-platform-adapter/          # 协议适配层
│   ├── adapter-core/                # Adapter 抽象接口
│   ├── adapter-dji-cloud/           # DJI Cloud API Adapter
│   ├── adapter-vendor-template/     # 新厂商 Adapter 模板
│   └── adapter-http-generic/        # 通用 HTTP 厂商 Adapter
├── drone-platform-core/             # 核心领域模型 + 事件定义
├── drone-platform-service/          # 核心业务服务（telemetry/command/task 关系紧密）
│   ├── telemetry/                   # 遥测处理管线
│   ├── command/                     # 命令下发
│   └── task/                        # 任务管理
├── drone-platform-alert/            # 告警引擎（独立模块，有独立规则存储和评估逻辑）
├── drone-platform-detection/        # AI 检测 + 结果推送（独立模块，有独立推理和推送依赖）
├── drone-platform-stream/           # 流媒体抽象层
├── drone-platform-inference/        # AI 推理抽象层
└── drone-platform-api/              # REST API (Controller)
```

## 技术栈

| 组件 | 技术选型 | 说明 |
|------|---------|------|
| 基础框架 | mila-framework (Spring Boot 4.0) | 开发脚手架，注意：4.0 为 milestone 版本，需验证核心依赖兼容性 |
| MQTT Broker | EMQX | 集群、MQTT 5.0 |
| 流媒体 | SRS | RTMP/RTSP/WebRTC |
| 时序存储 | PostgreSQL + TimescaleDB | 遥测历史 |
| 关系存储 | PostgreSQL + MyBatisFlex | 业务数据 |
| 缓存 | Redis | 设备状态、实时数据 |
| 认证 | Sa-Token | RBAC + 双层视图 |
| AI 推理 | 可插拔 | 内置模型 + 第三方 API |
| 事件总线 | Redis Pub/Sub | 可升级为 Redis Streams（持久化+消费者组） |

## 不在本次范围

- 前端 UI 设计与实现
- AI 模型训练与模型管理
- 无人机固件 OTA 升级
- 3D 地图与航线编辑器
- 计费与运营统计

## 可观测性

| 维度 | 方案 | 关键指标 |
|------|------|---------|
| Tracing | Micrometer Tracing + Zipkin/Jaeger | 遥测端到端延迟、命令下发→确认全链路 |
| Metrics | Micrometer + Prometheus | 遥测接收速率、命令成功率/延迟、MQTT 连接数、告警触发率、AI 推理延迟 |
| Logging | mila-framework TraceIdFilter + JsonLogLayout | 扩展 MQTT 消息的 traceId 传播（Adapter 层注入） |

- MQTT 消息的 trace 传播：Adapter InboundHandler 从消息 payload 提取或生成 traceId，写入 MDC，下游日志自动关联
- 告警指标：`alert_triggered_total{rule, level, device}` → Prometheus → Grafana 告警看板

## CI/CD 与配置管理

| 组件 | 方案 |
|------|------|
| CI | GitLab CI / GitHub Actions — mvn compile + test + docker build |
| CD | Docker Compose（小规模）/ K8s Helm Chart（生产） |
| 配置中心 | Nacos 或 Apollo — 多环境配置、Adapter 动态配置热更新 |
| 镜像仓库 | Harbor（私有化）/ 阿里云 ACR（公有云） |
