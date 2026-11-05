# 寻晤智能agent系统 · 前端

基于 **React 19 + TypeScript 5.8 + Vite 6** 的 AI Agent 对话与管理界面。

## 技术栈

| 类别 | 选型 |
| --- | --- |
| 框架 | React 19 |
| 语言 | TypeScript 5.8 |
| 构建 | Vite 6 |
| 样式 | SCSS（自定义设计系统） |
| 动画 | GSAP + @gsap/react |
| 流式通信 | fetch + ReadableStream（SSE） |
| 路由 | react-router-dom 7 |
| 部署 | Docker（多阶段构建 → nginx） |

## 目录结构

```text
src/
├── api/                # 接口封装（agent / chat / conversation / knowledge / model / tool）
├── components/         # 布局组件（Layout）
├── hooks/gsap.ts       # GSAP 动画预设（入场 / 消息 / 侧边栏项）
├── pages/              # ChatPage / AgentsPage / KnowledgePage / ModelsPage / ToolsPage
├── styles/             # variables.scss / mixins.scss / main.scss
├── App.tsx             # 路由定义
└── main.tsx            # 入口
```

## 本地开发

```bash
npm install --registry=https://registry.npmmirror.com
npm run dev        # http://localhost:5173
```

## 构建

```bash
npm run build      # 产物输出到 dist/
npm run preview    # 本地预览构建产物
```

## Docker 构建

```bash
docker build -t xunwu-frontend .
docker run -p 80:80 xunwu-frontend
```

`nginx.conf` 已配置 SPA 路由回退与 `/api` 反向代理（SSE 场景关闭缓冲）。
