# syntax=docker/dockerfile:1
# 多阶段构建：Maven 编译 → JRE 运行
# 每次 docker compose build backend 都会基于最新源码重新编译打包，
# 彻底解决"容器里跑的是旧 jar"的问题。
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
# 阿里云 Maven 镜像（服务器下载 Maven Central 极慢，曾致部署 20 分钟）
COPY .mvn/settings.xml .mvn/settings.xml
# 先拉依赖并走缓存，未改动 pom 时后续构建复用
RUN --mount=type=cache,target=/root/.m2 mvn -B -s /build/.mvn/settings.xml dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -s /build/.mvn/settings.xml package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /build/target/backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai"

ENTRYPOINT ["bash", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
