package com.jzqs.app.dispatch.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.common.realtime.RealtimeAudienceModule;
import com.jzqs.app.common.test.BaseDbIntegrationTest;
import com.jzqs.app.dispatch.api.DispatchRiderMonthlyStatItem;
import com.jzqs.app.dispatch.api.DispatchRiderMonthlyStatsResponse;
import com.jzqs.app.dispatch.api.DispatchRiderProfileUpsertResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 回归测试：骑手 ↔ 区域双写同步。
 *
 * 背景（8.24-8.26 反复出现）：
 * - 75310f3：编辑骑手改区域不同步 dispatch_area_bindings
 * - 83f6c4f：骑手端读的是废弃字段 default_rider_profile_id，应读 rider_profiles.default_area_code
 * - a813d60：区域设置默认骑手时不回写 rider_profiles.default_area_code，骑手端永远显示"未分配"
 * - 2faa25f：无跨区互斥校验，本区域订单可分配给已属其他区域的骑手
 */
class RiderAreaBindingSyncTest extends BaseDbIntegrationTest {

    private DispatchAssignmentModule assignment;

    @BeforeEach
    void setUp() {
        resetTables();
        assignment = new DispatchAssignmentModule(jdbc, new DispatchBatchModule(jdbc), mock(RealtimeAudienceModule.class));
    }

