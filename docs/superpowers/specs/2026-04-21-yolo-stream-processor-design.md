# YOLO 视频流推理与标注系统设计文档

> 日期：2026-04-21
> 状态：已修复
> 技术栈：Python + Ultralytics YOLO + OpenCV + FFmpeg + FastAPI + SQLite

## 1. 概述

构建一个高性能的 YOLO 视频流推理与标注系统，支持：
- 多种输入源（RTMP/RTSP 流、视频文件、图片）
- 多模型并发推理（支持用户自定义训练模型）
- 实时标注与 RTMP 推流输出
- REST API 管理（流管理、模型上传切换、Webhook）
- CPU/GPU 异构支持

## 2. 系统架构

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         FastAPI (管理平面)                              │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐ │
│  │ Stream   │  │  Model   │  │  Task    │  │  Monitor │  │  Auth    │ │
│  │ Manager  │  │  Upload  │  │  Config  │  │  Status  │  │(API Key) │ │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘  └──────────┘ │
└────────────────────────────────┬────────────────────────────────────────┘
                                 │
                    ┌────────────▼────────────┐
                    │        SQLite DB        │
                    │  ┌──────────────────┐  │
                    │  │ streams          │  │
                    │  │ models           │  │
                    │  │ api_keys         │  │
                    │  └──────────────────┘  │
                    └─────────────────────────┘
                                 │
┌────────────────────────────────▼────────────────────────────────────────┐
│                     Stream Processing Engine                           │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    Pipeline Supervisor                            │   │
│  │  ┌─────────────┐  ┌─────────────┐       ┌─────────────┐        │   │
│  │  │  Pipeline 1 │  │  Pipeline 2 │  ...  │  Pipeline N │        │   │
│  │  │  (RTMP-1)   │  │  (FILE-1)   │       │  (RTSP-N)   │        │   │
│  │  └─────────────┘  └─────────────┘       └─────────────┘        │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                 │                                       │
│  ┌─────────────────────────────▼───────────────────────────────────┐   │
│  │                      Model Pool (Hot Reload)                     │   │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐                        │   │
│  │  │ Model-1 │  │ Model-2 │  │ Model-N │   (多模型并发)             │   │
│  │  └──────────┘  └──────────┘  └──────────┘                        │   │
│  └─────────────────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────────────────┘
```

### 2.1 Pipeline 内部结构

```
输入源 ──► [Decoder] ──► [Frame Queue] ──► [Inference] ──► [Annotate] ──► [Encoder] ──► RTMP Output
              │                                      │
              └──────── 视频文件/图片 ─────────────────┘
```

**Frame Queue 背压与丢帧策略：**

| 参数 | 说明 | 默认值 |
|------|------|--------|
| queue_maxsize | 帧队列最大容量 | 30 |
| drop_policy | 队列满时丢帧策略 | `drop_oldest` |

- `drop_oldest`：丢弃队列中最旧帧，保留最新帧（适合实时监控场景）
- `drop_newest`：丢弃最新帧，处理旧帧（适合需要完整时序的场景）
- `block`：阻塞解码器直到队列有空位（可能造成 RTMP 源断连）
```

**分支说明：**
- `input_type=rtmp`：RTMP/RTSP 协议流，持续抓帧直到主动停止
- `input_type=file`：视频文件处理完毕自动停止
- `input_type=image`：单帧推理后自动停止

### 2.2 Model Pool Hot Reload

**模型切换策略：**

| 策略 | 说明 | 适用场景 |
|------|------|----------|
| graceful | 等待当前帧推理完成后切换，最长等待 5s 超时后强制切换 | 默认策略，保证结果连续性 |
| immediate | 立即切换模型，当前帧结果丢弃 | 需要快速切换的紧急场景 |

**切换流程：**
1. API 调用 `POST /models/{id}/activate`，ModelPool 标记新模型为 `pending_active`
2. 通知所有关联 Pipeline 暂停取帧（设置 switch flag）
3. 等待当前推理帧完成（graceful）或直接中断（immediate）
4. 卸载旧模型、加载新模型（如尚未加载）
5. Pipeline 恢复取帧，使用新模型推理
6. 更新 DB 中 `models.status` 和 `streams.model_id`

**GPU 显存管理：** 旧模型卸载前先释放 GPU 显存，避免与新模型共存导致 OOM。

### 2.3 Webhook 结果推送

