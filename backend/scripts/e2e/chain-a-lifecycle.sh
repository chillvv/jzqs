#!/usr/bin/env bash
# E2E 链路 A：顾客下单 → 派单 → 送达核销 完整生命周期（成功路径）
# 用法：bash chain-a-lifecycle.sh
set -uo pipefail
BASE=http://127.0.0.1:8080
ADMIN_TOKEN=$(cat /tmp/e2e_admin_token)
OPENID="e2e_openid_01"
PHONE="13900008888"
AREA="中南路区域"
SERVE_DATE=$(date -d "+1 day" +%Y-%m-%d)
PASS=0; FAIL=0
ok()   { PASS=$((PASS+1)); echo "  ✅ PASS: $1"; }
bad()  { FAIL=$((FAIL+1)); echo "  ❌ FAIL: $1"; }
jget() { python3 -c "import json,sys; d=json.load(sys.stdin); print(d$1)" 2>/dev/null; }
MYSQL() { mysql -h127.0.0.1 -P3306 -ujzqs -pjzqs_password_123 -N "$@" 2>/dev/null; }

# 清理上次运行残留（按 openid 定位 E2E 客户，级联删子表）
echo "清理上次 E2E 残留数据..."
CID=$(MYSQL jzqs -e "SELECT id FROM customers WHERE openid='$OPENID' LIMIT 1;")
if [ -n "$CID" ]; then
  MYSQL jzqs -e "DELETE FROM customers WHERE id=$CID;"
fi

echo "===== 链路A：完整生命周期（serveDate=$SERVE_DATE） ====="

echo "[1/8] 顾客注册(dev-phone)"
REG=$(curl -s -X POST $BASE/api/mobile/auth/dev-phone -H "Content-Type: application/json" \
  -d "{\"openid\":\"$OPENID\",\"phone\":\"$PHONE\"}")
CUST_TOKEN=$(echo "$REG" | jget "['data']['token']")
CUST_ID=$(echo "$REG" | jget "['data']['customerId']")
CODE=$(echo "$REG" | jget "['code']")
[ "$CODE" = "OK" ] && [ -n "$CUST_TOKEN" ] && [ -n "$CUST_ID" ] && ok "顾客注册 customerId=$CUST_ID" || bad "注册失败: $REG"
echo "$CUST_TOKEN" > /tmp/e2e_customer_token

echo "[2/8] 顾客保存地址"
ADDR=$(curl -s -X POST $BASE/api/mobile/customer/addresses -H "Authorization: Bearer $CUST_TOKEN" -H "Content-Type: application/json" \
  -d "{\"contactName\":\"端到端客户\",\"contactPhone\":\"$PHONE\",\"addressLine\":\"E2E测试路1号\",\"areaCode\":\"$AREA\",\"isDefault\":true}")
ADDR_ID=$(echo "$ADDR" | jget "['data']['id']")
[ -n "$ADDR_ID" ] && ok "地址已保存 addressId=$ADDR_ID" || bad "地址保存失败: $ADDR"

echo "[3/8] 管理员授权 10 餐次"
GRANT=$(curl -s -X POST $BASE/api/admin/customers/$CUST_ID/wallet/grant -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"mealDelta":10,"validityDays":30,"remark":"E2E链路A授权"}')
[ "$(echo "$GRANT" | jget "['code']")" = "OK" ] && ok "授权成功" || bad "授权失败: $GRANT"

echo "[4/8] 顾客下单（明天 LUNCH）"
ORDER=$(curl -s -X POST $BASE/api/mobile/customer/orders -H "Authorization: Bearer $CUST_TOKEN" -H "Content-Type: application/json" \
  -d "{\"serveDate\":\"$SERVE_DATE\",\"mealPeriod\":\"LUNCH\",\"deliveryAddress\":\"E2E测试路1号\",\"note\":\"E2E链路A\",\"quantity\":1}")
ORDER_ID=$(echo "$ORDER" | jget "['data']['orderId']")
[ "$(echo "$ORDER" | jget "['code']")" = "OK" ] && [ -n "$ORDER_ID" ] && ok "下单成功 orderId=$ORDER_ID" || bad "下单失败: $ORDER"

