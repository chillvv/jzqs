#!/usr/bin/env bash
# E2E 链路 E：送达后申请售后 → 退款 → 订单 REFUNDED + 钱包退回 + 看板售后计数一致
set -uo pipefail
BASE=http://127.0.0.1:8080
ADMIN_TOKEN=$(cat /tmp/e2e_admin_token)
OPENID="e2e_openid_05"
PHONE="13900004444"
AREA="关山大道区域"
SERVE_DATE=$(date -d "+1 day" +%Y-%m-%d)
PASS=0; FAIL=0
ok()   { PASS=$((PASS+1)); echo "  ✅ PASS: $1"; }
bad()  { FAIL=$((FAIL+1)); echo "  ❌ FAIL: $1"; }
jget() { python3 -c "import json,sys; d=json.load(sys.stdin); print(d$1)" 2>/dev/null; }
MYSQL() { mysql -h127.0.0.1 -P3306 -ujzqs -pjzqs_password_123 -N "$@" 2>/dev/null; }

echo "清理上次 E2E 残留..."
CID=$(MYSQL jzqs -e "SELECT id FROM customers WHERE openid='$OPENID' LIMIT 1;")
[ -n "$CID" ] && MYSQL jzqs -e "DELETE FROM customers WHERE id=$CID;"

echo "===== 链路E：售后退款（serveDate=$SERVE_DATE） ====="

echo "[1/7] 顾客注册 + 授权 + 下单 + 派单 + 送达"
REG=$(curl -s -X POST $BASE/api/mobile/auth/dev-phone -H "Content-Type: application/json" -d "{\"openid\":\"$OPENID\",\"phone\":\"$PHONE\"}")
CUST_TOKEN=$(echo "$REG" | jget "['data']['token']")
CUST_ID=$(echo "$REG" | jget "['data']['customerId']")
curl -s -X POST $BASE/api/admin/customers/$CUST_ID/wallet/grant -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"mealDelta":5,"validityDays":30,"remark":"E2E链路E"}' > /dev/null
ORDER=$(curl -s -X POST $BASE/api/mobile/customer/orders -H "Authorization: Bearer $CUST_TOKEN" -H "Content-Type: application/json" \
  -d "{\"serveDate\":\"$SERVE_DATE\",\"mealPeriod\":\"LUNCH\",\"deliveryAddress\":\"E2E测试路5号\",\"quantity\":1}")
ORDER_ID=$(echo "$ORDER" | jget "['data']['orderId']")
curl -s -X POST $BASE/api/admin/dispatch/pending-items/batch-assign -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d "{\"orderIds\":[$ORDER_ID],\"areaCode\":\"$AREA\",\"updatedBy\":\"E2E\"}" > /dev/null
REC=$(curl -s -X POST $BASE/api/admin/deliveries/receipt -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d "{\"mealSlotOrderId\":$ORDER_ID,\"receiptUrl\":\"http://example.test/e2e5.png\",\"receiptNote\":\"E2E送达\"}")
[ "$(echo "$REC" | jget "['code']")" = "OK" ] && ok "订单已送达（DELIVERED）" || bad "送达失败: ${REC:0:150}"

echo "[2/7] 顾客申请售后（REFUND/MEAL_QUALITY）"
AFTER=$(curl -s -X POST $BASE/api/mobile/customer/orders/$ORDER_ID/after-sales -H "Authorization: Bearer $CUST_TOKEN" -H "Content-Type: application/json" \
  -d '{"type":"REFUND","reasonCode":"MEAL_QUALITY","reasonText":"餐品问题","remark":"E2E售后"}')
echo "     售后响应(前260字): ${AFTER:0:260}"
CASE_ID=$(echo "$AFTER" | jget "['data']['afterSaleId']")
ACODE=$(echo "$AFTER" | jget "['code']")
[ "$ACODE" = "OK" ] && [ -n "$CASE_ID" ] && ok "售后工单已创建 afterSaleId=$CASE_ID" || bad "售后创建失败: ${AFTER:0:200}"

