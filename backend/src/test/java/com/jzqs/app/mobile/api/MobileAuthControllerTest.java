package com.jzqs.app.mobile.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jzqs.app.mobile.MobileAuthService;
import java.util.Map;
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
    private MobileAuthService mobileAuthService;

    @Test
    void shouldReturnRegisterStateForUnregisteredWechatUser() throws Exception {
        given(mobileAuthService.wxLogin(eq("test-code"))).willReturn(
            Map.of(
                "authMode", "DEV_SIMULATION",
                "registered", false,
                "needPhoneAuth", true,
                "needName", false,
                "openid", "dev_test"
            )
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
            Map.of(
                "authMode", "DEV_SIMULATION",
                "registered", true,
                "needPhoneAuth", false,
                "needName", false,
                "openid", "dev_test",
                "token", "token_1_mock",
                "customerId", 1L
            )
        );

        mockMvc.perform(post("/api/mobile/auth/bind-phone")
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
            Map.of(
                "authMode", "DEV_SIMULATION",
                "registered", true,
                "needPhoneAuth", false,
                "needName", false,
                "openid", "dev_test",
                "token", "token_1_mock",
                "customerId", 1L
            )
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
            Map.of(
                "authMode", "DEV_SIMULATION",
                "registered", true,
                "needPhoneAuth", false,
                "needName", false,
                "token", "token_1_mock",
                "customerId", 1L
            )
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
}
