# 寻晤智能agent系统 — 自检问题清单

> 首次检查：2025-11-05
> 修复完成：2025-11-06
> 检查范围：后端 `backend/`、前端 `frontend/`、部署 `docker-compose.yml` / `Dockerfile` / `nginx.conf`
> 验证方式：`mvn compile`、`npm run build`、JDK 21 运行时行为验证、Spring AI 1.1.0 字节码核对、
> Docker 全栈端到端验证（API + Playwright UI）
>
> **总计 37 项，全部 ✅ 已修复**（P0 ×6、P1 ×10、P2 ×5、二轮 ×8、三轮 ×5、四轮 ×3）

图例：✅ 已修复 ｜ ⏳ 已规避/降级处理 ｜ ➖ 确认无需处理

---

## 🔴 P0 — 阻断性缺陷（功能实际不可用）

### 1. ✅ 后端缺少 `/api/agents` 控制器，智能体管理页完全不可用

- **现象**：前端 `AgentsPage` 调用 `GET/POST/PUT/DELETE /api/agents`，后端 `controller/` 目录下**没有 `AgentController`**，全部返回 404。
- **影响**：无法创建 / 编辑 / 删除智能体。而对话页依赖智能体存在，等于**整个产品主流程跑不通**。
- **修复**：新增 `AgentController` + 专用 `AgentRequest` DTO（屏蔽客户端注入 `id` / `createdAt` 等内部字段），
  实现列表 / 详情 / 新建 / 更新（动态 `<set>` 部分更新）/ 删除，并对 `name` 必填、`toolIds` JSONB 归一化、
  `status` 白名单、`maxSteps` 范围做校验。

### 2. ✅ `ToolRegistry.getTools()` 返回不可变 `List.of()`，`ChatService` 直接 `addAll` 抛异常

- **代码**：
  ```java
  // ToolRegistry
  public List<ToolCallback> getTools(List<String> names) {
      if (names == null || names.isEmpty()) {
          return List.of();          // ← 不可变
      }
      ...
  }
  // ChatService.buildContext
  List<ToolCallback> tools = toolRegistry.getTools(toolNames);
  tools.addAll(toolRegistry.getFixedTools());   // ← UnsupportedOperationException
  ```
- **验证**：JDK 21 实测 `List.of().addAll(List.of())` → `UnsupportedOperationException`（即使参数为空也抛）。
- **影响**：前端新建智能体默认 `toolIds: '[]'` → `toolNames` 为空 → **每次对话都 500**。默认流程必坏。
- **修复**：`getTools()` / `getFixedTools()` 统一改为 `Collectors.toCollection(ArrayList::new)`；
  `ChatService` 侧再包一层 `new ArrayList<>(...)`，双重保险。

### 3. ✅ `AgentExecutor.runStream` 每个 step 调用模型两次，且没有真正的逐字流式输出

- **原代码**：先 `streamModel()` 拿文本流，再 `callModel()` 拿 `tool_calls`。
- **问题**：
  1. **双倍推理成本与延迟**；
  2. **两次生成结果可能不一致** —— 用户看到的文本与实际发起的工具调用来自不同生成，历史里只保留第二次的响应；
  3. `tokenSink.asFlux().collectList().block()` 把全部 token 收齐后才拼成一个字符串发一条 `token` 事件 → **前端收不到逐字输出**；
  4. `contentBuilder` 累加了却从未使用（死代码）。
- **修复**：重写为**单次调用** `ChatClient.stream().chatResponse()`，边收 token 边推送，
  同时按 index 合并 Spring AI 流式聚合的 `tool_calls`（`mergeToolCalls()`：优先非空 `id`/`type`/`name`、取更长 `arguments`）。
  删除 `execute()` / `callModel()` / `streamModel()`，新增私有 `StreamingOutcome` record。
- **补充修复**：推送通道由 `Sinks.many().unicast().onBackpressureBuffer()` 改为
  `Flux.create(..., FluxSink.OverflowStrategy.BUFFER)`，消除"订阅前有界缓冲溢出丢事件"的隐患；
  并在循环 / 工具执行 / token 回调中检查 `sink.isCancelled()`，客户端断连后立即停止模型调用。

### 4. ✅ `toolNames` 未过滤，含未注册工具名时直接抛异常

- **原代码**：`ChatService.buildContext` 把**原始**的 `agent.toolIds` 解析结果塞进 `context.setToolNames()`。
- **机制核对**：`DefaultToolCallingManager.resolveToolDefinitions()` 会以 `toolCallbacks` 为基础，对 `toolNames` 中**不在 callbacks 里的名字**调用 `toolCallbackResolver.resolve(name)`；解析不到就抛 `IllegalStateException`。而 `OpenAiChatModel` / `OllamaChatModel` / `ZhiPuAiChatModel` 在 `createRequest` 阶段**无条件**调用 `resolveToolDefinitions`（与 `internalToolExecutionEnabled` 无关）。
- **影响**：智能体配置里出现已禁用 / 已删除 / 拼错的工具名 → 该智能体**所有对话 500**，而不是降级忽略。
- **修复**：`ToolRegistry` 新增 `resolveToolNames()`（只返回已注册且启用的名字）与 `getFixedToolNames()`，
  `ChatService` 用它们填充 `toolNames`；`buildChatOptions` 中对 null 做了兜底。

