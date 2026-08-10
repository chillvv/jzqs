# syntax=docker/dockerfile:1
# 注意：本镜像只负责运行 jar，构建由本地 maven 完成（见 build.sh）。
# 本地编译命令示例：
#   docker run --rm -v "$PWD":/app -v "$HOME/.m2":/root/.m2 -w /app maven:3.9.9-eclipse-temurin-17 mvn -B clean package -DskipTests
# 编译产物：target/backend-0.0.1-SNAPSHOT.jar
FROM eclipse-temurin:17-jre
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY target/backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai"

ENTRYPOINT ["bash", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
