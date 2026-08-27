#!/usr/bin/env bash
# E2E 链路 C：下单 → 派单 → 备注 → 取消 → 管理员删除 → 级联清理验证（连带影响）
set -uo pipefail
BASE=http://127.0.0.1:8080
ADMIN_TOKEN=$(cat /tmp/e2e_admin_token)
OPENID="e2e_openid_03"
PHONE="13900006666"
AREA="南湖区域"
SERVE_DATE=$(date -d "+1 day" +%Y-%m-%d)
PASS=0; FAIL=0
ok()   { PASS=$((PASS+1)); echo "  ✅ PASS: $1"; }
bad()  { FAIL=$((FAIL+1)); echo "  ❌ FAIL: $1"; }
jget() { python3 -c "import json,sys; d=json.load(sys.stdin); print(d$1)" 2>/dev/null; }
MYSQL() { mysql -h127.0.0.1 -P3306 -ujzqs -pjzqs_password_123 -N "$@" 2>/dev/null; }

echo "清理上次 E2E 残留..."
CID=$(MYSQL jzqs -e "SELECT id FROM customers WHERE openid='$OPENID' LIMIT 1;")
[ -n "$CID" ] && MYSQL jzqs -e "DELETE FROM customers WHERE id=$CID;"

echo "===== 链路C：删除订单级联清理（serveDate=$SERVE_DATE） ====="

echo "[1/7] 顾客注册 + 授权 + 下单"
REG=$(curl -s -X POST $BASE/api/mobile/auth/dev-phone -H "Content-Type: application/json" -d "{\"openid\":\"$OPENID\",\"phone\":\"$PHONE\"}")
CUST_TOKEN=$(echo "$REG" | jget "['data']['token']")
CUST_ID=$(echo "$REG" | jget "['data']['customerId']")
curl -s -X POST $BASE/api/admin/customers/$CUST_ID/wallet/grant -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"mealDelta":5,"validityDays":30,"remark":"E2E链路C"}' > /dev/null
ORDER=$(curl -s -X POST $BASE/api/mobile/customer/orders -H "Authorization: Bearer $CUST_TOKEN" -H "Content-Type: application/json" \
  -d "{\"serveDate\":\"$SERVE_DATE\",\"mealPeriod\":\"LUNCH\",\"deliveryAddress\":\"E2E测试路3号\",\"quantity\":1}")
ORDER_ID=$(echo "$ORDER" | jget "['data']['orderId']")
[ -n "$ORDER_ID" ] && ok "下单成功 orderId=$ORDER_ID" || bad "下单失败: $ORDER"

echo "[2/7] 管理员派单（产生派单+批次记录）"
curl -s -X POST $BASE/api/admin/dispatch/pending-items/batch-assign -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d "{\"orderIds\":[$ORDER_ID],\"areaCode\":\"$AREA\",\"updatedBy\":\"E2E\"}" > /dev/null
DA_CNT=$(MYSQL jzqs -e "SELECT COUNT(*) FROM dispatch_assignments WHERE meal_slot_order_id=$ORDER_ID;")
BI_CNT=$(MYSQL jzqs -e "SELECT COUNT(*) FROM dispatch_batch_items WHERE meal_slot_order_id=$ORDER_ID;")
[ "$DA_CNT" = "1" ] && ok "dispatch_assignments 已生成" || bad "派单记录缺失"
[ "$BI_CNT" -ge 1 ] && ok "dispatch_batch_items 已生成（$BI_CNT 条）" || bad "批次项缺失"

echo "[3/7] 管理员加备注（order_notes）"
NOTE=$(curl -s -X POST $BASE/api/admin/orders/$ORDER_ID/notes -H "Authorization: Bearer $ADMIN_TOKEN" -H "Content-Type: application/json" \
  -d '{"noteType":"MERCHANT","scopeType":"ORDER_ONCE","content":"E2E链路C备注"}')
[ "$(echo "$NOTE" | jget "['code']")" = "OK" ] && ok "备注已添加" || bad "备注失败: ${NOTE:0:150}"

echo "[4/7] 管理员取消订单"
CAN=$(curl -s -X POST $BASE/api/admin/orders/$ORDER_ID/cancel -H "Authorization: Bearer $ADMIN_TOKEN")
[ "$(echo "$CAN" | jget "['code']")" = "OK" ] && ok "取消成功" || bad "取消失败: ${CAN:0:150}"
MYSQL jzqs -e "SELECT CONCAT('状态=',status) FROM meal_slot_orders WHERE id=$ORDER_ID;"

