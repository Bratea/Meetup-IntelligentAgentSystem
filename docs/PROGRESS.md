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

### P16 健壮性加固与端到端复验 ✅

**后端**

- `AgentExecutor`：客户端断连时**真正中断**模型调用。原实现只在循环 / 工具执行 /
  token 回调里检查 `sink.isCancelled()`，但 `blockLast` 仍会空等整个模型响应
  （最坏 10 分钟），持续消耗本地算力。改为 `Sinks.One` 取消信号 +
  `takeUntilOther` + `CountDownLatch`：`FluxSink.onCancel` 触发后，
  正在进行的流式调用立即中断。
- `AgentExecutor`：修正达到最大步数时事件里的步数（原来多报 1 步）。
- `AgentExecutor`：模型超时由底层 `IllegalStateException` 改为受检异常包装，
  由 `toUserMessage` 统一翻译成「模型响应超时，请稍后重试或减小输入长度」。
- `ChatService.doStream`：校验 `conversationId` 是否存在、是否属于当前智能体。
  原实现直接落库，伪造 / 已删除的 id 会触发外键约束异常（500），
  跨智能体的 id 则会静默把消息写进别人的会话。
- `KnowledgeController`：`search` / `searchContext` 校验查询词非空，
  避免 `embeddingService.embed(null)` 抛异常兜底成 500。
- `AgentController`：`name` 长度上限 128（与 `VARCHAR(128)` 对齐），超长返回 400。

**前端**

- `ChatPage`：`tool_result` 按「第一个还没有结果」的条目匹配，不再按工具名匹配。
  同一轮重复调用同名工具时，原实现会把结果写到错误的条目上。
- `KnowledgePage`：修复轮询计数被加两次导致提前停止的问题
  （`setInterval` 前误增一次，实际轮询时长减半）。

**验证**

- 后端 `mvn test`（JDK 21）：**113 通过 / 0 失败 / 0 错误**
  （新增 `AgentControllerTest` 10 个、`ChatServiceTest` +2、`KnowledgeControllerTest` +3）。
- 前端 `npm run build`：通过，产物 JS 661.08 kB（gzip 210.51 kB）、CSS 25.83 kB（gzip 5.39 kB）。
- 本地全链路实测（PostgreSQL 17 + pgvector / Ollama bge-m3 + qwen2.5:7b）：
  文档上传 → 向量化 → 检索命中（相似度 0.594）、普通对话、工具调用（`current_time`）、
  RAG 注入问答、重新生成（旧回复被替换）、消息落库、断连取消全部正常。

---

## P17：模型扩展与语义修正

**后端**

- `ChatClientRegistry`：新增 LongCat（OpenAI 兼容协议）模型接入。
  Spring AI 自动配置只创建一个 `OpenAiChatModel`（已被 DeepSeek 占用），
  因此手工构造独立实例；未配置 `LONGCAT_API_KEY` 时只登记元信息并标记不可用，
  不影响其他模型。配置项 `longcat.base-url` / `longcat.api-key` / `longcat.model`
  均支持环境变量覆盖。
- `AgentExecutor.toUserMessage`：兜底分支不再把底层异常 message 回给客户端
  （可能含地址、连接串等内部信息），细节只留日志。
- `KnowledgeService.updateStatus`：状态离开 FAILED 时清空 `error_message`，
  避免接口向调用方残留过时的失败原因。
- `ChatClientRegistry.resolveModelId`：未知模型改为抛异常，不再静默回退默认模型。
- `ChatClientRegistry.contains`：改为纯查询语义，未知模型返回 false。
- `ChatClientRegistry`：`bge-m3` 不再标记为 `defaultModel`（它是向量模型，
  模型页本就会出现两个「默认」徽章）。

**前端**

- `ModelsPage`：`providerMeta` 补充 LongCat 图标与名称。

**验证**

- 后端 `mvn test`：**118 通过 / 0 失败 / 0 错误**（新增 `ChatClientRegistryTest` 5 个）。
- 前端 `npm run build`：通过，产物 JS 661.12 kB（gzip 210.53 kB）、CSS 25.83 kB（gzip 5.39 kB）。
- 本地实测（带 `LONGCAT_API_KEY` 启动）：`/api/models` 正确显示 LongCat 可用；
  LongCat 普通对话、RAG 问答（正确引用知识库内容）、工具调用均正常；
  未知模型返回 `未注册的模型: nope，可用模型: [...]` 错误事件；
  空模型回退默认模型正常。

---

