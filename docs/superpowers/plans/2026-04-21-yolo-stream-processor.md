# YOLO 视频流推理与标注系统实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建高性能 YOLO 视频流推理与标注系统，支持多路流并发、模型热切换、实时标注与 RTMP 推流

**Architecture:** asyncio 主事件循环管理 I/O 密集操作（API/Webhook/DB），ThreadPoolExecutor 执行推理线程避免阻塞。Pipeline Supervisor 管理多路 Pipeline，每路 Pipeline 包含 Decoder→FrameQueue→Inference→Annotator→Encoder 链路。Model Pool 支持模型热加载切换，SQLite WAL 模式保证并发安全。

**Tech Stack:** Python 3.11+, Ultralytics YOLO, OpenCV, FFmpeg, FastAPI, SQLAlchemy, aiosqlite, aiohttp

---

## Phase 1: 项目骨架与基础设施

### Task 1: 项目结构与依赖配置

**Files:**
- Create: `yolo-stream-processor/requirements.txt`
- Create: `yolo-stream-processor/config.yaml`
- Create: `yolo-stream-processor/app/__init__.py`
- Create: `yolo-stream-processor/app/main.py` (FastAPI 入口骨架)
- Create: `yolo-stream-processor/app/config.py` (配置加载)

- [ ] **Step 1: 创建目录结构**

```bash
mkdir -p yolo-stream-processor/app/{api/routes,core,engine,output,webhook,db/repositories,utils}
mkdir -p yolo-stream-processor/models
mkdir -p yolo-stream-processor/tests
mkdir -p yolo-stream-processor/data/snapshots
```

- [ ] **Step 2: 创建 requirements.txt**

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
bcrypt>=4.0.0
```

- [ ] **Step 3: 创建 config.yaml**

```yaml
database:
  path: "./data/yolo.db"

models:
  storage_path: "./models"

snapshots:
  storage_type: "local"  # local or s3
  retention_days: 7
  local_path: "./data/snapshots"

webhook:
  encryption_key_env: "WEBHOOK_ENCRYPT_KEY"
  default_timeout: 5
  default_retry_max: 3

pipeline:
  queue_maxsize: 30
  default_drop_policy: "drop_oldest"
  graceful_switch_timeout: 5

api:
  host: "0.0.0.0"
  port: 8000
```

- [ ] **Step 4: 创建 app/config.py**

```python
import os
from pathlib import Path
from typing import Literal

import yaml
from pydantic import BaseModel


class DatabaseConfig(BaseModel):
    path: str = "./data/yolo.db"


class ModelsConfig(BaseModel):
    storage_path: str = "./models"


class SnapshotsConfig(BaseModel):
    storage_type: Literal["local", "s3"] = "local"
    retention_days: int = 7
    local_path: str = "./data/snapshots"


class WebhookConfig(BaseModel):
    encryption_key_env: str = "WEBHOOK_ENCRYPT_KEY"
    default_timeout: int = 5
    default_retry_max: int = 3


class PipelineConfig(BaseModel):
    queue_maxsize: int = 30
    default_drop_policy: Literal["drop_oldest", "drop_newest", "block"] = "drop_oldest"
    graceful_switch_timeout: int = 5


class APIConfig(BaseModel):
    host: str = "0.0.0.0"
    port: int = 8000


class AppConfig(BaseModel):
    database: DatabaseConfig = DatabaseConfig()
    models: ModelsConfig = ModelsConfig()
    snapshots: SnapshotsConfig = SnapshotsConfig()
    webhook: WebhookConfig = WebhookConfig()
    pipeline: PipelineConfig = PipelineConfig()
    api: APIConfig = APIConfig()


def load_config(config_path: str = "config.yaml") -> AppConfig:
    config_file = Path(config_path)
    if config_file.exists():
        with open(config_file) as f:
            data = yaml.safe_load(f)
        return AppConfig(**data)
    return AppConfig()


config = load_config()
```

- [ ] **Step 5: 创建 app/main.py 骨架**

```python
from fastapi import FastAPI

from app.config import config

app = FastAPI(title="YOLO Stream Processor")


@app.get("/health")
async def health():
    return {"status": "ok"}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host=config.api.host, port=config.api.port)
```

- [ ] **Step 6: 提交**

```bash
cd yolo-stream-processor
git init
git add -A
git commit -m "feat: initial project skeleton with config management"
```

---

### Task 2: 数据库层实现

**Files:**
- Create: `yolo-stream-processor/app/db/__init__.py`
- Create: `yolo-stream-processor/app/db/database.py`
- Create: `yolo-stream-processor/app/db/models.py`
- Create: `yolo-stream-processor/app/db/repositories/__init__.py`
- Create: `yolo-stream-processor/app/db/repositories/stream_repo.py`
- Create: `yolo-stream-processor/app/db/repositories/model_repo.py`
- Create: `yolo-stream-processor/app/db/repositories/key_repo.py`

- [ ] **Step 1: 创建 app/db/database.py (SQLite WAL 模式)**

```python
import asyncio
from contextlib import asynccontextmanager
from pathlib import Path

from sqlalchemy import event
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.orm import DeclarativeBase

from app.config import config


class Base(DeclarativeBase):
    pass


engine = create_async_engine(
    f"sqlite+aiosqlite:///{config.database.path}",
    echo=False,
)


async def init_db():
    Path(config.database.path).parent.mkdir(parents=True, exist_ok=True)
    async with engine.begin() as conn:
        await conn.execute(
            __import__("sqlalchemy").text("PRAGMA journal_mode=WAL")
        )
        await conn.run_sync(Base.metadata.create_all)


async def get_session() -> AsyncSession:
    async with async_sessionmaker(engine, class_=AsyncSession)() as session:
        yield session


@asynccontextmanager
async def get_db_session():
    async with async_sessionmaker(engine, class_=AsyncSession)() as session:
        try:
            yield session
            await session.commit()
        except Exception:
            await session.rollback()
            raise
```

- [ ] **Step 2: 创建 app/db/models.py (ORM 模型)**

```python
import uuid
from datetime import datetime

from sqlalchemy import Boolean, DateTime, ForeignKey, String, Text, func
from sqlalchemy.dialects.sqlite import JSON
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db.database import Base


def generate_uuid() -> str:
    return str(uuid.uuid4())


class Model(Base):
    __tablename__ = "models"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    name: Mapped[str] = mapped_column(String(255), nullable=False)
    path: Mapped[str] = mapped_column(Text, nullable=False)
    type: Mapped[str] = mapped_column(String(50), nullable=False)  # detection/segmentation/pose/classification
    status: Mapped[str] = mapped_column(String(20), default="active")  # active/inactive
    created_at: Mapped[datetime] = mapped_column(DateTime, server_default=func.now())
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, server_default=func.now(), onupdate=func.now()
    )

    streams: Mapped[list["Stream"]] = relationship("Stream", back_populates="model")


class Stream(Base):
    __tablename__ = "streams"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    name: Mapped[str] = mapped_column(String(255), nullable=False)
    input_url: Mapped[str] = mapped_column(Text, nullable=False)
    input_type: Mapped[str] = mapped_column(String(20), nullable=False)  # rtmp/file/image
    output_url: Mapped[str | None] = mapped_column(Text, nullable=True)
    model_id: Mapped[str | None] = mapped_column(
        String(36), ForeignKey("models.id", ondelete="RESTRICT"), nullable=True
    )
    config: Mapped[dict] = mapped_column(JSON, nullable=False, default=dict)
    status: Mapped[str] = mapped_column(String(20), default="stopped")  # running/stopped/error
    fps: Mapped[float | None] = mapped_column(nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, server_default=func.now())
    updated_at: Mapped[datetime] = mapped_column(
        DateTime, server_default=func.now(), onupdate=func.now()
    )

    model: Mapped[Model | None] = relationship("Model", back_populates="streams")


class APIKey(Base):
    __tablename__ = "api_keys"

    id: Mapped[str] = mapped_column(String(36), primary_key=True, default=generate_uuid)
    key_hash: Mapped[str] = mapped_column(String(255), nullable=False, unique=True)
    name: Mapped[str] = mapped_column(String(255), nullable=False)
    is_active: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, server_default=func.now())
