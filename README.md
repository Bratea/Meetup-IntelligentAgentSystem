# 寻晤智能agent系统

> 基于 **Spring Boot 3.5.8 + Spring AI 1.1.0** 的 AI Agent 应用平台，支持智能体自主思考、工具调用、知识库检索（RAG）与多模型切换，前端基于 **React 19 + TypeScript + SCSS + GSAP + SSE** 实现流式对话。

![tech](https://img.shields.io/badge/Spring%20Boot-3.5.8-6DB33F) ![java](https://img.shields.io/badge/Java-21-ED8B00) ![springai](https://img.shields.io/badge/Spring%20AI-1.1.0-6DB33F) ![react](https://img.shields.io/badge/React-19-61DAFB) ![ts](https://img.shields.io/badge/TypeScript-5.8-3178C6) ![pgvector](https://img.shields.io/badge/pgvector-0.8-336791)

---

## ✨ 功能特性

| 模块 | 说明 |
| --- | --- |
| 🧠 **Agent 执行引擎** | 基于 Spring AI 实现 **Think-Execute** 执行循环，串联模型推理、工具调用与结果回传；状态机管理任务执行状态，并设置**最大执行步数**，避免智能体陷入无限循环。 |
| 🔧 **工具管理与调用** | 抽象统一工具接口与工具管理服务，区分**固定工具**与**可选工具**，支持为智能体配置可用工具；通过 `ToolCallingManager` 统一管理工具调度与执行，降低工具实现与 Agent 核心流程之间的耦合。 |
| 📚 **RAG 知识检索** | 基于 **PostgreSQL + pgvector** 构建向量存储和相似度检索流程，使用 **Ollama 本地运行 bge-m3** 模型生成文档及查询文本的向量，将检索到的知识片段组装为上下文提供给 Agent。为向量字段建立 **HNSW 索引**，并通过 **Top-K** 限制检索结果数量。 |
| 🤖 **多模型接入** | 设计 `ChatClientRegistry` 模型管理组件，根据模型标识获取对应的 `ChatClient`，统一管理 **DeepSeek / 智谱 / Ollama** 的调用入口，实现模型选择与 Agent 执行逻辑解耦。 |
| 💨 **流式交互** | 基于 **SSE** 实现后端模型响应的实时推送，前端使用 React 与 TypeScript 接收并展示生成内容，减少用户等待完整回答的感知时间。 |
| 🎨 **前端体验** | **SCSS** 设计系统（变量 / Mixin / 渐变 / 玻璃拟态）+ **GSAP** 入场动画与路由过渡，暗色主题，工具调用过程可视化。 |

---

## 🧱 技术栈

- **后端**：Java 21、Spring Boot 3.5.8、Spring AI 1.1.0、MyBatis、Spring WebFlux（SSE）
- **数据库**：PostgreSQL 17、pgvector（HNSW 索引）
- **模型**：Ollama（bge-m3 向量模型 / qwen2.5 对话模型）、DeepSeek API、智谱 GLM API
- **前端**：React 19、TypeScript 5.8、Vite 6、SCSS、GSAP、fetch stream（SSE）
- **部署**：Docker Compose（PostgreSQL + Ollama + 后端 + 前端）

---

## 📁 项目结构

```text
寻晤智能agent系统/
├── backend/                        # Spring Boot 后端
│   ├── Dockerfile                      # 多阶段构建（Maven 打包 → JRE 运行）
│   ├── .mvn/settings.xml               # Maven 镜像配置
│   └── src/main/java/com/xunwu/agent/
│       ├── AgentApplication.java       # 启动类
│       ├── agent/                      # Agent 执行引擎（Think-Execute + 状态机）
│       ├── tool/                       # 工具接口 / ToolCallingManager / 内置工具
│       ├── model/                      # ChatClientRegistry 多模型管理
│       ├── rag/                        # pgvector 向量存储 / 检索 / 知识库
│       ├── chat/                       # SSE 流式对话
│       ├── controller/                 # REST API
│       ├── mapper/                     # MyBatis Mapper
│       ├── entity/                     # 数据库实体
│       └── config/                     # 配置类
│   └── src/main/resources/
│       ├── application.yml
│       └── mapper/                     # MyBatis XML
├── frontend/                       # React + TypeScript 前端
│   ├── Dockerfile                      # 多阶段构建（Node 构建 → nginx 托管）
│   ├── nginx.conf                      # SPA 路由回退 + /api 反代（SSE 关闭缓冲）
│   └── src/
│       ├── pages/                      # 聊天 / 智能体 / 知识库 / 模型 / 工具
│       ├── api/                        # 接口封装（含 SSE 流解析）
│       ├── components/                 # 布局组件
│       ├── hooks/gsap.ts               # GSAP 动画预设
│       └── styles/                     # SCSS 设计系统（变量 / Mixin / 全局）
├── deploy/
│   ├── postgres/init/              # 数据库初始化脚本（扩展 + 表结构）
│   └── ollama/pull-models.sh       # Ollama 模型拉取脚本
├── docker-compose.yml              # 全栈编排
└── docs/                           # 设计文档
```

---

## 🚀 快速开始

### 方式一：Docker Compose 全栈启动（推荐）

```bash
# 1. 启动基础设施 + 后端 + 前端
docker compose up -d --build

# 2. 拉取 Ollama 模型
docker exec -it xunwu-ollama ollama pull bge-m3
docker exec -it xunwu-ollama ollama pull qwen2.5:7b
# 或使用脚本
./deploy/ollama/pull-models.sh

# 3. 访问
# 前端 http://localhost
# 后端 http://localhost:8080/api/health
```

> 首次构建后端镜像需要下载 Maven 依赖，耗时较长，请耐心等待。

### 方式二：本地开发

**1. 启动基础设施**

```bash
docker compose up -d postgres ollama
```

**2. 拉取模型**

```bash
./deploy/ollama/pull-models.sh
```

**3. 启动后端**

```bash
cd backend
JAVA_HOME=<你的 JDK 21 路径> mvn -s .mvn/settings.xml spring-boot:run
# 默认端口 8080
```

**4. 启动前端**

```bash
cd frontend
npm install --registry=https://registry.npmmirror.com
npm run dev
# 默认端口 5173
```

### 配置模型 API Key（可选）

通过环境变量传入，未配置时默认使用本地 Ollama 模型：

```bash
export DEEPSEEK_API_KEY=sk-xxxx
export ZHIPU_API_KEY=xxxx
docker compose up -d
```

---

## 🔌 主要 API

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/health` | 健康检查 |
| GET | `/api/models` | 获取可用模型列表 |
| GET | `/api/tools` | 获取可用工具列表 |
| GET | `/api/agents` | 智能体列表 |
| POST | `/api/agents` | 创建智能体 |
| GET | `/api/agents/{id}` | 智能体详情 |
| PUT | `/api/agents/{id}` | 更新智能体 |
| DELETE | `/api/agents/{id}` | 删除智能体 |
| GET | `/api/conversations` | 会话列表 |
| POST | `/api/conversations` | 创建会话 |
| GET | `/api/conversations/{id}/messages` | 会话消息 |
| POST | `/api/chat/stream` | **SSE 流式对话** |
| GET | `/api/knowledge/documents` | 知识库文档列表 |
| POST | `/api/knowledge/documents` | 上传文档（自动分段 + 向量化） |
| DELETE | `/api/knowledge/documents/{id}` | 删除文档 |
| POST | `/api/knowledge/search` | 知识库检索测试 |

---

## 🎯 核心设计

### 1. 可控的 Agent 执行流程

关闭 Spring AI 框架内部自动工具执行，由业务层显式管理模型响应、工具调用及执行结果，并通过最大执行步数限制控制单次任务的执行过程。

```
用户输入 → [THINKING] 模型推理
              ↓
        有 tool_calls?
          ├─ 是 → [TOOL_CALLING] 执行工具 → 结果回传 → 继续推理
          └─ 否 → [COMPLETED] 输出最终回答
              ↓
        步数 ≥ maxSteps → [MAX_STEPS_REACHED] 终止
```

### 2. RAG 检索优化

- 为 pgvector 向量字段建立 **HNSW 索引**，加速大规模向量数据的相似度检索；
- 通过限制 **Top-K** 检索结果数量，减少无关知识片段带来的上下文冗余，降低模型输入开销；
- 文档上传后异步完成「分段 → 向量化 → 入库」，不阻塞接口响应。

### 3. 多模型调用解耦

通过 `ChatClientRegistry` 按模型标识动态获取 `ChatClient`，将模型配置与 Agent 执行流程分离，简化模型切换及后续接入新模型的开发工作。

### 4. SSE 流式输出

后端通过 `Sinks.Many<AgentEvent>` 推送 `delta` / `tool_call` / `done` / `error` 事件；前端用 `fetch` + `ReadableStream` 实现 SSE 解析（`EventSource` 不支持 POST 与自定义请求头），并实时追加渲染。nginx 反代层已关闭缓冲，保证流式输出不被截断。

---

## 📄 License

MIT