### 2.4 Webhook 流控与背压

**批量策略：**
- `batch_by_time`：每 `batch_window_ms` 毫秒推送一批检测结果
- `batch_by_count`：累计 `batch_size` 次检测后推送

**背压机制：**
- Webhook 推送慢于推理时，Results Queue 积压
- Queue 满（`webhook_queue_maxsize=100`）时丢弃最旧结果，防止内存无限增长
- `on_failure: pause_pipeline` 可在持续失败时暂停 Pipeline

### 2.4 优雅停机

**停止流程（`PATCH /streams/{id}/stop`）：**
1. 设置 Pipeline 停止标志（switch flag = False）
2. 等待当前帧推理完成（最长 5s 超时）
3. 通知 Decoder 停止拉流
4. 释放 Pipeline 资源（线程、GPU 显存）
5. 更新 DB：`streams.status = 'stopped'`

**Supervisor 崩溃恢复：**
- 每次 Pipeline 启动时写入 PID 到 `streams.config.pid`
- Supervisor 启动时检查孤儿流（PID 不存在或已退出），自动清理并更新 DB 状态

### 2.3 Webhook 结果推送

```
Pipeline ──► [Results Queue] ──► [Webhook Dispatcher] ──► 外部 Webhook URL
                              │
                              └── [Results DB] (可选历史记录)
```

## 3. 核心模块

| 模块 | 职责 | 技术选型 |
|------|------|----------|
| **StreamManager** | 管理多路流生命周期 | asyncio (管理 I/O) + threading (推理) |
| **Pipeline** | 单路流处理管道 | OpenCV + Queue |
| **ModelPool** | 多模型加载/切换/卸载 | Ultralytics API |
| **InferenceEngine** | GPU 推理 + 结果处理 | torch/cuda |
| **Annotator** | 绘制检测框/标签 | OpenCV |
| **OutputManager** | FFmpeg 推流 | ffmpeg-python/subprocess |
| **APIServer** | 管理接口 | FastAPI |
| **WebhookDispatcher** | 异步推送检测结果 | asyncio + aiohttp |

### 3.1 并发模型

```
┌─────────────────────────────────────────────────┐
│           asyncio 事件循环 (主线程)               │
│  ┌──────────┐  ┌──────────┐  ┌──────────────┐  │
│  │ FastAPI  │  │ Webhook  │  │ DB 写入      │  │
│  │ 路由     │  │ 推送     │  │ (单写者)     │  │
│  └──────────┘  └──────────┘  └──────────────┘  │
└──────────────────────┬──────────────────────────┘
                       │ asyncio.run_in_executor
┌──────────────────────▼──────────────────────────┐
│          ThreadPoolExecutor (推理线程池)          │
│  ┌─────────────┐  ┌─────────────┐               │
│  │ Pipeline 1  │  │ Pipeline N  │  ...          │
│  │ (解码+推理) │  │ (解码+推理) │               │
│  └─────────────┘  └─────────────┘               │
└─────────────────────────────────────────────────┘
```

**职责划分：**
- **asyncio 事件循环**：仅负责管理平面（API 路由、Webhook 推送、DB 读写等 I/O 密集操作）
- **ThreadPoolExecutor**：每路 Pipeline 的解码+推理在独立线程中运行，避免阻塞事件循环
- **线程间通信**：Pipeline 线程通过 `asyncio.run_coroutine_threadsafe()` 向事件循环提交 DB 更新和 Webhook 推送任务

## 4. 数据库模型

### 4.0 并发写入策略

SQLite 在多线程写入时会产生锁竞争。采用以下策略：

- **WAL 模式**：启用 `PRAGMA journal_mode=WAL`，允许读写并发
- **单写者模式**：所有 DB 写操作通过 asyncio 事件循环中的专用写协程串行化，Pipeline 线程通过 `run_coroutine_threadsafe` 提交写请求
- **大流量场景**：若并发流数 > 10 路，建议迁移至 PostgreSQL（docker-compose 已预留基础设施）

### 4.1 模型表 (models)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | TEXT PRIMARY KEY | UUID |
| name | TEXT NOT NULL | 模型名称 |
| path | TEXT NOT NULL | 模型文件路径 |
| type | TEXT NOT NULL | detection/segmentation/pose/classification |
| status | TEXT DEFAULT 'active' | active/inactive |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