    private void insertBaseFixture() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        jdbc.update(
            "INSERT INTO customers (id, name, phone, source, active, customer_status, is_priority_customer) "
                + "VALUES (1, '测试客户', '13800138000', 'TEST', 1, 'ACTIVE', 0)"
        );
        jdbc.update(
            "INSERT INTO customer_addresses (id, customer_id, contact_name, contact_phone, address_line, area_code, is_default) "
                + "VALUES (101, 1, '测试客户', '13800138000', '测试路1号', '老城区', 0)"
        );
        jdbc.update(
            "INSERT INTO daily_orders (id, customer_id, serve_date, source, status) VALUES (201, 1, ?, 'ADMIN', 'ACTIVE')",
            tomorrow
        );
        jdbc.update(
            "INSERT INTO meal_slot_orders (id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, status) "
                + "VALUES (401, 201, 'LUNCH', 'LUNCH', 1, 101, 'PENDING_DISPATCH')"
        );
    }

    @Test
    @DisplayName("跨区指派被拦截：已归属其他区域的骑手不可被分配到本区域（2faa25f 回归）")
    void crossAreaAssignmentIsRejected() {
        insertBaseFixture();
        jdbc.update(
            "INSERT INTO rider_profiles (id, rider_name, phone, employment_status, default_area_code, auth_status) "
                + "VALUES (10, '骑手甲', '13000000001', 'ACTIVE', '区域A', 'ACTIVE'), "
                + "       (11, '骑手乙', '13000000002', 'ACTIVE', '区域B', 'ACTIVE')"
        );
        jdbc.update(
            "INSERT INTO dispatch_area_bindings (id, area_code, default_rider_profile_id, backup_rider_profile_id, updated_by) "
                + "VALUES (1, '区域A', 10, NULL, 'SYSTEM'), (2, '区域B', 11, NULL, 'SYSTEM')"
        );
        jdbc.update(
            "INSERT INTO dispatch_assignments (id, meal_slot_order_id, rider_name, rider_profile_id, area_code, status, sequence_number) "
                + "VALUES (1, 401, '骑手乙', 11, '区域B', 'PENDING', 1)"
        );

        assertThatThrownBy(() -> assignment.assignRiderToArea("区域B", "骑手甲", "LUNCH"))
            .as("骑手甲已归属区域A，跨区指派到区域B必须被拒绝")
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("分配骑手到区域后双向同步：default_area_code 与 default_rider_profile_id 一致，旧绑定被释放")
    void assignRiderToAreaSyncsBothWays() {
        insertBaseFixture();
        jdbc.update(
            "INSERT INTO rider_profiles (id, rider_name, phone, employment_status, default_area_code, auth_status) "
                + "VALUES (10, '骑手甲', '13000000001', 'ACTIVE', NULL, 'ACTIVE'), "
                + "       (11, '骑手乙', '13000000002', 'ACTIVE', '区域B', 'ACTIVE')"
        );
        jdbc.update(
            "INSERT INTO dispatch_area_bindings (id, area_code, default_rider_profile_id, backup_rider_profile_id, updated_by) "
                + "VALUES (1, '区域A', 10, NULL, 'SYSTEM'), (2, '区域B', 11, NULL, 'SYSTEM')"
        );
        jdbc.update(
            "INSERT INTO dispatch_assignments (id, meal_slot_order_id, rider_name, rider_profile_id, area_code, status, sequence_number) "
                + "VALUES (1, 401, '骑手乙', 11, '区域B', 'PENDING', 1)"
        );

        assignment.assignRiderToArea("区域B", "骑手甲", "LUNCH");

        // 骑手甲 default_area_code 回写为区域B（骑手端"我的"页读的就是这个字段）
        String riderA = jdbc.queryForObject("SELECT default_area_code FROM rider_profiles WHERE id = 10", String.class);
        String riderB = jdbc.queryForObject("SELECT default_area_code FROM rider_profiles WHERE id = 11", String.class);
        assertThat(riderA).as("骑手甲应归属区域B").isEqualTo("区域B");
        assertThat(riderB).as("骑手乙应被释放归属").isNull();

        // dispatch_area_bindings 双向同步：区域B 默认骑手变为骑手甲，区域A 被释放
        Long areaBDefault = jdbc.queryForObject(
            "SELECT default_rider_profile_id FROM dispatch_area_bindings WHERE area_code = '区域B'", Long.class);
        Long areaADefault = jdbc.queryForObject(
            "SELECT default_rider_profile_id FROM dispatch_area_bindings WHERE area_code = '区域A'", Long.class);
        assertThat(areaBDefault).isEqualTo(10L);
        assertThat(areaADefault).isNull();

        // 订单从骑手乙转移给骑手甲
        String riderName = jdbc.queryForObject(
            "SELECT rider_name FROM dispatch_assignments WHERE meal_slot_order_id = 401", String.class);
        assertThat(riderName).isEqualTo("骑手甲");
    }

    @Test
    @DisplayName("编辑骑手改区域：dispatch_area_bindings 释放 + 未完成订单迁移 + 回写（75310f3 回归）")
    void updateRiderProfileSyncsAreaChange() {
        insertBaseFixture();
        jdbc.update(
            "INSERT INTO rider_profiles (id, rider_name, phone, employment_status, default_area_code, auth_status) "
                + "VALUES (10, '骑手甲', '13000000001', 'ACTIVE', '区域A', 'ACTIVE')"
        );
        jdbc.update(
            "INSERT INTO dispatch_area_bindings (id, area_code, default_rider_profile_id, backup_rider_profile_id, updated_by) "
                + "VALUES (1, '区域A', 10, NULL, 'SYSTEM')"
        );
        jdbc.update(
            "INSERT INTO dispatch_assignments (id, meal_slot_order_id, rider_name, rider_profile_id, area_code, status, sequence_number) "
                + "VALUES (1, 401, '骑手甲', 10, '区域A', 'PENDING', 1)"
        );

        DispatchRiderAdminModule riderAdmin = new DispatchRiderAdminModule(jdbc);
        DispatchRiderAdminModule.AreaBindingUpdater updater = mock(DispatchRiderAdminModule.AreaBindingUpdater.class);

        riderAdmin.updateRiderProfile(10, "骑手甲", "骑手甲", "13000000001", "区域B", null, "admin", updater);

        // rider_profiles 回写新区域
        String newArea = jdbc.queryForObject("SELECT default_area_code FROM rider_profiles WHERE id = 10", String.class);
        assertThat(newArea).isEqualTo("区域B");

        // 旧区域默认绑定被释放
        Long areaADefault = jdbc.queryForObject(
            "SELECT default_rider_profile_id FROM dispatch_area_bindings WHERE area_code = '区域A'", Long.class);
        assertThat(areaADefault).isNull();

        // 未完成订单迁移到新区域
        String orderArea = jdbc.queryForObject(
            "SELECT area_code FROM dispatch_assignments WHERE meal_slot_order_id = 401", String.class);
        assertThat(orderArea).isEqualTo("区域B");

        // 通过收敛的 DispatchService.updateAreaBinding 同步新区域绑定
        verify(updater).update(eq("区域B"), isNull(), eq(10L), isNull(), eq("admin"));
    }

    @Test
    @DisplayName("骑手月度统计：只算已送达订单、按订单数计数、单均成本 = 月薪 ÷ 当月单量")
    void monthlyStatsCountsDeliveredOrdersAndUnitCost() {
        insertCustomerFixture();
        jdbc.update(
            "INSERT INTO rider_profiles (id, rider_name, phone, employment_status, default_area_code, auth_status, monthly_salary) "
                + "VALUES (10, '骑手甲', '13000000001', 'ACTIVE', '区域A', 'ACTIVE', 6000.00), "
                + "       (11, '骑手乙', '13000000002', 'ACTIVE', '区域B', 'ACTIVE', 4500.00)"
        );
        // 9 月：骑手甲 3 单已送达；骑手乙 1 单已送达 + 1 单配送中（未送达不计入）
        // 每个订单必须落在不同 serve_date：daily_orders 有 (customer_id, serve_date) 唯一约束
        insertOrderWithAssignment(401, "2026-09-10", "DELIVERED", 10, "骑手甲");
        insertOrderWithAssignment(402, "2026-09-11", "DELIVERED", 10, "骑手甲");
        insertOrderWithAssignment(403, "2026-09-12", "DELIVERED", 10, "骑手甲");
        insertOrderWithAssignment(404, "2026-09-13", "DELIVERED", 11, "骑手乙");
        insertOrderWithAssignment(405, "2026-09-14", "DISPATCHING", 11, "骑手乙");
        // 8 月已送达：不得计入 9 月
        insertOrderWithAssignment(406, "2026-08-31", "DELIVERED", 10, "骑手甲");

        DispatchRiderAdminModule riderAdmin = new DispatchRiderAdminModule(jdbc);
        DispatchRiderMonthlyStatsResponse stats = riderAdmin.riderMonthlyStats("2026-09");

        assertThat(stats.month()).isEqualTo("2026-09");
        assertThat(stats.totalDeliveredCount()).as("只统计 9 月已送达订单").isEqualTo(4);
        assertThat(stats.unassignedDeliveredCount()).as("本用例没有未归属骑手的孤儿单").isZero();

        DispatchRiderMonthlyStatItem riderA = findRider(stats, 10L);
        DispatchRiderMonthlyStatItem riderB = findRider(stats, 11L);
        assertThat(riderA.deliveredCount()).as("骑手甲 3 单").isEqualTo(3);
        assertThat(riderA.sharePercent()).isEqualByComparingTo("75.0");
        assertThat(riderA.monthlySalary()).isEqualByComparingTo("6000.00");
        assertThat(riderA.costPerOrder()).as("6000 / 3").isEqualByComparingTo("2000.00");

        assertThat(riderB.deliveredCount()).as("骑手乙只算已送达的 1 单").isEqualTo(1);
        assertThat(riderB.sharePercent()).isEqualByComparingTo("25.0");
        assertThat(riderB.costPerOrder()).as("4500 / 1").isEqualByComparingTo("4500.00");

        assertThat(stats.totalMonthlyCost()).as("两名骑手月薪合计").isEqualByComparingTo("10500.00");
        assertThat(stats.averageCostPerOrder()).as("10500 / 4").isEqualByComparingTo("2625.00");
    }

    @Test
    @DisplayName("骑手月度统计：未设置月薪的骑手只给单量与占比，不给单均成本")
    void monthlyStatsWithoutSalaryHasNoUnitCost() {
        insertCustomerFixture();
        jdbc.update(
            "INSERT INTO rider_profiles (id, rider_name, phone, employment_status, default_area_code, auth_status) "
                + "VALUES (10, '骑手甲', '13000000001', 'ACTIVE', '区域A', 'ACTIVE')"
        );
        insertOrderWithAssignment(401, "2026-09-10", "DELIVERED", 10, "骑手甲");

        DispatchRiderMonthlyStatsResponse stats = new DispatchRiderAdminModule(jdbc).riderMonthlyStats("2026-09");

        DispatchRiderMonthlyStatItem riderA = findRider(stats, 10L);
        assertThat(riderA.deliveredCount()).isEqualTo(1);
        assertThat(riderA.sharePercent()).isEqualByComparingTo("100.0");
        assertThat(riderA.monthlySalary()).isNull();
        assertThat(riderA.costPerOrder()).isNull();
        assertThat(stats.totalMonthlyCost()).isEqualByComparingTo("0");
        assertThat(stats.averageCostPerOrder()).isNull();
    }

    @Test
    @DisplayName("骑手建档与编辑：月薪落库并可清空")
    void monthlySalaryIsPersistedOnCreateAndUpdate() {
        DispatchRiderAdminModule riderAdmin = new DispatchRiderAdminModule(jdbc);
        DispatchRiderAdminModule.AreaBindingUpdater updater = mock(DispatchRiderAdminModule.AreaBindingUpdater.class);

        DispatchRiderProfileUpsertResponse created = riderAdmin.createRider(
            "骑手丙", "骑手丙", "13000000003", null, "ACTIVE", new BigDecimal("5000.00"), "admin", updater);
        assertThat(readSalary(created.riderId())).isEqualByComparingTo("5000.00");

        riderAdmin.updateRiderProfile(
            created.riderId(), "骑手丙", "骑手丙", "13000000003", null, new BigDecimal("6800.50"), "admin", updater);
        assertThat(readSalary(created.riderId())).isEqualByComparingTo("6800.50");

        riderAdmin.updateRiderProfile(
            created.riderId(), "骑手丙", "骑手丙", "13000000003", null, null, "admin", updater);
        assertThat(readSalary(created.riderId())).as("传 null 表示清空月薪").isNull();
    }

    private void insertCustomerFixture() {
        jdbc.update(
            "INSERT INTO customers (id, name, phone, source, active, customer_status, is_priority_customer) "
                + "VALUES (1, '测试客户', '13800138000', 'TEST', 1, 'ACTIVE', 0)"
        );
        jdbc.update(
            "INSERT INTO customer_addresses (id, customer_id, contact_name, contact_phone, address_line, area_code, is_default) "
                + "VALUES (101, 1, '测试客户', '13800138000', '测试路1号', '区域A', 0)"
        );
    }

    private void insertOrderWithAssignment(
        long orderId, String serveDate, String status, long riderProfileId, String riderName
    ) {
        jdbc.update(
            "INSERT INTO daily_orders (id, customer_id, serve_date, source, status) VALUES (?, 1, ?, 'ADMIN', 'ACTIVE')",
            orderId + 1000, LocalDate.parse(serveDate)
        );
        jdbc.update(
            "INSERT INTO meal_slot_orders (id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, status) "
                + "VALUES (?, ?, 'LUNCH', 'LUNCH', 1, 101, ?)",
            orderId, orderId + 1000, status
        );
        jdbc.update(
            "INSERT INTO dispatch_assignments (id, meal_slot_order_id, rider_name, rider_profile_id, area_code, status, sequence_number) "
                + "VALUES (?, ?, ?, ?, '区域A', ?, 1)",
            orderId, orderId, riderName, riderProfileId, status
        );
    }

    private DispatchRiderMonthlyStatItem findRider(DispatchRiderMonthlyStatsResponse stats, long riderId) {
        return stats.riders().stream()
            .filter(item -> item.riderId() == riderId)
            .findFirst()
            .orElseThrow(() -> new AssertionError("统计结果里没有骑手 " + riderId));
    }

    private BigDecimal readSalary(long riderId) {
        return jdbc.queryForObject("SELECT monthly_salary FROM rider_profiles WHERE id = ?", BigDecimal.class, riderId);
    }
}
