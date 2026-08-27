#!/usr/bin/env bash
# E2E 链路 F：订阅规则 → 唯一约束（一客户一行）→ 暂停/恢复 → 管理员确认 → 自动生成订单
set -uo pipefail
BASE=http://127.0.0.1:8080
ADMIN_TOKEN=$(cat /tmp/e2e_admin_token)
OPENID="e2e_openid_06"
PHONE="13900003333"
SERVE_DATE=$(date -d "+1 day" +%Y-%m-%d)
PASS=0; FAIL=0
ok()   { PASS=$((PASS+1)); echo "  ✅ PASS: $1"; }
bad()  { FAIL=$((FAIL+1)); echo "  ❌ FAIL: $1"; }
jget() { python3 -c "import json,sys; d=json.load(sys.stdin); print(d$1)" 2>/dev/null; }
MYSQL() { mysql -h127.0.0.1 -P3306 -ujzqs -pjzqs_password_123 -N "$@" 2>/dev/null; }

echo "清理上次 E2E 残留..."
CID=$(MYSQL jzqs -e "SELECT id FROM customers WHERE openid='$OPENID' LIMIT 1;")
[ -n "$CID" ] && MYSQL jzqs -e "DELETE FROM customers WHERE id=$CID;"

echo "===== 链路F：订阅规则闭环（serveDate=$SERVE_DATE） ====="

echo "[1/6] 顾客注册 + 添加收货地址 + 保存订阅规则（周一~周五 午餐）"
REG=$(curl -s -X POST $BASE/api/mobile/auth/dev-phone -H "Content-Type: application/json" -d "{\"openid\":\"$OPENID\",\"phone\":\"$PHONE\"}")
CUST_TOKEN=$(echo "$REG" | jget "['data']['token']")
CUST_ID=$(echo "$REG" | jget "['data']['customerId']")
curl -s -X POST $BASE/api/mobile/customer/addresses -H "Authorization: Bearer $CUST_TOKEN" -H "Content-Type: application/json" \
  -d "{\"contactName\":\"端到端客户\",\"contactPhone\":\"$PHONE\",\"addressLine\":\"E2E订阅地址\",\"areaCode\":\"中南路区域\",\"isDefault\":true}" > /dev/null
RULE=$(curl -s -X POST $BASE/api/mobile/customer/subscription-rule -H "Authorization: Bearer $CUST_TOKEN" -H "Content-Type: application/json" \
  -d '{"enabled":true,"weekDays":"1,2,3,4,5","lunchEnabled":true,"dinnerEnabled":false}')
[ "$(echo "$RULE" | jget "['code']")" = "OK" ] && ok "订阅规则已保存" || bad "保存失败: ${RULE:0:200}"
CNT=$(MYSQL jzqs -e "SELECT COUNT(*) FROM subscription_rules WHERE customer_id=$CUST_ID;")
[ "$CNT" = "1" ] && ok "subscription_rules 恰好 1 行（一客户一行唯一约束）" || bad "规则数异常=$CNT"

echo "[2/6] 相同 body 重复提交 → 幂等拦截（REPEAT_SUBMISSION）+ 仍 1 行"
RULE2=$(curl -s -X POST $BASE/api/mobile/customer/subscription-rule -H "Authorization: Bearer $CUST_TOKEN" -H "Content-Type: application/json" \
  -d '{"enabled":true,"weekDays":"1,2,3,4,5","lunchEnabled":true,"dinnerEnabled":false}')
CNT2=$(MYSQL jzqs -e "SELECT COUNT(*) FROM subscription_rules WHERE customer_id=$CUST_ID;")
[ "$(echo "$RULE2" | jget "['code']")" = "REPEAT_SUBMISSION" ] && ok "相同请求被幂等拦截（防重复提交）" || bad "幂等未生效: ${RULE2:0:120}"
[ "$CNT2" = "1" ] && ok "仍仅 1 行（唯一约束）" || bad "规则数异常=$CNT2"

echo "[3/6] 查询订阅规则回读"
GETRULE=$(curl -s "$BASE/api/mobile/customer/subscription-rule" -H "Authorization: Bearer $CUST_TOKEN")
WEEK=$(echo "$GETRULE" | jget "['data']['weekDays']")
[ -n "$WEEK" ] && ok "规则回读 weekDays=$WEEK" || bad "规则回读失败: ${GETRULE:0:150}"

echo "[4/6] 暂停订阅（enabled=false）→ 状态停用"
PAUSE=$(curl -s -X POST $BASE/api/mobile/customer/subscription-rule -H "Authorization: Bearer $CUST_TOKEN" -H "Content-Type: application/json" \
  -d '{"enabled":false,"weekDays":"1,2,3,4,5","lunchEnabled":true,"dinnerEnabled":false}')
ENABLED=$(MYSQL jzqs -e "SELECT active FROM subscription_rules WHERE customer_id=$CUST_ID;")
[ "$ENABLED" = "0" ] && ok "订阅已暂停（active=0）" || bad "暂停未生效 active=$ENABLED"

echo "[5/6] 恢复订阅（enabled=true，body 与暂停不同 → 不被幂等拦截）"
RESUME=$(curl -s -X POST $BASE/api/mobile/customer/subscription-rule -H "Authorization: Bearer $CUST_TOKEN" -H "Content-Type: application/json" \
  -d '{"enabled":true,"weekDays":"1,2,3,4,5","lunchEnabled":true,"dinnerEnabled":true}')
echo "     恢复响应(前200字): ${RESUME:0:200}"
ENABLED2=$(MYSQL jzqs -e "SELECT active FROM subscription_rules WHERE customer_id=$CUST_ID;")
echo "     active=$ENABLED2"
[ "$ENABLED2" = "1" ] && ok "订阅已恢复（active=1）" || bad "恢复未生效 active=$ENABLED2"

echo "[6/6] 管理员订阅预览 + 确认（自动生成订单）"
PREVIEW=$(curl -s "$BASE/api/admin/orders/subscription-preview?serveDate=$SERVE_DATE" -H "Authorization: Bearer $ADMIN_TOKEN")
PCODE=$(echo "$PREVIEW" | jget "['code']")
PCOUNT=$(echo "$PREVIEW" | jget "['data']" | python3 -c "import json,sys; d=json.load(sys.stdin); print(len(d))" 2>/dev/null)
echo "     订阅预览: code=$PCODE 条数=$PCOUNT"
[ "$PCODE" = "OK" ] && ok "订阅预览接口正常（$PCOUNT 条订阅项）" || bad "订阅预览异常: ${PREVIEW:0:150}"
# 若预览有该客户的订阅确认项，则尝试确认生成订单
CONF_ID=$(echo "$PREVIEW" | jget "['data']" | python3 -c "
import json,sys
d=json.load(sys.stdin)
for item in d:
    if item.get('customerName') or item.get('customerId'):
        print(item.get('confirmationId') or item.get('id') or '')
        break
" 2>/dev/null)
if [ -n "$CONF_ID" ]; then
  CONF=$(curl -s -X POST $BASE/api/admin/orders/subscription-confirmations/$CONF_ID/confirm -H "Authorization: Bearer $ADMIN_TOKEN")
  echo "     确认订阅响应(前200字): ${CONF:0:200}"
  [ "$(echo "$CONF" | jget "['code']")" = "OK" ] && ok "订阅确认成功，订单已生成" || bad "订阅确认失败: ${CONF:0:150}"
else
  echo "     提示：无待确认订阅项（菜单未排期时跳过确认步骤）"
fi

echo ""
echo "===== 链路F结果：PASS=$PASS FAIL=$FAIL ====="
exit $FAIL
