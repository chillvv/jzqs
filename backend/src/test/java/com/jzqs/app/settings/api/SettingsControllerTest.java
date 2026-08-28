package com.jzqs.app.settings.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jzqs.app.common.aop.aspect.AuditActionAspect;
import com.jzqs.app.common.aop.aspect.IdempotentAspect;
import com.jzqs.app.common.aop.aspect.RateLimitAspect;
import com.jzqs.app.common.aop.store.TestIdempotencyStore;
import com.jzqs.app.common.aop.store.InMemoryRateLimitStore;
import com.jzqs.app.settings.service.SettingsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(SettingsController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({
    RateLimitAspect.class,
    IdempotentAspect.class,
    AuditActionAspect.class,
    InMemoryRateLimitStore.class,
    TestIdempotencyStore.class
})
class SettingsControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private InMemoryRateLimitStore inMemoryRateLimitStore;

    @Autowired
    private TestIdempotencyStore testIdempotencyStore;

    @MockBean
    private SettingsService settingsService;

    @AfterEach
    void tearDown() {
        inMemoryRateLimitStore.clear();
    }

    private RequestPostProcessor admin() {
        return request -> {
            request.setAttribute("userId", 7L);
            request.setAttribute("userType", "admin");
            request.setAttribute("adminDisplayName", "运营A");
            return request;
        };
    }

    @Test
    void shouldUpdateOrderingToggleThroughService() throws Exception {
        OperationSettingsResponse response = new OperationSettingsResponse(
            false,
            "暂停接单中",
            "节假日公告",
            "店休一天",
            "恢复：重新开启接单",
            "[]",
            3,
            7,
            3,
            false,
            false,
            "11:30",
            "17:30",
            false,
            "",
            "",
            "",
            false,
            "",
            "",
            "",
            ""
        );
        given(settingsService.updateOrderingEnabled(false)).willReturn(response);

        mockMvc.perform(post("/api/admin/settings/ordering-toggle")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "enabled": false
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orderingEnabled").value(false))
            .andExpect(jsonPath("$.data.orderingStatusLabel").value("暂停接单中"));

        verify(settingsService).updateOrderingEnabled(false);
    }

    @Test
    void shouldUpdateBannerImagesThroughService() throws Exception {
        OperationSettingsResponse response = new OperationSettingsResponse(
            true,
            "通道开启中",
            "",
            "",
            "熔断：一键暂停接单 (假期店休使用)",
            "[{\"imageUrl\":\"/uploads/settings-banners/banner.jpg\",\"enabled\":true}]",
            5,
            7,
            3,
            false,
            false,
            "11:30",
            "17:30",
            false,
            "",
            "",
            "",
            false,
            "",
            "",
            "",
            ""
        );
        given(settingsService.updateBannerImages(
            eq("[\"/uploads/settings-banners/banner.jpg\"]"),
            eq(5)
        )).willReturn(response);

        mockMvc.perform(post("/api/admin/settings/banner-images")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "bannerImages": "[\\\"/uploads/settings-banners/banner.jpg\\\"]",
                      "bannerIntervalSeconds": 5
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.bannerIntervalSeconds").value(5));

        verify(settingsService).updateBannerImages("[\"/uploads/settings-banners/banner.jpg\"]", 5);
    }

    @Test
    void shouldPauseOrderingWithNoticeThroughService() throws Exception {
        OperationSettingsResponse response = new OperationSettingsResponse(
            false,
            "暂停接单中",
            "临时停业",
            "今天店休",
            "恢复：重新开启接单",
            "[]",
            3,
            7,
            3,
            true,
            false,
            "11:30",
            "17:30",
            true,
            "临时停业\n今天店休",
            "",
            "",
            true,
            "临时停业\n今天店休",
            "",
            "",
            ""
        );
        given(settingsService.pauseOrderingWithNotice(
            eq("临时停业"),
            eq("今天店休"),
            eq(true),
            eq("临时停业\n今天店休")
        )).willReturn(response);

        mockMvc.perform(post("/api/admin/settings/ordering/pause-with-notice")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "临时停业",
                      "description": "今天店休",
                      "popupEnabled": true,
                      "popupContent": "临时停业\\n今天店休"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.orderingEnabled").value(false))
            .andExpect(jsonPath("$.data.popupAnnouncementEnabled").value(true));

        verify(settingsService).pauseOrderingWithNotice("临时停业", "今天店休", true, "临时停业\n今天店休");
    }

    @Test
    void shouldListDispatchAreaCodes() throws Exception {
        given(settingsService.listDispatchAreaCodes()).willReturn(new DispatchAreaCodeListResponse(
            java.util.List.of("A01", "B02", "C03")
        ));

        mockMvc.perform(get("/api/admin/settings/dispatch-area-codes"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.areaCodes[0]").value("A01"))
            .andExpect(jsonPath("$.data.areaCodes[1]").value("B02"))
            .andExpect(jsonPath("$.data.areaCodes[2]").value("C03"));
    }

    @Test
    void shouldListDispatchAreaMemories() throws Exception {
        given(settingsService.listDispatchAreaMemories("A01")).willReturn(new DispatchAreaMemoryListResponse(
            "A01",
            java.util.List.of(new DispatchAreaMemoryListResponse.AreaMemoryItem(
                1L,
                "ROUTE_PREFERENCE",
                "午餐先写字楼",
                "A 区午餐高峰先写字楼后住宅",
                "ALL",
                2,
                "ACTIVE",
                "2026-07-04 11:00:00"
            ))
        ));

        mockMvc.perform(get("/api/admin/settings/dispatch-area-memories")
                .param("areaCode", "A01"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.areaCode").value("A01"))
            .andExpect(jsonPath("$.data.items[0].title").value("午餐先写字楼"))
            .andExpect(jsonPath("$.data.items[0].status").value("ACTIVE"));
    }

    @Test
    void shouldUpdateDispatchAreaMemory() throws Exception {
        given(settingsService.updateDispatchAreaMemory(
            eq(1L),
            eq(new DispatchAreaMemoryUpdateRequest(
                "午餐先写字楼",
                "A 区午餐高峰先写字楼，再收住宅",
                "ALL",
                "ACTIVE"
            )),
            eq("运营A")
        )).willReturn(new DispatchAreaMemoryListResponse(
            "A01",
            java.util.List.of(new DispatchAreaMemoryListResponse.AreaMemoryItem(
                1L,
                "ROUTE_PREFERENCE",
                "午餐先写字楼",
                "A 区午餐高峰先写字楼，再收住宅",
                "ALL",
                2,
                "ACTIVE",
                "2026-07-04 11:05:00"
            ))
        ));

        mockMvc.perform(post("/api/admin/settings/dispatch-area-memories/1")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "午餐先写字楼",
                      "summary": "A 区午餐高峰先写字楼，再收住宅",
                      "applicableScene": "ALL",
                      "status": "ACTIVE"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.areaCode").value("A01"))
            .andExpect(jsonPath("$.data.items[0].summary").value("A 区午餐高峰先写字楼，再收住宅"));
    }

    @Test
    void shouldGetDispatchAreaMemorySources() throws Exception {
        given(settingsService.getDispatchAreaMemorySources(1L)).willReturn(new DispatchAreaMemorySourceListResponse(
            "A01",
            1L,
            "午餐先写字楼",
            java.util.List.of(new DispatchAreaMemorySourceListResponse.MemorySourceItem(
                88L,
                "MIXED",
                "午餐高峰先送写字楼",
                "A 区午餐先写字楼后住宅",
                "AI 已理解午餐先写字楼再收住宅",
                "SUCCESS",
                "2026-07-04 11:08:00"
            ))
        ));

        mockMvc.perform(get("/api/admin/settings/dispatch-area-memories/1/sources"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.memoryId").value(1))
            .andExpect(jsonPath("$.data.memoryTitle").value("午餐先写字楼"))
            .andExpect(jsonPath("$.data.items[0].correctionMode").value("MIXED"));
    }

    @Test
    void shouldDeleteDispatchAreaMemory() throws Exception {
        given(settingsService.deleteDispatchAreaMemory(eq(1L), eq("运营A"))).willReturn(new DispatchAreaMemoryListResponse(
            "A01",
            java.util.List.of()
        ));

        mockMvc.perform(post("/api/admin/settings/dispatch-area-memories/1/delete")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.areaCode").value("A01"))
            .andExpect(jsonPath("$.data.items").isArray());
    }
}