### 5. ✅ RAG 知识库从未接入对话

- **证据**：`AgentRequest.enableRag`、`AgentContext.knowledgeContext` 两个字段**定义了但全文无任何赋值/读取**。
- **修复**：
  - `AgentContext` 新增 `knowledgeQuery` / `knowledgeLoaded`；
  - `ChatService` 在 `enableRag` 时把用户消息写入 `knowledgeQuery`；
  - `AgentExecutor.loadKnowledge()` 在异步线程中检索，把命中片段作为 `SystemMessage` 注入，失败时降级为"无知识库"不中断对话；
  - 新增 `KnowledgeSearchTool`（FIXED）作为模型自主发起的二次检索入口，与自动注入互补；
  - 前端对话页新增「RAG 开 / RAG 关」切换按钮。

---

## 🟠 P1 — 严重问题（稳定性 / 正确性）

### 6. ✅ `DocumentSplitter` 存在死循环，可致线程挂死 + OOM

- **原代码**：
  ```java
  int start = 0;
  while (start < text.length()) {
      int end = Math.min(start + chunkSize, text.length());
      chunks.add(text.substring(start, end));
      start = end - overlap;      // ← 可能不前进
      if (start < 0) start = 0;
  }
  ```
- **触发条件**：单段长度落在 `(chunkSize, chunkSize + overlap]` 区间。例：`chunkSize=500, overlap=50, len=520` → start 序列 `0 → 450 → 470 → 470 → 470 …` 永不退出。
- **修复**：`overlap` 夹紧到 `chunkSize - 1`；`hardSplit()` 引入 `lastStart` 游标，若本轮 `start` 未前进则强制 +1。
  另在 `XunwuProperties.validate()` 中对 `chunkSize` / `chunkOverlap` 做启动期校验。

### 7. ✅ `KnowledgeService.processDocument` 事务回滚导致 FAILED 状态丢失

- **原代码**：`updateStatus(documentId, "FAILED")` 之后 `throw new BizException(500, ...)`，外层 `@Transactional` 回滚掉 FAILED。
- **修复**：去掉方法级 `@Transactional`，改用 `TransactionTemplate` 显式包裹"批量插入分段 + 更新状态"这一原子操作；
  失败路径调用独立的 `markFailed()`，异常信息走 `rootMessage()` 提取根因。

### 8. ✅ `@Transactional` 包裹远程网络调用，长时间占用数据库连接

- **修复**：同第 7 条 —— 事务边界收窄到纯 DB 操作，Ollama embedding 的 HTTP 调用放在事务外。

### 9. ✅ 异步任务用裸 `new Thread(...)`，无线程池、无异常兜底

- **修复**：`AsyncConfig` 统一提供 `taskExecutor` 线程池（核心 4 / 最大 16 / 队列 200 / `CallerRunsPolicy` / 优雅停机），
  `KnowledgeController.upload` 与 `AgentExecutor.executeStream` 全部改为提交到该池；
  移除 `@EnableAsync` 与未被使用的 `agentExecutor` 命名。

### 10. ✅ 全局异常处理泄露内部信息，且所有异常都返回 HTTP 200

- **修复**：`GlobalExceptionHandler` 所有方法改为返回 `ResponseEntity<ApiResponse<?>>` 并设置正确 HTTP 状态码；
  `Exception.class` 兜底返回固定的"系统繁忙，请稍后重试"，不再回显 `e.getMessage()`；
  新增 `HttpMessageNotReadableException` / `MissingServletRequestParameterException` 处理器。

### 11. ✅ 前端 `request.ts` 不校验 HTTP 状态码

- **修复**：新增 `NetworkError` 类；非 2xx 时读取响应体里的 `message` 字段抛出可读错误；
  JSON 解析失败时回退为"响应解析失败"而不是 `Unexpected token '<'`。

### 12. ✅ `ChatPage` 默认模型可能选到 embedding 模型

- **修复**：默认模型只在 `type === 'chat'` 的过滤结果里挑选；并新增 `useEffect` 兜底纠正：
  若当前 `selectedModel` 不在 chat 列表中，自动切回默认 chat 模型。

### 13. ✅ 会话不可复用，历史消息永不加载

- **修复**：`ChatPage` 重写 —— 首次发消息时创建会话并写入 `conversationId`，之后复用；
  顶部新增会话下拉可切换历史会话（切换时拉取该会话消息并重建消息列表）；
  「清空」按钮重置为「新会话」。

### 14. ✅ `ChatService.loadHistory` 无条数上限

- **修复**：新增常量 `MAX_HISTORY_MESSAGES = 20`，只取最近 20 条；
  加载时跳过 `tool` 角色消息，避免历史工具调用上下文污染。

### 15. ✅ 知识库检索接口缺少参数校验与边界

- **修复**：`KnowledgeController` 统一校验 `name` / `content` 长度（`MAX_CONTENT_LENGTH = 200_000`），
  `normalizeTopK()` 统一夹紧到 `[1, 20]`；`query` 与 `search` 行为对齐。

