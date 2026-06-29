package com.jzqs.app.order.persistence;

import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.order.api.ManualCreateCustomerAddressResponse;
import com.jzqs.app.order.api.ManualCreateCustomerSearchResponse;
import com.jzqs.app.order.api.OrderNoteItemResponse;
import com.jzqs.app.order.api.OrderNotesResponse;
import com.jzqs.app.order.api.OrderPrepItemResponse;
import com.jzqs.app.order.api.OrderPrepStatsResponse;
import com.jzqs.app.order.api.SubscriptionConfirmationItem;
import com.jzqs.app.order.api.SubscriptionPreviewItem;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrderQueryRepository {
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;

    public OrderQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SubscriptionPreviewItem> findSubscriptionPreview(LocalDate targetDate) {
        String sql = """
            SELECT
                sr.customer_id,
                c.name AS customer_name,
                c.phone AS customer_phone,
                'LUNCH' AS meal_period,
                sr.lunch_delivery_meal_period AS delivery_meal_period,
                ca.id AS address_id,
                ca.address_line AS delivery_address,
                COALESCE(sr.merchant_remark, '-') AS merchant_remark,
                COALESCE(mw.total_meals - mw.reserved_meals - mw.consumed_meals, 0) AS remaining_meals,
                CASE WHEN COALESCE(mw.total_meals - mw.reserved_meals - mw.consumed_meals, 0) > 0 THEN TRUE ELSE FALSE END AS has_balance
            FROM subscription_rules sr
            JOIN customers c ON c.id = sr.customer_id
            LEFT JOIN meal_wallets mw ON mw.customer_id = sr.customer_id AND mw.active = TRUE
            LEFT JOIN customer_addresses ca ON ca.id = sr.default_address_id
            WHERE sr.active = TRUE
              AND sr.paused = FALSE
              AND sr.lunch_enabled = TRUE
              AND sr.start_date <= ?
              AND sr.end_date >= ?
              AND FIND_IN_SET(?, sr.week_days) > 0

            UNION ALL

            SELECT
                sr.customer_id,
                c.name AS customer_name,
                c.phone AS customer_phone,
                'DINNER' AS meal_period,
                sr.dinner_delivery_meal_period AS delivery_meal_period,
                ca.id AS address_id,
                ca.address_line AS delivery_address,
                COALESCE(sr.merchant_remark, '-') AS merchant_remark,
                COALESCE(mw.total_meals - mw.reserved_meals - mw.consumed_meals, 0) AS remaining_meals,
                CASE WHEN COALESCE(mw.total_meals - mw.reserved_meals - mw.consumed_meals, 0) > 0 THEN TRUE ELSE FALSE END AS has_balance
            FROM subscription_rules sr
            JOIN customers c ON c.id = sr.customer_id
            LEFT JOIN meal_wallets mw ON mw.customer_id = sr.customer_id AND mw.active = TRUE
            LEFT JOIN customer_addresses ca ON ca.id = sr.default_address_id
            WHERE sr.active = TRUE
              AND sr.paused = FALSE
              AND sr.dinner_enabled = TRUE
              AND sr.start_date <= ?
              AND sr.end_date >= ?
              AND FIND_IN_SET(?, sr.week_days) > 0

            ORDER BY customer_id, meal_period
            """;

        String targetDayOfWeek = String.valueOf(targetDate.getDayOfWeek().getValue());
        return jdbcTemplate.query(sql, (rs, rowNum) -> new SubscriptionPreviewItem(
            rs.getLong("customer_id"),
            rs.getString("customer_name"),
            rs.getString("customer_phone"),
            rs.getString("meal_period"),
            rs.getString("delivery_meal_period"),
            rs.getLong("address_id"),
            rs.getString("delivery_address"),
            rs.getString("merchant_remark"),
            rs.getInt("remaining_meals"),
            rs.getBoolean("has_balance")
        ), targetDate, targetDate, targetDayOfWeek, targetDate, targetDate, targetDayOfWeek);
    }

    public OrderPrepStatsResponse loadPrepStats() {
        Integer totalMeals = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(quantity), 0) FROM meal_slot_orders",
            Integer.class
        );
        Integer lunchCount = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(quantity), 0) FROM meal_slot_orders WHERE meal_period = 'LUNCH'",
            Integer.class
        );
        Integer dinnerCount = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(quantity), 0) FROM meal_slot_orders WHERE meal_period = 'DINNER'",
            Integer.class
        );
        Integer selfOrderCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM daily_orders WHERE source = 'MINIAPP'",
            Integer.class
        );
        Integer staffOrderCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM daily_orders WHERE source = 'BACKEND'",
            Integer.class
        );
        Integer subscriptionCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM meal_slot_orders WHERE confirmed_from_subscription = TRUE",
            Integer.class
        );
        Integer adminRemarkCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM meal_slot_orders WHERE merchant_remark IS NOT NULL AND merchant_remark <> ''",
            Integer.class
        );
        return new OrderPrepStatsResponse(
            nvl(totalMeals),
            nvl(lunchCount),
            nvl(dinnerCount),
            nvl(selfOrderCount),
            nvl(staffOrderCount),
            nvl(subscriptionCount),
            nvl(adminRemarkCount),
            0
        );
    }

    public PageResponse<OrderPrepItemResponse> findPrepPage(LocalDate targetDate) {
        String sql = """
            SELECT
                mso.id,
                c.name AS customer_name,
                c.phone AS customer_phone,
                mso.meal_period,
                mso.delivery_meal_period,
                CASE
                    WHEN ms.meal_name IS NULL THEN CASE WHEN mso.meal_period = 'LUNCH' THEN '午餐 / 待配置菜品' ELSE '晚餐 / 待配置菜品' END
                    WHEN mso.meal_period = 'LUNCH' THEN CONCAT('午餐 / ', ms.meal_name)
                    ELSE CONCAT('晚餐 / ', ms.meal_name)
                END AS meal_summary,
                mso.quantity,
                COALESCE(mso.user_note, mso.note, '') AS user_note,
                COALESCE(mso.merchant_remark, '') AS merchant_remark,
                ca.address_line AS delivery_address,
                do.source,
                CASE WHEN c.is_priority_customer = TRUE OR mso.is_priority = TRUE THEN TRUE ELSE FALSE END AS priority_customer,
                CASE WHEN mso.confirmed_from_subscription = TRUE THEN TRUE ELSE FALSE END AS fixed_subscription,
                mso.status,
                CASE
                    WHEN EXISTS (
                        SELECT 1
                        FROM aftersale_cases ac2
                        WHERE ac2.meal_slot_order_id = mso.id
                          AND ac2.issue_type = 'REFUND'
                          AND ac2.status IN ('PENDING', 'PROCESSING', 'APPROVED')
                    ) THEN 'REFUND_PROCESSING'
                    ELSE mso.status
                END AS display_status,
                CASE
                    WHEN EXISTS (
                        SELECT 1
                        FROM aftersale_cases ac2
                        WHERE ac2.meal_slot_order_id = mso.id
                          AND ac2.issue_type = 'REFUND'
                          AND ac2.status IN ('PENDING', 'PROCESSING', 'APPROVED')
                    ) THEN '退款处理中'
                    WHEN mso.status = 'DELIVERED' THEN '已完成'
                    WHEN mso.status = 'DISPATCHING' THEN '配送中'
                    WHEN mso.status = 'REFUNDED' THEN '已退款'
                    WHEN mso.status = 'CANCELLED' THEN '已取消'
                    ELSE '待配送'
                END AS display_status_label,
                CASE WHEN mso.status = 'PENDING_DISPATCH' THEN TRUE ELSE FALSE END AS can_assign,
                CASE WHEN mso.status NOT IN ('CANCELLED', 'DELIVERED') THEN TRUE ELSE FALSE END AS can_cancel,
                CASE WHEN mso.status = 'DISPATCHING' THEN TRUE ELSE FALSE END AS can_receipt,
                CASE
                    WHEN mso.status = 'CANCELLED' THEN '已释放餐次'
                    WHEN mso.status = 'DELIVERED' THEN '已核销'
                    ELSE '已占用'
                END AS wallet_status_label,
                COALESCE(ari.reference_image_url, '') AS reference_image_url,
                COALESCE(dr.receipt_url, '') AS receipt_url,
                COALESCE(dr.receipt_note, '') AS receipt_note,
                dr.delivered_at
            FROM meal_slot_orders mso
            JOIN daily_orders do ON do.id = mso.daily_order_id
            JOIN customers c ON c.id = do.customer_id
            JOIN customer_addresses ca ON ca.id = mso.address_id
            LEFT JOIN menu_week_items ms ON ms.serve_date = do.serve_date
                AND ms.meal_period = mso.meal_period
                AND ms.slot_status = 'ACTIVE'
                AND EXISTS (SELECT 1 FROM menu_weeks mw2 WHERE mw2.id = ms.week_id AND mw2.status = 'PUBLISHED')
            LEFT JOIN address_reference_images ari ON ari.customer_address_id = mso.address_id
            LEFT JOIN (
                SELECT meal_slot_order_id, receipt_url, receipt_note, delivered_at
                FROM delivery_receipts dr_inner
                WHERE dr_inner.id = (
                    SELECT MAX(id) FROM delivery_receipts WHERE meal_slot_order_id = dr_inner.meal_slot_order_id
                )
            ) dr ON dr.meal_slot_order_id = mso.id
            WHERE do.serve_date = ?
              AND mso.status <> 'REFUNDED'
              AND NOT EXISTS (
                SELECT 1
                FROM aftersale_cases ac
                WHERE ac.meal_slot_order_id = mso.id
                  AND ac.refund_blocking = TRUE
                  AND ac.status = 'COMPLETED'
              )
            ORDER BY mso.id
            """;
        List<PrepOrderRow> rows = jdbcTemplate.query(sql, (rs, rowNum) -> new PrepOrderRow(
            rs.getLong("id"),
            rs.getString("customer_name"),
            rs.getString("customer_phone"),
            rs.getString("meal_period"),
            rs.getString("delivery_meal_period"),
            rs.getString("meal_summary"),
            rs.getInt("quantity"),
            rs.getString("user_note"),
            rs.getString("merchant_remark"),
            rs.getString("delivery_address"),
            rs.getString("source"),
            rs.getBoolean("priority_customer"),
            rs.getBoolean("fixed_subscription"),
            rs.getString("status"),
            rs.getString("display_status"),
            rs.getString("display_status_label"),
            rs.getBoolean("can_assign"),
            rs.getBoolean("can_cancel"),
            rs.getBoolean("can_receipt"),
            rs.getString("wallet_status_label"),
            rs.getString("reference_image_url"),
            rs.getString("receipt_url"),
            rs.getString("receipt_note"),
            formatTimestamp(rs.getTimestamp("delivered_at"))
        ), targetDate);
        Map<Long, OrderNoteProjection> noteProjections = loadOrderNoteProjections(rows.stream().map(PrepOrderRow::id).toList());
        List<OrderPrepItemResponse> items = rows.stream()
            .map(row -> toOrderPrepItem(row, noteProjections.get(row.id())))
            .toList();
        return PageResponse.of(items, 1, 20, items.size());
    }

    public List<SubscriptionConfirmationItem> findSubscriptionConfirmations(LocalDate serveDate) {
        String sql = """
            SELECT
                sc.id,
                c.name AS customer_name,
                c.phone AS customer_phone,
                sc.meal_period,
                sc.quantity,
                ca.address_line,
                sc.user_note,
                sc.merchant_remark,
                sc.is_priority,
                sc.status
            FROM subscription_confirmations sc
            JOIN customers c ON c.id = sc.customer_id
            LEFT JOIN customer_addresses ca ON ca.id = sc.address_id
            WHERE sc.serve_date = ?
            ORDER BY sc.id
            """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new SubscriptionConfirmationItem(
            rs.getLong("id"),
            rs.getString("customer_name"),
            rs.getString("customer_phone"),
            rs.getString("meal_period"),
            rs.getInt("quantity"),
            rs.getString("address_line"),
            rs.getString("user_note"),
            rs.getString("merchant_remark"),
            rs.getBoolean("is_priority"),
            rs.getString("status")
        ), java.sql.Date.valueOf(serveDate));
    }

    public boolean orderExists(long orderId) {
        Integer count = jdbcTemplate.queryForObject(
            """
                SELECT COUNT(*)
                FROM meal_slot_orders
                WHERE id = ?
                """,
            Integer.class,
            orderId
        );
        return count != null && count > 0;
    }

    public OrderNotesResponse findOrderNotes(long orderId) {
        List<OrderNoteItemResponse> notes = jdbcTemplate.query(
            """
                SELECT id, note_type, source_type, scope_type, content, effective_status, created_at
                FROM order_notes
                WHERE meal_slot_order_id = ?
                ORDER BY note_type, created_at, id
                """,
            (rs, rowNum) -> new OrderNoteItemResponse(
                rs.getLong("id"),
                stringValue(rs.getString("note_type")),
                stringValue(rs.getString("source_type")),
                stringValue(rs.getString("scope_type")),
                stringValue(rs.getString("content")),
                stringValue(rs.getString("effective_status")),
                formatTimestamp(rs.getTimestamp("created_at"))
            ),
            orderId
        );
        List<OrderNoteItemResponse> userNotes = notes.stream()
            .filter(note -> "USER".equals(note.noteType()))
            .toList();
        List<OrderNoteItemResponse> merchantNotes = notes.stream()
            .filter(note -> "MERCHANT".equals(note.noteType()))
            .toList();
        return new OrderNotesResponse(userNotes, merchantNotes);
    }

    public List<ManualCreateCustomerSearchResponse> searchManualCreateCustomers(String normalizedKeyword) {
        String likeKeyword = "%" + normalizedKeyword + "%";
        List<ManualCreateCustomerSearchRow> customerRows = jdbcTemplate.query(
            """
                SELECT c.id,
                       COALESCE(c.name, '') AS name,
                       COALESCE(c.phone, '') AS phone,
                       COALESCE(wallet_summary.remaining_meals, 0) AS remaining_meals
                FROM customers c
                LEFT JOIN (
                    SELECT customer_id,
                           COALESCE(SUM(total_meals), 0) - COALESCE(SUM(reserved_meals), 0) - COALESCE(SUM(consumed_meals), 0) AS remaining_meals
                    FROM meal_wallets
                    WHERE active = TRUE
                    GROUP BY customer_id
                ) wallet_summary ON wallet_summary.customer_id = c.id
                WHERE c.active = TRUE
                  AND (COALESCE(c.name, '') LIKE ? OR COALESCE(c.phone, '') LIKE ?)
                ORDER BY CASE
                    WHEN COALESCE(c.phone, '') = ? THEN 0
                    WHEN COALESCE(c.phone, '') LIKE ? THEN 1
                    WHEN COALESCE(c.name, '') = ? THEN 2
                    ELSE 3
                END, c.id DESC
                LIMIT 20
                """,
            (rs, rowNum) -> new ManualCreateCustomerSearchRow(
                rs.getLong("id"),
                stringValue(rs.getString("name")),
                stringValue(rs.getString("phone")),
                rs.getInt("remaining_meals")
            ),
            likeKeyword,
            likeKeyword,
            normalizedKeyword,
            likeKeyword,
            normalizedKeyword
        );
        if (customerRows.isEmpty()) {
            return List.of();
        }

        List<Long> customerIds = customerRows.stream()
            .map(ManualCreateCustomerSearchRow::customerId)
            .toList();
        Map<Long, List<ManualCreateCustomerAddressResponse>> addressesByCustomerId = new LinkedHashMap<>();
        customerIds.forEach(customerId -> addressesByCustomerId.put(customerId, new ArrayList<>()));

        String placeholders = String.join(",", customerIds.stream().map(id -> "?").toList());
        List<ManualCreateCustomerAddressRow> addressRows = jdbcTemplate.query(
            """
                SELECT id, customer_id, address_line, COALESCE(area_code, '') AS area_code, is_default
                FROM customer_addresses
                WHERE customer_id IN ("""
                + placeholders
                + """
                )
                ORDER BY customer_id, is_default DESC, id ASC
                """,
            (rs, rowNum) -> new ManualCreateCustomerAddressRow(
                rs.getLong("id"),
                rs.getLong("customer_id"),
                stringValue(rs.getString("address_line")),
                stringValue(rs.getString("area_code")),
                rs.getBoolean("is_default")
            ),
            customerIds.toArray()
        );
        for (ManualCreateCustomerAddressRow row : addressRows) {
            long customerId = row.customerId();
            addressesByCustomerId.computeIfAbsent(customerId, ignored -> new ArrayList<>()).add(
                new ManualCreateCustomerAddressResponse(
                    row.addressId(),
                    row.addressLine(),
                    row.areaCode(),
                    row.isDefault()
                )
            );
        }

        return customerRows.stream()
            .map(row -> {
                long customerId = row.customerId();
                return new ManualCreateCustomerSearchResponse(
                    customerId,
                    row.customerName(),
                    row.customerPhone(),
                    row.remainingMeals(),
                    List.copyOf(addressesByCustomerId.getOrDefault(customerId, List.of()))
                );
            })
            .toList();
    }

    private Map<Long, OrderNoteProjection> loadOrderNoteProjections(List<Long> orderIds) {
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = String.join(",", orderIds.stream().map(id -> "?").toList());
        List<OrderNoteRow> rows = jdbcTemplate.query(
            """
                SELECT meal_slot_order_id, note_type, content
                FROM order_notes
                WHERE effective_status = 'ACTIVE'
                  AND meal_slot_order_id IN (
            """
                + placeholders
                + """
                  )
                ORDER BY meal_slot_order_id, created_at, id
                """,
            (rs, rowNum) -> new OrderNoteRow(
                rs.getLong("meal_slot_order_id"),
                rs.getString("note_type"),
                rs.getString("content")
            ),
            orderIds.toArray()
        );
        Map<Long, NoteAccumulator> accumulators = new LinkedHashMap<>();
        for (OrderNoteRow row : rows) {
            NoteAccumulator accumulator = accumulators.computeIfAbsent(row.orderId(), ignored -> new NoteAccumulator());
            accumulator.add(stringValue(row.noteType()), stringValue(row.content()));
        }
        Map<Long, OrderNoteProjection> projections = new LinkedHashMap<>();
        for (Long orderId : orderIds) {
            NoteAccumulator accumulator = accumulators.get(orderId);
            if (accumulator != null) {
                projections.put(orderId, accumulator.toProjection());
            }
        }
        return projections;
    }

    private OrderPrepItemResponse toOrderPrepItem(PrepOrderRow row, OrderNoteProjection projection) {
        return new OrderPrepItemResponse(
            row.id(),
            row.customerName(),
            row.customerPhone(),
            row.mealPeriod(),
            row.deliveryMealPeriod(),
            row.mealSummary(),
            row.quantity(),
            resolveProjectedUserNote(projection, row.userNote()),
            resolveProjectedMerchantRemark(projection, row.merchantRemark()),
            row.deliveryAddress(),
            row.source(),
            row.priorityCustomer(),
            row.fixedSubscription(),
            row.status(),
            row.displayStatus(),
            row.displayStatusLabel(),
            row.canAssign(),
            row.canCancel(),
            row.canReceipt(),
            row.walletStatusLabel(),
            row.referenceImageUrl(),
            row.receiptUrl(),
            row.receiptNote(),
            row.deliveredAt()
        );
    }

    private String resolveProjectedUserNote(OrderNoteProjection projection, String legacyValue) {
        if (projection != null && projection.hasOrderNotes()) {
            return projection.userNote();
        }
        return normalizeLegacyNote(legacyValue);
    }

    private String resolveProjectedMerchantRemark(OrderNoteProjection projection, String legacyValue) {
        if (projection != null && projection.hasOrderNotes()) {
            return projection.merchantRemark();
        }
        return normalizeLegacyNote(legacyValue);
    }

    private String normalizeLegacyNote(String value) {
        String normalized = stringValue(value);
        return "-".equals(normalized) ? "" : normalized;
    }

    private String formatTimestamp(Timestamp value) {
        return value == null ? "" : value.toLocalDateTime().format(DATETIME_FORMATTER);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private record PrepOrderRow(
        long id,
        String customerName,
        String customerPhone,
        String mealPeriod,
        String deliveryMealPeriod,
        String mealSummary,
        int quantity,
        String userNote,
        String merchantRemark,
        String deliveryAddress,
        String source,
        boolean priorityCustomer,
        boolean fixedSubscription,
        String status,
        String displayStatus,
        String displayStatusLabel,
        boolean canAssign,
        boolean canCancel,
        boolean canReceipt,
        String walletStatusLabel,
        String referenceImageUrl,
        String receiptUrl,
        String receiptNote,
        String deliveredAt
    ) {
    }

    private record ManualCreateCustomerSearchRow(
        long customerId,
        String customerName,
        String customerPhone,
        int remainingMeals
    ) {
    }

    private record ManualCreateCustomerAddressRow(
        long addressId,
        long customerId,
        String addressLine,
        String areaCode,
        boolean isDefault
    ) {
    }

    private record OrderNoteProjection(String userNote, String merchantRemark, boolean hasOrderNotes) {
    }

    private record OrderNoteRow(long orderId, String noteType, String content) {
    }

    private static final class NoteAccumulator {
        private final List<String> userNotes = new ArrayList<>();
        private final List<String> merchantNotes = new ArrayList<>();

        private void add(String noteType, String content) {
            String normalized = content == null ? "" : content.trim();
            if (normalized.isBlank() || "-".equals(normalized)) {
                return;
            }
            List<String> target = "MERCHANT".equals(noteType) ? merchantNotes : userNotes;
            if (!target.contains(normalized)) {
                target.add(normalized);
            }
        }

        private OrderNoteProjection toProjection() {
            return new OrderNoteProjection(
                String.join(" / ", userNotes),
                String.join(" / ", merchantNotes),
                true
            );
        }
    }
}