## P18：边界校验与工具健壮性

**后端**

- `ChatPage`：补卸载时中止在途流的清理。原先只有「停止」按钮会 abort，
  路由切走后 fetch 仍在后台跑，后端继续生成直到完成（白烧算力）。
  与 P16-1 的 `FluxSink.onCancel` 配合才能真正停下模型调用。
- `AgentController`：`description` / `avatar` / `modelId` 补齐列宽校验
  （512 / 255 / 64），与已有的 `name`(128) 统一走 `assertFitsColumn`，超长返回 400。
- `CurrentTimeTool`：pattern 完全由模型生成，非法值会让
  `DateTimeFormatter.ofPattern` 抛异常并导致工具调用失败；现在捕获后回退默认格式。
- `KnowledgeSearchTool`：底层异常 message 不再直接回给模型，
  只有 `BizException`（已脱敏，会提示该拉取哪个模型）才回传，其余统一兜底。

**验证**

- 后端 `mvn test`：**126 通过 / 0 失败 / 0 错误**（新增 `CurrentTimeToolTest` 4 个、
  `AgentControllerTest` +4）。
- 前端 `npm run build`：通过。
- 实测：非法 pattern（`yyyy-To`）回退默认格式且模型正确理解；
  知识库检索工具返回命中结果；超长 `description` / `modelId` 均返回 400。

---

## P19：固定工具重复注册

**问题**

`knowledge_search` 是固定工具（所有智能体自动生效），但前端把它当成普通可选项渲染。
一旦勾选，`toolIds` 里就会同时出现它，`buildContext` 先按 `toolIds` 取一遍、
再 `addAll(getFixedTools())` 追加一遍，同名工具注册两次。

Spring AI 的 `ToolCallingChatOptions.validateToolCallbacks` 对同名工具直接抛
`IllegalStateException`，实测整个对话只推出一个 `thinking` 事件就失败：

```
java.lang.IllegalStateException: Multiple tools with the same name (knowledge_search)
    found in ToolCallingChatOptions
```

**修复**

- `ToolRegistry`：新增 `getMergedTools(names)` / `getMergedToolNames(names)`，
  按工具名去重合并可选工具与固定工具，从根上保证同名只出现一次。
- `ChatService.buildContext`：改用上述两个方法，删掉手工 `addAll` 追加。
- `AgentsPage`：FIXED 工具渲染为禁用且常开的勾选框（附「自动启用」提示），
  并且不再提交到 `toolIds`；maxSteps 输入上限由 20 对齐到后端的 50。

**验证**

- 后端 `mvn test`：**131 通过 / 0 失败**（`ToolRegistryTest` +5 个去重用例）。
- 前端 `npm run build`：通过。
- 实测：构造 `toolIds=["knowledge_search","current_time"]` 的智能体，
  对话正常完成并返回 `done`（修复前只推 thinking 就报错）。

---

## P20：资源释放、异常脱敏与并发幂等

**问题**

1. `AgentExecutor.callModelStreaming` 用 `subscribe(...)` 等流式调用结束，
   但把返回的 `Disposable` 丢掉了。一旦 `latch.await` 超时（10 分钟），
   底层 HTTP 调用仍在后台跑完 —— 连接、线程、模型算力全部白烧，
   订阅也不会被回收。

2. `AgentToolService.execute` 的兜底分支把 `e.getMessage()` 原样写进返回给模型的
   JSON 错误对象。工具底层异常（DB、连接池、SQL）的 message 可能含连接串、
   表名、内部地址，这些内容会经模型转述进入对话内容。

3. `KnowledgeService.processDocument` 由 `taskExecutor` 异步执行，本身没有幂等保护。
   前端连点两次「重新向量化」会提交两个任务，两者都执行
   「删旧分段 → 插入新分段」，最终同一文档下出现两份重复分段，
   检索结果被重复内容占满。

4. `KnowledgePage` 的 `actionError` 只在 `expandedId === doc.id` 时渲染。
   而「重新向量化」是对未展开文档也能执行的操作，失败时用户看不到任何反馈。
   另外轮询计数 `pollCountRef` 在所有文档处理完成后没有归零。

5. `ToolRegistry.resolveToolNames` 在 P19 之后已无生产调用方
   （被 `getMergedToolNames` 取代），只剩测试引用，属于死代码。

**修复**

- `AgentExecutor`：持有 `Disposable`，在 `latch.await` 超时与 `InterruptedException`
  两条路径上都调用 `dispose()`，确保底层订阅被释放。
