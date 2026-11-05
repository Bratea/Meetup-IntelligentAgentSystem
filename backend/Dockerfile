# ===================== 构建阶段 =====================
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# 先拷贝依赖描述与自定义 settings，利用 Docker 层缓存
COPY pom.xml ./
COPY .mvn .mvn

# 预下载依赖（失败不阻断，最终构建仍会完整解析）
RUN mvn -s .mvn/settings.xml -B -DskipTests dependency:go-offline || true

# 再拷贝源码并打包
COPY src ./src
RUN mvn -s .mvn/settings.xml -B -DskipTests clean package

# ===================== 运行阶段 =====================
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# 时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 安装 curl 用于 HEALTHCHECK
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# 从构建阶段拷贝可执行 jar
COPY --from=builder /build/target/xunwu-agent-1.0.0.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=12 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