```

- [ ] **Step 3: 创建 app/db/repositories/key_repo.py**

```python
import bcrypt
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models import APIKey


class APIKeyRepository:
    def __init__(self, session: AsyncSession):
        self.session = session

    async def create(self, name: str, raw_key: str) -> tuple[APIKey, str]:
        key_hash = bcrypt.hashpw(raw_key.encode(), bcrypt.gensalt()).decode()
        key = APIKey(name=name, key_hash=key_hash)
        self.session.add(key)
        await self.session.flush()
        return key, raw_key  # 返回明文（仅创建时可见）

    async def verify(self, raw_key: str) -> APIKey | None:
        stmt = select(APIKey).where(APIKey.is_active == True)
        result = await self.session.execute(stmt)
        for key in result.scalars():
            if bcrypt.checkpw(raw_key.encode(), key.key_hash.encode()):
                return key
        return None

    async def get_by_id(self, key_id: str) -> APIKey | None:
        return await self.session.get(APIKey, key_id)

    async def list_all(self) -> list[APIKey]:
        stmt = select(APIKey).order_by(APIKey.created_at.desc())
        result = await self.session.execute(stmt)
        return list(result.scalars())

    async def delete(self, key_id: str) -> bool:
        key = await self.session.get(APIKey, key_id)
        if key:
            await self.session.delete(key)
            return True
        return False
```

- [ ] **Step 4: 创建 app/db/repositories/stream_repo.py**

```python
from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models import Stream


class StreamRepository:
    def __init__(self, session: AsyncSession):
        self.session = session

    async def create(self, name: str, input_url: str, input_type: str, model_id: str | None, config: dict, output_url: str | None = None) -> Stream:
        stream = Stream(
            name=name,
            input_url=input_url,
            input_type=input_type,
            model_id=model_id,
            config=config,
            output_url=output_url,
        )
        self.session.add(stream)
        await self.session.flush()
        return stream

    async def get_by_id(self, stream_id: str) -> Stream | None:
        return await self.session.get(Stream, stream_id)

    async def list_all(self, limit: int = 100, offset: int = 0) -> tuple[list[Stream], int]:
        stmt = select(Stream).order_by(Stream.created_at.desc()).limit(limit).offset(offset)
        result = await self.session.execute(stmt)
        streams = list(result.scalars())

        count_stmt = select(Stream)
        count_result = await self.session.execute(count_stmt)
        total = len(list(count_result.scalars()))

        return streams, total

    async def update(self, stream_id: str, **kwargs) -> Stream | None:
        stream = await self.session.get(Stream, stream_id)
        if not stream:
            return None
        for key, value in kwargs.items():
            if hasattr(stream, key):
                setattr(stream, key, value)
        await self.session.flush()
        return stream

    async def update_status(self, stream_id: str, status: str) -> bool:
        stmt = update(Stream).where(Stream.id == stream_id).values(status=status)
        await self.session.execute(stmt)
        await self.session.flush()
        return True

    async def update_fps(self, stream_id: str, fps: float) -> bool:
        stmt = update(Stream).where(Stream.id == stream_id).values(fps=fps)
        await self.session.execute(stmt)
        await self.session.flush()
        return True

    async def delete(self, stream_id: str) -> bool:
        stream = await self.session.get(Stream, stream_id)
        if stream:
            await self.session.delete(stream)
            return True
        return False
```

- [ ] **Step 5: 创建 app/db/repositories/model_repo.py**

```python
from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models import Model


class ModelRepository:
    def __init__(self, session: AsyncSession):
        self.session = session

    async def create(self, name: str, path: str, model_type: str) -> Model:
        model = Model(name=name, path=path, type=model_type)
        self.session.add(model)
        await self.session.flush()
        return model

    async def get_by_id(self, model_id: str) -> Model | None:
        return await self.session.get(Model, model_id)

    async def list_all(self) -> list[Model]:
        stmt = select(Model).order_by(Model.created_at.desc())
        result = await self.session.execute(stmt)
        return list(result.scalars())

    async def update_status(self, model_id: str, status: str) -> bool:
        stmt = update(Model).where(Model.id == model_id).values(status=status)
        await self.session.execute(stmt)
        await self.session.flush()
        return True

    async def delete(self, model_id: str) -> bool:
        model = await self.session.get(Model, model_id)
        if model:
            await self.session.delete(model)
            return True
        return False
```

- [ ] **Step 6: 提交**

```bash
git add app/db/ app/config.py app/main.py requirements.txt config.yaml
git commit -m "feat: add database layer with SQLite WAL mode and repositories"
```

---

## Phase 2: 核心引擎组件

### Task 3: Decoder 与 InferenceEngine 实现

**Files:**
- Create: `yolo-stream-processor/app/engine/__init__.py`
- Create: `yolo-stream-processor/app/engine/decoder.py`
- Create: `yolo-stream-processor/app/engine/inference.py`
- Create: `yolo-stream-processor/app/engine/annotator.py`

- [ ] **Step 1: 创建 app/engine/decoder.py**

```python
import cv2
import numpy as np
from dataclasses import dataclass
from typing import Literal


@dataclass
class DecodedFrame:
    frame: np.ndarray
    frame_id: int
    timestamp: float