### 4.2 流表 (streams)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | TEXT PRIMARY KEY | UUID |
| name | TEXT NOT NULL | 流名称 |
| input_url | TEXT NOT NULL | 输入源 RTMP/视频路径/图片 |
| input_type | TEXT NOT NULL | rtmp/file/image |
| output_url | TEXT | 输出 RTMP（可 null） |
| model_id | TEXT REFERENCES models(id) ON DELETE RESTRICT | 关联模型（删除模型前需先删除关联流） |
| config | JSON NOT NULL | 推理配置 |
| status | TEXT DEFAULT 'stopped' | running/stopped/error |
| fps | REAL | 当前帧率 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

**行为约定：**
- `output_url = null`：仅执行推理+标注+Webhook，不输出 RTMP 流
- `input_type = image`：推理单帧后自动停止

### 4.3 API Key 表 (api_keys)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | TEXT PRIMARY KEY | UUID |
| key_hash | TEXT NOT NULL UNIQUE | API Key 哈希值 |
| name | TEXT NOT NULL | Key 名称 |
| is_active | BOOLEAN DEFAULT true | 是否启用 |
| created_at | DATETIME | 创建时间 |

## 5. API 设计

### 5.1 认证

所有 API 通过 `X-API-Key` 请求头认证。

**认证流程：**
1. 客户端携带 `X-API-Key: <明文Key>` 请求
2. 服务端计算 `hash = bcrypt.hash(raw_key)` 并与 DB 中 `key_hash` 比对
3. 若 `is_active=false`，返回 `401 Unauthorized`
4. 每次 POST/PUT/PATCH/DELETE 操作均需认证，GET 操作可选择是否认证

**Key 生命周期：**
- 创建时返回明文 Key（仅此一次，后续不可找回）
- 支持禁用（`is_active=false`）而不删除，用于临时吊销访问权限
- 删除 Key 前需确保无活跃流关联

### 5.2 流管理 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/streams | 创建推理管道 |
| GET | /api/v1/streams | 列出所有流（分页） |
| GET | /api/v1/streams/{id} | 获取流详情 |
| PUT | /api/v1/streams/{id} | 更新流配置 |
| PATCH | /api/v1/streams/{id}/start | 启动流 |
| PATCH | /api/v1/streams/{id}/stop | 停止流 |
| DELETE | /api/v1/streams/{id} | 删除流 |

### 5.3 模型管理 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/models | 注册模型（上传文件或路径） |
| GET | /api/v1/models | 列出所有模型 |
| GET | /api/v1/models/{id} | 获取模型详情 |
| DELETE | /api/v1/models/{id} | 删除模型 |
| PATCH | /api/v1/models/{id}/activate | 激活模型 |

### 5.4 监控 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/v1/streams/{id}/stats | 单流统计 |
| GET | /api/v1/monitor/stats | 全局统计 |
| GET | /api/v1/monitor/health | 健康检查 |

### 5.5 认证管理 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/keys | 创建 API Key |
| GET | /api/v1/keys | 列出 Key（不返回明文） |
| DELETE | /api/v1/keys/{id} | 删除 Key |

### 5.6 创建流请求体示例

```json
{
  "name": "camera-01",
  "input_url": "rtmp://192.168.1.100/live/stream",
  "input_type": "rtmp",
  "output_url": "rtmp://localhost/live/camera-01-annotated",
  "model_id": "yolo-person-v1",
  "config": {
    "conf_threshold": 0.3,
    "iou_threshold": 0.45,
    "classes": ["person", "car"],
    "batch_size": 1,
    "webhook": {
      "enabled": true,
      "url": "https://your-server.com/webhook",
      "interval_ms": 1000,
      "include_image": true,
      "image_quality": 80,
      "max_image_width": 640,
      "auth_header": "X-Webhook-Secret",
      "auth_value": "your-secret-value"
    }
  }
}
```