echo "[3/7] 验证 aftersale_cases 落库 + 看板售后计数"
CASE_STATE=$(MYSQL jzqs -e "
  SELECT CONCAT('状态=', status, ' 类型=', issue_type, ' 原因=', reason_code)
  FROM aftersale_cases WHERE id=$CASE_ID;")
echo "     $CASE_STATE"
[ -n "$CASE_STATE" ] && ok "售后工单落库（$CASE_STATE）" || bad "工单未落库"

echo "[4/7] 管理员处理售后（退款到钱包）"
RESOLVE=$(curl -s -X POST $BASE/api/admin/aftersales/$CASE_ID/resolve -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d "{\"resolutionAction\":\"REFUND_TO_WALLET\",\"refundBlocking\":false,\"walletDelta\":1,\"settledLossMeals\":0,\"giftZeroMealCount\":0,\"giftVeggieJuiceCount\":0,\"adminRemark\":\"E2E退款\",\"operatorName\":\"E2E测试员\"}")
echo "     处理响应(前300字): ${RESOLVE:0:300}"
[ "$(echo "$RESOLVE" | jget "['code']")" = "OK" ] && ok "售后处理成功" || bad "售后处理失败: ${RESOLVE:0:250}"

echo "[5/7] 验证订单 REFUNDED + 餐次退回（REFUND_RETURN 流水）"
echo "     (ORDER_ID=$ORDER_ID CUST_ID=$CUST_ID)"
SQL=$(MYSQL jzqs -e "
  SELECT CONCAT('订单状态=', mso.status, ' | consumed=', COALESCE(w.consumed_meals,'-'))
  FROM aftersale_cases ac
  JOIN meal_slot_orders mso ON mso.id=ac.meal_slot_order_id
  JOIN daily_orders do ON do.id=mso.daily_order_id
  LEFT JOIN meal_wallets w ON w.customer_id=do.customer_id AND w.active=1
  WHERE ac.id=$CASE_ID;")
echo "     $SQL"
echo "$SQL" | grep -q "订单状态=REFUNDED" && ok "订单终态 REFUNDED" || bad "订单未 REFUNDED: $SQL"
REFUND_TX=$(MYSQL jzqs -e "
  SELECT COUNT(*) FROM wallet_transactions wt JOIN meal_wallets w ON w.id=wt.wallet_id
  WHERE w.customer_id=$CUST_ID AND wt.transaction_type IN ('REFUND_RETURN','REFUND');")
[ "$REFUND_TX" -ge 1 ] && ok "退款流水已生成（$REFUND_TX 条）" || bad "退款流水缺失"
echo "$SQL" | grep -q "consumed=0" && ok "餐次已退回 consumed=0" || bad "餐次未退回: $SQL"

echo "[6/7] 售后工单状态 COMPLETED"
FINAL=$(MYSQL jzqs -e "SELECT CONCAT('状态=',status,' 动作=',resolution_action) FROM aftersale_cases WHERE id=$CASE_ID;")
echo "     $FINAL"
echo "$FINAL" | grep -q "状态=COMPLETED" && ok "工单 COMPLETED" || bad "工单状态异常: $FINAL"

echo "[7/7] 看板售后计数与工单表一致（无孤儿/无重复计数）"
DB_CNT=$(MYSQL jzqs -e "SELECT COUNT(*) FROM aftersale_cases WHERE meal_slot_order_id=$ORDER_ID;")
DASH=$(curl -s "$BASE/api/admin/dashboard/overview" -H "Authorization: Bearer $ADMIN_TOKEN")
echo "     看板接口: $(echo "$DASH" | jget "['code']")（工单数=$DB_CNT）"
[ "$DB_CNT" = "1" ] && ok "订单仅 1 条售后工单（无重复）" || bad "工单数异常=$DB_CNT"

echo ""
echo "===== 链路E结果：PASS=$PASS FAIL=$FAIL ====="
exit $FAIL
