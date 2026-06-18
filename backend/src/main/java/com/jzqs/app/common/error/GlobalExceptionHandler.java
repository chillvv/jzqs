package com.jzqs.app.common.error;
import com.jzqs.app.common.api.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusiness(BusinessException ex) {
        if (ex.getErrorCode() == ErrorCode.UNAUTHORIZED) {
            return ResponseEntity.status(401)
                .body(ApiResponse.failure(ex.getErrorCode().name(), ex.getMessage(), ex.getData()));
        }
        return ResponseEntity.badRequest()
            .body(ApiResponse.failure(ex.getErrorCode().name(), ex.getMessage(), ex.getData()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<String>> handleValidation(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = fieldError == null ? "request validation failed" : fieldError.getField() + " " + fieldError.getDefaultMessage();
        return ResponseEntity.badRequest()
            .body(ApiResponse.failure(ErrorCode.VALIDATION_ERROR.name(), message));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<String>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(413)
            .body(ApiResponse.failure(ErrorCode.VALIDATION_ERROR.name(), "上传文件过大，请上传 5MB 以内的图片"));
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiResponse<String>> handleMultipart(MultipartException ex) {
        return ResponseEntity.badRequest()
            .body(ApiResponse.failure(ErrorCode.VALIDATION_ERROR.name(), "上传文件失败，请重新选择图片后重试"));
    }
}
