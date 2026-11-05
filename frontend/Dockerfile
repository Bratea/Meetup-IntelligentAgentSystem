# ===================== 构建阶段 =====================
FROM node:22-alpine AS builder

WORKDIR /build

# 优先使用国内镜像加速
ENV NPM_CONFIG_REGISTRY=https://registry.npmmirror.com

COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
RUN npm run build

# ===================== 运行阶段 =====================
FROM nginx:1.27-alpine

# 拷贝构建产物
COPY --from=builder /build/dist /usr/share/nginx/html

# 拷贝 nginx 配置（SPA 路由回退 + 后端 API 反代）
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=15s --timeout=3s --start-period=5s --retries=6 \
  CMD wget -qO- http://localhost/ >/dev/null 2>&1 || exit 1

CMD ["nginx", "-g", "daemon off;"]
