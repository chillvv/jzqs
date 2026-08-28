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

@WebMvcTest(MobileAuthController.class)
class MobileAuthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @MockBean
    private MobileAuthService mobileAuthService;

    @Test
    void shouldReturnRegisterStateForUnregisteredWechatUser() throws Exception {
        given(mobileAuthService.wxLogin(eq("test-code"))).willReturn(
            new MobileAuthStateResponse("DEV_SIMULATION", "dev_test", "session_dev_test", false, true, false, null, null)
        );

        mockMvc.perform(post("/api/mobile/auth/wx-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "code": "test-code"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.authMode").value("DEV_SIMULATION"))
            .andExpect(jsonPath("$.data.registered").value(false))
            .andExpect(jsonPath("$.data.needPhoneAuth").value(true))
            .andExpect(jsonPath("$.data.needName").value(false));
    }

    @Test
    void shouldAcceptBindPhoneRequest() throws Exception {
        given(mobileAuthService.bindPhone(eq("dev_test"), eq("13800000001"), eq("林晓"))).willReturn(
            new MobileAuthStateResponse("DEV_SIMULATION", "dev_test", "session_dev_test", true, false, false, "token_1_mock", 1L)
        );

        mockMvc.perform(post("/api/mobile/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "openid": "dev_test",
                      "phone": "13800000001",
                      "nickname": "林晓"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.registered").value(true))
            .andExpect(jsonPath("$.data.needPhoneAuth").value(false))
            .andExpect(jsonPath("$.data.needName").value(false))
            .andExpect(jsonPath("$.data.token").value("token_1_mock"))
            .andExpect(jsonPath("$.data.customerId").value(1));
    }

    @Test
    void shouldAcceptDevPhoneRequest() throws Exception {
        given(mobileAuthService.bindDevPhone(eq("dev_test"), eq("13800000001"))).willReturn(
            new MobileAuthStateResponse("DEV_SIMULATION", "dev_test", "session_dev_test", true, false, false, "token_1_mock", 1L)
        );

        mockMvc.perform(post("/api/mobile/auth/dev-phone")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "openid": "dev_test",
                      "phone": "13800000001"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.registered").value(true))
            .andExpect(jsonPath("$.data.needPhoneAuth").value(false))
            .andExpect(jsonPath("$.data.needName").value(false))
            .andExpect(jsonPath("$.data.token").value("token_1_mock"))
            .andExpect(jsonPath("$.data.customerId").value(1));
    }

    @Test
    void shouldCompleteProfileAndReturnToken() throws Exception {
        given(mobileAuthService.completeProfile(eq("dev_test"), eq("林晓"))).willReturn(
            new MobileAuthStateResponse("DEV_SIMULATION", "dev_test", null, true, false, false, "token_1_mock", 1L)
        );

        mockMvc.perform(post("/api/mobile/auth/complete-profile")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "openid": "dev_test",
                      "nickname": "林晓"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.registered").value(true))
            .andExpect(jsonPath("$.data.token").value("token_1_mock"))
            .andExpect(jsonPath("$.data.customerId").value(1));
    }

    @Test
    void shouldVerifyToken() throws Exception {
        given(mobileAuthService.verify(eq("token_1_mock")))
            .willReturn(new MobileTokenVerifyResponse(true, 1L, "customer"));

        mockMvc.perform(get("/api/mobile/auth/verify").header("Authorization", "Bearer token_1_mock"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.valid").value(true))
            .andExpect(jsonPath("$.data.userId").value(1L))
            .andExpect(jsonPath("$.data.userType").value("customer"));
    }
}