echo "[5/8] 验证下单即扣餐（consumed=1）+ 订单待派单"
SQL1=$(MYSQL jzqs -e "
  SELECT CONCAT('status=', mso.status, ' | 钱包total=', w.total_meals, ' | 钱包consumed=', w.consumed_meals)
  FROM meal_slot_orders mso
  JOIN daily_orders do ON do.id=mso.daily_order_id
  LEFT JOIN meal_wallets w ON w.customer_id=do.customer_id AND w.active=1
  WHERE mso.id=$ORDER_ID;")
echo "     $SQL1"
echo "$SQL1" | grep -q "status=PENDING_DISPATCH" && ok "订单状态 PENDING_DISPATCH" || bad "订单状态异常: $SQL1"
echo "$SQL1" | grep -q "consumed=1" && ok "下单即扣餐 consumed=1（取消则退回）" || bad "餐次未扣: $SQL1"

echo "[6/8] 管理员批量派单（区域=$AREA）"
PENDING=$(curl -s "$BASE/api/admin/dispatch/pending-items?mealPeriod=LUNCH&serveDate=$SERVE_DATE" -H "Authorization: Bearer $ADMIN_TOKEN")
echo "     待派单: $(echo "$PENDING" | jget "['code']") 数量=$(echo "$PENDING" | jget "['data']" | python3 -c "import json,sys; print(len(json.load(sys.stdin)))" 2>/dev/null)"
ASSIGN=$(curl -s -X POST $BASE/api/admin/dispatch/pending-items/batch-assign -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d "{\"orderIds\":[$ORDER_ID],\"areaCode\":\"$AREA\",\"updatedBy\":\"E2E测试\"}")
echo "     派单响应(前260字): ${ASSIGN:0:260}"
SQL2=$(MYSQL jzqs -e "
  SELECT CONCAT('订单=', mso.status, ' 派单=', COALESCE(da.status,'-'), ' 骑手=', COALESCE(da.rider_name,'-'), ' 区域=', COALESCE(da.area_code,'-'))
  FROM meal_slot_orders mso LEFT JOIN dispatch_assignments da ON da.meal_slot_order_id=mso.id WHERE mso.id=$ORDER_ID;")
echo "     $SQL2"
echo "$SQL2" | grep -q "派单=DISPATCHING" && ok "订单已派单（DISPATCHING）" || bad "派单未生效: $SQL2"

echo "[7/8] 管理员送达核销"
RECEIPT=$(curl -s -X POST $BASE/api/admin/deliveries/receipt -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d "{\"mealSlotOrderId\":$ORDER_ID,\"receiptUrl\":\"http://example.test/e2e.png\",\"receiptNote\":\"E2E送达\"}")
RCODE=$(echo "$RECEIPT" | jget "['code']")
[ "$RCODE" = "OK" ] && ok "送达核销成功" || bad "送达失败: ${RECEIPT:0:200}"

echo "[8/8] 终态验证：订单 DELIVERED + 回执生成 + 派单同步"
SQL3=$(MYSQL jzqs -e "
  SELECT CONCAT('订单=', mso.status, ' | 回执数=', (SELECT COUNT(*) FROM delivery_receipts dr WHERE dr.meal_slot_order_id=mso.id), ' | 派单=', COALESCE(da.status,'-'))
  FROM meal_slot_orders mso
  LEFT JOIN dispatch_assignments da ON da.meal_slot_order_id=mso.id
  WHERE mso.id=$ORDER_ID;")
echo "     $SQL3"
TX=$(MYSQL jzqs -e "
  SELECT CONCAT('钱包流水: ', GROUP_CONCAT(CONCAT(transaction_type,'(',meal_delta,')') ORDER BY wt.id SEPARATOR ','))
  FROM wallet_transactions wt JOIN meal_wallets w ON w.id=wt.wallet_id WHERE w.customer_id=$CUST_ID;")
echo "     $TX"
echo "$SQL3" | grep -q "订单=DELIVERED" && ok "订单终态 DELIVERED" || bad "订单未达终态: $SQL3"
echo "$SQL3" | grep -q "回执数=1" && ok "回执已生成" || bad "回执缺失: $SQL3"
echo "$SQL3" | grep -q "派单=DELIVERED" && ok "派单记录同步 DELIVERED（V24 口径）" || bad "派单记录未同步: $SQL3"

echo ""
echo "===== 链路A结果：PASS=$PASS FAIL=$FAIL ====="
exit $FAIL