### 16. ✅ 数据库约束与代码不一致

- **修复**：
  - `AgentMapper.insert` 改用 `COALESCE(#{modelId}, 'qwen2.5:7b')`、`COALESCE(#{toolIds}, '[]'::jsonb)`、`COALESCE(#{maxSteps}, 8)`；
  - `02_schema.sql` 的 `agent.model_id` 默认值改为 `'qwen2.5:7b'`；
  - `embedding vector(1024)` 的维度说明已在 README 中标注需与 `xunwu.rag.embedding-dimensions` 一致（Ollama 侧 bge-m3 固定 1024）。

---

## 🟡 P2 — 一般问题（代码质量 / 体验）

### 17. ✅ 死代码与未使用成员（已全部清理）

| 位置 | 处理 |
|------|------|
| `EmbeddingService` | 移除未使用的 `parseVector()` 与 `OllamaEmbeddingOptions` import |
| `ChatClientRegistry` | 移除 `resolveChatModel()` / `buildOptions()` / `apply*Option()` / `availableModelIds` / `getChatModel` |
| `AgentExecutor` | 移除 `execute()`（非流式入口） |
| `ToolCallingManagerImpl` | 整个类删除（死代码；且按 `call.name()` 取结果存在同名工具串结果风险） |
| `AgentState.isTerminal()` | 移除 |
| `AsyncConfig` | 重写，`agentExecutor` → `taskExecutor`，移除 `@EnableAsync` |
| `ToolRegistry.getOptionalTools()` / `register(Supplier)` 重载 | 移除（无调用方） |
| `AgentEvent` | 移除未使用的 `error()` 工厂与 `ExecutionResult` |
| `AgentToolService` / `CalculatorTool` / `CurrentTimeTool` / `ChatService` | `ObjectMapper` 提为 `static final` |
| `HealthController` | 改为真实探测 DB（`Connection.isValid`）与 Ollama（`GET /api/tags`），返回 UP/DOWN 明细 |
| `ChatController` | Javadoc 更新为 `fetch` 流式示例 |
| 全项目未使用 import | 脚本清理 |

### 18. ✅ 工具实现细节

- `CalculatorTool`：支持一元正负号与负数运算；除零、NaN、Infinite 均返回明确错误而非抛异常。
- `CalculatorTool` / `AgentToolService`：错误响应统一用 `ObjectMapper` 序列化，不再手工拼 JSON 字符串。
- `ToolRegistry.getFixedTools()`：现在会过滤 `enabled`。
- 新增 `KnowledgeSearchTool`，`ToolType.FIXED` 分支有了真实实现。

### 19. ✅ 前端体验

- `ChatPage` 消息改用稳定自增 `id`（`idSeedRef`）作为 React key，不再用数组下标。
- 滚动改为仅在"消息条数变化"时触发（`lastCountRef` 守卫），流式输出期间不再反复平滑滚动。
- `KnowledgePage` 改为 `useRef` + 定时轮询（上限 150 次），移除 `setTimeout` 猜测。
- `KnowledgeDocumentMapper` 列表查询改为 `selectSummaryList()`（`LEFT(content, 200) AS content_preview`），
  列表页不再传输整篇文档；实体新增 `contentPreview` 字段。
- `KnowledgePage` 时间统一走 `formatTime()` 格式化为本地时间。
- `streamChat` 增加 `decoder.decode(value, { stream: true })` 与结束时的 `decoder.decode()` flush。
- `streamChat` 新增 `AbortError` 分支：中断时只触发 `onComplete`，不误报网络错误。

### 20. ✅ 部署与配置

- `nginx.conf` 新增 `client_max_body_size 20m;`。
- `backend/Dockerfile` 的 `apt-get install curl` 加上 `--no-install-recommends`。
- `logging.level.com.xunwu.agent: debug` → `info`；`org.springframework.ai: warn`。
- `01_extensions.sql` 移除未使用的 `pg_trgm`。
- 新增 `.env.example`，补全 `DEEPSEEK_API_KEY` / `ZHIPU_API_KEY` / `OLLAMA_BASE_URL` 等配置样例。
- `WebConfig` 的 CORS 来源改为可配置 `${xunwu.cors.allowed-origins:...}`。
- `application.yml` 的 `base-url` 补上默认值 `${DEEPSEEK_BASE_URL:https://api.deepseek.com}`。

---

## 🔵 第二轮修复（本地 Docker 端到端验证发现）

### 21. ✅ 未配置任何 API Key 时应用无法启动

Spring AI 的 `OpenAiChatAutoConfiguration.openAiApi()` 会对 `api-key` 做
`Assert.hasText` 校验，`application.yml` 里没有默认值时整个上下文启动失败。

- `application.yml` 的 `DEEPSEEK_API_KEY` / `ZHIPU_API_KEY` 补占位默认值
  `${DEEPSEEK_API_KEY:sk-not-configured}`。
- `ChatClientRegistry` 改为**直接读环境变量**（`${DEEPSEEK_API_KEY:}` / `${ZHIPU_API_KEY:}`）
  判断可用性，占位值不会让模型被误判为可用。