echo "[5/7] 管理员删除订单"
DEL=$(curl -s -X POST $BASE/api/admin/orders/$ORDER_ID/delete -H "Authorization: Bearer $ADMIN_TOKEN")
echo "     删除响应: ${DEL:0:200}"
[ "$(echo "$DEL" | jget "['code']")" = "OK" ] && ok "删除成功" || bad "删除失败"

echo "[6/7] 级联清理验证：订单 + 5 张子表全部无残留"
ORDER_LEFT=$(MYSQL jzqs -e "SELECT COUNT(*) FROM meal_slot_orders WHERE id=$ORDER_ID;")
DA_LEFT=$(MYSQL jzqs -e "SELECT COUNT(*) FROM dispatch_assignments WHERE meal_slot_order_id=$ORDER_ID;")
BI_LEFT=$(MYSQL jzqs -e "SELECT COUNT(*) FROM dispatch_batch_items WHERE meal_slot_order_id=$ORDER_ID;")
REC_LEFT=$(MYSQL jzqs -e "SELECT COUNT(*) FROM delivery_receipts WHERE meal_slot_order_id=$ORDER_ID;")
NOTE_LEFT=$(MYSQL jzqs -e "SELECT COUNT(*) FROM order_notes WHERE meal_slot_order_id=$ORDER_ID;")
SUB_LEFT=$(MYSQL jzqs -e "SELECT COUNT(*) FROM customer_delivery_subscriptions WHERE meal_slot_order_id=$ORDER_ID;")
AFT_LEFT=$(MYSQL jzqs -e "SELECT COUNT(*) FROM aftersale_cases WHERE meal_slot_order_id=$ORDER_ID;")
echo "     订单=$ORDER_LEFT 派单=$DA_LEFT 批次项=$BI_LEFT 回执=$REC_LEFT 备注=$NOTE_LEFT 订阅=$SUB_LEFT 售后=$AFT_LEFT"
[ "$ORDER_LEFT" = "0" ] && ok "订单已删除" || bad "订单残留=$ORDER_LEFT"
[ "$DA_LEFT" = "0" ] && ok "dispatch_assignments 级联清理" || bad "派单残留=$DA_LEFT"
[ "$BI_LEFT" = "0" ] && ok "dispatch_batch_items 级联清理" || bad "批次项残留=$BI_LEFT"
[ "$NOTE_LEFT" = "0" ] && ok "order_notes 级联清理" || bad "备注残留=$NOTE_LEFT"
[ "$SUB_LEFT" = "0" ] && ok "customer_delivery_subscriptions 级联清理" || bad "订阅残留=$SUB_LEFT"

echo "[7/7] daily_order 自动删除 + 无孤儿引用"
DO_LEFT=$(MYSQL jzqs -e "SELECT COUNT(*) FROM daily_orders do LEFT JOIN meal_slot_orders mso ON mso.daily_order_id=do.id WHERE mso.id IS NULL AND do.customer_id=$CUST_ID;")
ORPHAN=$(MYSQL jzqs -e "
  SELECT (SELECT COUNT(*) FROM dispatch_assignments da LEFT JOIN meal_slot_orders mso ON mso.id=da.meal_slot_order_id WHERE mso.id IS NULL)
       + (SELECT COUNT(*) FROM dispatch_batch_items dbi LEFT JOIN meal_slot_orders mso ON mso.id=dbi.meal_slot_order_id WHERE mso.id IS NULL)
       + (SELECT COUNT(*) FROM delivery_receipts dr LEFT JOIN meal_slot_orders mso ON mso.id=dr.meal_slot_order_id WHERE mso.id IS NULL)
       + (SELECT COUNT(*) FROM order_notes on_ LEFT JOIN meal_slot_orders mso ON mso.id=on_.meal_slot_order_id WHERE mso.id IS NULL)
       + (SELECT COUNT(*) FROM aftersale_cases ac LEFT JOIN meal_slot_orders mso ON mso.id=ac.meal_slot_order_id WHERE mso.id IS NULL);")
echo "     空 daily_order 数=$DO_LEFT 孤儿引用总数=$ORPHAN"
[ "$DO_LEFT" = "0" ] && ok "无残留空 daily_order" || bad "空 daily_order 残留=$DO_LEFT"
[ "$ORPHAN" = "0" ] && ok "无孤儿引用（售后/回执/派单/批次/备注全部闭环）" || bad "发现孤儿引用=$ORPHAN"

echo ""
echo "===== 链路C结果：PASS=$PASS FAIL=$FAIL ====="
exit $FAIL
