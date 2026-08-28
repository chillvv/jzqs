package com.jzqs.app.packageplan.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PackageGrantServiceImplTest {

    @Test
    void grantPackageLocksCustomerBeforeReadingWallets() {
        JdbcTemplate jdbcTemplate = org.mockito.Mockito.mock(
            JdbcTemplate.class,
            org.mockito.Mockito.withSettings().strictness(Strictness.LENIENT)
        );
        PackageGrantServiceImpl service = new PackageGrantServiceImpl(jdbcTemplate);

        when(jdbcTemplate.queryForObject(
            eq("SELECT id FROM customers WHERE id = ? FOR UPDATE"),
            eq(Long.class),
            eq(9921L)
        )).thenReturn(9921L);
        when(jdbcTemplate.queryForObject(
            eq("SELECT id FROM package_plans WHERE package_code = ?"),
            eq(Long.class),
            eq("MONTH_33")
        )).thenReturn(1L);
        when(jdbcTemplate.queryForList(
            startsWith("SELECT id FROM meal_wallets"),
            eq(Long.class),
            eq(9921L)
        )).thenReturn(List.of(19921L));
        service.grantPackage(9921L, "MONTH_33", 33, "后台客服", 7L);

        verify(jdbcTemplate).queryForObject(
            "SELECT id FROM customers WHERE id = ? FOR UPDATE",
            Long.class,
            9921L
        );
        verify(jdbcTemplate, atLeastOnce()).queryForList(
            startsWith("SELECT id FROM meal_wallets"),
            eq(Long.class),
            eq(9921L)
        );
    }
}