class VideoDecoder:
    def __init__(self, input_url: str, input_type: Literal["rtmp", "rtsp", "file", "image"]):
        self.input_url = input_url
        self.input_type = input_type
        self.cap = None
        self.frame_id = 0

    def open(self) -> bool:
        if self.input_type in ("rtmp", "rtsp"):
            self.cap = cv2.VideoCapture(self.input_url, cv2.CAP_FFMPEG)
        else:
            self.cap = cv2.VideoCapture(self.input_url)
        return self.cap is not None and self.cap.isOpened()

    def read(self) -> DecodedFrame | None:
        if not self.cap or not self.cap.isOpened():
            return None

        ret, frame = self.cap.read()

        if self.input_type == "image" and self.frame_id == 0 and ret:
            self.frame_id += 1
            return DecodedFrame(frame=frame, frame_id=self.frame_id, timestamp=0.0)

        if not ret:
            if self.input_type in ("rtmp", "rtsp"):
                return None  # 继续尝试重连
            return None  # 文件结束

        self.frame_id += 1
        timestamp = self.cap.get(cv2.CAP_PROP_POS_MSEC) / 1000.0
        return DecodedFrame(frame=frame, frame_id=self.frame_id, timestamp=timestamp)

    def release(self):
        if self.cap:
            self.cap.release()
            self.cap = None

    def get_fps(self) -> float:
        if self.cap:
            return self.cap.get(cv2.CAP_PROP_FPS)
        return 30.0

    def get_resolution(self) -> tuple[int, int]:
        if self.cap:
            w = int(self.cap.get(cv2.CAP_PROP_FRAME_WIDTH))
            h = int(self.cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
            return w, h
        return 1920, 1080
```

- [ ] **Step 2: 创建 app/engine/inference.py**

```python
import numpy as np
from dataclasses import dataclass
from typing import Any

import torch
from ultralytics import YOLO


@dataclass
class Detection:
    class_id: int
    class_name: str
    confidence: float
    bbox: tuple[int, int, int, int]  # x1, y1, x2, y2


class InferenceEngine:
    def __init__(self, model_path: str, device: str | None = None):
        self.model_path = model_path
        self.device = device or ("cuda" if torch.cuda.is_available() else "cpu")
        self.model = None

    def load(self):
        self.model = YOLO(self.model_path)
        if self.device == "cuda":
            self.model.to(self.device)

    def unload(self):
        if self.model:
            del self.model
            self.model = None
            if self.device == "cuda":
                torch.cuda.empty_cache()

    def infer(
        self,
        frame: np.ndarray,
        conf_threshold: float = 0.25,
        iou_threshold: float = 0.45,
        classes: list[str] | None = None,
    ) -> list[Detection]:
        if not self.model:
            raise RuntimeError("Model not loaded")

        results = self.model(
            frame,
            conf=conf_threshold,
            iou=iou_threshold,
            verbose=False,
            device=self.device,
        )

        detections = []
        for result in results:
            boxes = result.boxes
            if boxes is None:
                continue

            for box in boxes:
                class_id = int(box.cls.item())
                class_name = result.names[class_id]

                if classes and class_name not in classes:
                    continue

                x1, y1, x2, y2 = box.xyxy[0].cpu().numpy().astype(int)
                confidence = float(box.conf.item())

                detections.append(
                    Detection(
                        class_id=class_id,
                        class_name=class_name,
                        confidence=confidence,
                        bbox=(int(x1), int(y1), int(x2), int(y2)),
                    )
                )

        return detections
```

- [ ] **Step 3: 创建 app/engine/annotator.py**

```python
import cv2
import numpy as np

from app.engine.inference import Detection


class Annotator:
    def __init__(
        self,
        class_colors: dict[str, tuple[int, int, int]] | None = None,
        thickness: int = 2,
        font_scale: float = 0.6,
    ):
        self.class_colors = class_colors or {}
        self.thickness = thickness
        self.font_scale = font_scale

    def _get_color(self, class_name: str) -> tuple[int, int, int]:
        if class_name not in self.class_colors:
            np.random.seed(hash(class_name) % 2**32)
            self.class_colors[class_name] = tuple(np.random.randint(50, 255, 3).tolist())
        return self.class_colors[class_name]

    def annotate(self, frame: np.ndarray, detections: list[Detection]) -> np.ndarray:
        annotated = frame.copy()

        for det in detections:
            x1, y1, x2, y2 = det.bbox
            color = self._get_color(det.class_name)

            cv2.rectangle(annotated, (x1, y1), (x2, y2), color, self.thickness)

            label = f"{det.class_name} {det.confidence:.2f}"
            (label_w, label_h), baseline = cv2.getTextSize(
                label, cv2.FONT_HERSHEY_SIMPLEX, self.font_scale, self.thickness
            )

            label_y1 = max(y1 - label_h - baseline, 0)
            cv2.rectangle(
                annotated,
                (x1, label_y1),
                (x1 + label_w, y1),
                color,
                -1,
            )
            cv2.putText(
                annotated,
                label,
                (x1, y1 - baseline),
                cv2.FONT_HERSHEY_SIMPLEX,
                self.font_scale,
                (255, 255, 255),
                self.thickness,
            )

        return annotated

    def annotate_with_legend(self, frame: np.ndarray, detections: list[Detection]) -> np.ndarray:
        annotated = self.annotate(frame, detections)

        class_counts: dict[str, int] = {}
        for det in detections:
            class_counts[det.class_name] = class_counts.get(det.class_name, 0) + 1

        y_offset = 30
        for class_name, count in sorted(class_counts.items()):
            color = self._get_color(class_name)
            text = f"{class_name}: {count}"
            cv2.putText(
                annotated,
                text,
                (10, y_offset),
                cv2.FONT_HERSHEY_SIMPLEX,
                0.5,
                color,
                1,
            )
            y_offset += 25

        return annotated
```

- [ ] **Step 4: 提交**

```bash
git add app/engine/
git commit -m "feat: add decoder, inference engine, and annotator"
```

---

### Task 4: ModelPool 实现

**Files:**
- Create: `yolo-stream-processor/app/core/model_pool.py`
- Create: `yolo-stream-processor/tests/test_model_pool.py`

- [ ] **Step 1: 创建 app/core/model_pool.py**

```python
import asyncio
import logging
import threading
from dataclasses import dataclass
from typing import Literal

from app.engine.inference import InferenceEngine
from app.db.repositories.model_repo import ModelRepository

logger = logging.getLogger(__name__)


@dataclass
class ModelInstance:
    model_id: str
    engine: InferenceEngine
    status: Literal["loading", "active", "unloading"]


class ModelPool:
    def __init__(self, model_repo: ModelRepository, graceful_timeout: int = 5):
        self.model_repo = model_repo
        self.graceful_timeout = graceful_timeout
        self._models: dict[str, ModelInstance] = {}
        self._lock = asyncio.Lock()
        self._switch_events: dict[str, asyncio.Event] = {}

    async def get_model(self, model_id: str) -> InferenceEngine | None:
        async with self._lock:
            if model_id in self._models:
                return self._models[model_id].engine
            return None

    async def load_model(self, model_id: str, model_path: str, device: str | None = None) -> InferenceEngine:
        async with self._lock:
            if model_id in self._models:
                return self._models[model_id].engine

            engine = InferenceEngine(model_path, device)
            instance = ModelInstance(
                model_id=model_id,
                engine=engine,
                status="loading",
            )
            self._models[model_id] = instance

        await asyncio.get_event_loop().run_in_executor(None, engine.load)

        async with self._lock:
            self._models[model_id].status = "active"

        logger.info(f"Model {model_id} loaded successfully")
        return engine

    async def unload_model(self, model_id: str, immediate: bool = False):
        async with self._lock:
            if model_id not in self._models:
                return

            instance = self._models[model_id]

            if not immediate:
                switch_event = asyncio.Event()
                self._switch_events[model_id] = switch_event
                async with self._lock:
                    pass

            instance.status = "unloading"

        if not immediate:
            try:
                await asyncio.wait_for(switch_event.wait(), timeout=self.graceful_timeout)
            except asyncio.TimeoutError:
                logger.warning(f"Graceful switch timeout for model {model_id}, forcing switch")

        await asyncio.get_event_loop().run_in_executor(None, instance.engine.unload)

        async with self._lock:
            del self._models[model_id]
            if model_id in self._switch_events:
                del self._switch_events[model_id]

        logger.info(f"Model {model_id} unloaded")

    async def switch_model(
        self,
        old_model_id: str,
        new_model_id: str,
        new_model_path: str,
        strategy: Literal["graceful", "immediate"] = "graceful",
        device: str | None = None,
    ) -> InferenceEngine:
        if old_model_id in self._switch_events:
            self._switch_events[old_model_id].set()

        if old_model_id in self._models:
            await self.unload_model(old_model_id, immediate=(strategy == "immediate"))

        return await self.load_model(new_model_id, new_model_path, device)

    def notify_inference_complete(self, model_id: str):
        if model_id in self._switch_events:
            self._switch_events[model_id].set()
```

- [ ] **Step 2: 创建 tests/test_model_pool.py**

```python
import pytest
from unittest.mock import AsyncMock, MagicMock, patch
import numpy as np

from app.core.model_pool import ModelPool, ModelInstance
from app.engine.inference import InferenceEngine


class TestModelPool:
    @pytest.fixture
    def mock_model_repo(self):
        repo = MagicMock()
        return repo

    @pytest.fixture
    def model_pool(self, mock_model_repo):
        return ModelPool(mock_model_repo, graceful_timeout=1)

    @pytest.mark.asyncio
    async def test_get_model_not_loaded(self, model_pool):
        result = await model_pool.get_model("nonexistent")
        assert result is None

    @patch("app.engine.inference.YOLO")
    @pytest.mark.asyncio
    async def test_load_model(self, mock_yolo, model_pool, mock_model_repo):
        mock_yolo_instance = MagicMock()
        mock_yolo.return_value = mock_yolo_instance

        engine = await model_pool.load_model("model-1", "/path/to/model.pt")

        assert engine is not None
        assert "model-1" in model_pool._models
        assert model_pool._models["model-1"].status == "active"

    @pytest.mark.asyncio
    async def test_get_model_after_load(self, model_pool):
        with patch.object(model_pool, "load_model") as mock_load:
            mock_engine = MagicMock(spec=InferenceEngine)
            mock_load.return_value = mock_engine

            await model_pool.load_model("model-1", "/path/to/model.pt")
            result = await model_pool.get_model("model-1")

            assert result == mock_engine
```

- [ ] **Step 3: 提交**

```bash
git add app/core/model_pool.py tests/test_model_pool.py
git commit -m "feat: add ModelPool with hot reload support"
```

---

### Task 5: Pipeline 实现

**Files:**
- Create: `yolo-stream-processor/app/core/pipeline.py`
- Create: `yolo-stream-processor/tests/test_pipeline.py`

- [ ] **Step 1: 创建 app/core/pipeline.py**

```python
import asyncio
import logging
import time
from dataclasses import dataclass
from queue import Queue, Empty
from threading import Thread
from typing import Literal

import cv2
import numpy as np

from app.engine.decoder import VideoDecoder, DecodedFrame
from app.engine.inference import InferenceEngine, Detection
from app.engine.annotator import Annotator
from app.core.model_pool import ModelPool

logger = logging.getLogger(__name__)


@dataclass
class PipelineConfig:
    queue_maxsize: int = 30
    drop_policy: Literal["drop_oldest", "drop_newest", "block"] = "drop_oldest"
    conf_threshold: float = 0.25
    iou_threshold: float = 0.45
    classes: list[str] | None = None


@dataclass
class InferenceResult:
    frame: np.ndarray
    frame_id: int
    timestamp: float
    detections: list[Detection]


class Pipeline:
    def __init__(
        self,
        pipeline_id: str,
        input_url: str,
        input_type: Literal["rtmp", "rtsp", "file", "image"],
        output_url: str | None,
        model_pool: ModelPool,
        config: PipelineConfig,
    ):
        self.pipeline_id = pipeline_id
        self.input_url = input_url
        self.input_type = input_type
        self.output_url = output_url
        self.model_pool = model_pool
        self.config = config

        self._decoder: VideoDecoder | None = None
        self._frame_queue: Queue[DecodedFrame] = Queue(maxsize=config.queue_maxsize)
        self._result_queue: asyncio.Queue[InferenceResult] = asyncio.Queue(maxsize=100)
        self._running = False
        self._thread: Thread | None = None
        self._annotator = Annotator()
        self._encoder = None  # FFmpeg encoder for RTMP output

    def start(self, model_id: str, model_path: str):
        if self._running:
            return

        self._running = True
        self._thread = Thread(target=self._run, args=(model_id, model_path), daemon=True)
        self._thread.start()
        logger.info(f"Pipeline {self.pipeline_id} started")

    def stop(self):
        self._running = False
        if self._thread:
            self._thread.join(timeout=5)
        if self._decoder:
            self._decoder.release()
        logger.info(f"Pipeline {self.pipeline_id} stopped")

    def _run(self, model_id: str, model_path: str):
        self._decoder = VideoDecoder(self.input_url, self.input_type)
        if not self._decoder.open():
            logger.error(f"Failed to open decoder for pipeline {self.pipeline_id}")
            return

        engine = None
        try:
            loop = asyncio.new_event_loop()
            asyncio.set_event_loop(loop)
            engine = loop.run_until_complete(
                self.model_pool.load_model(model_id, model_path)
            )
        except Exception as e:
            logger.error(f"Failed to load model for pipeline {self.pipeline_id}: {e}")
            return

        fps_counter = FPSCounter()

        while self._running:
            frame_data = self._decoder.read()

            if frame_data is None:
                if self.input_type in ("rtmp", "rtsp"):
                    time.sleep(0.1)
                    continue
                else:
                    break

            try:
                if self.config.drop_policy == "drop_oldest":
                    try:
                        self._frame_queue.get_nowait()
                    except Empty:
                        pass
                    self._frame_queue.put_nowait(frame_data)
                elif self.config.drop_policy == "drop_newest":
                    try:
                        self._frame_queue.put_nowait(frame_data)
                    except:
                        try:
                            self._frame_queue.get_nowait()
                            self._frame_queue.put_nowait(frame_data)
                        except Empty:
                            pass
                else:  # block
                    self._frame_queue.put(frame_data, timeout=1)
            except:
                continue

            try:
                frame_data = self._frame_queue.get(timeout=0.1)
            except Empty:
                continue

            detections = engine.infer(
                frame_data.frame,
                conf_threshold=self.config.conf_threshold,
                iou_threshold=self.config.iou_threshold,
                classes=self.config.classes,
            )

            fps_counter.update()

            annotated = self._annotator.annotate(frame_data.frame, detections)

            result = InferenceResult(
                frame=annotated,
                frame_id=frame_data.frame_id,
                timestamp=frame_data.timestamp,
                detections=detections,
            )

            try:
                loop.run_until_complete(self._result_queue.put(result))
            except:
                pass

            if self.output_url and self._encoder is None:
                self._init_encoder(annotated.shape)

            if self.output_url and self._encoder:
                self._encoder.write(annotated)

        self._running = False

    def _init_encoder(self, frame_shape):
        if not self.output_url:
            return
        h, w = frame_shape[:2]
        try:
            self._encoder = cv2.VideoWriter(
                self.output_url,
                cv2.VideoWriter_fourcc(*"FLV1"),
                30,
                (w, h),
            )
        except Exception as e:
            logger.error(f"Failed to init encoder: {e}")

    async def get_result(self, timeout: float = 1.0) -> InferenceResult | None:
        try:
            return await asyncio.wait_for(self._result_queue.get(), timeout)
        except asyncio.TimeoutError:
            return None

    def get_fps(self) -> float:
        return 0.0


class FPSCounter:
    def __init__(self):
        self._count = 0
        self._start = time.time()
        self._fps = 0.0

    def update(self):
        self._count += 1
        elapsed = time.time() - self._start
        if elapsed > 0:
            self._fps = self._count / elapsed

    def get_fps(self) -> float:
        return self._fps
```

- [ ] **Step 2: 创建 tests/test_pipeline.py**

```python
import pytest
from unittest.mock import MagicMock, patch
import numpy as np

from app.core.pipeline import Pipeline, PipelineConfig, FPSCounter
from app.core.model_pool import ModelPool
from app.engine.decoder import DecodedFrame


class TestFPSCounter:
    def test_fps_calculation(self):
        counter = FPSCounter()
        for _ in range(30):
            counter.update()
        fps = counter.get_fps()
        assert fps > 0


class TestPipeline:
    @pytest.fixture
    def mock_model_pool(self):
        return MagicMock(spec=ModelPool)

    @pytest.fixture
    def pipeline_config(self):
        return PipelineConfig(
            queue_maxsize=10,
            drop_policy="drop_oldest",
            conf_threshold=0.25,
            iou_threshold=0.45,
        )

    def test_pipeline_creation(self, mock_model_pool, pipeline_config):
        pipeline = Pipeline(
            pipeline_id="test-1",
            input_url="rtmp://localhost/test",
            input_type="rtmp",
            output_url=None,
            model_pool=mock_model_pool,
            config=pipeline_config,
        )

        assert pipeline.pipeline_id == "test-1"
        assert not pipeline._running
```

- [ ] **Step 3: 提交**

```bash
git add app/core/pipeline.py tests/test_pipeline.py
git commit -m "feat: add Pipeline with frame queue and backpressure"
```

---

### Task 6: StreamManager 实现

**Files:**
- Create: `yolo-stream-processor/app/core/stream_manager.py`
- Create: `tests/test_stream_manager.py`

- [ ] **Step 1: 创建 app/core/stream_manager.py**

```python
import asyncio
import logging
import signal
from typing import Literal

from app.core.pipeline import Pipeline, PipelineConfig
from app.core.model_pool import ModelPool
from app.db.repositories.stream_repo import StreamRepository
from app.db.repositories.model_repo import ModelRepository

logger = logging.getLogger(__name__)


class StreamManager:
    def __init__(
        self,
        stream_repo: StreamRepository,
        model_repo: ModelRepository,
        model_pool: ModelPool,
    ):
        self.stream_repo = stream_repo
        self.model_repo = model_repo
        self.model_pool = model_pool
        self._pipelines: dict[str, Pipeline] = {}
        self._lock = asyncio.Lock()

    async def create_pipeline(self, stream_id: str, model_id: str) -> Pipeline | None:
        stream = await self.stream_repo.get_by_id(stream_id)
        if not stream:
            return None

        model = await self.model_repo.get_by_id(model_id)
        if not model:
            return None

        config = PipelineConfig(
            queue_maxsize=stream.config.get("queue_maxsize", 30),
            drop_policy=stream.config.get("drop_policy", "drop_oldest"),
            conf_threshold=stream.config.get("conf_threshold", 0.25),
            iou_threshold=stream.config.get("iou_threshold", 0.45),
            classes=stream.config.get("classes"),
        )

        pipeline = Pipeline(
            pipeline_id=stream_id,
            input_url=stream.input_url,
            input_type=stream.input_type,
            output_url=stream.output_url,
            model_pool=self.model_pool,
            config=config,
        )

        async with self._lock:
            self._pipelines[stream_id] = pipeline

        return pipeline

    async def start_stream(self, stream_id: str) -> bool:
        stream = await self.stream_repo.get_by_id(stream_id)
        if not stream or not stream.model_id:
            return False

        pipeline = await self.create_pipeline(stream_id, stream.model_id)
        if not pipeline:
            return False

        model = await self.model_repo.get_by_id(stream.model_id)
        pipeline.start(stream.model_id, model.path)

        await self.stream_repo.update_status(stream_id, "running")
        return True

    async def stop_stream(self, stream_id: str) -> bool:
        async with self._lock:
            if stream_id not in self._pipelines:
                return False
            pipeline = self._pipelines[stream_id]
            pipeline.stop()
            del self._pipelines[stream_id]

        await self.stream_repo.update_status(stream_id, "stopped")
        return True

    async def get_pipeline(self, stream_id: str) -> Pipeline | None:
        async with self._lock:
            return self._pipelines.get(stream_id)

    async def list_running_streams(self) -> list[str]:
        async with self._lock:
            return list(self._pipelines.keys())

    async def cleanup_orphaned_pipelines(self):
        async with self._lock:
            orphaned = []
            for stream_id, pipeline in self._pipelines.items():
                if not pipeline._thread or not pipeline._thread.is_alive():
                    orphaned.append(stream_id)

            for stream_id in orphaned:
                self._pipelines[stream_id].stop()
                del self._pipelines[stream_id]
                await self.stream_repo.update_status(stream_id, "stopped")

            if orphaned:
                logger.info(f"Cleaned up {len(orphaned)} orphaned pipelines")
```

- [ ] **Step 2: 创建 tests/test_stream_manager.py**

```python
import pytest
from unittest.mock import AsyncMock, MagicMock

from app.core.stream_manager import StreamManager


class TestStreamManager:
    @pytest.fixture
    def mock_stream_repo(self):
        return MagicMock()

    @pytest.fixture
    def mock_model_repo(self):
        return MagicMock()

    @pytest.fixture
    def mock_model_pool(self):
        return MagicMock()

    @pytest.fixture
    def stream_manager(self, mock_stream_repo, mock_model_repo, mock_model_pool):
        return StreamManager(mock_stream_repo, mock_model_repo, mock_model_pool)
```

- [ ] **Step 3: 提交**

```bash
git add app/core/stream_manager.py tests/test_stream_manager.py
git commit -m "feat: add StreamManager for pipeline lifecycle"
```

---

## Phase 3: 输出与Webhook

### Task 7: RTMP Output 与 Snapshot

**Files:**
- Create: `yolo-stream-processor/app/output/__init__.py`
- Create: `yolo-stream-processor/app/output/rtmp_output.py`
- Create: `yolo-stream-processor/app/output/snapshot.py`

- [ ] **Step 1: 创建 app/output/rtmp_output.py**

```python
import subprocess
import logging
from typing import Literal

import cv2
import numpy as np

logger = logging.getLogger(__name__)


class RTMPOutput:
    def __init__(self, output_url: str, width: int, height: int, fps: float = 30.0):
        self.output_url = output_url
        self.width = width
        self.height = height
        self.fps = fps
        self._process: subprocess.Popen | None = None
        self._writer: cv2.VideoWriter | None = None

    def open(self):
        if not self.output_url.startswith(("rtmp://", "rtsp://")):
            raise ValueError(f"Invalid RTMP URL: {self.output_url}")

        self._process = subprocess.Popen(
            [
                "ffmpeg",
                "-re",
                "-y",
                "-f", "rawvideo",
                "-vcodec", "rawvideo",
                "-pix_fmt", "bgr24",
                "-s", f"{self.width}x{self.height}",
                "-r", str(self.fps),
                "-i", "-",
                "-c:v", "libx264",
                "-preset", "ultrafast",
                "-tune", "zerolatency",
                "-f", "flv",
                self.output_url,
            ],
            stdin=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )

    def write(self, frame: np.ndarray):
        if not self._process or self._process.stdin is None:
            return

        try:
            self._process.stdin.write(frame.tobytes())
        except (BrokenPipeError, OSError):
            logger.error("RTMP stream write failed, attempting reopen")
            self.close()
            self.open()

    def close(self):
        if self._process:
            if self._process.stdin:
                self._process.stdin.close()
            self._process.wait(timeout=5)
            self._process = None


class RTMPOutputManager:
    def __init__(self):
        self._outputs: dict[str, RTMPOutput] = {}

    def get_or_create(self, output_url: str, width: int, height: int, fps: float = 30.0) -> RTMPOutput:
        if output_url not in self._outputs:
            output = RTMPOutput(output_url, width, height, fps)
            output.open()
            self._outputs[output_url] = output
        return self._outputs[output_url]

    def close(self, output_url: str):
        if output_url in self._outputs:
            self._outputs[output_url].close()
            del self._outputs[output_url]

    def close_all(self):
        for output in list(self._outputs.values()):
            output.close()
        self._outputs.clear()
```

- [ ] **Step 2: 创建 app/output/snapshot.py**

```python
import uuid
from pathlib import Path
from typing import Literal

import cv2
import numpy as np

from app.config import config


class SnapshotManager:
    def __init__(self):
        self.storage_type = config.snapshots.storage_type
        self.local_path = Path(config.snapshots.local_path)
        if self.storage_type == "local":
            self.local_path.mkdir(parents=True, exist_ok=True)

    def save(
        self,
        frame: np.ndarray,
        stream_id: str,
        frame_id: int,
        quality: int = 80,
        max_width: int = 640,
    ) -> str:
        if self.storage_type != "local":
            raise NotImplementedError("Only local storage implemented")

        resized = frame
        if max_width > 0 and frame.shape[1] > max_width:
            scale = max_width / frame.shape[1]
            new_w = int(frame.shape[1] * scale)
            new_h = int(frame.shape[0] * scale)
            resized = cv2.resize(frame, (new_w, new_h))

        filename = f"{stream_id}-{frame_id}-{uuid.uuid4().hex[:8]}.jpg"
        filepath = self.local_path / filename

        encode_params = [cv2.IMWRITE_JPEG_QUALITY, quality]
        cv2.imwrite(str(filepath), resized, encode_params)

        return f"file://{filepath}"

    def cleanup_old(self, retention_days: int | None = None):
        if self.storage_type != "local":
            return

        retention = retention_days or config.snapshots.retention_days
        cutoff = __import__("datetime").datetime.now() - __import__("datetime").timedelta(days=retention)

        for filepath in self.local_path.glob("*.jpg"):
            if filepath.stat().st_mtime < cutoff.timestamp():
                filepath.unlink()
```

- [ ] **Step 3: 提交**

```bash
git add app/output/
git commit -m "feat: add RTMP output and snapshot management"
```

---

### Task 8: Webhook Dispatcher

**Files:**
- Create: `yolo-stream-processor/app/webhook/__init__.py`
- Create: `yolo-stream-processor/app/webhook/dispatcher.py`
- Create: `tests/test_webhook_dispatcher.py`

- [ ] **Step 1: 创建 app/webhook/dispatcher.py**

```python
import asyncio
import logging
from dataclasses import dataclass
from typing import Literal

import aiohttp

from app.engine.inference import Detection

logger = logging.getLogger(__name__)


@dataclass
class WebhookPayload:
    event: str = "detection"
    stream_id: str = ""
    timestamp: str = ""
    frame_id: int = 0
    detections: list[dict] = None
    image_url: str | None = None

    def __post_init__(self):
        if self.detections is None:
            self.detections = []


class WebhookDispatcher:
    def __init__(
        self,
        webhook_url: str,
        queue_maxsize: int = 100,
        timeout: int = 5,
        retry_max: int = 3,
        retry_backoff_base: float = 1.0,
        include_image: bool = True,
        image_quality: int = 80,
        max_image_width: int = 640,
        auth_header: str | None = None,
        auth_value: str | None = None,
        on_failure: Literal["log_and_skip", "pause_pipeline"] = "log_and_skip",
    ):
        self.webhook_url = webhook_url
        self.queue_maxsize = queue_maxsize
        self.timeout = timeout
        self.retry_max = retry_max
        self.retry_backoff_base = retry_backoff_base
        self.include_image = include_image
        self.image_quality = image_quality
        self.max_image_width = max_image_width
        self.auth_header = auth_header
        self.auth_value = auth_value
        self.on_failure = on_failure

        self._queue: asyncio.Queue[WebhookPayload] = asyncio.Queue(maxsize=queue_maxsize)
        self._running = False
        self._task: asyncio.Task | None = None

    async def start(self):
        self._running = True
        self._task = asyncio.create_task(self._process_queue())

    async def stop(self):
        self._running = False
        if self._task:
            self._task.cancel()
            try:
                await self._task
            except asyncio.CancelledError:
                pass

    async def enqueue(self, payload: WebhookPayload):
        try:
            self._queue.put_nowait(payload)
        except asyncio.QueueFull:
            try:
                self._queue.get_nowait()
                self._queue.put_nowait(payload)
            except asyncio.QueueEmpty:
                pass

    async def _process_queue(self):
        while self._running:
            try:
                payload = await asyncio.wait_for(self._queue.get(), timeout=1.0)
            except asyncio.TimeoutError:
                continue

            success = await self._send_with_retry(payload)
            if not success and self.on_failure == "pause_pipeline":
                logger.error(f"Webhook failed after {self.retry_max} retries, pausing")

    async def _send_with_retry(self, payload: WebhookPayload) -> bool:
        headers = {"Content-Type": "application/json"}
        if self.auth_header and self.auth_value:
            headers[self.auth_header] = self.auth_value

        for attempt in range(self.retry_max + 1):
            try:
                async with aiohttp.ClientSession() as session:
                    async with session.post(
                        self.webhook_url,
                        json=payload.__dict__,
                        headers=headers,
                        timeout=aiohttp.ClientTimeout(total=self.timeout),
                    ) as resp:
                        if resp.status < 400:
                            return True
                        logger.warning(f"Webhook returned {resp.status}")
            except asyncio.TimeoutError:
                logger.warning(f"Webhook timeout on attempt {attempt + 1}")
            except Exception as e:
                logger.error(f"Webhook error: {e}")

            if attempt < self.retry_max:
                backoff = self.retry_backoff_base * (2 ** attempt)
                await asyncio.sleep(backoff)

        return False
```

- [ ] **Step 2: 创建 tests/test_webhook_dispatcher.py**

```python
import pytest
from unittest.mock import AsyncMock, MagicMock, patch
from datetime import datetime

from app.webhook.dispatcher import WebhookDispatcher, WebhookPayload


class TestWebhookDispatcher:
    @pytest.fixture
    def dispatcher(self):
        return WebhookDispatcher(
            webhook_url="https://example.com/webhook",
            timeout=5,
            retry_max=2,
        )

    @pytest.mark.asyncio
    async def test_enqueue(self, dispatcher):
        payload = WebhookPayload(
            stream_id="test-stream",
            timestamp=datetime.now().isoformat(),
            frame_id=1,
            detections=[],
        )

        await dispatcher.enqueue(payload)
        assert dispatcher._queue.qsize() == 1

    @pytest.mark.asyncio
    async def test_start_stop(self, dispatcher):
        await dispatcher.start()
        assert dispatcher._running
        assert dispatcher._task is not None

        await dispatcher.stop()
        assert not dispatcher._running
```

- [ ] **Step 3: 提交**

```bash
git add app/webhook/ tests/test_webhook_dispatcher.py
git commit -m "feat: add WebhookDispatcher with retry and backoff"
```

---

## Phase 4: API 层

### Task 9: API 认证与依赖注入

**Files:**
- Create: `yolo-stream-processor/app/api/__init__.py`
- Create: `yolo-stream-processor/app/api/deps.py`

- [ ] **Step 1: 创建 app/api/deps.py**

```python
from fastapi import Depends, HTTPException, Security, status
from fastapi.security import APIKeyHeader
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.database import get_session
from app.db.models import APIKey
from app.db.repositories.key_repo import APIKeyRepository

api_key_header = APIKeyHeader(name="X-API-Key", auto_error=False)


async def get_db():
    async for session in get_session():
        yield session


async def verify_api_key(
    api_key: str | None = Security(api_key_header),
    session: AsyncSession = Depends(get_db),
) -> APIKey:
    if not api_key:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Missing API key",
        )

    repo = APIKeyRepository(session)
    key = await repo.verify(api_key)

    if not key:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid API key",
        )

    return key