- `AgentToolService`：把「取工具」与「执行工具」拆开。
  未注册工具名回传（模型需要知道这个工具不存在，否则会反复调用）；
  执行异常统一替换为兜底文案，底层信息只留日志。
- `KnowledgeService`：按文档 ID 分段加锁（16 个常驻锁对象）串行化处理。
  锁对象不移除，因此不会出现「后来者拿到新锁、与持旧锁者并发」的窗口，
  也不会泄漏锁对象。
- `KnowledgePage`：`actionError` 提升到页面级渲染（保证一定可见），
  并在所有文档处理结束后把轮询计数归零。
- `ToolRegistry`：删除 `resolveToolNames` 及其测试，
  清理 `ChatServiceTest` 里对应的过期 mock，
  修正 `getTools_returnsMutableList` 中「调用方会 addAll 固定工具」的过时注释。

**验证**

- 后端 `mvn test`：**134 通过 / 0 失败 / 0 错误**
  （新增 `AgentToolServiceTest` 4 个，删除 `resolveToolNames` 用例 1 个）。
- 前端 `npm run build`：通过。
- 实测：普通对话与 RAG 问答正常；
  构造带不存在工具（`not_exist_tool`）的智能体，对话正常完成并返回 `done`，
  未出现 500。

## P21：关联校验、异常兜底与 CI

**发现**

1. `WebConfig` 把 MVC 异步超时设成 `600_000`（10 分钟），与
   `AgentExecutor.MODEL_TIMEOUT` 相同。但一次对话会跑多步、
   每步各有一次模型调用，整条 SSE 流的实际时长是「步数 × 单步时长」。
   超时与单步时长相等时，多步对话会先被 MVC 挡断，
   表现是「聊着聊着连接就断了」，且日志里看不到业务异常。

2. `ConversationController.create` 只校验 `agentId` 非空，
   不校验智能体是否存在。`conversation.agent_id` 是 NOT NULL 外键，
   传一个不存在的 id 会直接触发约束异常，
   在补上 P21-3 之前表现为 500。

3. `GlobalExceptionHandler` 没有处理 `DataIntegrityViolationException`：
   所有外键 / 非空 / 唯一约束冲突都掉进兜底的 `Exception` 分支变 500，
   客户端拿到「系统繁忙」，无法知道是自己的参数写错了。

4. 仓库已有 137 个单元测试，但没有任何 CI。
   push / PR 之后无法自动验证，回归只能靠本地手跑，
   前端类型错误与后端测试失败都可能在合并后才被发现。

**修复**

- `WebConfig`：MVC 异步超时提升到 `1_800_000`（30 分钟），
  作为整条 SSE 流的上限。足够覆盖多步对话，
  同时避免异常情况下异步任务无限期挂住
  （客户端断连仍由 `FluxSink.onCancel` 提前结束）。
- `ConversationController`：注入 `AgentMapper`，
  创建前校验智能体存在，不存在返回 404。
- `GlobalExceptionHandler`：新增 `DataIntegrityViolationException` 处理器，
  返回 400 + 可读文案，原始信息只进日志。
- 新增 `.github/workflows/ci.yml`：
  后端 job 跑 `mvn -s .mvn/settings.xml test` 并上传 surefire 报告；
  前端 job 跑 `npm ci` + `npm run build`（含 `tsc -b` 类型检查）并上传产物；
  带 `concurrency` 组，新提交直接取消同分支的旧任务。

**验证**

- 后端 `mvn test`：**137 通过 / 0 失败 / 0 错误**
  （新增 `GlobalExceptionHandlerTest` 2 个、`ConversationControllerTest` +1）。
- 前端 `npm run build`：通过。
- CI YAML 经解析校验：触发器 `push` / `pull_request` → `main`，
  job 结构 `backend` / `frontend` 正确。
- 端到端实测：`POST /api/conversations` 传不存在的 agentId 返回 404
  （修复前会触发外键约束异常），传正常 agentId 返回 200；
  LongCat-2.5 带 RAG 问答正确引用知识库内容（pgvector），
  事件序列 thinking / token / done 正常。
- 构建备注（Windows）：应用运行时执行 `mvn package` 会因 jar 被占用
  导致 `repackage` 的 rename 失败（报 PluginExecutionException），
  留下未重打包的 thin jar。打包前先停掉运行中的实例。
  校验产物大小时用 `stat -c %s`，不要用 `ls -l | awk '{print $5}'`
  （Git Bash 下该列不是字节数）。
