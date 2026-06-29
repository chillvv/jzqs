package com.jzqs.app.common.error;
import com.jzqs.app.common.api.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Object>> handleBusiness(BusinessException ex) {
        return ResponseEntity.status(resolveBusinessStatus(ex.getErrorCode()))
            .body(ApiResponse.failure(ex.getErrorCode().name(), ex.getMessage(), ex.getData()));
    }

    private HttpStatus resolveBusinessStatus(ErrorCode errorCode) {
        if (errorCode == null) {
            return HttpStatus.BAD_REQUEST;
        }
        return switch (errorCode) {
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case USER_NOT_FOUND,
                 CUSTOMER_NOT_FOUND,
                 PACKAGE_NOT_FOUND,
                 MENU_NOT_FOUND,
                 ORDER_NOT_FOUND,
                 RIDER_TASK_NOT_FOUND,
                 SUBSCRIPTION_CONFIRMATION_NOT_FOUND,
                 AFTERSALE_NOT_FOUND,
                 RIDER_NOT_FOUND,
                 SUBSCRIPTION_RULE_NOT_FOUND,
                 ADDRESS_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DISPATCH_AREA_HAS_ACTIVE_ORDERS,
                 ALREADY_ORDERED,
                 USERNAME_ALREADY_EXISTS,
                 REPEAT_SUBMISSION -> HttpStatus.CONFLICT;
            case WALLET_BALANCE_NOT_ENOUGH,
                 ORDERING_DISABLED,
                 ORDER_LOCKED,
                 ORDER_STATUS_INVALID,
                 DELIVERY_RECEIPT_REQUIRED,
                 INSUFFICIENT_MEALS,
                 NOT_ORDERABLE_STATE,
                 CUSTOMER_STATUS_INVALID,
                 COST_ENTRY_INVALID,
                 ORDER_NOTE_INVALID,
                 WX_PHONE_AUTH_REQUIRED,
                 INVALID_DATE_RANGE,
                 NO_MEAL_ENABLED -> HttpStatus.UNPROCESSABLE_ENTITY;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;
        };
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<String>> handleValidation(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = fieldError == null ? "请求参数校验失败" : fieldError.getField() + " " + fieldError.getDefaultMessage();
        return ResponseEntity.badRequest()
            .body(ApiResponse.failure(ErrorCode.VALIDATION_ERROR.name(), message));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<String>> handleBind(BindException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = fieldError == null ? "请求参数校验失败" : fieldError.getField() + " " + fieldError.getDefaultMessage();
        return ResponseEntity.badRequest()
            .body(ApiResponse.failure(ErrorCode.VALIDATION_ERROR.name(), message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<String>> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
            .findFirst()
            .map(ConstraintViolation::getMessage)
            .orElse("请求参数校验失败");
        return ResponseEntity.badRequest()
            .body(ApiResponse.failure(ErrorCode.VALIDATION_ERROR.name(), message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<String>> handleUnreadableJson(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
            .body(ApiResponse.failure(ErrorCode.VALIDATION_ERROR.name(), "请求内容格式不正确，请检查后重试"));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<String>> handleMissingRequestParameter(MissingServletRequestParameterException ex) {
        return ResponseEntity.badRequest()
            .body(ApiResponse.failure(ErrorCode.VALIDATION_ERROR.name(), ex.getParameterName() + "不能为空"));
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

    @ExceptionHandler({DuplicateKeyException.class, DataIntegrityViolationException.class})
    public ResponseEntity<ApiResponse<String>> handleConflict(Exception ex) {
        return ResponseEntity.status(409)
            .body(ApiResponse.failure(ErrorCode.VALIDATION_ERROR.name(), "数据冲突，请刷新后重试"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<String>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
            .body(ApiResponse.failure(ErrorCode.VALIDATION_ERROR.name(), ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<String>> handleUnexpected(Exception ex) {
        log.error("Unhandled application exception", ex);
        return ResponseEntity.status(500)
            .body(ApiResponse.failure("INTERNAL_SERVER_ERROR", "系统繁忙，请稍后重试"));
    }
}
