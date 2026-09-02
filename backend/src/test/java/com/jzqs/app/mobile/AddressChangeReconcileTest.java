package com.jzqs.app.mobile;

import static org.assertj.core.api.Assertions.assertThat;

import com.jzqs.app.common.test.BaseDbIntegrationTest;
import com.jzqs.app.order.persistence.OrderDispatchRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 回归测试：客户换地址后的派单区域重算（reconcileDispatchArea）。
 *
 * 背景（8.24 事故 935af7f）：
 * - 查地址记忆只按 address_id 未按 customer_id → 跨客户误用记忆（已修复为按客户查）
 * - 用 area_code 判空，但空壳记录是 '' 非 NULL → 快照被刷成空串
 * - 刷新空壳记录时残留旧 rider_profile_id
 *
 * 背景（9.1 事故 503）：
 * - 无记忆时把派单快照写成魔法值 'PENDING'（不存在的假区域）→ 骑手中心按真实区域
 *   对账少单、工作台"待分配"又因派单行存在而不可见（静默死锁）。
 *
 * 本次验证：换地址后撤销原派单、订单回退待派状态；无记忆地址写空壳绑定后
 * 订单停留在「待分配」（派单行已删、不再出现假 PENDING 区域）。
 */
class AddressChangeReconcileTest extends BaseDbIntegrationTest {

    private MobileAddressModule module;

    @BeforeEach
    void setUp() {
        resetTables();
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        jdbc.update(
            "INSERT INTO customers (id, name, phone, source, active, customer_status, is_priority_customer) "
                + "VALUES (1, '测试客户', '13800138000', 'TEST', 1, 'ACTIVE', 0)"
        );
        jdbc.update(
            "INSERT INTO customer_addresses (id, customer_id, contact_name, contact_phone, address_line, area_code, is_default) "
                + "VALUES (101, 1, '测试客户', '13800138000', '测试路1号', '老城区', 1), "
                + "       (102, 1, '测试客户', '13800138000', '新地址路2号', '', 0)"
        );
        jdbc.update(
            "INSERT INTO rider_profiles (id, rider_name, phone, employment_status, default_area_code, auth_status) "
                + "VALUES (5, '张三', '13900139000', 'ACTIVE', '老城区', 'ACTIVE')"
        );
        jdbc.update(
            "INSERT INTO daily_orders (id, customer_id, serve_date, source, status) VALUES (201, 1, ?, 'ADMIN', 'ACTIVE')",
            tomorrow
        );
        jdbc.update(
            "INSERT INTO meal_slot_orders (id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, status) "
                + "VALUES (301, 201, 'LUNCH', 'LUNCH', 1, 101, 'DISPATCHING')"
        );
        jdbc.update(
            "INSERT INTO dispatch_assignments (id, meal_slot_order_id, rider_name, rider_profile_id, area_code, status, sequence_number) "
                + "VALUES (1, 301, '张三', 5, '老城区', 'DISPATCHING', 1)"
        );
        // 地址 101 有真实记忆；地址 102 无任何记忆
        jdbc.update(
            "INSERT INTO rider_address_bindings (customer_id, address_id, meal_period, address_fingerprint, area_code, rider_profile_id, manually_confirmed, updated_reason) "
                + "VALUES (1, 101, 'LUNCH', '测试路1号', '老城区', 5, 1, 'AREA_CONFIRMED')"
        );

        module = new MobileAddressModule(jdbc, new OrderDispatchRepository(jdbc));
    }

    @Test
    @DisplayName("换到无记忆新地址：写入空壳绑定 + 撤销派单回退待派，不产生假 PENDING 区域")
    void changeToUnknownAddressWritesShellBindingAndRevertsDispatch() {
        module.changeCustomerOrderAddress(1, 301, 102);

        // 订单已切换地址
        Long newAddressId = jdbc.queryForObject(
            "SELECT address_id FROM meal_slot_orders WHERE id = 301", Long.class);
        assertThat(newAddressId).isEqualTo(102L);

        // 空壳绑定记录：area_code=''、rider_profile_id=NULL、reason=ADDRESS_CHANGED_PENDING_CONFIRM
        List<Map<String, Object>> shellBindings = jdbc.queryForList(
            "SELECT area_code, rider_profile_id, manually_confirmed, updated_reason "
                + "FROM rider_address_bindings WHERE customer_id = 1 AND address_id = 102"
        );
        assertThat(shellBindings).hasSize(1);
        Map<String, Object> shell = shellBindings.get(0);
        // 空壳语义：area_code 为空串或 NULL 均视为"无区域待确认"（旧代码混用 '' 与 NULL 三态）
        assertThat(shell.get("area_code")).isIn("", null);
        assertThat(shell.get("rider_profile_id")).isNull();
        assertThat(shell.get("updated_reason")).isEqualTo("ADDRESS_CHANGED_PENDING_CONFIRM");

        // 9.1 事故回归：派单行必须被撤销（不允许残留 area_code='PENDING' 假区域快照）
        Integer assignmentCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM dispatch_assignments WHERE meal_slot_order_id = 301", Integer.class);
        assertThat(assignmentCount)
            .as("换到无记忆地址后应撤销派单，让订单回到工作台待分配（禁止假 PENDING 区域）")
            .isEqualTo(0);

        // 订单状态回退待派 → 工作台待分配可见
        String orderStatus = jdbc.queryForObject(
            "SELECT status FROM meal_slot_orders WHERE id = 301", String.class);
        assertThat(orderStatus).isEqualTo("PENDING_DISPATCH");
    }