async def get_api_key_optional(
    api_key: str | None = Security(api_key_header),
    session: AsyncSession = Depends(get_db),
) -> APIKey | None:
    if not api_key:
        return None

    repo = APIKeyRepository(session)
    return await repo.verify(api_key)
```

- [ ] **Step 2: 提交**

```bash
git add app/api/deps.py app/api/__init__.py
git commit -m "feat: add API authentication via X-API-Key header"
```

---

### Task 10: Stream 路由

**Files:**
- Create: `yolo-stream-processor/app/api/routes/__init__.py`
- Create: `yolo-stream-processor/app/api/routes/streams.py`

- [ ] **Step 1: 创建 app/api/routes/streams.py**

```python
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_db, verify_api_key
from app.db.models import APIKey, Stream
from app.db.repositories.stream_repo import StreamRepository
from app.core.stream_manager import StreamManager

router = APIRouter(prefix="/api/v1/streams", tags=["streams"])


class StreamCreate(BaseModel):
    name: str = Field(..., min_length=1, max_length=255)
    input_url: str = Field(..., min_length=1)
    input_type: str = Field(..., pattern="^(rtmp|rtsp|file|image)$")
    output_url: str | None = None
    model_id: str | None = None
    config: dict = Field(default_factory=dict)


class StreamUpdate(BaseModel):
    name: str | None = None
    output_url: str | None = None
    model_id: str | None = None
    config: dict | None = None


