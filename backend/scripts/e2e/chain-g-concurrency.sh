#!/usr/bin/env bash
# E2E 链路 G：并发重复提交 → 幂等拦截 + 唯一约束兜底（防重复下单/重复授权）
set -uo pipefail
BASE=http://127.0.0.1:8080
ADMIN_TOKEN=$(cat /tmp/e2e_admin_token)
OPENID="e2e_openid_07"
PHONE="13900002222"
SERVE_DATE=$(date -d "+1 day" +%Y-%m-%d)
PASS=0; FAIL=0
ok()   { PASS=$((PASS+1)); echo "  ✅ PASS: $1"; }
bad()  { FAIL=$((FAIL+1)); echo "  ❌ FAIL: $1"; }
jget() { python3 -c "import json,sys; d=json.load(sys.stdin); print(d$1)" 2>/dev/null; }
MYSQL() { mysql -h127.0.0.1 -P3306 -ujzqs -pjzqs_password_123 -N "$@" 2>/dev/null; }

echo "清理上次 E2E 残留..."
CID=$(MYSQL jzqs -e "SELECT id FROM customers WHERE openid='$OPENID' LIMIT 1;")
[ -n "$CID" ] && MYSQL jzqs -e "DELETE FROM customers WHERE id=$CID;"

echo "===== 链路G：并发幂等与唯一约束（serveDate=$SERVE_DATE） ====="

echo "[1/5] 顾客注册 + 授权 10 餐"
REG=$(curl -s -X POST $BASE/api/mobile/auth/dev-phone -H "Content-Type: application/json" -d "{\"openid\":\"$OPENID\",\"phone\":\"$PHONE\"}")
CUST_TOKEN=$(echo "$REG" | jget "['data']['token']")
CUST_ID=$(echo "$REG" | jget "['data']['customerId']")
GRANT=$(curl -s -X POST $BASE/api/admin/customers/$CUST_ID/wallet/grant -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"mealDelta":10,"validityDays":30,"remark":"E2E链路G授权"}')
[ "$(echo "$GRANT" | jget "['code']")" = "OK" ] && ok "顾客注册+授权" || bad "授权失败: ${GRANT:0:150}"

echo "[2/5] 并发 2 个相同下单请求（同 body）→ 仅 1 单生成 + 1 次幂等拦截"
BODY="{\"serveDate\":\"$SERVE_DATE\",\"mealPeriod\":\"LUNCH\",\"deliveryAddress\":\"E2E并发地址\",\"quantity\":1}"
curl -s -X POST $BASE/api/mobile/customer/orders -H "Authorization: Bearer $CUST_TOKEN" -H "Content-Type: application/json" -d "$BODY" > /tmp/e2e_g1.json &
P1=$!
curl -s -X POST $BASE/api/mobile/customer/orders -H "Authorization: Bearer $CUST_TOKEN" -H "Content-Type: application/json" -d "$BODY" > /tmp/e2e_g2.json &
P2=$!
wait $P1 $P2
C1=$(jget "['code']" < /tmp/e2e_g1.json)
C2=$(jget "['code']" < /tmp/e2e_g2.json)
echo "     请求1: $C1 | 请求2: $C2"
ORDER_CNT=$(MYSQL jzqs -e "SELECT COUNT(*) FROM meal_slot_orders mso JOIN daily_orders do ON do.id=mso.daily_order_id WHERE do.customer_id=$CUST_ID;")
[ "$ORDER_CNT" = "1" ] && ok "并发相同下单仅生成 1 个订单（幂等生效）" || bad "订单数=$ORDER_CNT（应=1）"
{ [ "$C1" = "REPEAT_SUBMISSION" ] || [ "$C2" = "REPEAT_SUBMISSION" ]; } && ok "至少 1 个请求被幂等拦截" || bad "幂等未拦截: $C1 / $C2"

