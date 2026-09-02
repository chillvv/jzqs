package com.jzqs.app.dashboard;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 看板"待处理售后"计数与售后列表可见条数的一致性回归测试。
 *
 * <p>历史缺陷：看板计数用裸 {@code COUNT(*) FROM aftersale_cases}，
 * 而售后列表用 INNER JOIN 客户/餐次订单/日订单 展示。当订单被删除、售后单变成孤儿后，
 * 计数 +1 但列表查不出来，表现为"看板显示 1 条待处理售后，点进去却是空的"。
 *
 * <p>本测试直接读取生产源码，断言两处 SQL 的 JOIN 口径一致。
 * 任何一方以后改了 JOIN 链而另一方没同步，测试都会失败。
 */
class AftersaleCountConsistencyTest {

    private static final Path DASHBOARD_SERVICE = Paths.get(
        "src/main/java/com/jzqs/app/dashboard/service/impl/DashboardServiceImpl.java"
    );
    private static final Path AFTERSALE_SERVICE = Paths.get(
        "src/main/java/com/jzqs/app/aftersale/service/impl/AftersaleServiceImpl.java"
    );

    private static String readSource(Path path) throws IOException {
        assertThat(Files.exists(path)).as("源码文件应存在: " + path).isTrue();
        return Files.readString(path, StandardCharsets.UTF_8).replaceAll("\\s+", " ").toLowerCase();
    }

    private static final String JOIN_MEAL_SLOT =
        "join meal_slot_orders mso on mso.id = ac.meal_slot_order_id";
    private static final String JOIN_DAILY_ORDERS =
        "join daily_orders ord on ord.id = mso.daily_order_id";
    private static final String JOIN_CUSTOMERS =
        "join customers c on c.id = ac.customer_id";

    @Test
    @DisplayName("看板待处理售后计数不得使用裸 COUNT(*)，必须 JOIN 订单以排除孤儿售后单")
    void dashboardCountMustJoinOrders() throws IOException {
        String source = readSource(DASHBOARD_SERVICE);

        assertThat(source)
            .as("原缺陷写法：裸 COUNT(*) FROM aftersale_cases WHERE status IN (...)，会统计到孤儿售后单")
            .doesNotContain("select count(*) from aftersale_cases where status in");

        assertThat(source)
            .as("计数必须 JOIN meal_slot_orders，否则订单被删后的孤儿售后单会被计入")
            .contains(JOIN_MEAL_SLOT);
        assertThat(source)
            .as("计数必须 JOIN daily_orders，与列表口径一致")
            .contains(JOIN_DAILY_ORDERS);
        assertThat(source)
            .as("计数必须 JOIN customers，与列表口径一致")
            .contains(JOIN_CUSTOMERS);
    }

    @Test
    @DisplayName("售后列表仍保持 INNER JOIN 口径（计数以它为基准对齐）")
    void aftersaleListKeepsJoinChain() throws IOException {
        String source = readSource(AFTERSALE_SERVICE);

        assertThat(source).contains(JOIN_MEAL_SLOT);
        assertThat(source).contains(JOIN_DAILY_ORDERS);
        assertThat(source).contains(JOIN_CUSTOMERS);
    }

    @Test
    @DisplayName("删除订单链路必须级联清理售后工单，避免产生孤儿数据")
    void deleteOrderMustCascadeAftersaleCases() throws IOException {
        String repository = readSource(Paths.get(
            "src/main/java/com/jzqs/app/order/persistence/OrderOperationRepository.java"
        ));
        String service = readSource(Paths.get(
            "src/main/java/com/jzqs/app/order/service/impl/OrderOperationServiceImpl.java"
        ));

        assertThat(repository)
            .as("仓储层应提供按订单删除售后工单的能力")
            .contains("delete from aftersale_cases where meal_slot_order_id = ?");
        assertThat(service)
            .as("删除订单时必须调用 deleteAftersaleCases 级联清理")
            .contains("deleteaftersalecases");
    }
}