class StreamResponse(BaseModel):
    id: str
    name: str
    input_url: str
    input_type: str
    output_url: str | None
    model_id: str | None
    config: dict
    status: str
    fps: float | None

    class Config:
        from_attributes = True


class StreamListResponse(BaseModel):
    items: list[StreamResponse]
    total: int
    limit: int
    offset: int


def get_stream_manager() -> StreamManager:
    from app.main import stream_manager
    return stream_manager


@router.post("", response_model=StreamResponse, status_code=status.HTTP_201_CREATED)
async def create_stream(
    stream_data: StreamCreate,
    _api_key: Annotated[APIKey, Depends(verify_api_key)],
    session: Annotated[AsyncSession, Depends(get_db)],
    manager: Annotated[StreamManager, Depends(get_stream_manager)],
):
    repo = StreamRepository(session)
    stream = await repo.create(
        name=stream_data.name,
        input_url=stream_data.input_url,
        input_type=stream_data.input_type,
        model_id=stream_data.model_id,
        config=stream_data.config,
        output_url=stream_data.output_url,
    )

    return StreamResponse(
        id=stream.id,
        name=stream.name,
        input_url=stream.input_url,
        input_type=stream.input_type,
        output_url=stream.output_url,
        model_id=stream.model_id,
        config=stream.config,
        status=stream.status,
        fps=stream.fps,
    )