echo "[3/5] 并发 2 个管理员授权（同 body）→ 仅加一次餐次"
ADMIN_BODY='{"mealDelta":5,"validityDays":30,"remark":"E2E链路G并发授权"}'
curl -s -X POST $BASE/api/admin/customers/$CUST_ID/wallet/grant -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" -d "$ADMIN_BODY" > /tmp/e2e_g3.json &
Q1=$!
curl -s -X POST $BASE/api/admin/customers/$CUST_ID/wallet/grant -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" -d "$ADMIN_BODY" > /tmp/e2e_g3b.json &
Q2=$!
wait $Q1 $Q2
G1=$(jget "['code']" < /tmp/e2e_g3.json)
G2=$(jget "['code']" < /tmp/e2e_g3b.json)
echo "     授权1: $G1 | 授权2: $G2"
TOTAL=$(MYSQL jzqs -e "SELECT total_meals FROM meal_wallets WHERE customer_id=$CUST_ID AND active=1;")
echo "     钱包 total_meals=$TOTAL（首授10 + 并发5 = 应=15）"
[ "$TOTAL" = "15" ] && ok "并发授权仅生效一次（total=15）" || bad "并发授权重复生效 total=$TOTAL"
{ [ "$G1" = "REPEAT_SUBMISSION" ] || [ "$G2" = "REPEAT_SUBMISSION" ]; } && ok "授权幂等拦截生效" || bad "授权幂等未拦截: $G1 / $G2"

echo "[4/5] 账单一致性：钱包流水无重复 GRANT（10 + 5）"
GRANT_CNT=$(MYSQL jzqs -e "SELECT COUNT(*) FROM wallet_transactions wt JOIN meal_wallets w ON w.id=wt.wallet_id WHERE w.customer_id=$CUST_ID AND wt.transaction_type='GRANT';")
echo "     GRANT 流水数=$GRANT_CNT"
[ "$GRANT_CNT" = "2" ] && ok "GRANT 流水恰好 2 条（首授+并发授权一次）" || bad "GRANT 流水数=$GRANT_CNT"

echo "[5/5] 唯一约束兜底：DB 层直接并发插同一客户钱包 → 仅 1 行生效（独立测试客户）"
DB_CID=$(MYSQL jzqs -e "INSERT INTO customers (name, phone, source, active, customer_status, is_priority_customer) VALUES ('并发测试', '13900001110', 'TEST', 1, 'FORMAL', 0); SELECT LAST_INSERT_ID();")
INSERT_SQL="INSERT INTO meal_wallets (customer_id, total_meals, reserved_meals, consumed_meals, active) VALUES ($DB_CID, 3, 0, 0, 1)"
mysql -h127.0.0.1 -P3306 -ujzqs -pjzqs_password_123 jzqs -e "$INSERT_SQL" >/dev/null 2>&1; rm -f /tmp/e2e_ia /tmp/e2e_ib
( mysql -h127.0.0.1 -P3306 -ujzqs -pjzqs_password_123 jzqs -e "$INSERT_SQL" >/dev/null 2>&1; echo "A:$?" > /tmp/e2e_ia ) &
( mysql -h127.0.0.1 -P3306 -ujzqs -pjzqs_password_123 jzqs -e "$INSERT_SQL" >/dev/null 2>&1; echo "B:$?" > /tmp/e2e_ib ) &
wait
ACTIVE_CNT=$(MYSQL jzqs -e "SELECT COUNT(*) FROM meal_wallets WHERE customer_id=$DB_CID AND active=1;")
echo "     生效钱包数=$ACTIVE_CNT（独立测试客户 $DB_CID）"
[ "$ACTIVE_CNT" = "1" ] && ok "DB 唯一约束兜底：并发插入仅 1 行生效" || bad "唯一约束失效 active 钱包=$ACTIVE_CNT"
MYSQL jzqs -e "DELETE FROM customers WHERE id=$DB_CID;"

echo ""
echo "===== 链路G结果：PASS=$PASS FAIL=$FAIL ====="
exit $FAIL
