# YOLO 低空无人机目标检测平台设计文档

> 日期：2026-04-21
> 状态：草稿
> 架构：轻量单进程 + Pipeline Supervisor

## 1. 概述

构建一个基于 YOLOv26 的低空无人机目标检测平台，支持：

- **多种输入源**：RTSP 实时流、RTMP 推流、图片、视频文件
- **多种输出方式**：RTMP 推流 + 本地文件 + Webhook 回调
- **多模型管理**：默认 YOLOv26x-obb.pt，支持上传自定义模型，API 热切换
- **实时标注**：OBB 旋转框 + 目标跟踪 ID + 自定义叠加层
- **异常容错**：自动重连、降级处理、Webhook 重试

**性能目标**：4-8 路并发（GPU）/ 2-4 路（边缘设备），端到端延迟 < 500ms

## 2. 系统架构

```
┌──────────────────────────────────────────────────────┐
│                    FastAPI (管理平面)                 │
│  Stream API  │  Model API  │  Webhook Config  │  Auth│
└──────────────────────────┬───────────────────────────┘
                           │
                    SQLite (本地文件库)
                           │
┌──────────────────────────▼───────────────────────────┐
│              Pipeline Supervisor (单进程)              │
│  ┌─────────────────────────────────────────────┐    │
│  │ Pipeline 1 (RTSP-1) → Decoder→推理→标注→Encoder│    │
│  │ Pipeline 2 (RTMP-1) → Decoder→推理→标注→Encoder│    │
│  │ Pipeline N (FILE-N) → Decoder→推理→标注→Encoder│    │
│  └─────────────────────────────────────────────┘    │
│                           │                          │
│              ┌────────────▼────────────┐            │
│              │      Model Pool          │            │
│              │  (YOLOv26x-obb.pt)      │            │
│              │  + Custom Models        │            │
│              └─────────────────────────┘            │
└──────────────────────────────────────────────────────┘
                           │
          ┌────────────────┼────────────────┐
          ▼                ▼                ▼
      RTMP Output     File Output     Webhook Push
```

### 2.1 Pipeline 内部结构

```
RTSP/RTMP 输入
     │
     ▼
[Decoder] ───► [Frame Queue] ───► [Inference (YOLOv26)]
     │                                    │
     │ (图片/视频文件直接解码)              │
     │                                    ▼
     │                            [OBB 检测结果]
     │                                    │
     │                                    ▼
     │                              [Annotator]
     │                              检测框+跟踪ID+叠加层
     │                                    │
     ▼                                    ▼
[Encoder]                           [Output]
RTMP 推流                              │
     │                          ┌──────┴──────┐
     │                          ▼             ▼
     ▼                     RTMP Stream   Webhook Push
本地 MP4 文件                 │             │
                              ▼             ▼
                        业务系统      回调结果
```

### 2.2 模型热切换

- API 调用 `PATCH /models/{id}/activate`
- ModelPool 标记新模型为 `pending_active`
- 等待当前帧推理完成后切换（graceful），最长 5s 超时
- 切换期间 Pipeline 暂停取帧，避免推理冲突

## 3. API 规范

### 3.1 模型管理

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/models` | 列出所有模型 |
| `POST` | `/models` | 上传新模型（.pt/.onnx） |
| `DELETE` | `/models/{id}` | 删除模型 |
| `PATCH` | `/models/{id}/activate` | 激活模型（热切换） |

### 3.2 流管理

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/streams` | 创建流（RTSP/RTMP/文件） |
| `GET` | `/streams` | 列出所有流 |
| `GET` | `/streams/{id}` | 获取流状态 |
| `PATCH` | `/streams/{id}/start` | 启动流 |
| `PATCH` | `/streams/{id}/stop` | 停止流 |
| `DELETE` | `/streams/{id}` | 删除流 |

### 3.3 Webhook 与系统

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/webhooks` | 配置 Webhook URL |
| `GET` | `/health` | 健康检查 |

### 3.4 创建流请求体

```json
{
  "name": "camera-001",
  "input_url": "rtsp://192.168.1.100:554/stream1",
  "input_type": "rtsp",
  "model_id": "yolo26x-obb",
  "output": {
    "rtmp_url": "rtmp://cdn.example.com/live/result",
    "save_file": true,
    "file_path": "/output/camera-001.mp4"
  },
  "webhook_url": "https://your-server.com/callback"
}
```

## 4. 标注输出

### 4.1 标注内容

- **OBB 旋转框**：绘制带旋转角度的检测框
- **目标跟踪 ID**：同一目标跨帧分配唯一 ID
- **自定义叠加层**：统计信息、热力图、ROI 区域（可开关）

### 4.2 输出方式

| 方式 | 说明 |
|------|------|
| RTMP 推流 | 标注后重新编码推流到 RTMP 地址 |
| 本地文件 | 保存为 MP4/AVI 文件 |
| Webhook | HTTP POST 推送检测结果到业务系统 |

## 5. 异常处理

| 场景 | 处理策略 |
|------|---------|
| RTSP 连接断开 | 自动重连（3次，间隔 5s/10s/30s） |
| 推理超时 | 跳过当前帧，继续下一帧 |
| GPU OOM | 降级到 CPU 推理，或丢弃当前帧 |
| 模型加载失败 | 记录日志，返回 500，保留原模型 |
| Webhook 推送失败 | 队列缓存 + 重试（最多 3 次） |

## 6. 技术栈

- **推理框架**：Ultralytics YOLOv26 + OpenCV
- **流处理**：FFmpeg（解码/编码/RTMP 推流）
- **Web 框架**：FastAPI
- **数据库**：SQLite（WAL 模式）
- **目标跟踪**：ByteTrack 或 SORT
- **容器化**：Docker

## 7. 部署方式

- **Docker 容器**：打包成镜像，K8s 或单机 Docker 运行
- **纯 Python 服务**：直接在服务器/边缘设备运行
- **边缘优化**：支持 NVIDIA Jetson、RK3588（TensorRT 加速）