### 22. ✅ PostgreSQL 拒绝 varchar → jsonb 隐式转换

`tool_ids` / `tool_calls` / `metadata` 三列都是 `jsonb`，MyBatis 传入的
`String` 绑定参数在 PG 侧是 `varchar`，`INSERT`/`UPDATE` 直接报
`column is of type jsonb but expression is of type character varying`。

- `AgentMapper.xml` INSERT / UPDATE 均改为 `CAST(#{toolIds} AS jsonb)`。
- `MessageMapper.xml` INSERT 改为 `CAST(#{toolCalls} AS jsonb)`。
- `KnowledgeSegmentMapper.xml` INSERT / batchInsert 改为 `CAST(#{metadata} AS jsonb)`。

### 23. ✅ SSE 链路上抛异常会导致 500 + `HttpMessageNotWritableException`

`produces = text/event-stream` 的响应一旦提交响应头，HTTP 状态码就无法再改。
`AgentExecutor.executeStream()` 里 `emitter.error(e)` 与 `ChatService` 里
直接 `throw` 都会被 Spring MVC 的 JSON 转换器接管，前端只会收到半截流。

- `AgentExecutor.executeStream()` 捕获异常后改为
  `emitter.next(AgentEvent.error(toUserMessage(e)))` + `emitter.complete()`。
- 新增 `toUserMessage(Exception)`：`WebClientResponseException` 按状态码翻译
  （404 → 提示 `ollama pull`；401/403 → 鉴权失败；5xx → 暂时不可用），
  `ConnectException` / `UnknownHostException` → 无法连接模型服务，
  `TimeoutException` → 模型响应超时。
- `ChatService.stream()` 外层再包 try/catch，并追加
  `.onErrorResume(e -> Flux.just(AgentEvent.error(...)))` 作为最终兜底。
- 前端 `case 'error'` 现在会把服务端文案追加到助手气泡并加 ⚠️ 前缀。

### 24. ✅ Spring MVC 异步请求没有专用线程池

`Flux` 返回值会触发 `request.startAsync()`，未配置 `AsyncTaskExecutor` 时
Spring MVC 回退到 `SimpleAsyncTaskExecutor`（每次新建线程、无上限），
启动日志有明确告警。

- `AsyncConfig` 新增 `mvcAsyncExecutor`（core 2 / max 8 / queue 64 / 拒绝策略 Abort）。
- `WebConfig.configureAsyncSupport()` 注册该 executor，并设
  `defaultTimeout(600_000)`（10 分钟，与 `MODEL_TIMEOUT` 对齐）。
- 启动告警已消失。

### 25. ✅ 向量化失败时错误信息不可读

Ollama 返回 404 时 Spring AI 抛的是 `NonTransientAiException`，
消息形如 `HTTP 404 - {"error":"model 'bge-m3' not found"}`，没有类型化的状态码访问器。

- `EmbeddingService` 捕获异常并用 `resolveHttpStatus()`（正则 `HTTP\s+(\d{3})`）
  从消息里提取状态码，映射为：404 → "向量化模型 bge-m3 未安装，请先执行
  `ollama pull bge-m3` 拉取"；401/403 → 鉴权失败；5xx → 暂时不可用；
  `ConnectException` → 无法连接向量化服务。
- `BizException.serviceUnavailable()`（code 503）与
  `GlobalExceptionHandler.mapStatus()` 支持任意 4xx/5xx 码
  （原先只硬编码 400/404/500）。

### 26. ✅ 模型列表的 `available` 只反映"配置了"，不反映"装好了"

`qwen2.5:7b` 只要 Ollama 的 `ChatModel` bean 存在就标记可用，模型页显示"可用"，
但真发消息才报 404，体验割裂；`bge-m3` 则完全没有登记。

- `ChatClientRegistry` 新增 `refreshOllamaAvailability()`：调用
  `GET {ollamaBaseUrl}/api/tags` 解析已安装模型名（正则 `"name"\s*:\s*"([^"]+)"`），
  带 30 秒 TTL 缓存，超时 3 秒，任何异常降级为"未安装"。
- 新增 `bge-m3`（`type=embedding`）登记，与 `qwen2.5:7b` 同组展示。
- `listModels()` 每次调用前刷新，因此 `/api/models` 返回的 `available`
  是"模型是否真的拉下来了"。

### 27. ✅ 智能体 `toolIds` 前后端契约不一致

前端发送 `string[]`，后端 `AgentRequest.toolIds` 是 `String`，
导致 `InvalidDataAccessApiUsageException`。

- `AgentRequest.toolIds` 改为 `List<String>`，新增 `toJsonArray(List<String>)`
  统一做 trim / 去重 / 丢空值 / 序列化。

### 28. ✅ 死代码与重复注册

- 删除 `ToolRegistry.getOptionalTools()`（无调用方）。
- 删除 `ToolRegistry.register(Supplier<String>)` 重载（无调用方）。
- 删除 `ModelInfo.defaults()`（无调用方）。
- `ModelInfo` / `CurrentTimeTool` / `AgentController` 的 `ObjectMapper`
  提升为 `static final`，避免每次调用新建实例。

