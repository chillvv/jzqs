package com.jzqs.app.common.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jzqs.app.common.util.JwtClaims;
import com.jzqs.app.common.util.JwtUtils;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class AuthFilterTest {

    private final AuthFilter filter = new AuthFilter(new ObjectMapper());

    @Test
    void injectsCustomerIdForProtectedCustomerRoutes() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/mobile/customer/subscription-rule");
        request.addHeader("Authorization", "Bearer " + JwtUtils.generateToken(JwtClaims.customer(42L, "openid-42")));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(request.getAttribute("customerId")).isEqualTo(42L);
        assertThat(request.getAttribute("userId")).isEqualTo(42L);
        assertThat(request.getAttribute("userType")).isEqualTo("customer");
    }

    @Test
    void verifyEndpointRequiresAuthorizationHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/mobile/auth/verify");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("登录态已失效");
    }
}
