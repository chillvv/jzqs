package com.jzqs.app;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TestDatasourceIsolationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void shouldUseDedicatedTestDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            assertTrue(
                url.contains("/jzqs_test"),
                () -> "Tests must not use the real jzqs database, actual url: " + url
            );
        }
    }
}