### 29. ✅ Docker 环境下后端无法启动：空串 API Key 触发 Spring AI 断言

`docker-compose.yml` 里 `${DEEPSEEK_API_KEY:-}` 在用户未配置时传的是**空串**，
而 Spring 占位符 `${VAR:default}` 只在变量**未定义**时才取默认值 —— 变量存在但为空串
仍然拿到空串。于是 `OpenAIAutoConfigurationUtil.resolveConnectionProperties()` 的
`Assert.hasText` 直接失败，`ChatClientRegistry` 初始化中断，后端容器反复重启。
（`ZhiPuAiChatAutoConfiguration` 同样有 `hasText` 断言。）

- 新增 `ApiKeyPlaceholderEnvironmentPostProcessor`（注册在
  `META-INF/spring.factories`），把空白的 `DEEPSEEK_API_KEY` / `ZHIPU_API_KEY`
  改写为占位值 `sk-not-configured`。
- `ChatClientRegistry` 新增 `PLACEHOLDER_API_KEY` 常量，`hasText()` 会把占位值
  识别为"未配置"，不会误报模型可用。
- 本地开发显式 `export DEEPSEEK_API_KEY=` 的场景同样被覆盖。

### 30. ✅ 前端容器 HEALTHCHECK 一直 unhealthy

`wget -qO- http://localhost/` 失败：Alpine 镜像里 `localhost` 只解析到 `::1`，
而 nginx 只 `listen 80`（IPv4）。

- HEALTHCHECK 改用 `http://127.0.0.1/`。
- `nginx.conf` 补 `listen [::]:80;`。

### 31. ✅ 模型 API Key 占位值语义

- `docker-compose.yml` 补注释说明空串会被后处理器改写。
- `.env.example` 保持空值样例，用户复制后不会导致启动失败。

### 32. ✅ Ollama 已安装模型判定：`/api/tags` 的 name 带 tag

`refreshOllamaAvailability()` 用 `names.contains("bge-m3")` 判断，但 Ollama 返回的是
`{"name":"bge-m3:latest"}`，导致 bge-m3 实际已安装却仍显示 `available=false`
（`/api/models` 与前端模型下拉都拿不到它）。

- 新增 `matchesInstalled(Set, modelId)`：完全相等直接命中；注册名不带 `:` 时，
  任意 tag 都算命中。这样 `bge-m3` ↔ `bge-m3:latest`、`qwen2.5:7b` ↔ `qwen2.5:7b`
  两种写法都能正确匹配。

### 33. ✅ 知识库失败原因不可见

文档处理失败时只把 `status` 置为 `FAILED`，前端只能看到一个没有下文的红色标签，
用户无法判断是模型没装、Ollama 挂了还是内容有问题。

- `knowledge_document` 新增 `error_message`（`02_schema.sql` + 幂等迁移
  `03_migrations.sql`，已存在的库也能补列）。
- `KnowledgeService.markFailed()` 把原因落库（截断 500 字）。
- 列表接口一并返回 `error_message`，前端在 FAILED 时展示红色原因块。

### 34. ✅ 连接类故障被误判为"系统繁忙"

Spring AI / WebClient 对连不上 Ollama 的包装层次不固定：实测连接拒绝拿到的是
`java.nio.channels.ClosedChannelException`，既不是 `ConnectException` 也不是
`UnknownHostException`，于是走到兜底分支，用户看到"向量化服务调用失败，请稍后重试"
或"系统繁忙"，没有任何可操作的信息。

- 新增 `ThrowableUtils`：`isConnectionFailure()` 覆盖
  `ConnectException` / `UnknownHostException` / `ClosedChannelException` /
  `SocketException` / `HttpConnectTimeoutException` / `ResourceAccessException`，
  并辅以连接关键字正则（connection refused/reset/closed/timed out）。
- `EmbeddingService` 与 `AgentExecutor` 统一改用它。
- 实测：Ollama 不可达时文档失败原因变为
  "无法连接向量化服务，请确认 Ollama 已启动"，
  SSE 错误事件变为"无法连接模型服务，请确认 Ollama 已启动、网络可达"。

### 35. ✅🔴 P0 工具调用 100% 失败（最严重）

端到端实测发现：模型正确发出了 `tool_calling` 事件，但 `tool_result` 恒为
`{"detail":"Conversion from JSON to java.lang.String failed"}`，
即所有工具调用都拿不到结果，智能体最多只能"承认工具不可用"然后自己编答案。

**根因**：`ToolRegistry` 用 Spring AI 的 `FunctionToolCallback` 注册工具，
并设置 `.inputType(String.class)`。而 `FunctionToolCallback.call(String, ToolContext)`
内部固定执行 `JsonParser.fromJson(toolInput, toolInputType)`（见
`FunctionToolCallback` 字节码偏移 25-30）。模型给出的是 JSON **对象**
（`{"expression":"128*46"}`），`fromJson(obj, String.class)` 必然抛
`Conversion from JSON to java.lang.String failed`。
`FunctionToolCallback.Builder.build()` 还 `Assert.notNull(inputType)`，
所以也没法靠"不设置 inputType"绕过。

