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
| P14 | 接口加固 + 单测补齐 + 前端 Markdown/会话管理升级 | ✅ 完成 | 见下方提交 |

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

### P11 自检与修复 ✅

- 通读全部源码，问题清单见 `docs/ISSUES.md`（P0 ×5 / P1 ×10 / P2 ×5 / 第二轮 ×8）。
- **P0**：补齐 `/api/agents` 控制器；`ToolRegistry.getTools()` 改为可变列表；
  `AgentExecutor` 改为单次 `ChatClient.stream().chatResponse()` 拿 token 与
  tool_calls（去掉重复推理）；`toolNames` 过滤为已注册工具；RAG 真正接入对话。
- **P1**：`DocumentSplitter` 死循环；`KnowledgeService` 用 `TransactionTemplate`
  隔离远程 HTTP；FAILED 状态不再因回滚丢失；全局异常返回真实 HTTP 状态码且不泄露
  内部信息；`ChatPage` 默认模型排除 embedding 类型；会话复用 + 历史加载 + 条数上限。
- **P2**：死代码清理、`CalculatorTool` 负数与除零、前端 SSE 解码 flush、
  稳定 React key、轮询 `setInterval` + 清理、nginx 上传体积、
  `Flux.create` + `isCancelled()` 提前终止。
- **第二轮**：API Key 占位默认值（否则应用起不来）、JSONB 显式 CAST、
  SSE 错误事件化、MVC 专用异步线程池、向量化错误翻译、Ollama 已安装模型探测、
  `toolIds` 数组契约、死代码再清理。
- 本地 Docker 实测通过（Postgres + Ollama）：后端启动无告警，
  智能体 CRUD / 工具勾选 / SSE 错误路径 / 知识库状态流转 / pgvector 排序均正常，
  前端四个页面 Playwright 实测零报错。

### P12 Docker 全栈验证与收尾 ✅

- `docker compose build` 后端 / 前端镜像均构建成功。
- `docker compose up -d` 四个容器全部 healthy：
  - `xunwu-postgres`（PostgreSQL 17 + pgvector）
  - `xunwu-ollama`
  - `xunwu-backend`（`/api/health` → UP，database + ollama 均 UP）
  - `xunwu-frontend`（nginx，首页 HTTP 200）
- 经 nginx 反代实测 `/api/models`、`/api/agents`、`/api/knowledge/search`、
  `/api/chat/stream` 全部正常。
- 修复 Docker 环境下空串 API Key 导致 Spring AI 断言失败、后端无法启动的问题
  （新增 `ApiKeyPlaceholderEnvironmentPostProcessor`）。
- 修复前端容器 HEALTHCHECK 因 Alpine `localhost` 只解析 IPv6 而一直 unhealthy。
- 前端 Playwright 走 80 端口实测对话 / 知识库上传 / 智能体页，零 console error。

## P13. 端到端验证（Docker 全栈）

**环境**：docker compose 全栈，Ollama 已装 `bge-m3` + `qwen2.5:7b`。

**API 级验证（经 nginx:80）**

| 场景 | 结果 |
|---|---|
| 创建智能体（含 toolIds 数组） | agent id=6，model=qwen2.5:7b，tools=["calculator","current_time"] |
| 上传知识文档 | PROCESSING → READY，segments=1 |
| RAG 自动注入问答 | "如何新增智能体？" → 正确复述知识库内容 |
| 多工具同轮调用 | current_time + calculator 各一次，回答"今天是…计算结果是 972" |
| 计算器 | `{"expression":"(128*46)"}` → `{"result":5888.0}` |
| knowledge_search 固定工具 | 返回 3 条，相似度 0.670 / 0.419 / 0.355，排序正确 |

**UI 级验证（Playwright + Chrome，经 nginx:80）**

- 智能体 / 知识库 / 模型 / 工具 / 对话 五个页面全部渲染正常；
- 控制台错误与 pageerror 均为 **0**；
- 对话页工具调用卡片正确展示工具名、参数与返回值；
- RAG 问答返回"知识库文档分段默认大小是 500 字。"；
- 构建产物：JS 364.14 kB（gzip 120.52 kB）、CSS 19.01 kB（gzip 4.30 kB），零警告。

**容器状态**：4 个容器全部 healthy；`/api/health` 返回 `database=UP, ollama=UP`。

### P14 接口加固与前端体验升级 ✅

**后端**

