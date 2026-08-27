#!/usr/bin/env bash
# E2E 链路 B：下单 → 取消 → 餐次退回 + 派单清理 + 批次统计刷新
set -uo pipefail
BASE=http://127.0.0.1:8080
ADMIN_TOKEN=$(cat /tmp/e2e_admin_token)
OPENID="e2e_openid_02"
PHONE="13900007777"
AREA="光谷大道区域"
SERVE_DATE=$(date -d "+1 day" +%Y-%m-%d)
PASS=0; FAIL=0
ok()   { PASS=$((PASS+1)); echo "  ✅ PASS: $1"; }
bad()  { FAIL=$((FAIL+1)); echo "  ❌ FAIL: $1"; }
jget() { python3 -c "import json,sys; d=json.load(sys.stdin); print(d$1)" 2>/dev/null; }
MYSQL() { mysql -h127.0.0.1 -P3306 -ujzqs -pjzqs_password_123 -N "$@" 2>/dev/null; }

echo "清理上次 E2E 残留..."
CID=$(MYSQL jzqs -e "SELECT id FROM customers WHERE openid='$OPENID' LIMIT 1;")
[ -n "$CID" ] && MYSQL jzqs -e "DELETE FROM customers WHERE id=$CID;"

echo "===== 链路B：取消退款（serveDate=$SERVE_DATE） ====="

echo "[1/6] 顾客注册 + 授权 5 餐 + 下单"
REG=$(curl -s -X POST $BASE/api/mobile/auth/dev-phone -H "Content-Type: application/json" -d "{\"openid\":\"$OPENID\",\"phone\":\"$PHONE\"}")
CUST_TOKEN=$(echo "$REG" | jget "['data']['token']")
CUST_ID=$(echo "$REG" | jget "['data']['customerId']")
[ -n "$CUST_ID" ] && ok "顾客注册 customerId=$CUST_ID" || bad "注册失败: $REG"
curl -s -X POST $BASE/api/admin/customers/$CUST_ID/wallet/grant -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"mealDelta":5,"validityDays":30,"remark":"E2E链路B授权"}' > /dev/null
ORDER=$(curl -s -X POST $BASE/api/mobile/customer/orders -H "Authorization: Bearer $CUST_TOKEN" -H "Content-Type: application/json" \
  -d "{\"serveDate\":\"$SERVE_DATE\",\"mealPeriod\":\"LUNCH\",\"deliveryAddress\":\"E2E测试路2号\",\"quantity\":1}")
ORDER_ID=$(echo "$ORDER" | jget "['data']['orderId']")
[ -n "$ORDER_ID" ] && ok "下单成功 orderId=$ORDER_ID" || bad "下单失败: $ORDER"

echo "[2/6] 先派单（制造派单记录）"
ASSIGN=$(curl -s -X POST $BASE/api/admin/dispatch/pending-items/batch-assign -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d "{\"orderIds\":[$ORDER_ID],\"areaCode\":\"光谷大道区域\",\"updatedBy\":\"E2E\"}")
ASSIGNED=$(MYSQL jzqs -e "SELECT COUNT(*) FROM dispatch_assignments WHERE meal_slot_order_id=$ORDER_ID;")
echo "     派单: $(echo "$ASSIGN" | jget "['data']" | python3 -c "import json,sys; d=json.load(sys.stdin); print('成功'+str(d['successCount']))" 2>/dev/null) 派单记录数=$ASSIGNED"
[ "$ASSIGNED" = "1" ] && ok "派单记录已生成" || bad "派单记录缺失"

BATCH_BEFORE=$(MYSQL jzqs -e "
  SELECT COALESCE(GROUP_CONCAT(CONCAT('batch',b.id,'(total=',b.total_count,')')),'-')
  FROM dispatch_batches b WHERE b.rider_profile_id=(SELECT rider_profile_id FROM dispatch_assignments WHERE meal_slot_order_id=$ORDER_ID LIMIT 1);")
echo "     派单批次(取消前): $BATCH_BEFORE"

echo "[3/6] 顾客取消订单"
CANCEL=$(curl -s -X POST $BASE/api/mobile/customer/orders/$ORDER_ID/cancel -H "Authorization: Bearer $CUST_TOKEN")
[ "$(echo "$CANCEL" | jget "['code']")" = "OK" ] && ok "取消成功" || bad "取消失败: ${CANCEL:0:200}"

echo "[4/6] 验证订单 CANCELLED + 餐次退回（consumed 归零 + REFUND 流水）"
SQL=$(MYSQL jzqs -e "
  SELECT CONCAT('status=', mso.status, ' | consumed=', w.consumed_meals, ' | reserved=', w.reserved_meals)
  FROM meal_slot_orders mso JOIN daily_orders do ON do.id=mso.daily_order_id
  LEFT JOIN meal_wallets w ON w.customer_id=do.customer_id AND w.active=1 WHERE mso.id=$ORDER_ID;")
echo "     $SQL"
echo "$SQL" | grep -q "status=CANCELLED" && ok "订单终态 CANCELLED" || bad "订单状态异常: $SQL"
echo "$SQL" | grep -q "consumed=0" && ok "餐次已退回 consumed=0" || bad "餐次未退回: $SQL"
REFUND=$(MYSQL jzqs -e "SELECT COUNT(*) FROM wallet_transactions wt JOIN meal_wallets w ON w.id=wt.wallet_id WHERE w.customer_id=$CUST_ID AND wt.transaction_type='REFUND';")
[ "$REFUND" -ge 1 ] && ok "REFUND 流水已生成（$REFUND 条）" || bad "REFUND 流水缺失"

echo "[5/6] 验证派单记录已清理"
LEFT=$(MYSQL jzqs -e "SELECT COUNT(*) FROM dispatch_assignments WHERE meal_slot_order_id=$ORDER_ID;")
[ "$LEFT" = "0" ] && ok "取消后 dispatch_assignments 已清理" || bad "派单记录残留=$LEFT"

echo "[6/6] 验证批次统计已刷新（总单量减 1）"
BATCH_AFTER=$(MYSQL jzqs -e "
  SELECT COALESCE(GROUP_CONCAT(CONCAT('batch',b.id,'(total=',b.total_count,')')),'-')
  FROM dispatch_batches b WHERE b.rider_profile_id=(SELECT rider_profile_id FROM dispatch_assignments WHERE meal_slot_order_id=$ORDER_ID LIMIT 1);")
echo "     派单批次(取消后): $BATCH_AFTER"
echo "     （取消前: $BATCH_BEFORE）"
[ "$BATCH_BEFORE" != "$BATCH_AFTER" ] && ok "批次统计发生变化（已刷新）" || bad "批次统计未刷新（可能无批次归属）"

echo ""
echo "===== 链路B结果：PASS=$PASS FAIL=$FAIL ====="
exit $FAIL