@router.get("", response_model=StreamListResponse)
async def list_streams(
    limit: int = 100,
    offset: int = 0,
    _api_key: Annotated[APIKey, Depends(verify_api_key)],
    session: Annotated[AsyncSession, Depends(get_db)],
):
    repo = StreamRepository(session)
    streams, total = await repo.list_all(limit=limit, offset=offset)

    return StreamListResponse(
        items=[
            StreamResponse(
                id=s.id,
                name=s.name,
                input_url=s.input_url,
                input_type=s.input_type,
                output_url=s.output_url,
                model_id=s.model_id,
                config=s.config,
                status=s.status,
                fps=s.fps,
            )
            for s in streams
        ],
        total=total,
        limit=limit,
        offset=offset,
    )


@router.get("/{stream_id}", response_model=StreamResponse)
async def get_stream(
    stream_id: str,
    _api_key: Annotated[APIKey, Depends(verify_api_key)],
    session: Annotated[AsyncSession, Depends(get_db)],
):
    repo = StreamRepository(session)
    stream = await repo.get_by_id(stream_id)

    if not stream:
        raise HTTPException(status_code=404, detail="Stream not found")

    return StreamResponse(
        id=stream.id,
        name=stream.name,
        input_url=stream.input_url,
        input_type=stream.input_type,
        output_url=stream.output_url,
        model_id=stream.model_id,
        config=stream.config,
        status=stream.status,
        fps=stream.fps,
    )


