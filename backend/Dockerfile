# ===================== 构建阶段 =====================
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# 先拷贝依赖描述与自定义 settings，利用 Docker 层缓存
COPY pom.xml ./
COPY .mvn .mvn
RUN mvn -s .mvn/settings.xml -B -DskipTests dependency:go-offline

# 再拷贝源码并打包
COPY src ./src
RUN mvn -s .mvn/settings.xml -B -DskipTests clean package

# ===================== 运行阶段 =====================
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# 时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 从构建阶段拷贝可执行 jar
COPY --from=builder /build/target/xunwu-agent-1.0.0.jar app.jar

# 健康检查
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=12 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
