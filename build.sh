#!/usr/bin/env bash
# 本地编译后端（使用 maven 镜像，避免镜像内构建）。
# 编译产物：backend/target/backend-0.0.1-SNAPSHOT.jar
# 之后运行：docker compose up -d backend
set -euo pipefail

cd "$(dirname "$0")/backend"

docker run --rm \
  -v "$PWD":/app \
  -v "$HOME/.m2":/root/.m2 \
  -w /app \
  maven:3.9.9-eclipse-temurin-17 \
  mvn -B clean package -DskipTests

echo "✅ 编译完成：backend/target/backend-0.0.1-SNAPSHOT.jar"