- `ConversationController` 重写：请求/响应 DTO 隔离，客户端无法注入 `id`/`createdAt`/`updatedAt`；会话存在性校验；状态白名单。
- 新增 `MessageMapper.deleteAfterId` + XML：重新生成时清理旧回复，避免历史重复。
- 新增 `KnowledgeSegmentMapper.deleteById` / `updateContent` + XML。
- 新增 `KnowledgeSegmentInfo` 实体：对外只暴露 `hasEmbedding`，不回传原始向量。
- `KnowledgeService`：新增 `listSegments` / `reprocessDocument` / `deleteSegment` / `updateSegment` / `toSegmentInfo`。
- `KnowledgeController` 新增 4 个端点：`GET /documents/{id}/segments`、`POST /documents/{id}/reprocess`、`PUT /segments/{id}`、`DELETE /segments/{id}`。
- `ChatService`：抽出 `executeAndPersist`，新增 `regenerate(conversationId, modelId, enableRag)`。
- `ChatController`：新增 `GET /api/chat/regenerate`。
- 单测补齐至 **32 个**，全部通过：`DocumentSplitter`(8) / `CalculatorTool`(6) / `ToolRegistry`(8) / `KnowledgeSearchService`(3) / `EmbeddingService`(3) / `AgentExecutor`(4)。

**前端**

- 新增 `react-markdown` + `remark-gfm` + `react-syntax-highlighter`（`PrismLight` 按需注册 30 个常用语言，避免全量语言包拖大体积）。
- 新增 `Markdown` 组件：GFM 表格 / 任务列表 / 删除线、代码块 oneDark 高亮（长代码块显示行号）、行内代码。
- `ChatPage`：Markdown 渲染、消息复制 / 重新生成按钮、会话切换器（重命名 / 删除）、工具事件卡片、欢迎语快捷指令。
- `KnowledgePage`：分段明细展开、整篇重新向量化、分段编辑（重新向量化）、分段删除。
- API 层：`chat.ts` 抽出 `consumeSseStream` 并新增 `streamRegenerate`；`conversation.ts` 新增 `update`；`knowledge.ts` 新增 4 个方法与 `KnowledgeSegment` 类型。

**验证**

- 后端 `mvn test`（JDK 21）：**32 通过 / 0 失败**。
- 前端 `npm run build`：通过，产物 JS 659.62 kB（gzip 210.12 kB）、CSS 25.57 kB（gzip 5.36 kB）。

### P15 后端测试补齐与管理端功能收口 ✅

**后端单测（本轮新增 66 个用例，总量 32 → 98）**

| 测试类 | 用例数 | 覆盖点 |
|---|---|---|
| `ChatServiceTest` | 18 | `stream` / `regenerate` 全分支：空消息、智能体不存在、新建 / 复用会话、空白回复不落库、模型覆盖、关闭 RAG、执行器抛错、重新生成（会话不存在 / 无用户消息 / 清理旧回复 / 重插用户消息）、上下文组装（systemPrompt + 工具 + 历史，`tool` 角色被过滤） |
| `KnowledgeServiceTest` | 17 | 分段入库、空分段失败、向量化失败落库并保留原因、缺失文档跳过、分段明细映射（`hasEmbedding` 语义）、重新向量化、分段增删改、向量化先失败时内容不被改坏 |
| `ConversationControllerTest` | 13 | DTO 隔离（客户端注入 `id`/`createdAt`/`updatedAt` 被丢弃）、名称空值回落、状态白名单、局部更新只改提交字段、存在性校验、列表按 `agentId` 过滤 |
| `KnowledgeControllerTest` | 18 | 上传校验（名称 / 内容长度）、分段明细、重新向量化（校验先于异步提交）、分段编辑 / 删除、`topK` 边界收敛、`searchContext` 组装 |

**后端**

- `ConversationController` 的请求体新增独立 DTO，写入路径只拷贝白名单字段。
- `KnowledgeController.normalizeTopK` 对可空入参做了边界收敛：缺失时取 `xunwu.rag.top-k`，
  最终夹在 `1..20` 区间内（本轮由测试覆盖）。

**前端（补齐管理端能力）**

- `AgentsPage`：表单新增「状态」字段（启用 / 停用）；列表新增「停用 / 启用」按钮。
  修复编辑已停用智能体时会被静默改回 `ACTIVE` 的问题。
- `ChatPage`：会话选择器新增「归档 / 取消归档」，归档会话带「已归档」标记；
  智能体选择器过滤 `INACTIVE` 智能体，选中项被停用后自动回落到首个可用智能体。
- `ChatPage.scss`：新增 `picker-item-meta` / `picker-item-badge` 样式。

**验证**

- 后端 `mvn test`（JDK 21）：**98 通过 / 0 失败 / 0 错误**。
- 前端 `npm run build`：通过，产物 JS 661.05 kB（gzip 210.49 kB）、CSS 25.83 kB（gzip 5.39 kB）。
