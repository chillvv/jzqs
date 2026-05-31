package com.jzqs.app.common.api;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.List;
import org.junit.jupiter.api.Test;
class ApiResponseTest {
    @Test
    void shouldBuildSuccessResponse() {
        ApiResponse<String> response = ApiResponse.success("ok");
        assertEquals("OK", response.code());
        assertEquals("success", response.message());
        assertEquals("ok", response.data());
    }
    @Test
    void shouldBuildPageResponse() {
        PageResponse<String> page = PageResponse.of(List.of("A", "B"), 1, 20, 2);
        assertEquals(2, page.items().size());
        assertEquals(1, page.page());
        assertEquals(20, page.pageSize());
        assertEquals(2, page.total());
    }
}
