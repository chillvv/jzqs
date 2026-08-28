package com.jzqs.app.dispatch.service.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

@ExtendWith(MockitoExtension.class)
class JdbcAreaMemoryModuleTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private AreaMemoryModule module;

    @BeforeEach
    void setUp() {
        module = new JdbcAreaMemoryModule(jdbcTemplate, new ObjectMapper());
    }

    @Test
    @SuppressWarnings("unchecked")
    void memoriesAreIsolatedByAreaCode() throws Exception {
        doAnswer(invocation -> {
            KeyHolder keyHolder = invocation.getArgument(1);
            ((GeneratedKeyHolder) keyHolder).getKeyList().add(Map.of("GENERATED_KEY", 11L));
            return 1;
        }).when(jdbcTemplate).update(any(org.springframework.jdbc.core.PreparedStatementCreator.class), any(KeyHolder.class));

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("A01"), eq("LUNCH"), eq("LUNCH")))
            .thenAnswer(invocation -> {
                RowMapper<AreaMemoryModule.AreaMemoryItem> rowMapper = invocation.getArgument(1);
                ResultSet rs = mock(ResultSet.class);
                when(rs.getLong("id")).thenReturn(1L);
                when(rs.getString("area_code")).thenReturn("A01");
                when(rs.getString("memory_type")).thenReturn("ROUTE_PREFERENCE");
                when(rs.getString("title")).thenReturn("午餐先写字楼");
                when(rs.getString("summary")).thenReturn("A 区午餐高峰先写字楼后住宅");
                when(rs.getString("applicable_scene")).thenReturn("ALL");
                when(rs.getInt("weight")).thenReturn(1);
                when(rs.getString("status")).thenReturn("ACTIVE");
                when(rs.getString("source_correction_ids")).thenReturn("[11]");
                return List.of(rowMapper.mapRow(rs, 0));
            });
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("B01"), eq("LUNCH"), eq("LUNCH")))
            .thenReturn(List.of());

        long correctionId = module.recordCorrection(new AreaMemoryModule.RecordCorrectionCommand(
            "A01",
            List.of("光谷大道 1 号"),
            List.of(1001L, 1002L),
            List.of(1002L, 1001L),
            "这个地址在 A 区午餐要后置",
            "A 区高峰期先写字楼后住宅",
            "admin"
        ));

        assertEquals(11L, correctionId);
        assertEquals(1, module.loadRoutingMemory("A01", "LUNCH").size());
        assertTrue(module.loadRoutingMemory("B01", "LUNCH").isEmpty());
    }

    @Test
    void mergeMemoryUpdatesExistingPreference() {
        when(jdbcTemplate.queryForList(anyString(), eq(99L)))
            .thenReturn(List.of(Map.of(
                "area_code", "A01",
                "merchant_instruction", "这个地址虽然近，但午餐要后置",
                "merchant_reason_summary", "午餐高峰先写字楼再收住宅"
            )));
        when(jdbcTemplate.queryForList(anyString(), eq("A01"), eq("午餐高峰先写字楼再收住宅")))
            .thenReturn(List.of(Map.of(
                "id", 7L,
                "source_correction_ids", "[88]",
                "weight", 2
            )));
        when(jdbcTemplate.update(anyString(), any(), any(), any(), any())).thenReturn(1);

        AreaMemoryModule.MergeMemoryResult result = module.mergeMemory(99L);

        assertEquals(7L, result.memoryId());
        assertFalse(result.created());
        assertEquals("午餐高峰先写字楼再收住宅", result.summary());
    }
}
