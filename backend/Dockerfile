# ============================================================================
# 后端镜像：多阶段构建
#   构建阶段用 Maven 官方镜像（带 JDK），运行阶段只要 JRE，镜像小一半以上
#   注意：这里不复制 .mvn/maven.config——那是本机修复仓库路径用的，容器内不需要
# ============================================================================

# ---------- 构建 ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# 容器内构建走阿里云镜像：直连 Maven Central 在国内网络下会卡十几分钟
# 海外环境可删掉这一行，恢复默认源
COPY docker/m2-settings.xml /root/.m2/settings.xml

# 先只拷 pom 拉依赖，源码变动时这一层能命中缓存
COPY pom.xml ./
RUN mvn -B -ntp -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -ntp -DskipTests package

# ---------- 运行 ----------
FROM eclipse-temurin:17-jre

# 探活用 curl；不装的话 healthcheck 跑不起来
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# 非 root 运行：容器被攻破时降低影响面
RUN groupadd -r app && useradd -r -g app -d /app app

WORKDIR /app
COPY --from=build /build/target/im-server-*.jar /app/app.jar
RUN mkdir -p /app/uploads && chown -R app:app /app

USER app
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Shanghai"

HEALTHCHECK --interval=30s --timeout=3s --start-period=45s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
