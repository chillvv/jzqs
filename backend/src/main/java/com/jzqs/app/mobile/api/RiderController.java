package com.jzqs.app.mobile.api;

import com.jzqs.app.common.api.ApiResponse;
import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.mobile.MobileAuthService;
import com.jzqs.app.mobile.MobilePortalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 骑手 API 统一入口
 * 提供 RESTful 风格的 API，符合重构文档规范
 * 
 * @author Kiro AI
 * @since 2026-05-22
 */
@RestController
@RequestMapping("/api/rider")
@Tag(name = "骑手接口", description = "骑手小程序相关接口")
public class RiderController {
    
    private final MobileAuthService mobileAuthService;
    private final MobilePortalService mobilePortalService;

    public RiderController(
        MobileAuthService mobileAuthService,
        MobilePortalService mobilePortalService
    ) {
        this.mobileAuthService = mobileAuthService;
        this.mobilePortalService = mobilePortalService;
    }

    // ==================== 认证相关 ====================

    /**
     * 骑手注册
     * 首次使用：手机号+姓名注册，自动绑定微信
     */
    @PostMapping("/register")
    @Operation(summary = "骑手注册", description = "首次使用，手机号+姓名注册，自动绑定微信")
    public ApiResponse<RiderLoginResponse> register(
        @Valid @RequestBody RiderRegisterRequest request
    ) {
        return ApiResponse.success(
            mobileAuthService.riderRegister(request.phone(), request.name(), request.openid())
        );
    }

    /**
     * 骑手微信一键登录
     * 通过微信获取手机号并登录
     */
    @PostMapping("/wechat-login")
    @Operation(summary = "微信一键登录", description = "通过微信获取手机号并登录")
    public ApiResponse<RiderLoginResponse> wechatLogin(
        @Valid @RequestBody RiderWechatLoginRequest request
    ) {
        return ApiResponse.success(
            mobileAuthService.riderWechatLogin(
                request.openid(),
                request.code()
            )
        );
    }

    /**
     * 骑手手机号登录
     * 已注册用户通过手机号登录
     */
    @PostMapping("/phone-login")
    @Operation(summary = "手机号登录", description = "已注册用户通过手机号登录")
    public ApiResponse<RiderLoginResponse> phoneLogin(
        @Valid @RequestBody RiderPhoneLoginRequest request
    ) {
        return ApiResponse.success(
            mobileAuthService.riderPhoneLogin(request.phone(), request.openid())
        );
    }

    /**
     * 骑手登录（混合模式）
     * @deprecated 使用 register 或 phoneLogin 替代
     */
    @PostMapping("/login")
    @Operation(summary = "骑手登录（已废弃）", description = "请使用 /register 或 /phone-login")
    public ApiResponse<RiderLoginResponse> login(
        @Valid @RequestBody RiderLoginRequest request
    ) {
        return ApiResponse.success(
            mobileAuthService.riderMixedLogin(request.phone(), request.name(), request.openid())
        );
    }

    /**
     * 获取骑手个人信息
     */
    @GetMapping("/me")
    @Operation(summary = "获取个人信息", description = "获取当前登录骑手的个人信息")
    public ApiResponse<RiderAuthProfileResponse> me(
        @Parameter(description = "骑手姓名", required = true)
        @RequestParam String riderName
    ) {
        return ApiResponse.success(mobileAuthService.riderProfile(riderName));
    }

    // ==================== 订单相关 ====================

    /**
     * 获取订单列表
     * 支持按餐期筛选：lunch（午餐）、dinner（晚餐）
     */
    @GetMapping("/orders")
    @Operation(summary = "获取订单列表", description = "获取骑手的配送订单列表，支持按餐期筛选")
    public ApiResponse<PageResponse<RiderQueueItemResponse>> getOrders(
        @Parameter(description = "骑手姓名", required = true)
        @RequestParam String riderName,
        @Parameter(description = "餐期类型：lunch 或 dinner")
        @RequestParam(required = false) String mealType
    ) {
        return ApiResponse.success(mobilePortalService.riderQueue(riderName));
    }

    /**
     * 获取订单详情
     */
    @GetMapping("/orders/{orderId}")
    @Operation(summary = "获取订单详情", description = "获取指定订单的详细信息")
    public ApiResponse<RiderQueueItemResponse> getOrderDetail(
        @Parameter(description = "订单ID（batchItemId）", required = true)
        @PathVariable long orderId,
        @Parameter(description = "骑手姓名", required = true)
        @RequestParam String riderName
    ) {
        return ApiResponse.success(mobilePortalService.riderQueueItem(orderId, riderName));
    }

    @GetMapping("/address-reference")
    @Operation(summary = "获取地址参考图", description = "按地址记录查询当前生效的参考图")
    public ApiResponse<RiderAddressReferenceResponse> getAddressReference(
        @Parameter(description = "骑手姓名", required = true)
        @RequestParam String riderName,
        @Parameter(description = "地址记录ID", required = true)
        @RequestParam long addressId
    ) {
        return ApiResponse.success(mobilePortalService.riderAddressReference(riderName, addressId));
    }

    @PostMapping("/address-reference/batch")
    @Operation(summary = "批量设置地址参考图", description = "为多个地址记录批量绑定同一张参考图")
    public ApiResponse<Map<String, Object>> saveBatchAddressReferenceImage(
        @Parameter(description = "骑手姓名", required = true)
        @RequestParam String riderName,
        @Valid @RequestBody RiderBatchAddressReferenceRequest request
    ) {
        return ApiResponse.success(mobilePortalService.saveBatchAddressReferenceImage(riderName, request));
    }

