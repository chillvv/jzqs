package com.jzqs.app.customer.service.impl;

import com.jzqs.app.customer.service.CustomerMainSheetSyncService;
import com.jzqs.app.customer.sync.CustomerMainSheetRow;
import com.jzqs.app.customer.sync.CustomerMainSheetSyncRequest;
import com.jzqs.app.customer.sync.CustomerMainSheetSyncSummaryResponse;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerMainSheetSyncServiceImpl implements CustomerMainSheetSyncService {
    private static final String MAIN_SHEET_NAME = "客户主表";
    private static final String IMPORT_PACKAGE_CODE = "EXCEL_IMPORT_202605";
    private static final String IMPORT_PACKAGE_NAME = "Excel导入套餐";
    private static final int MAY_REMAINING_MEALS_COLUMN = 44;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final JdbcTemplate jdbcTemplate;
    private final DataFormatter dataFormatter = new DataFormatter(Locale.ROOT);

    public CustomerMainSheetSyncServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public CustomerMainSheetSyncSummaryResponse sync(CustomerMainSheetSyncRequest request) throws IOException {
        ParseResult parseResult = parseMainSheet(Path.of(request.filePath()));
        if (request.clearExisting()) {
            clearCustomerData();
        }
        long packagePlanId = ensureImportPackagePlan();
        int importedCount = insertRows(parseResult.rows(), packagePlanId);
        return new CustomerMainSheetSyncSummaryResponse(
            importedCount,
            parseResult.skippedCount(),
            importedCount,
            request.filePath()
        );
    }

    private ParseResult parseMainSheet(Path filePath) throws IOException {
        try (InputStream inputStream = Files.newInputStream(filePath);
             Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheet(MAIN_SHEET_NAME);
            if (sheet == null) {
                throw new IllegalArgumentException("未找到工作表: " + MAIN_SHEET_NAME);
            }
            List<CustomerMainSheetRow> rows = new ArrayList<>();
            int skippedCount = 0;
            for (Row row : sheet) {
                String name = cellText(row.getCell(1));
                String phone = normalizePhone(row.getCell(2));
                if (!isValidCustomerRow(name, phone)) {
                    skippedCount++;
                    continue;
                }
                rows.add(new CustomerMainSheetRow(
                    name,
                    phone,
                    cellDate(row.getCell(3)),
                    cellDate(row.getCell(4)),
                    Math.max(0, cellInt(row.getCell(MAY_REMAINING_MEALS_COLUMN)))
                ));
            }
            return new ParseResult(rows, skippedCount);
        }
    }

    private boolean isValidCustomerRow(String name, String phone) {
        if (name == null || name.isBlank() || phone == null || phone.isBlank()) {
            return false;
        }
        return switch (name.trim()) {
            case "序号", "会员姓名", "日期", "销售", "食材包装", "人工水电", "利润", "合计送餐" -> false;
            default -> true;
        };
    }

    private String normalizePhone(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue())
                .stripTrailingZeros()
                .toPlainString()
                .replace(".0", "");
        }
        String text = cellText(cell);
        return text == null ? null : text.replace(" ", "");
    }

    private String cellText(Cell cell) {
        if (cell == null) {
            return null;
        }
        String text = dataFormatter.formatCellValue(cell);
        return text == null ? null : text.trim();
    }

    private LocalDate cellDate(Cell cell) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        String text = cellText(cell);
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text, DATE_FORMATTER);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private int cellInt(Cell cell) {
        if (cell == null) {
            return 0;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            return (int) Math.round(cell.getNumericCellValue());
        }
        if (cell.getCellType() == CellType.FORMULA) {
            FormulaEvaluator evaluator = cell.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
            CellType evaluatedType = evaluator.evaluateFormulaCell(cell);
            if (evaluatedType == CellType.NUMERIC) {
                return (int) Math.round(cell.getNumericCellValue());
            }
            if (evaluatedType == CellType.STRING) {
                String text = cell.getStringCellValue();
                if (text == null || text.isBlank()) {
                    return 0;
                }
                try {
                    return Integer.parseInt(text.trim());
                } catch (NumberFormatException ex) {
                    return 0;
                }
            }
            return 0;
        }
        String text = cellText(cell);
        if (text == null || text.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private void clearCustomerData() {
        jdbcTemplate.update("DELETE FROM dispatch_assignments");
        jdbcTemplate.update("DELETE FROM delivery_receipts");
        jdbcTemplate.update("DELETE FROM aftersale_actions");
        jdbcTemplate.update("DELETE FROM aftersale_cases");
        jdbcTemplate.update("DELETE FROM subscription_confirmations");
        jdbcTemplate.update("DELETE FROM meal_slot_orders");
        jdbcTemplate.update("DELETE FROM daily_orders");
        jdbcTemplate.update("DELETE FROM notification_logs");
        jdbcTemplate.update("DELETE FROM wallet_transactions");
        jdbcTemplate.update("DELETE FROM subscription_rules");
        jdbcTemplate.update("DELETE FROM meal_wallets");
        jdbcTemplate.update("DELETE FROM customer_addresses");
        jdbcTemplate.update("DELETE FROM customers");
    }

    private long ensureImportPackagePlan() {
        List<Long> existing = jdbcTemplate.queryForList(
            "SELECT id FROM package_plans WHERE package_code = ?",
            Long.class,
            IMPORT_PACKAGE_CODE
        );
        if (!existing.isEmpty()) {
            Long id = existing.get(0);
            jdbcTemplate.update(
                "UPDATE package_plans SET package_name = ?, total_meals = 0, enabled = TRUE WHERE id = ?",
                IMPORT_PACKAGE_NAME,
                id
            );
            return id;
        }
        return insertAndReturnId(
            "INSERT INTO package_plans (package_code, package_name, total_meals, enabled) VALUES (?, ?, ?, ?)",
            IMPORT_PACKAGE_CODE,
            IMPORT_PACKAGE_NAME,
            0,
            true
        );
    }

    private int insertRows(List<CustomerMainSheetRow> rows, long packagePlanId) {
        int imported = 0;
        for (CustomerMainSheetRow row : rows) {
            long customerId = insertCustomer(row);
            jdbcTemplate.update(
                """
                INSERT INTO meal_wallets
                    (customer_id, package_plan_id, total_meals, reserved_meals, consumed_meals, active, opened_at, expired_at, last_adjusted_at)
                VALUES
                    (?, ?, ?, 0, 0, TRUE, ?, ?, CURRENT_TIMESTAMP)
                """,
                customerId,
                packagePlanId,
                row.remainingMeals(),
                toTimestamp(row.openedAt()),
                toTimestamp(row.expiredAt())
            );
            imported++;
        }
        return imported;
    }

    private long insertCustomer(CustomerMainSheetRow row) {
        LocalDateTime now = LocalDateTime.now();
        Timestamp openedAt = toTimestamp(row.openedAt());
        return insertAndReturnId(
            """
            INSERT INTO customers
                (name, phone, source, customer_status, registered_at, first_paid_at, source_channel, active, created_at, updated_at)
            VALUES
                (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            row.name(),
            row.phone(),
            "BACKEND",
            "FORMAL",
            openedAt,
            openedAt,
            "EXCEL_IMPORT",
            true,
            Timestamp.valueOf(now),
            Timestamp.valueOf(now)
        );
    }

    private Timestamp toTimestamp(LocalDate value) {
        return value == null ? null : Timestamp.valueOf(value.atStartOfDay());
    }

    private long insertAndReturnId(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keyHolder);
        Map<String, Object> keys = keyHolder.getKeys();
        if (keys != null && !keys.isEmpty()) {
            Object idValue = keys.containsKey("ID") ? keys.get("ID") : keys.get("id");
            if (idValue == null) {
                idValue = keys.values().iterator().next();
            }
            if (idValue instanceof Number number) {
                return number.longValue();
            }
        }
        Number key = keyHolder.getKey();
        if (key != null) {
            return key.longValue();
        }
        return 0L;
    }

    private record ParseResult(List<CustomerMainSheetRow> rows, int skippedCount) {
    }
}