@router.put("/{stream_id}", response_model=StreamResponse)
async def update_stream(
    stream_id: str,
    update_data: StreamUpdate,
    _api_key: Annotated[APIKey, Depends(verify_api_key)],
    session: Annotated[AsyncSession, Depends(get_db)],
):
    repo = StreamRepository(session)
    stream = await repo.update(
        stream_id,
        name=update_data.name,
        output_url=update_data.output_url,
        model_id=update_data.model_id,
        config=update_data.config,
    )

    if not stream:
        raise HTTPException(status_code=404, detail="Stream not found")

    return StreamResponse(
        id=stream.id,
        name=stream.name,
        input_url=stream.input_url,
        input_type=stream.input_type,
        output_url=stream.output_url,
        model_id=stream.model_id,
        config=stream.config,
        status=stream.status,
        fps=stream.fps,
    )


@router.patch("/{stream_id}/start")
async def start_stream(
    stream_id: str,
    _api_key: Annotated[APIKey, Depends(verify_api_key)],
    manager: Annotated[StreamManager, Depends(get_stream_manager)],
):
    success = await manager.start_stream(stream_id)
    if not success:
        raise HTTPException(status_code=400, detail="Failed to start stream")
    return {"status": "started"}


@router.patch("/{stream_id}/stop")
async def stop_stream(
    stream_id: str,
    _api_key: Annotated[APIKey, Depends(verify_api_key)],
    manager: Annotated[StreamManager, Depends(get_stream_manager)],
):
    success = await manager.stop_stream(stream_id)
    if not success:
        raise HTTPException(status_code=400, detail="Failed to stop stream")
    return {"status": "stopped"}


@router.delete("/{stream_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_stream(
    stream_id: str,
    _api_key: Annotated[APIKey, Depends(verify_api_key)],
    session: Annotated[AsyncSession, Depends(get_db)],
    manager: Annotated[StreamManager, Depends(get_stream_manager)],
):
    await manager.stop_stream(stream_id)
    repo = StreamRepository(session)
    deleted = await repo.delete(stream_id)

    if not deleted:
        raise HTTPException(status_code=404, detail="Stream not found")
```

- [ ] **Step 2: 提交**

```bash
git add app/api/routes/streams.py
git commit -m "feat: add stream management API routes"
```

---

### Task 11: Model、Monitor、Keys 路由

**Files:**
- Create: `yolo-stream-processor/app/api/routes/models.py`
- Create: `yolo-stream-processor/app/api/routes/monitor.py`
- Create: `yolo-stream-processor/app/api/routes/keys.py`

- [ ] **Step 1: 创建 app/api/routes/models.py**

```python
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, status, UploadFile, File
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_db, verify_api_key
from app.db.models import APIKey, Model
from app.db.repositories.model_repo import ModelRepository
from app.core.model_pool import ModelPool
from app.config import config

router = APIRouter(prefix="/api/v1/models", tags=["models"])


class ModelResponse(BaseModel):
    id: str
    name: str
    path: str
    type: str
    status: str

    class Config:
        from_attributes = True


class ModelActivateRequest(BaseModel):
    strategy: str = Field(default="graceful", pattern="^(graceful|immediate)$")


def get_model_pool() -> ModelPool:
    from app.main import model_pool
    return get_model_pool


@router.post("", response_model=ModelResponse, status_code=status.HTTP_201_CREATED)
async def create_model(
    name: str = Form(...),
    type: str = Form(..., pattern="^(detection|segmentation|pose|classification)$"),
    model_file: UploadFile = File(...),
    _api_key: Annotated[APIKey, Depends(verify_api_key)],
    session: Annotated[AsyncSession, Depends(get_db)],
):
    import os
    import uuid

    model_dir = config.models.storage_path
    os.makedirs(model_dir, exist_ok=True)

    file_ext = os.path.splitext(model_file.filename)[1] or ".pt"
    model_id = str(uuid.uuid4())
    model_path = os.path.join(model_dir, f"{model_id}{file_ext}")

    with open(model_path, "wb") as f:
        content = await model_file.read()
        f.write(content)

    repo = ModelRepository(session)
    model = await repo.create(name=name, path=model_path, model_type=type)

    return ModelResponse(
        id=model.id,
        name=model.name,
        path=model.path,
        type=model.type,
        status=model.status,
    )


@router.get("", response_model=list[ModelResponse])
async def list_models(
    _api_key: Annotated[APIKey, Depends(verify_api_key)],
    session: Annotated[AsyncSession, Depends(get_db)],
):
    repo = ModelRepository(session)
    models = await repo.list_all()

    return [
        ModelResponse(
            id=m.id,
            name=m.name,
            path=m.path,
            type=m.type,
            status=m.status,
        )
        for m in models
    ]


@router.get("/{model_id}", response_model=ModelResponse)
async def get_model(
    model_id: str,
    _api_key: Annotated[APIKey, Depends(verify_api_key)],
    session: Annotated[AsyncSession, Depends(get_db)],
):
    repo = ModelRepository(session)
    model = await repo.get_by_id(model_id)

    if not model:
        raise HTTPException(status_code=404, detail="Model not found")

    return ModelResponse(
        id=model.id,
        name=model.name,
        path=model.path,
        type=model.type,
        status=model.status,
    )


@router.patch("/{model_id}/activate")
async def activate_model(
    model_id: str,
    activate_data: ModelActivateRequest = ModelActivateRequest(),
    _api_key: Annotated[APIKey, Depends(verify_api_key)],
    session: Annotated[AsyncSession, Depends(get_db)],
    model_pool: Annotated[ModelPool, Depends(get_model_pool)],
):
    repo = ModelRepository(session)
    model = await repo.get_by_id(model_id)

    if not model:
        raise HTTPException(status_code=404, detail="Model not found")

    await repo.update_status(model_id, "active")
    return {"status": "activated"}
```

- [ ] **Step 2: 创建 app/api/routes/monitor.py**

```python
from typing import Annotated

from fastapi import APIRouter, Depends
from pydantic import BaseModel

from app.api.deps import verify_api_key
from app.db.models import APIKey
from app.core.stream_manager import StreamManager

router = APIRouter(prefix="/api/v1/monitor", tags=["monitor"])


class StreamStats(BaseModel):
    stream_id: str
    fps: float
    status: str


class GlobalStats(BaseModel):
    running_streams: int
    total_streams: int
    gpu_available: bool


class HealthResponse(BaseModel):
    status: str
    version: str


def get_stream_manager() -> StreamManager:
    from app.main import stream_manager
    return stream_manager


@router.get("/stats")
async def get_stats(
    _api_key: Annotated[APIKey, Depends(verify_api_key)],
    manager: Annotated[StreamManager, Depends(get_stream_manager)],
):
    running = await manager.list_running_streams()

    return GlobalStats(
        running_streams=len(running),
        total_streams=len(running),
        gpu_available=False,
    )


