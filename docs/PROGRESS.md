# 寻晤智能agent系统 — 开发进度

> 每个阶段完成后提交一次 Git，并在本文档标注进度。

## 阶段总览

| 阶段 | 内容 | 状态 | 提交 |
| --- | --- | --- | --- |
| P0 | 项目初始化：目录、README、docker-compose、SQL schema | ✅ 完成 | `0c682dc` |
| P1 | 后端骨架：pom、application.yml、启动类、统一响应/异常 | ✅ 完成 | `51cbf96` |
| P2 | 数据库层：实体 + MyBatis Mapper + XML | ✅ 完成 | `51cbf96` |
| P3 | 多模型接入：ChatClientRegistry（DeepSeek/智谱/Ollama） | ✅ 完成 | `50f34f1` |
| P4 | 工具系统：Tool 接口、ToolCallingManager、内置工具 | ✅ 完成 | `50e777a` |
| P5 | Agent 执行引擎：Think-Execute 循环、状态机、最大步数 | ✅ 完成 | `6ec38eb` |
| P6 | SSE 流式接口 + 会话/消息管理 | ✅ 完成 | `244f0c3` |
| P7 | RAG：pgvector 向量存储、bge-m3、HNSW、TopK 检索 | ✅ 完成 | `3bd33b2` |
| P8 | 前端：Vite + TS + SCSS 脚手架、聊天页（SSE 实时渲染） | ✅ 完成 | 见下方提交 |
| P9 | 前端：智能体/知识库/模型/工具管理页 + GSAP 动画 | ✅ 完成 | 见下方提交 |
| P10 | 部署配置（Dockerfile / nginx / compose）、README 收尾 | ✅ 完成 | 见下方提交 |

## 详细记录

### P0 项目初始化 ✅

- 新增 `.gitignore`、`docker-compose.yml`（PostgreSQL+pgvector / Ollama）
- 新增 `deploy/postgres/init/01_extensions.sql`、`02_schema.sql`（含 HNSW 索引）
- 新增 `deploy/ollama/pull-models.sh`
- 新增 `README.md`

### P1 后端骨架 ✅

- `pom.xml`：Spring Boot 3.5.8 + Spring AI 1.1.0 + MyBatis + PostgreSQL
- `application.yml`：数据源、多模型配置、Agent/RAG 参数
- `AgentApplication`、`XunwuProperties`
- `ApiResponse` / `BizException` / `GlobalExceptionHandler`
- `WebConfig`（CORS）、`AsyncConfig`、`HealthController`

### P2 数据库层 ✅

- 实体：`Agent` / `Conversation` / `Message` / `KnowledgeDocument` / `KnowledgeSegment` / `KnowledgeSearchResult`
- Mapper + XML：CRUD + pgvector 余弦检索 SQL（`embedding <=> query`）

### P3 多模型接入 ✅

- `ModelInfo` 模型元信息
- `ChatClientRegistry`：按模型标识动态获取 `ChatClient`，注册 DeepSeek / 智谱 / Ollama
- 接口：`GET /api/models`

### P4 工具系统 ✅

- `ToolType`（FIXED / OPTIONAL）
- `ToolRegistry`：统一注册/查询/按名获取工具
- `ToolCallingManagerImpl`：显式工具执行，降低与 Agent 核心流程耦合
- 内置工具：`current_time`、`calculator`
- 接口：`GET /api/tools`

### P5 Agent 执行引擎 ✅

- `AgentState` 状态机：`IDLE` / `THINKING` / `TOOL_CALLING` / `COMPLETED` / `MAX_STEPS_REACHED` / `FAILED`
- `AgentExecutor`：Think-Execute 循环，`internalToolExecutionEnabled(false)` 关闭框架自动工具执行
- 最大执行步数限制，防止智能体无限循环
- `AgentEvent`：SSE 事件（`thinking` / `token` / `tool_calling` / `tool_result` / `done` / `error` / `max_steps`）
- `AgentToolService`：统一工具调度执行

### P6 SSE 流式接口 + 会话管理 ✅

- `ChatService`：组装 `AgentContext`、加载历史、保存消息
- `ChatController`：`GET /api/chat/stream`（SSE）
- `ConversationController`：会话 CRUD
- `MessageController`：消息查询/删除

### P7 RAG 知识检索 ✅

- `EmbeddingService`：Ollama bge-m3 向量化，向量与 PG 数组互转
- `DocumentSplitter`：文档分段（带重叠），优先按段落切分
- `KnowledgeSearchService`：pgvector 余弦检索 + Top-K，结果组装为上下文
- `KnowledgeService`：文档入库、分段、批量向量化、状态管理
- 接口：`GET/POST/DELETE /api/knowledge/documents`、`POST /api/knowledge/search`

### P8 前端脚手架 + 聊天页 ✅

- Vite 6 + React 19 + TypeScript 5.8 脚手架
- **SCSS 设计系统**：`variables.scss`（颜色/渐变/尺寸/阴影/层级）、`mixins.scss`（glass / glow-border / text-gradient / scrollbar / pulse）、`main.scss`（全局组件类）
- `Layout`：侧边栏 + 导航 + 状态指示
- `ChatPage`：SSE 流式渲染（fetch + ReadableStream）、工具调用可视化、多模型/智能体切换
- 欢迎空状态 + 快捷提问 chips

### P9 前端管理页 + 动画 ✅

- `AgentsPage`：智能体 CRUD + 工具勾选
- `KnowledgePage`：文档上传 + 检索测试
- `ModelsPage`：模型分组卡片
- `ToolsPage`：工具卡片 + Schema 展示
- **GSAP 动画**：侧边栏入场 stagger、页面入场、消息/工具调用入场、路由切换过渡
- 构建产物：JS 359 kB（gzip 119 kB）、CSS 18.8 kB（gzip 4.3 kB）

### P10 部署配置收尾 ✅

- `backend/Dockerfile`：多阶段构建（Maven 打包 → JRE 运行）
- `frontend/Dockerfile`：多阶段构建（Node 构建 → nginx 托管）
- `frontend/nginx.conf`：SPA 路由回退 + `/api` 反代（SSE 关闭缓冲）
- `docker-compose.yml` 扩展为全栈编排（postgres + ollama + backend + frontend）
- `application.yml` 支持 `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` / `OLLAMA_BASE_URL` 等环境变量
- 新增 `management.endpoint.health` 探针配置
- README 更新：全栈启动说明、技术栈、项目结构
