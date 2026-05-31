#!/bin/bash

# 微信小程序统一认证 API 测试脚本
# 使用方法：bash test-auth-api.sh

BASE_URL="http://localhost:8081"

echo "========================================="
echo "微信小程序统一认证 API 测试"
echo "========================================="
echo ""

# 测试 1: 静默登录（顾客）
echo "测试 1: 静默登录（顾客）"
echo "POST /api/auth/login"
curl -X POST "${BASE_URL}/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"code":"test123","userType":"customer"}' \
  | jq '.'
echo ""
echo ""

# 测试 2: 静默登录（骑手）
echo "测试 2: 静默登录（骑手）"
echo "POST /api/auth/login"
curl -X POST "${BASE_URL}/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"code":"test456","userType":"rider"}' \
  | jq '.'
echo ""
echo ""

# 测试 3: 绑定手机号（顾客）
echo "测试 3: 绑定手机号（顾客）"
echo "POST /api/auth/bind-phone"
curl -X POST "${BASE_URL}/api/auth/bind-phone" \
  -H "Content-Type: application/json" \
  -d '{"code":"13800138000","userType":"customer"}' \
  | jq '.'
echo ""
echo ""

# 保存 token 用于后续测试
TOKEN=$(curl -s -X POST "${BASE_URL}/api/auth/bind-phone" \
  -H "Content-Type: application/json" \
  -d '{"code":"13800138000","userType":"customer"}' \
  | jq -r '.data.token')

if [ "$TOKEN" != "null" ] && [ -n "$TOKEN" ]; then
  echo "获取到 Token: ${TOKEN:0:20}..."
  echo ""
  
  # 测试 4: 验证 token
  echo "测试 4: 验证 token"
  echo "GET /api/auth/verify"
  curl -X GET "${BASE_URL}/api/auth/verify" \
    -H "Authorization: Bearer ${TOKEN}" \
    | jq '.'
  echo ""
  echo ""
  
  # 测试 5: 退出登录
  echo "测试 5: 退出登录"
  echo "POST /api/auth/logout"
  curl -X POST "${BASE_URL}/api/auth/logout" \
    -H "Authorization: Bearer ${TOKEN}" \
    | jq '.'
  echo ""
else
  echo "未能获取 Token，跳过后续测试"
fi

echo ""
echo "========================================="
echo "测试完成"
echo "========================================="
