package com.jzqs.app.auth;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminAuthController.class)
class AdminAuthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminAuthService adminAuthService;

    @Test
    void shouldLoginWithAdminPhoneAndPassword() throws Exception {
        given(adminAuthService.login(eq("17671863805"), eq("17671863805")))
            .willReturn(new AdminAuthLoginResponse("token-1", 1L, "商家后台", "17671863805", "OWNER"));

        mockMvc.perform(post("/api/admin/auth/login")
                .contentType("application/json")
                .content("""
                    {
                      "phone": "17671863805",
                      "password": "17671863805"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.phone").value("17671863805"))
            .andExpect(jsonPath("$.data.token").value("token-1"))
            .andExpect(jsonPath("$.data.role").value("OWNER"));
    }

    @Test
    void shouldRejectCustomerPhoneLoginForAdminPortal() throws Exception {
        given(adminAuthService.login(eq("13800000001"), eq("13800000001")))
            .willThrow(new BusinessException(ErrorCode.UNAUTHORIZED, "手机号或密码错误"));

        mockMvc.perform(post("/api/admin/auth/login")
                .contentType("application/json")
                .content("""
                    {
                      "phone": "13800000001",
                      "password": "13800000001"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("手机号或密码错误"));
    }

    @Test
    void shouldReturnAdminProfileFromToken() throws Exception {
        given(adminAuthService.me(eq("token-1")))
            .willReturn(new AdminAuthProfileResponse(1L, "商家后台", "17671863805", "OWNER"));

        mockMvc.perform(get("/api/admin/auth/me")
                .header("Authorization", "Bearer token-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userId").value(1))
            .andExpect(jsonPath("$.data.phone").value("17671863805"))
            .andExpect(jsonPath("$.data.role").value("OWNER"));
    }

    @Test
    void shouldChangePasswordWithBearerToken() throws Exception {
        given(adminAuthService.changePassword(eq("token-1"), eq("17671863805"), eq("new-pass-123")))
            .willReturn(java.util.Map.of("status", "UPDATED"));

        mockMvc.perform(post("/api/admin/auth/change-password")
                .header("Authorization", "Bearer token-1")
                .contentType("application/json")
                .content("""
                    {
                      "oldPassword": "17671863805",
                      "newPassword": "new-pass-123"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("UPDATED"));
    }

    @Test
    void shouldLogoutWithBearerToken() throws Exception {
        given(adminAuthService.logout(eq("token-1")))
            .willReturn(java.util.Map.of("status", "LOGGED_OUT"));

        mockMvc.perform(post("/api/admin/auth/logout")
                .header("Authorization", "Bearer token-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("LOGGED_OUT"));
    }
}