    @Test
    @DisplayName("换到已有记忆的新地址：撤销原派单回退待派，由自动归区按新地址记忆重新派单")
    void changeToRememberedAddressRevertsAndReDispatchesByMemory() {
        // 地址 102 本身有真实记忆（同客户），区域为"新城区"，默认骑手李四
        jdbc.update(
            "INSERT INTO rider_address_bindings (customer_id, address_id, meal_period, address_fingerprint, area_code, rider_profile_id, manually_confirmed, updated_reason) "
                + "VALUES (1, 102, 'LUNCH', '新地址路2号', '新城区', 6, 1, 'AREA_CONFIRMED')"
        );
        jdbc.update(
            "INSERT INTO rider_profiles (id, rider_name, phone, employment_status, default_area_code, auth_status) "
                + "VALUES (6, '李四', '13900139001', 'ACTIVE', '新城区', 'ACTIVE')"
        );
        jdbc.update(
            "INSERT INTO dispatch_area_bindings (id, area_code, default_rider_profile_id, backup_rider_profile_id, updated_by) "
                + "VALUES (2, '新城区', 6, NULL, 'SYSTEM')"
        );

        module.changeCustomerOrderAddress(1, 301, 102);

        // 撤销原派单：不再保留旧骑手张三的快照
        Integer oldRiderAssignments = jdbc.queryForObject(
            "SELECT COUNT(*) FROM dispatch_assignments WHERE meal_slot_order_id = 301 AND rider_profile_id = 5", Integer.class);
        assertThat(oldRiderAssignments)
            .as("换到有记忆地址后应撤销原骑手派单，由自动归区按记忆区域重新派单")
            .isEqualTo(0);

        // 订单回退待派状态，后续由 ensureRememberedAssignments 自动归区到新城区/李四
        String orderStatus = jdbc.queryForObject(
            "SELECT status FROM meal_slot_orders WHERE id = 301", String.class);
        assertThat(orderStatus).isEqualTo("PENDING_DISPATCH");
    }

    @Test
    @DisplayName("已送达订单换地址（商家端免窗口）：保留派单与回执记录，不撤销")
    void deliveredOrderKeepsDispatchRecords() {
        jdbc.update("UPDATE meal_slot_orders SET status = 'DELIVERED' WHERE id = 301");

        module.changeCustomerOrderAddressByMerchant(1, 301, 102);

        Integer assignmentCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM dispatch_assignments WHERE meal_slot_order_id = 301", Integer.class);
        assertThat(assignmentCount)
            .as("已送达订单的派单记录必须保留（商家端可改任意日期订单）")
            .isEqualTo(1);
        String orderStatus = jdbc.queryForObject(
            "SELECT status FROM meal_slot_orders WHERE id = 301", String.class);
        assertThat(orderStatus).isEqualTo("DELIVERED");
    }

    @Test
    @DisplayName("地址删除防护：进行中订单引用的地址禁止删除（9.2 孤儿地址事故回归）")
    void deleteAddressReferencedByActiveOrderIsRejected() {
        // 地址 101 被进行中（DISPATCHING）的订单 301 引用，删除必须被拒绝
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> module.deleteCustomerAddress(1, 101)
            )
            .isInstanceOf(com.jzqs.app.common.error.BusinessException.class)
            .hasMessageContaining("进行中的订单");

        Integer addressCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM customer_addresses WHERE id = 101", Integer.class);
        assertThat(addressCount).as("被进行中订单引用的地址不得被物理删除").isEqualTo(1);

        // 订单转终态后，地址可以正常删除
        jdbc.update("UPDATE meal_slot_orders SET status = 'REFUNDED' WHERE id = 301");
        module.deleteCustomerAddress(1, 101);
        addressCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM customer_addresses WHERE id = 101", Integer.class);
        assertThat(addressCount).as("无进行中订单引用的地址可正常删除").isEqualTo(0);
    }
}