**修复**：新增 `RawJsonToolCallback`，直接实现 `ToolCallback`，
把模型给出的原始 JSON 串原样交给工具处理函数，不做框架层类型转换：

- 工具侧签名保持 `BiFunction<String, ToolContext, String>`，三个内置工具零改动；
- 附带好处：兼容个别把参数序列化成字符串的模型，不会反序列化失败；
- `ToolRegistry` 去掉 `FunctionToolCallback` 与 `inputType(String.class)`。

**实测**（qwen2.5:7b）：
- `calculator`：模型发出 `{"expression":"(128*46)"}` →
  `{"result":5888.0,"expression":"(128*46)"}` → 最终回答"计算结果是 5888.0。"
- `knowledge_search`（FIXED 工具）：模型发出
  `{"topK":4,"query":"《寻晚系统内部规范》 文档分段 默认大小 重叠字数"}` →
  返回 3 条 pgvector 余弦相似度结果（0.670 / 0.419 / 0.355，排序正确）→
  最终回答"文档分段默认大小为500字，重叠字数为50字。"

### 36. ✅ 模型偶发空回复 → 空气泡 + 空历史

实测出现一次：`done` 事件的 `data` 为空字符串，前端留下一个没有内容的助手气泡，
数据库也写入了一条 `content=''` 的 assistant 消息，会污染后续上下文。

- `AgentExecutor`：`toolCalls` 为空且 `content` 为空白时，不再发 `done("")`，
  改发 `AgentEvent.error("模型本次没有返回内容，请重试")` 并 `log.warn` 记录步数。
- `ChatService`：`done` 内容为空时跳过落库，避免历史里出现空气泡。

### 37. ✅ 达到最大步数时丢失已生成内容

原实现只有在某一步"没有 tool_calls"时才发 `done`。若智能体在工具循环里耗尽
`maxSteps`，用户只会看到"已达最大执行步数"，前面已经流式出来的文字全部丢失，
且数据库里没有任何 assistant 记录。

- `AgentExecutor`：跨步用 `StringBuilder lastContent` 保留最近一步的非空文本；
  达到 `maxSteps` 时若有内容则改发 `done(部分内容)`（前端照常展示、后端照常落库），
  否则仍发 `maxSteps`。

---

## ✅ 已确认正常的部分

- `mvn -s .mvn/settings.xml clean compile` 通过，无编译错误（修复后复验）。
- `npm run build`（`tsc -b && vite build`）通过，零类型错误、零警告；
  产物 JS 364.02 kB / CSS 18.85 kB（gzip 120.48 kB / 4.27 kB）。
- `backend/.mvn/settings.xml`、`frontend/package-lock.json` 均已纳入 Git
  （`.gitignore` 的 `.mvn/*` + `!.mvn/settings.xml` 白名单生效）。
- `docker-compose.yml` 四个服务、健康检查、网络、volume 配置完整；
  `nginx.conf` 已正确关闭 `proxy_buffering`（SSE 必需）。
- 数据库外键级联完整：`agent → conversation → message`、
  `knowledge_document → knowledge_segment` 均为 `ON DELETE CASCADE`。
- 五个 Mapper XML 的 SQL 与实体字段映射一致；pgvector 余弦距离检索与 HNSW 索引写法正确。
- **本地 Docker 端到端实测**（Postgres + Ollama 容器）：
  - 后端正常启动，`SimpleAsyncTaskExecutor` 告警为 0。
  - `/api/models` 正确区分可用/不可用：DeepSeek 与智谱未配 Key → 不可用，
    `qwen2.5:7b` / `bge-m3` 未拉取 → 不可用。
  - 智能体 CRUD 全通：创建（`toolIds` 数组）、部分更新（只改名字保留 `toolIds`）、
    清空 `toolIds`。
  - 工具勾选 → 保存 → 后端 `toolIds` 落库验证通过。
  - SSE 各失败路径均正确返回 `error` 事件并在前端渲染
    （模型 404 / 智能体不存在 / 空消息 / 模型未授权）。
  - 知识检索在 `bge-m3` 未安装时返回 HTTP 503 + 明确提示；
    文档上传正确经历 `PROCESSING → FAILED`。
  - pgvector 余弦检索 SQL 实测排序正确（同向/正交/反向三组向量）。
  - 前端 Playwright 实测：对话页、智能体页、知识库页、模型页均无
    console error / pageerror，空模型时显示"暂无可用模型"。
- **Docker Compose 全栈实测**（postgres + ollama + backend + frontend）：
  四个容器全部 healthy，`/api/health` 返回 UP（database + ollama 均 UP），
  nginx 首页 HTTP 200，`/api/models`、`/api/agents`、`/api/knowledge/search`、
  `/api/chat/stream` 经 nginx 反代全部正常；
  前端 Playwright 走 80 端口实测对话、知识库上传、智能体页零报错。
- Spring AI 1.1.0 工具调用语义已做字节码级核对：
  `OllamaChatOptions` 实现 `ToolCallingChatOptions`，
  `OllamaChatModel.buildRequestPrompt()` 会把传入的 `ToolCallingChatOptions`
  通过 `ModelOptionsUtils.copyToTarget` 拷贝进 `OllamaChatOptions` 再合并默认值，
  因此业务层直接构造 `ToolCallingChatOptions` 是安全的。
