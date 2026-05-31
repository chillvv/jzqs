package com.jzqs.app.customer.sync;

import static org.assertj.core.api.Assertions.assertThat;

import com.jzqs.app.customer.service.CustomerMainSheetSyncService;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

@SpringBootTest
class CustomerMainSheetSyncServiceTest {
    @Autowired
    private CustomerMainSheetSyncService syncService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldParseOnlyValidCustomerRows() throws Exception {
        Path workbook = createWorkbook(List.of(
            new Object[] {"序号", "会员姓名", "电话", "开卡日期", "过期日期"},
            new Object[] {1, "日期", null, null, null},
            new Object[] {7, "竹子", "13237187884", "2026-05-10", "2026-06-10", null, null, null, null, null, null, 2, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, 0, 2},
            new Object[] {8, "肖玥", "13177366419", "2026-05-01", "2026-06-01", null, null, null, null, null, null, 8, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, 0, 8}
        ));

        CustomerMainSheetSyncSummaryResponse summary = syncService.sync(
            new CustomerMainSheetSyncRequest(true, workbook.toString())
        );

        assertThat(summary.importedCount()).isEqualTo(2);
        assertThat(summary.walletCount()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM customers", Integer.class)).isEqualTo(2);
    }

    @Test
    void shouldKeepBalanceEqualToMayRemainingMealsAndReportSkippedRows() throws Exception {
        Path workbook = createWorkbook(List.of(
            new Object[] {"序号", "会员姓名", "电话", "开卡日期", "过期日期"},
            new Object[] {1, "日期", null, null, null},
            new Object[] {7, "竹子", "13237187884", "2026-05-10", "2026-06-10", null, null, null, null, null, null, 2, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, 0, 2},
            new Object[] {8, "肖玥", "13177366419", "2026-05-01", "2026-06-01", null, null, null, null, null, null, 8, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, 0, 8}
        ));

        CustomerMainSheetSyncSummaryResponse summary = syncService.sync(
            new CustomerMainSheetSyncRequest(true, workbook.toString())
        );

        Integer bambooBalance = jdbcTemplate.queryForObject(
            "SELECT total_meals - reserved_meals - consumed_meals FROM meal_wallets mw JOIN customers c ON c.id = mw.customer_id WHERE c.phone = ?",
            Integer.class,
            "13237187884"
        );
        Integer xiaoyueBalance = jdbcTemplate.queryForObject(
            "SELECT total_meals - reserved_meals - consumed_meals FROM meal_wallets mw JOIN customers c ON c.id = mw.customer_id WHERE c.phone = ?",
            Integer.class,
            "13177366419"
        );

        assertThat(bambooBalance).isEqualTo(2);
        assertThat(xiaoyueBalance).isEqualTo(8);
        assertThat(summary.skippedCount()).isGreaterThan(0);
    }

    @Test
    void shouldReadFormulaCellsForMayRemainingMeals() throws Exception {
        Path workbook = createWorkbook(List.of(
            new Object[] {"序号", "会员姓名", "电话", "开卡日期", "过期日期"},
            new Object[] {7, "竹子", "13237187884", null, null, null, null, null, null, null, null, 2, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, 0, "=L2-AR2"}
        ));

        syncService.sync(new CustomerMainSheetSyncRequest(true, workbook.toString()));

        Integer bambooBalance = jdbcTemplate.queryForObject(
            "SELECT total_meals - reserved_meals - consumed_meals FROM meal_wallets mw JOIN customers c ON c.id = mw.customer_id WHERE c.phone = ?",
            Integer.class,
            "13237187884"
        );

        assertThat(bambooBalance).isEqualTo(2);
    }

    private Path createWorkbook(List<Object[]> rows) throws IOException {
        Path file = Files.createTempFile("customer-main-sheet-", ".xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             OutputStream outputStream = Files.newOutputStream(file)) {
            Sheet sheet = workbook.createSheet("客户主表");
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Row row = sheet.createRow(rowIndex);
                Object[] values = rows.get(rowIndex);
                for (int cellIndex = 0; cellIndex < values.length; cellIndex++) {
                    Cell cell = row.createCell(cellIndex);
                    Object value = values[cellIndex];
                    if (value == null) {
                        continue;
                    }
                    if (value instanceof Number number) {
                        cell.setCellValue(number.doubleValue());
                    } else if (value instanceof String text && text.startsWith("=")) {
                        cell.setCellFormula(text.substring(1));
                    } else {
                        cell.setCellValue(String.valueOf(value));
                    }
                }
            }
            workbook.write(outputStream);
        }
        return file;
    }
}
