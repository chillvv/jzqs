package com.jzqs.app.mobile.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jzqs.app.mobile.MobileAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(MobileRiderAuthController.class)
class MobileRiderAuthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @MockBean
    private MobileAuthService mobileAuthService;

    @Test
    void shouldReturnUnregisteredRiderWechatState() throws Exception {
        given(mobileAuthService.riderWxLogin(eq("test-code"))).willReturn(new RiderAuthStateResponse(
            "DEV_SIMULATION", "rider_test", false, true, null, null, null, null, null, "UNAUTHORIZED", false, null, null, null
        ));

        mockMvc.perform(post("/api/mobile/rider-auth/wx-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "code": "test-code"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.registered").value(false))
            .andExpect(jsonPath("$.data.needPhoneAuth").value(true))
            .andExpect(jsonPath("$.data.riderStatus").value("UNAUTHORIZED"));
    }

    @Test
    void shouldBindRiderPhone() throws Exception {
        given(mobileAuthService.bindRiderPhone(eq("rider_test"), eq("13800000009"), eq("骑手小李"))).willReturn(new RiderAuthStateResponse(
            "DEV_SIMULATION", "rider_test", true, false, 9L, "骑手小李", "骑手小李", "13800000009", null, "UNASSIGNED", false, null, null, "token_rider"
        ));

        mockMvc.perform(post("/api/mobile/rider-auth/bind-phone")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "openid": "rider_test",
                      "phone": "13800000009",
                      "nickname": "骑手小李"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.registered").value(true))
            .andExpect(jsonPath("$.data.riderStatus").value("UNASSIGNED"))
            .andExpect(jsonPath("$.data.phone").value("13800000009"));
    }

    @Test
    void shouldVerifyRiderToken() throws Exception {
        given(mobileAuthService.verifyRiderToken(eq("rider-token"))).willReturn(new RiderTokenVerifyResponse(
            "rider_test",
            9L,
            "骑手小李",
            "骑手小李",
            "13800000009",
            "高新区",
            "ACTIVE",
            true,
            "2026-05-15 20:00:00",
            "2026-05-15 20:05:00"
        ));

        mockMvc.perform(post("/api/mobile/rider-auth/verify-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "token": "rider-token"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.riderId").value(9L))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"))
            .andExpect(jsonPath("$.data.workbenchEnabled").value(true));
    }

    @Test
    void shouldReturnRiderProfile() throws Exception {
        given(mobileAuthService.riderProfile(eq("骑手小李"))).willReturn(new RiderAuthProfileResponse(
            1L,
            "骑手小李",
            "骑手小李",
            "13800000009",
            "高新区",
            "ACTIVE",
            true,
            "2026-05-15T20:00:00",
            "2026-05-15T20:05:00"
        ));

        mockMvc.perform(get("/api/mobile/rider-auth/me").requestAttr("riderName", "骑手小李"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.riderStatus").value("ACTIVE"))
            .andExpect(jsonPath("$.data.workbenchEnabled").value(true))
            .andExpect(jsonPath("$.data.areaCode").value("高新区"));
    }
}