- **P14 加固后复验**：
  - 后端 `mvn test`（JDK 21）32 个用例全通过（新增 6 个测试类）。
  - 前端 `npm run build` 通过；`Markdown.scss` 修掉一个 `$color-bg-soft)`
    多写右括号导致的解析错误。
  - 代码高亮由全量 prism 改为 `PrismLight` 按需注册，JS 体积
    1175 kB → 659 kB（gzip 399 kB → 210 kB）。

---

## ⏳ 遗留 / 需在目标机器上验证

1. ~~**Docker 端到端**：本地 Docker 环境受限，未做完整 `docker compose up` 验证~~
   → 已完成（见上「Docker Compose 全栈实测」）。
2. **`knowledge_segment.embedding` 维度**硬编码 `vector(1024)`，
   若更换 embedding 模型需同步修改 `02_schema.sql` 与 `xunwu.rag.embedding-dimensions`。
3. **Ollama 模型需预先拉取**（`bge-m3` / `qwen2.5:7b`），脚本见 `deploy/ollama/pull-models.sh`。
   本地网络对 `qwen2.5:7b` 的 Cloudflare R2 下载有重定向限制，
   需在可直连的环境执行 `ollama pull`。

### P15 测试补齐与管理端收口复验

- 后端 `mvn test`（JDK 21）：**98 通过 / 0 失败 / 0 错误**
  （本轮新增 `ChatServiceTest` 18、`KnowledgeServiceTest` 17、
  `ConversationControllerTest` 13、`KnowledgeControllerTest` 18）。
- `KnowledgeServiceTest` 用 `TransactionTemplate` mock 直接执行回调，
  避免为了"事务内行为"引入真实数据库；向量化失败路径断言
  `errorMessage` 落库且 `segmentMapper.batchInsert` 从未被调用。
- `ConversationControllerTest` 走 standalone MockMvc + `GlobalExceptionHandler`，
  断言客户端注入的 `id=999` / `createdAt` / `updatedAt` 不会进入 `insert` 的实体。
- 前端 `npm run build` 通过（1160 modules，JS 661.05 kB / CSS 25.83 kB）。
- 修复编辑已停用智能体时 `status` 被硬编码回 `ACTIVE` 的问题：
  `AgentsPage` 表单补上 `status` 字段并回显。

---

## 第五轮：健壮性加固（P16）

| 编号 | 问题 | 修复 |
| --- | --- | --- |
| P16-1 | 客户端断连后 `blockLast` 仍空等整个模型响应，最坏 10 分钟，持续消耗算力 | `Sinks.One` + `takeUntilOther` + `CountDownLatch`，`onCancel` 触发即中断 |
| P16-2 | 达到最大步数时 `done` / `max_steps` 事件里的步数多报 1 | 统一用 `maxSteps` 而非 `step.get()` |
| P16-3 | 模型超时抛出底层 `IllegalStateException`，提示不可读 | 包装为 `TimeoutException`，由 `toUserMessage` 翻译 |
| P16-4 | 伪造 / 跨智能体的 `conversationId` 会触发外键约束 500，或静默写进别人的会话 | `ChatService` 校验会话存在性与归属 |
| P16-5 | 空查询词直接调 `embeddingService.embed(null)`，由全局异常兜底成 500 | `KnowledgeController` 校验非空，返回 400 |
| P16-6 | 智能体名称超长触发 DB 约束异常 | 对齐 `VARCHAR(128)`，提前返回 400 |
| P16-7 | 同一轮重复调用同名工具时，`tool_result` 按名字匹配会写错条目 | 改为按「第一个还没有结果」的条目匹配 |
| P16-8 | 知识库轮询计数被加两次，实际轮询时长减半 | 去掉 `setInterval` 前的一次自增 |

**复验**：后端 `mvn test` 113 通过 / 0 失败；前端 `npm run build` 通过；
本地 PostgreSQL + Ollama 全链路实测（上传 / 向量化 / 检索 / 对话 / 工具 / RAG / 重新生成）全部正常。

---

## 第六轮：模型扩展与语义修正（P17）

| 编号 | 问题 | 修复 |
| --- | --- | --- |
| P17-1 | 仅支持 DeepSeek / 智谱 / Ollama，无法接入 LongCat 等 OpenAI 兼容协议模型 | `ChatClientRegistry.registerLongcat()` 手工构造独立 `OpenAiChatModel`（自动配置的实例已被 DeepSeek 占用）；未配置 Key 时只登记元信息并标记不可用 |
| P17-2 | `AgentExecutor.toUserMessage` 兜底分支把底层异常 message 直接回给客户端，与类注释「不泄露堆栈、连接串」相矛盾 | 未知异常只记日志，对外统一返回「模型调用失败，请稍后重试」 |
| P17-3 | 文档从 FAILED 重新处理成功后，旧的 `error_message` 仍留在库里 | `updateStatus` 在状态离开 FAILED 时清空失败原因 |
| P17-4 | 显式传入未注册的模型会被静默替换成默认模型，调用方以为在用 A 实际走了 B | `resolveModelId` 对未知模型抛 `IllegalArgumentException`，由 `ChatService` 转成 400 |
| P17-5 | `bge-m3`（向量模型）被标为 `defaultModel`，模型页出现两个「默认」徽章 | 去掉该标记；向量模型本就不参与对话默认模型选择 |
| P17-6 | `contains()` 内部走 `resolveModelId()`，未知模型会抛异常，与「纯查询」语义不符 | 改为直接查表，未知返回 false |

