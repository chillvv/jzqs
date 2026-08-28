package com.jzqs.app.mobile;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MobilePortalSourceHygieneTest {

    private static final Path SOURCE_PATH = Path.of(
        "src/main/java/com/jzqs/app/mobile/MobilePortalServiceImpl.java"
    );

    @Test
    void shouldNotKeepMiniappOrderDebugScaffoldingInSource() throws IOException {
        String content = Files.readString(SOURCE_PATH);

        assertFalse(content.contains("#region debug-point"));
        assertFalse(content.contains("reportMiniappOrderDebug("));
        assertFalse(content.contains("DEBUG_MINIAPP_ORDER_ENV_FILE"));
        assertFalse(content.contains("app.debug.miniapp-order-enabled"));
    }

    @Test
    void shouldNotUseWeaklyTypedJdbcMapHotspotsInSource() throws IOException {
        String content = Files.readString(SOURCE_PATH);

        assertFalse(content.contains("jdbcTemplate.queryForMap("));
        assertFalse(content.contains("jdbcTemplate.queryForList("));
        assertFalse(content.contains("List<Map<String, Object>>"));
        assertFalse(content.contains("new HashMap<>()"));
    }
}