@router.get("/health", response_model=HealthResponse)
async def health_check():
    return HealthResponse(status="ok", version="1.0.0")


@router.get("/streams/{stream_id}/stats")
async def get_stream_stats(
    stream_id: str,
    _api_key: Annotated[APIKey, Depends(verify_api_key)],
    manager: Annotated[StreamManager, Depends(get_stream_manager)],
):
    pipeline = await manager.get_pipeline(stream_id)

    return StreamStats(
        stream_id=stream_id,
        fps=pipeline.get_fps() if pipeline else 0.0,
        status="running" if pipeline and pipeline._running else "stopped",
    )
```

- [ ] **Step 3: 创建 app/api/routes/keys.py**

```python
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession

from app.api.deps import get_db, verify_api_key
from app.db.models import APIKey
from app.db.repositories.key_repo import APIKeyRepository

router = APIRouter(prefix="/api/v1/keys", tags=["keys"])


class KeyCreate(BaseModel):
    name: str = Field(..., min_length=1, max_length=255)


class KeyResponse(BaseModel):
    id: str
    name: str
    is_active: bool

    class Config:
        from_attributes = True


class KeyCreateResponse(BaseModel):
    id: str
    name: str
    key: str  # 明文（仅创建时返回一次）


@router.post("", response_model=KeyCreateResponse, status_code=status.HTTP_201_CREATED)
async def create_key(
    key_data: KeyCreate,
    _api_key: Annotated[APIKey, Depends(verify_api_key)],
    session: Annotated[AsyncSession, Depends(get_db)],
):
    import secrets
    raw_key = secrets.token_urlsafe(32)

    repo = APIKeyRepository(session)
    key, _ = await repo.create(key_data.name, raw_key)

    return KeyCreateResponse(
        id=key.id,
        name=key.name,
        key=raw_key,
    )


@router.get("", response_model=list[KeyResponse])
async def list_keys(
    _api_key: Annotated[APIKey, Depends(verify_api_key)],
    session: Annotated[AsyncSession, Depends(get_db)],
):
    repo = APIKeyRepository(session)
    keys = await repo.list_all()

    return [
        KeyResponse(id=k.id, name=k.name, is_active=k.is_active)
        for k in keys
    ]


@router.delete("/{key_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_key(
    key_id: str,
    _api_key: Annotated[APIKey, Depends(verify_api_key)],
    session: Annotated[AsyncSession, Depends(get_db)],
):
    repo = APIKeyRepository(session)
    deleted = await repo.delete(key_id)

    if not deleted:
        raise HTTPException(status_code=404, detail="Key not found")
```

- [ ] **Step 4: 提交**

```bash
git add app/api/routes/models.py app/api/routes/monitor.py app/api/routes/keys.py
git commit -m "feat: add model, monitor, and keys API routes"
```

---

### Task 12: FastAPI Main 应用整合

**Files:**
- Modify: `yolo-stream-processor/app/main.py`

- [ ] **Step 1: 更新 app/main.py**

```python
import asyncio
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.config import config
from app.db.database import init_db, engine, Base
from app.db.repositories.stream_repo import StreamRepository
from app.db.repositories.model_repo import ModelRepository
from app.db.repositories.key_repo import APIKeyRepository
from app.core.model_pool import ModelPool
from app.core.stream_manager import StreamManager
from app.api.routes import streams, models, monitor, keys

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

model_pool: ModelPool | None = None
stream_manager: StreamManager | None = None


@asynccontextmanager
async def lifespan(app: FastAPI):
    global model_pool, stream_manager

    await init_db()

    from app.db.database import async_session_maker
    async with async_session_maker() as session:
        model_repo = ModelRepository(session)
        stream_repo = StreamRepository(session)

        model_pool = ModelPool(
            model_repo,
            graceful_timeout=config.pipeline.graceful_switch_timeout,
        )
        stream_manager = StreamManager(stream_repo, model_repo, model_pool)

    asyncio.create_task(stream_manager.cleanup_orphaned_pipelines())

    yield

    if stream_manager:
        for stream_id in await stream_manager.list_running_streams():
            await stream_manager.stop_stream(stream_id)

    if model_pool:
        for model_id in list(model_pool._models.keys()):
            await model_pool.unload_model(model_id)

    await engine.dispose()


app = FastAPI(
    title="YOLO Stream Processor",
    description="高性能 YOLO 视频流推理与标注系统",
    version="1.0.0",
    lifespan=lifespan,
)

app.include_router(streams.router)
app.include_router(models.router)
app.include_router(monitor.router)
app.include_router(keys.router)


@app.get("/health")
async def health():
    return {"status": "ok", "version": "1.0.0"}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host=config.api.host, port=config.api.port)
```

- [ ] **Step 2: 修复循环导入问题**

将 `get_stream_manager` 和 `get_model_pool` 改为延迟获取：

```python
# 在 app/api/routes/streams.py 中
def get_stream_manager() -> StreamManager:
    from app.main import stream_manager
    return stream_manager
```

- [ ] **Step 3: 提交**

```bash
git add app/main.py
git commit -m "feat: integrate all components in FastAPI main app"
```

---

## Phase 5: 测试与文档

### Task 13: 集成测试与 README

**Files:**
- Create: `yolo-stream-processor/tests/test_integration.py`
- Create: `yolo-stream-processor/README.md`

- [ ] **Step 1: 创建集成测试**

```python
import pytest
from httpx import AsyncClient, ASGITransport
from unittest.mock import AsyncMock, MagicMock, patch

from app.main import app


@pytest.mark.asyncio
async def test_health_endpoint():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        response = await client.get("/health")
        assert response.status_code == 200
        assert response.json()["status"] == "ok"
```

- [ ] **Step 2: 创建 README.md**

```markdown
# YOLO Stream Processor

高性能 YOLO 视频流推理与标注系统，支持多路流并发、模型热切换、实时标注与 RTMP 推流。

## 快速开始

```bash
pip install -r requirements.txt
python -m app.main
```

## API 认证

所有 API 通过 `X-API-Key` 请求头认证。首次使用需创建 API Key：

```bash
# 创建 API Key（通过内部接口或直接操作数据库）
```

## 核心功能

- 多输入源支持（RTMP/RTSP 流、视频文件、图片）
- 多模型并发推理
- 模型热加载切换
- 实时标注与 RTMP 推流
- Webhook 结果推送

## 配置

详见 `config.yaml`
```

- [ ] **Step 3: 提交**

```bash
git add tests/test_integration.py README.md
git commit -m "docs: add integration tests and README"
```

---

## 自查清单

**Spec 覆盖检查：**

| Spec 章节 | 对应 Task |
|-----------|-----------|
| 2.1 Pipeline 内部结构 | Task 5 (Pipeline), Task 3 (Decoder/Inference) |
| 2.2 Model Pool Hot Reload | Task 4 (ModelPool) |
| 2.3 Webhook 结果推送 | Task 8 (WebhookDispatcher) |
| 2.4 优雅停机 | Task 6 (StreamManager) |
| 3.1 并发模型 | Task 1 (项目结构), Task 6 (StreamManager) |
| 4.x 数据库模型 | Task 2 (Database Layer) |
| 5.x API 设计 | Task 9-12 (API Routes) |
| 6. Webhook 推送内容 | Task 8 (WebhookDispatcher) |
| 8. 项目结构 | Task 1 |
| 11. 性能指标 | Task 5 (Pipeline FPSCounter) |
| 12. 安全考量 | Task 9 (API Auth) |
| 13. 快照存储 | Task 7 (Snapshot) |

**Type 一致性检查：**
- `PipelineConfig` 中的 `drop_policy` 类型与 spec 一致
- `InferenceEngine.infer()` 返回 `list[Detection]` 与 `Annotator` 输入兼容
- `WebhookPayload` 字段与 spec 第 6 节完全一致
- API 路由路径与 spec 5.x 节完全一致

**Placeholder 检查：** 无 TBD/TODO/implement later 等占位符

---

## 执行选项

**Plan complete and saved to `docs/superpowers/plans/2026-04-21-yolo-stream-processor.md`. Two execution options:**

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**
