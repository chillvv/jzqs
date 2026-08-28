package com.jzqs.app.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jzqs.app.common.api.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
class AuthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthController authController;

    @MockBean
    private AuthService authService;

    @Test
    void shouldRegisterRiderPhoneThroughUnifiedAuthApi() throws Exception {
        given(authService.registerPhone(eq("13800000009"), eq("骑手小李"), eq("openid-1"), eq("rider")))
            .willReturn(new AuthBindPhoneResponse(
                "token-r1",
                9L,
                "rider",
                "138****0009",
                "骑手小李",
                "PENDING",
                false
            ));

        mockMvc.perform(post("/api/auth/register-phone")
                .contentType("application/json")
                .content("""
                    {
                      "phone": "13800000009",
                      "nickname": "骑手小李",
                      "openid": "openid-1",
                      "userType": "rider"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.userType").value("rider"))
            .andExpect(jsonPath("$.data.riderName").value("骑手小李"))
            .andExpect(jsonPath("$.data.riderStatus").value("PENDING"))
            .andExpect(jsonPath("$.data.workbenchEnabled").value(false));
    }

    @Test
    void shouldLoginRiderPhoneThroughUnifiedAuthApi() throws Exception {
        given(authService.loginByPhone(eq("13800000009"), eq("openid-1"), eq("rider")))
            .willReturn(new AuthBindPhoneResponse(
                "token-r2",
                9L,
                "rider",
                "138****0009",
                "骑手小李",
                "ACTIVE",
                true
            ));

        mockMvc.perform(post("/api/auth/phone-login")
                .contentType("application/json")
                .content("""
                    {
                      "phone": "13800000009",
                      "openid": "openid-1",
                      "userType": "rider"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.userType").value("rider"))
            .andExpect(jsonPath("$.data.riderStatus").value("ACTIVE"))
            .andExpect(jsonPath("$.data.workbenchEnabled").value(true));
    }

    @Test
    void shouldReturnTypedLogoutResponse() {
        ApiResponse<AuthLogoutResponse> response = authController.logout("Bearer token-1");

        assertEquals("退出登录成功", response.data().message());
    }
}