**复验**：后端 `mvn test` 118 通过 / 0 失败（新增 `ChatClientRegistryTest` 5 个）；
前端 `npm run build` 通过；带 `LONGCAT_API_KEY` 启动后 `/api/models` 显示 LongCat 可用，
普通对话 / RAG 问答 / 工具调用实测正常，未知模型返回明确错误事件。

---

## 第七轮：边界校验与工具健壮性（P18）

| 编号 | 问题 | 修复 |
| --- | --- | --- |
| P18-1 | `ChatPage` 在 react-router 下会卸载，但只在「停止」时 abort；路由切走后 fetch 仍在后台跑，后端继续生成直到完成 | 补卸载清理 `abortRef.current?.abort()`，与 P16-1 的后端真中断配合 |
| P18-2 | `AgentController` 只校验 `name` 长度，`description`(512) / `avatar`(255) / `modelId`(64) 超长仍会触发 DB 约束异常变 500 | 统一 `assertFitsColumn` 校验，超长返回 400 |
| P18-3 | `CurrentTimeTool` 未捕获非法 pattern，`DateTimeFormatter.ofPattern` 抛 `IllegalArgumentException` 让工具调用失败 | 捕获后回退默认格式，并把回退后的 pattern 回填到返回里 |
| P18-4 | `KnowledgeSearchTool` 把底层异常 message 直接回给模型，可能含 SQL / 内部地址并进入对话内容 | 仅 `BizException`（已脱敏）回传，其余统一兜底文案 |

**复验**：后端 `mvn test` 126 通过 / 0 失败（新增 `CurrentTimeToolTest` 4 个、`AgentControllerTest` +4）；
前端 `npm run build` 通过；实测非法 pattern 回退默认格式、知识库工具返回结果、
超长 description / modelId 均返回 400。

---

## 第八轮：固定工具重复注册（P19）

| 编号 | 问题 | 修复 |
| --- | --- | --- |
| P19-1 | 固定工具 `knowledge_search` 出现在智能体表单的「可选工具」列表里，勾选后 `toolIds` 与固定工具重复注册，Spring AI 抛 `IllegalStateException: Multiple tools with the same name`，**整个对话直接失败** | `ToolRegistry` 新增 `getMergedTools` / `getMergedToolNames`（同名去重），`ChatService.buildContext` 改用；前端 FIXED 工具改为禁用的常开项且不提交到 `toolIds` |
| P19-2 | `AgentsPage` 的 maxSteps 输入上限写 20，后端允许 50，两端不一致 | 对齐为 50 |

**复验**：后端 `mvn test` 131 通过 / 0 失败（`ToolRegistryTest` +5）；
构造 `toolIds=["knowledge_search","current_time"]` 的智能体实测对话正常完成（修复前只推一个 thinking 事件就报错）；
前端 `npm run build` 通过。

---

## 第九轮：资源释放、异常脱敏与并发幂等（P20）

| 编号 | 问题 | 修复 |
| --- | --- | --- |
| P20-1 | `AgentExecutor.callModelStreaming` 丢弃了 `subscribe()` 返回的 `Disposable`：模型响应超时后底层 HTTP 调用仍在后台跑，白占连接与算力 | 持有 `Disposable`，超时 / 中断时 `dispose()` |
| P20-2 | `AgentToolService.execute` 把 `e.getMessage()` 原样回给模型，可能含连接串 / SQL / 内部地址并进入对话内容 | 只有「未注册的工具」名回传（让模型停止重复调用），其余统一兜底文案 |
| P20-3 | `KnowledgeService.processDocument` 无幂等保护：连点两次「重新向量化」会并发各插一份分段，检索结果全是重复内容 | 按文档 ID 分段加锁串行化（锁对象常驻，避免「新锁与旧锁并发」窗口） |
| P20-4 | `KnowledgePage` 的 `actionError` 只在分段展开时渲染，未展开文档的「重新向量化」失败完全无反馈；轮询计数在处理结束后未归零 | 错误提示提升到页面级；所有文档处理结束后归零计数 |
| P20-5 | `ToolRegistry.resolveToolNames` 生产代码已无调用方（被 `getMergedToolNames` 取代），只剩测试引用 | 删除方法及对应测试，清理 `ChatServiceTest` 过期 mock，修正 `getTools_returnsMutableList` 过时注释 |

**复验**：后端 `mvn test` 134 通过 / 0 失败（新增 `AgentToolServiceTest` 4 个，删除 `resolveToolNames` 对应用例 1 个）；
前端 `npm run build` 通过；实测普通对话、RAG 问答正常，
智能体配置不存在工具时被静默过滤、对话正常完成（不 500）。
