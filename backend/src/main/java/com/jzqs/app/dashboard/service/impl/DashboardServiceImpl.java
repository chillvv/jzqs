package com.jzqs.app.dashboard.service.impl;

import com.jzqs.app.dashboard.api.DashboardOverviewResponse;
import com.jzqs.app.dashboard.service.DashboardService;
import com.jzqs.app.subscription.api.LowBalanceSubscriptionItem;
import java.sql.Date;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DashboardServiceImpl implements DashboardService {
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("MM/dd");
    private final JdbcTemplate jdbcTemplate;

    public DashboardServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public DashboardOverviewResponse overview() {
        LocalDate businessDate = resolveBusinessDate();
        LocalDate upcomingServeDate = resolveUpcomingServeDate(businessDate);
        PackageReminderSettings settings = loadPackageReminderSettings();
        int deliveredToday = countByBusinessDate(
            "SELECT COUNT(*) FROM delivery_receipts WHERE CAST(delivered_at AS DATE) = ?",
            businessDate
        );
        int tomorrowMealCount = quantityByServeDate(upcomingServeDate, null);
        int tomorrowLunchCount = quantityByServeDate(upcomingServeDate, "LUNCH");
        int tomorrowDinnerCount = quantityByServeDate(upcomingServeDate, "DINNER");
        int newCardsToday = countDistinctCustomersByTransactionType(businessDate, "OPEN");
        int rechargeCustomersToday = countDistinctCustomersByTransactionType(businessDate, "GRANT");
        int aftersaleToday = countByBusinessDate(
            "SELECT COUNT(*) FROM aftersale_cases WHERE CAST(created_at AS DATE) = ?",
            businessDate
        );
        int cancellationsToday = quantityByCreatedDateAndStatus(businessDate, "CANCELLED");
        int totalOrdersToday = quantityByCreatedDateAndStatus(businessDate, null);
        int pendingOrdersToday = quantityByCreatedDateAndStatuses(businessDate, List.of("PENDING_CONFIRMATION", "CONFIRMED"));
        int pendingDispatchToday = quantityByCreatedDateAndStatus(businessDate, "PENDING_DISPATCH");
        int dispatchingOrdersToday = quantityByCreatedDateAndStatus(businessDate, "DISPATCHING");
        int deliveredOrdersToday = quantityByCreatedDateAndStatus(businessDate, "DELIVERED");
        int lowBalanceCustomers = countLowBalanceCustomers(settings.lowBalanceThreshold());
        int expiringSoonCustomers = countExpiringSoonCustomers(settings.expiryReminderDays());
        int openAftersaleCount = countOpenAftersales();
        int specialOrdersToday = countByBusinessDate(
            """
            SELECT COUNT(*)
            FROM meal_slot_orders mso
            JOIN daily_orders do ON do.id = mso.daily_order_id
            WHERE CAST(do.created_at AS DATE) = ?
              AND (
                COALESCE(NULLIF(TRIM(mso.user_note), ''), NULL) IS NOT NULL
                OR COALESCE(NULLIF(TRIM(mso.merchant_remark), ''), NULL) IS NOT NULL
                OR mso.is_priority = TRUE
              )
            """,
            businessDate
        );
        int menuRiskDays = countMenuRiskDays(upcomingServeDate);

        return new DashboardOverviewResponse(
            deliveredToday,
            tomorrowMealCount,
            tomorrowLunchCount,
            tomorrowDinnerCount,
            newCardsToday,
            rechargeCustomersToday,
            aftersaleToday,
            cancellationsToday,
            totalOrdersToday,
            pendingOrdersToday,
            pendingDispatchToday,
            dispatchingOrdersToday,
            deliveredOrdersToday,
            lowBalanceCustomers,
            expiringSoonCustomers,
            openAftersaleCount,
            specialOrdersToday,
            menuRiskDays,
            buildOrderTrend(businessDate),
            buildGrowthTrend(businessDate)
        );
    }

    @Override
    public List<LowBalanceSubscriptionItem> lowBalanceSubscriptions() {
        LocalDate today = LocalDate.now();
        PackageReminderSettings settings = loadPackageReminderSettings();
        String sql = """
            SELECT
                sr.customer_id,
                c.name AS customer_name,
                c.phone AS customer_phone,
                COALESCE(mw.total_meals - mw.reserved_meals - mw.consumed_meals, 0) AS remaining_meals,
                mw.expired_at,
                sr.lunch_enabled,
                sr.dinner_enabled,
                sr.id AS subscription_rule_id
            FROM subscription_rules sr
            JOIN customers c ON c.id = sr.customer_id
            LEFT JOIN meal_wallets mw ON mw.customer_id = sr.customer_id AND mw.active = TRUE
            WHERE sr.active = TRUE
              AND sr.paused = FALSE
              AND sr.start_date <= ?
              AND sr.end_date >= ?
              AND c.customer_status != 'DORMANT'
              AND (
                COALESCE(mw.total_meals - mw.reserved_meals - mw.consumed_meals, 0) <= ?
                OR (
                    mw.expired_at IS NOT NULL
                    AND DATEDIFF(CAST(mw.expired_at AS DATE), CURRENT_DATE) BETWEEN 0 AND ?
                )
              )
            ORDER BY remaining_meals ASC, mw.expired_at ASC, sr.customer_id
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new LowBalanceSubscriptionItem(
            rs.getLong("customer_id"),
            rs.getString("customer_name"),
            rs.getString("customer_phone"),
            rs.getInt("remaining_meals"),
            rs.getTimestamp("expired_at") == null ? "" : rs.getTimestamp("expired_at").toLocalDateTime().toLocalDate().toString(),
            remainingValidityDays(rs.getTimestamp("expired_at") == null ? null : rs.getTimestamp("expired_at").toLocalDateTime().toLocalDate()),
            resolveAlertCode(
                rs.getInt("remaining_meals"),
                rs.getTimestamp("expired_at") == null ? null : rs.getTimestamp("expired_at").toLocalDateTime().toLocalDate(),
                settings
            ),
            resolveAlertLabel(
                rs.getInt("remaining_meals"),
                rs.getTimestamp("expired_at") == null ? null : rs.getTimestamp("expired_at").toLocalDateTime().toLocalDate(),
                settings
            ),
            rs.getBoolean("lunch_enabled"),
            rs.getBoolean("dinner_enabled"),
            today.plusDays(1), // nextServeDate
            rs.getLong("subscription_rule_id")
        ), today, today, settings.lowBalanceThreshold(), settings.expiryReminderDays());
    }

    private LocalDate resolveBusinessDate() {
        List<LocalDate> candidates = new ArrayList<>();
        addIfPresent(candidates, queryDate("SELECT MAX(CAST(delivered_at AS DATE)) FROM delivery_receipts"));
        addIfPresent(candidates, queryDate("SELECT MAX(CAST(created_at AS DATE)) FROM daily_orders"));
        addIfPresent(candidates, queryDate("SELECT MAX(CAST(created_at AS DATE)) FROM wallet_transactions"));
        addIfPresent(candidates, queryDate("SELECT MAX(CAST(created_at AS DATE)) FROM aftersale_cases"));
        if (candidates.isEmpty()) {
            return LocalDate.now();
        }
        return candidates.stream().max(LocalDate::compareTo).orElse(LocalDate.now());
    }

    private LocalDate resolveUpcomingServeDate(LocalDate businessDate) {
        // 明日订单：统计的是明天的出餐日期（今天+1天）
        // 用户前一天下单，serve_date是明天，所以今天看明天的订单
        return businessDate.plusDays(1);
    }

    private int quantityByServeDate(LocalDate serveDate, String mealPeriod) {
        if (mealPeriod == null) {
            return countByBusinessDate(
                """
                SELECT COALESCE(SUM(mso.quantity), 0)
                FROM meal_slot_orders mso
                JOIN daily_orders do ON do.id = mso.daily_order_id
                WHERE do.serve_date = ?
                  AND mso.status <> 'REFUNDED'
                  AND NOT EXISTS (
                      SELECT 1
                      FROM aftersale_cases ac
                      WHERE ac.meal_slot_order_id = mso.id
                        AND ac.refund_blocking = TRUE
                        AND ac.status IN ('PENDING', 'PROCESSING', 'APPROVED', 'COMPLETED')
                  )
                """,
                serveDate
            );
        }
        return nvl(jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(SUM(mso.quantity), 0)
            FROM meal_slot_orders mso
            JOIN daily_orders do ON do.id = mso.daily_order_id
            WHERE do.serve_date = ?
              AND mso.meal_period = ?
              AND mso.status <> 'REFUNDED'
              AND NOT EXISTS (
                  SELECT 1
                  FROM aftersale_cases ac
                  WHERE ac.meal_slot_order_id = mso.id
                    AND ac.refund_blocking = TRUE
                    AND ac.status IN ('PENDING', 'PROCESSING', 'APPROVED', 'COMPLETED')
              )
            """,
            Integer.class,
            serveDate,
            mealPeriod
        ));
    }

    private int quantityByCreatedDateAndStatus(LocalDate businessDate, String status) {
        if (status == null) {
            return countByBusinessDate(
                "SELECT COALESCE(SUM(mso.quantity), 0) FROM meal_slot_orders mso JOIN daily_orders do ON do.id = mso.daily_order_id WHERE CAST(do.created_at AS DATE) = ?",
                businessDate
            );
        }
        return countByBusinessDate(
            "SELECT COALESCE(SUM(mso.quantity), 0) FROM meal_slot_orders mso JOIN daily_orders do ON do.id = mso.daily_order_id WHERE CAST(do.created_at AS DATE) = ? AND mso.status = ?",
            businessDate,
            status
        );
    }

    private int quantityByCreatedDateAndStatuses(LocalDate businessDate, List<String> statuses) {
        if (statuses.isEmpty()) {
            return 0;
        }
        return nvl(jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(mso.quantity), 0) FROM meal_slot_orders mso JOIN daily_orders do ON do.id = mso.daily_order_id WHERE CAST(do.created_at AS DATE) = ? AND mso.status IN (?, ?)",
            Integer.class,
            businessDate,
            statuses.get(0),
            statuses.get(1)
        ));
    }

    private int countDistinctCustomersByTransactionType(LocalDate businessDate, String transactionType) {
        return countByBusinessDate(
            """
            SELECT COUNT(DISTINCT mw.customer_id)
            FROM wallet_transactions wt
            JOIN meal_wallets mw ON mw.id = wt.wallet_id
            WHERE CAST(wt.created_at AS DATE) = ?
              AND wt.transaction_type = ?
            """,
            businessDate,
            transactionType
        );
    }

    private int countLowBalanceCustomers(int threshold) {
        return nvl(jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM meal_wallets
            WHERE active = TRUE
              AND (total_meals - reserved_meals - consumed_meals) <= ?
            """,
            Integer.class,
            threshold
        ));
    }

    private int countExpiringSoonCustomers(int reminderDays) {
        return nvl(jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM meal_wallets
            WHERE active = TRUE
              AND expired_at IS NOT NULL
              AND DATEDIFF(CAST(expired_at AS DATE), CURRENT_DATE) BETWEEN 0 AND ?
            """,
            Integer.class,
            reminderDays
        ));
    }

    private int countOpenAftersales() {
        return nvl(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM aftersale_cases WHERE status IN ('PENDING', 'PROCESSING', 'APPROVED')",
            Integer.class
        ));
    }

    private int countMenuRiskDays(LocalDate fromDate) {
        return nvl(jdbcTemplate.queryForObject(
            """
            SELECT COUNT(DISTINCT serve_date)
            FROM menu_week_items
            WHERE serve_date BETWEEN ? AND ?
              AND slot_status = 'UNCONFIGURED'
            """,
            Integer.class,
            fromDate,
            fromDate.plusDays(6)
        ));
    }

    private List<DashboardOverviewResponse.OrderTrendPoint> buildOrderTrend(LocalDate businessDate) {
        List<DashboardOverviewResponse.OrderTrendPoint> points = new ArrayList<>();
        LocalDate startDate = businessDate.minusDays(6);
        for (int i = 0; i < 7; i++) {
            LocalDate statDate = startDate.plusDays(i);
            points.add(new DashboardOverviewResponse.OrderTrendPoint(
                SHORT_DATE.format(statDate),
                quantityByCreatedDateAndStatus(statDate, null),
                quantityByCreatedDateAndMealPeriod(statDate, "LUNCH"),
                quantityByCreatedDateAndMealPeriod(statDate, "DINNER")
            ));
        }
        return points;
    }

    private List<DashboardOverviewResponse.GrowthTrendPoint> buildGrowthTrend(LocalDate businessDate) {
        List<DashboardOverviewResponse.GrowthTrendPoint> points = new ArrayList<>();
        LocalDate startDate = businessDate.minusDays(4);
        for (int i = 0; i < 5; i++) {
            LocalDate statDate = startDate.plusDays(i);
            points.add(new DashboardOverviewResponse.GrowthTrendPoint(
                SHORT_DATE.format(statDate),
                countDistinctCustomersByTransactionType(statDate, "OPEN"),
                countDistinctCustomersByTransactionType(statDate, "GRANT")
            ));
        }
        return points;
    }

    private int quantityByCreatedDateAndMealPeriod(LocalDate businessDate, String mealPeriod) {
        return nvl(jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(SUM(mso.quantity), 0)
            FROM meal_slot_orders mso
            JOIN daily_orders do ON do.id = mso.daily_order_id
            WHERE CAST(do.created_at AS DATE) = ?
              AND mso.meal_period = ?
            """,
            Integer.class,
            businessDate,
            mealPeriod
        ));
    }

    private LocalDate queryDate(String sql, Object... args) {
        Date value = jdbcTemplate.queryForObject(sql, Date.class, args);
        return value == null ? null : value.toLocalDate();
    }

    private void addIfPresent(List<LocalDate> dates, LocalDate value) {
        if (value != null) {
            dates.add(value);
        }
    }

    private int countByBusinessDate(String sql, Object... args) {
        return nvl(jdbcTemplate.queryForObject(sql, Integer.class, args));
    }

    private int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private int remainingValidityDays(LocalDate expiredAt) {
        if (expiredAt == null) {
            return 0;
        }
        return (int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), expiredAt);
    }

    private PackageReminderSettings loadPackageReminderSettings() {
        return jdbcTemplate.query(
            "SELECT package_expiry_reminder_days, package_low_balance_threshold FROM admin_settings WHERE id = 1",
            rs -> rs.next()
                ? new PackageReminderSettings(rs.getInt("package_expiry_reminder_days"), rs.getInt("package_low_balance_threshold"))
                : new PackageReminderSettings(7, 3)
        );
    }

    private String resolveAlertCode(int remainingMeals, LocalDate expiredAt, PackageReminderSettings settings) {
        int remainingDays = remainingValidityDays(expiredAt);
        if (expiredAt != null && remainingDays < 0) {
            return "EXPIRED";
        }
        if (expiredAt != null && remainingDays <= settings.expiryReminderDays()) {
            return "EXPIRING_SOON";
        }
        if (remainingMeals <= settings.lowBalanceThreshold()) {
            return "LOW_BALANCE";
        }
        return "";
    }

    private String resolveAlertLabel(int remainingMeals, LocalDate expiredAt, PackageReminderSettings settings) {
        String code = resolveAlertCode(remainingMeals, expiredAt, settings);
        return switch (code) {
            case "EXPIRED" -> "已过期";
            case "EXPIRING_SOON" -> "即将到期";
            case "LOW_BALANCE" -> "餐数不足";
            default -> "";
        };
    }

    private record PackageReminderSettings(
        int expiryReminderDays,
        int lowBalanceThreshold
    ) {
    }
}