### 5.7 streams.config JSON Schema

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["conf_threshold", "iou_threshold"],
  "properties": {
    "conf_threshold": {
      "type": "number", "minimum": 0, "maximum": 1,
      "description": "置信度阈值，默认 0.25"
    },
    "iou_threshold": {
      "type": "number", "minimum": 0, "maximum": 1,
      "description": "NMS IOU 阈值，默认 0.45"
    },
    "classes": {
      "type": "array", "items": { "type": "string" },
      "description": "仅检测指定类别，为空时检测所有类别"
    },
    "batch_size": {
      "type": "integer", "minimum": 1, "maximum": 8, "default": 1,
      "description": "批推理大小，GPU 模式下可提升吞吐"
    },
    "queue_maxsize": {
      "type": "integer", "minimum": 1, "maximum": 100, "default": 30,
      "description": "帧队列最大容量"
    },
    "drop_policy": {
      "type": "string", "enum": ["drop_oldest", "drop_newest", "block"],
      "default": "drop_oldest",
      "description": "队列满时丢帧策略"
    },
    "webhook": { "$ref": "#/definitions/webhook" }
  },
  "definitions": {
    "webhook": {
      "type": "object",
      "properties": {
        "enabled": { "type": "boolean", "default": false },
        "url": { "type": "string", "format": "uri" },
        "interval_ms": { "type": "integer", "minimum": 100, "maximum": 60000, "default": 1000 },
        "include_image": { "type": "boolean", "default": true },
        "include_annotated_only": { "type": "boolean", "default": false },
        "image_quality": { "type": "integer", "minimum": 1, "maximum": 100, "default": 80 },
        "max_image_width": { "type": "integer", "minimum": 100, "maximum": 3840, "default": 640 },
        "auth_header": { "type": "string" },
        "auth_value": { "type": "string" },
        "retry_max": { "type": "integer", "minimum": 0, "maximum": 10, "default": 3 },
        "on_failure": { "type": "string", "enum": ["log_and_skip", "pause_pipeline"], "default": "log_and_skip" }
      }
    }
  }
}

## 6. Webhook 推送内容

```json
{
  "event": "detection",
  "stream_id": "camera-01",
  "timestamp": "2026-04-21T10:30:00Z",
  "frame_id": 12345,
  "detections": [
    {
      "class": "person",
      "confidence": 0.95,
      "bbox": [x1, y1, x2, y2],
      "class_id": 0
    },
    {
      "class": "car",
      "confidence": 0.88,
      "bbox": [100, 200, 300, 450],
      "class_id": 2
    }
  ],
  "image_url": "https://storage.example.com/snapshots/camera-01-12345.jpg"
}
```

## 7. Webhook 配置参数

| 参数 | 说明 | 默认值 |
|------|------|--------|
| enabled | 是否启用 Webhook | false |
| url | Webhook 目标 URL | - |
| interval_ms | 推送间隔（ms） | 1000 |
| include_image | 是否包含图片 | true |
| include_annotated_only | 发送前是否仅保留标注区域（防止敏感信息泄露） | false |
| image_quality | JPEG 质量 0-100 | 80 |
| max_image_width | 图片最大宽度（px） | 640 |
| auth_header | 认证 Header 名称 | - |
| auth_value | 认证 Header 值 | - |

### 8.1 Webhook 失败处理

| 参数 | 说明 | 默认值 |
|------|------|--------|
| retry_max | 最大重试次数 | 3 |
| retry_backoff | 重试退避策略 | `exponential` |
| retry_backoff_base | 退避基数（秒） | 1 |
| timeout | 单次请求超时（秒） | 5 |
| on_failure | 重试耗尽后行为 | `log_and_skip` |

**退避计算：** 第 n 次重试等待 `retry_backoff_base * 2^n` 秒（1s, 2s, 4s）。

**on_failure 选项：**
- `log_and_skip`：记录错误日志，丢弃该次推送，Pipeline 继续运行（默认）
- `pause_pipeline`：暂停 Pipeline 并标记 `streams.status = error`

### 8.2 Webhook 认证值安全存储

`auth_value` 不以明文持久化到 DB。采用以下策略：

- **存储**：`auth_value` 使用 AES-256-GCM 加密后存入 `streams.config`，加密密钥从环境变量 `WEBHOOK_ENCRYPT_KEY` 读取
- **运行时**：解密仅在 WebhookDispatcher 推送时进行，解密后值不缓存超过单次请求生命周期
- **API 响应**：`GET /streams/{id}` 返回的 config 中 `auth_value` 字段始终为 `***`

## 8. 项目结构