    @PostMapping("/address-reference/{addressId}")
    @Operation(summary = "更新单个地址参考图", description = "将当前图片设置为指定地址的参考图")
    public ApiResponse<Map<String, Object>> replaceAddressReferenceImage(
        @Parameter(description = "地址记录ID", required = true)
        @PathVariable long addressId,
        @Parameter(description = "骑手姓名", required = true)
        @RequestParam String riderName,
        @Valid @RequestBody RiderAddressReferenceUpdateRequest request
    ) {
        return ApiResponse.success(
            mobilePortalService.replaceAddressReferenceImage(riderName, addressId, request.referenceImageUrl())
        );
    }

    /**
     * 标记订单完成
     */
    @PostMapping("/orders/{orderId}/complete")
    @Operation(summary = "标记订单完成", description = "提交送达回执，标记订单为已完成")
    public ApiResponse<Map<String, Object>> completeOrder(
        @Parameter(description = "订单ID", required = true)
        @PathVariable long orderId,
        @Parameter(description = "骑手姓名", required = true)
        @RequestParam String riderName,
        @Valid @RequestBody RiderReceiptRequest request
    ) {
        return ApiResponse.success(mobilePortalService.submitRiderReceipt(
            orderId,
            riderName,
            request.receiptFileKey(),
            request.receiptNote(),
            request.deliveredAt()
        ));
    }

    /**
     * 撤回订单状态
     * 将已完成的订单恢复为待配送状态
     */
    @PostMapping("/orders/{orderId}/revert")
    @Operation(summary = "撤回订单状态", description = "将已完成的订单恢复为待配送状态")
    public ApiResponse<Map<String, Object>> revertOrder(
        @Parameter(description = "订单ID", required = true)
        @PathVariable long orderId,
        @Parameter(description = "骑手姓名", required = true)
        @RequestParam String riderName
    ) {
        return ApiResponse.success(mobilePortalService.revertOrderStatus(orderId, riderName));
    }

    /**
     * 更新订单排序
     * 保存骑手自定义的配送顺序
     */
    @PostMapping("/orders/reorder")
    @Operation(summary = "更新订单排序", description = "保存骑手自定义的配送顺序")
    public ApiResponse<Map<String, Object>> reorderOrders(
        @Parameter(description = "骑手姓名", required = true)
        @RequestParam String riderName,
        @Valid @RequestBody RiderReorderRequest request
    ) {
        return ApiResponse.success(mobilePortalService.reorderRiderQueue(riderName, request.batchItemIds()));
    }

    // ==================== 文件上传 ====================

    @PostMapping(value = "/uploads/receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传送达图片", description = "上传骑手回执图片到本地服务器存储")
    public ApiResponse<RiderDeliveryUploadResponse> uploadReceipt(
        @Parameter(description = "骑手姓名", required = true)
        @RequestParam String riderName,
        @RequestPart("file") MultipartFile file
    ) {
        return ApiResponse.success(mobilePortalService.uploadRiderReceipt(riderName, file));
    }

    /**
     * 更新送达照片
     * 允许骑手重新编辑已提交的回执照片
     */
    @PutMapping("/orders/{orderId}/receipt")
    @Operation(summary = "更新送达照片", description = "重新编辑已提交的回执照片和备注")
    public ApiResponse<Map<String, Object>> updateReceipt(
        @Parameter(description = "订单ID", required = true)
        @PathVariable long orderId,
        @Parameter(description = "骑手姓名", required = true)
        @RequestParam String riderName,
        @Valid @RequestBody RiderReceiptUpdateRequest request
    ) {
        return ApiResponse.success(mobilePortalService.updateRiderReceipt(
            orderId,
            riderName,
            request.receiptFileKey(),
            request.receiptNote(),
            request.deliveredAt()
        ));
    }

    /**
     * 删除送达照片
     * 删除已提交的回执照片（保留回执记录，仅清空照片URL）
     */
    @DeleteMapping("/orders/{orderId}/receipt-image")
    @Operation(summary = "删除送达照片", description = "删除已提交的回执照片")
    public ApiResponse<Map<String, Object>> deleteReceiptImage(
        @Parameter(description = "订单ID", required = true)
        @PathVariable long orderId,
        @Parameter(description = "骑手姓名", required = true)
        @RequestParam String riderName
    ) {
        return ApiResponse.success(mobilePortalService.deleteRiderReceiptImage(orderId, riderName));
    }

    // ==================== 其他功能 ====================

    /**
     * 获取今日配送概览
     */
    @GetMapping("/summary")
    @Operation(summary = "获取配送概览", description = "获取今日配送任务的统计信息")
    public ApiResponse<RiderBatchSummaryResponse> getSummary(
        @Parameter(description = "骑手姓名", required = true)
        @RequestParam String riderName
    ) {
        return ApiResponse.success(mobilePortalService.riderSummary(riderName));
    }

    /**
     * 上报配送异常
     */
    @PostMapping("/orders/{orderId}/report-exception")
    @Operation(summary = "上报配送异常", description = "上报配送过程中遇到的异常情况")
    public ApiResponse<Map<String, Object>> reportException(
        @Parameter(description = "订单ID", required = true)
        @PathVariable long orderId,
        @Parameter(description = "骑手姓名", required = true)
        @RequestParam String riderName,
        @Valid @RequestBody RiderExceptionReportRequest request
    ) {
        return ApiResponse.success(mobilePortalService.reportDeliveryException(
            orderId,
            riderName,
            request.exceptionType(),
            request.exceptionNote(),
            request.exceptionImages()
        ));
    }
}
