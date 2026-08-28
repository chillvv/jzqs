package com.jzqs.app.common.error;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.validation.ConstraintViolationException;
import java.util.Set;
import com.jzqs.app.common.api.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsDuplicateKeyToConflictResponse() {
        ResponseEntity<ApiResponse<String>> response = handler.handleConflict(new DuplicateKeyException("duplicate"));

        assertEquals(409, response.getStatusCode().value());
        assertEquals("VALIDATION_ERROR", response.getBody().code());
    }

    @Test
    void mapsIllegalArgumentToValidationResponse() {
        ResponseEntity<ApiResponse<String>> response = handler.handleIllegalArgument(new IllegalArgumentException("bad argument"));

        assertEquals(400, response.getStatusCode().value());
        assertEquals("bad argument", response.getBody().message());
    }

    @Test
    void mapsUnexpectedExceptionToGenericServerError() {
        ResponseEntity<ApiResponse<String>> response = handler.handleUnexpected(new RuntimeException("boom"));

        assertEquals(500, response.getStatusCode().value());
        assertEquals("系统繁忙，请稍后重试", response.getBody().message());
    }

    @Test
    void mapsDataIntegrityViolationToConflictResponse() {
        ResponseEntity<ApiResponse<String>> response = handler.handleConflict(new DataIntegrityViolationException("constraint"));

        assertEquals(409, response.getStatusCode().value());
    }

    @Test
    void mapsConstraintViolationToValidationResponse() {
        ResponseEntity<ApiResponse<String>> response = handler.handleConstraintViolation(
            new ConstraintViolationException("bad request", Set.of())
        );

        assertEquals(400, response.getStatusCode().value());
        assertEquals("VALIDATION_ERROR", response.getBody().code());
    }

    @Test
    void mapsUnreadableJsonToValidationResponse() {
        ResponseEntity<ApiResponse<String>> response = handler.handleUnreadableJson(
            new HttpMessageNotReadableException("bad json")
        );

        assertEquals(400, response.getStatusCode().value());
        assertEquals("请求内容格式不正确，请检查后重试", response.getBody().message());
    }
}