```
yolo-stream-processor/
├── app/
│   ├── __init__.py
│   ├── main.py                    # FastAPI 入口
│   ├── config.py                  # 配置管理
│   ├── api/
│   │   ├── __init__.py
│   │   ├── routes/
│   │   │   ├── streams.py         # 流管理路由
│   │   │   ├── models.py          # 模型管理路由
│   │   │   ├── monitor.py         # 监控路由
│   │   │   └── keys.py            # 认证路由
│   │   ├── deps.py                # 依赖注入（认证等）
│   │   └── middleware.py          # 中间件
│   ├── core/
│   │   ├── __init__.py
│   │   ├── stream_manager.py      # 流生命周期管理
│   │   ├── model_pool.py          # 模型池管理
│   │   └── pipeline.py            # 单流处理管道
│   ├── engine/
│   │   ├── __init__.py
│   │   ├── inference.py           # 推理引擎
│   │   ├── annotator.py           # 标注器
│   │   └── decoder.py             # 视频解码
│   ├── output/
│   │   ├── __init__.py
│   │   ├── rtmp_output.py         # RTMP 推流
│   │   └── snapshot.py            # 快照保存
│   ├── webhook/
│   │   ├── __init__.py
│   │   └── dispatcher.py          # Webhook 推送
│   ├── db/
│   │   ├── __init__.py
│   │   ├── database.py            # SQLite 连接
│   │   ├── models.py              # ORM 模型
│   │   └── repositories/          # 数据访问层
│   └── utils/
│       ├── __init__.py
│       └── helpers.py
├── models/                        # YOLO 模型存储目录
├── tests/
│   ├── __init__.py
│   ├── test_stream_manager.py
│   ├── test_model_pool.py
│   ├── test_pipeline.py
│   └── test_api.py
├── requirements.txt
├── config.yaml
├── Dockerfile
├── docker-compose.yaml
└── README.md
```

## 9. 依赖

```
ultralytics>=8.0.0
opencv-python>=4.8.0
torch>=2.0.0
ffmpeg-python>=0.2.0
fastapi>=0.100.0
uvicorn>=0.22.0
sqlalchemy>=2.0.0
aiosqlite>=0.19.0
aiohttp>=3.8.0
pydantic>=2.0.0
python-multipart>=0.0.6
pyyaml>=6.0
pillow>=10.0.0
```

## 10. 部署方式

### 10.1 Docker 部署

```yaml
# docker-compose.yaml
services:
  yolo-stream-processor:
    build: .
    ports:
      - "8000:8000"
    volumes:
      - ./models:/app/models
      - ./data:/app/data
    environment:
      - CUDA_VISIBLE_DEVICES=0
    restart: unless-stopped
```

### 10.2 GPU 支持

- 使用 `nvidia/cuda` 基础镜像
- Docker Compose 配置 `deploy.resources.reservations.devices`

## 11. 性能指标目标

| 指标 | 目标值 | 测量方法 |
|------|--------|----------|
| 单流推理延迟 | < 50ms (GPU) / < 200ms (CPU) | 从帧进入 Inference Engine 到结果输出的端到端耗时（不含解码/编码） |
| 支持并发流数 | 10+ 路 | 10 路 1080p@30fps 流同时运行，帧率不下降 |
| 帧率 | 25-30 FPS | 输出帧率（标注后），使用 Prometheus `video_output_fps` 指标 |
| GPU 利用率 | > 80% | `nvidia-smi dmon -c 5` 采样 5 秒平均值 |

## 12. 安全考量

- API Key 存储使用 bcrypt 哈希
- 模型文件路径校验，防止路径遍历
- 输入源 URL 校验（仅允许 rtmp://, rtsp://, file://, http://, https:// 协议）
- Webhook URL 需 HTTPS（代码层面强制验证，非空时拒绝非 HTTPS）
- `auth_value` 使用 AES-256-GCM 加密存储（详见 8.2 节）

## 13. 快照存储方案

**存储位置：** 本地文件系统（`/app/data/snapshots/`）或 S3 兼容对象存储（通过 `SNAPSHOT_STORAGE_TYPE` 配置）

**处理流程：**
1. `include_image=true` 时，标注帧由 `output/snapshot.py` 处理
2. 按 `image_quality` 和 `max_image_width` 压缩
3. 生成 UUID 文件名避免路径遍历：`{stream_id}-{frame_id}.jpg`
4. 上传到存储（本地或 S3）
5. 生成 `image_url` 写入 Webhook payload

**清理策略：**
- 本地存储：`SNAPSHOT_RETENTION_DAYS=7`（默认 7 天清理）
- S3：配置生命周期规则自动过期
