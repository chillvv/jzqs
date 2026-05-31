package com.jzqs.app.user.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.user.model.dto.UserCreateRequest;
import com.jzqs.app.user.model.vo.UserDetailResponse;
import com.jzqs.app.user.model.vo.UserItemResponse;
import com.jzqs.app.user.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminUserController.class)
class AdminUserControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @Test
    void shouldReturnUserPage() throws Exception {
        given(userService.page(any())).willReturn(PageResponse.of(List.of(
            new UserItemResponse(1L, "admin", "系统管理员", "13800000000", "ADMIN", "ENABLED"),
            new UserItemResponse(2L, "ops01", "运营专员", "13800000011", "OPERATOR", "ENABLED")
        ), 1, 20, 2));

        mockMvc.perform(get("/api/admin/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.page").value(1))
            .andExpect(jsonPath("$.data.items.length()").value(2))
            .andExpect(jsonPath("$.data.items[0].username").value("admin"));
    }

    @Test
    void shouldCreateUser() throws Exception {
        UserCreateRequest request = new UserCreateRequest("ops02", "运营2号", "13800000033", "OPERATOR");
        given(userService.create(any())).willReturn(new UserDetailResponse(
            4L, "ops02", "运营2号", "13800000033", "OPERATOR", "ENABLED", "2026-05-11 12:00:00", "2026-05-11 12:00:00"
        ));

        mockMvc.perform(post("/api/admin/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value("OK"))
            .andExpect(jsonPath("$.data.id").value(4))
            .andExpect(jsonPath("$.data.username").value("ops02"));
    }
}
