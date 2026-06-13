package com.jzqs.app.dispatch.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DispatchCreateRiderRequest(
    @NotBlank
    @Size(min = 2, max = 20, message = "姓名长度需在2到20个字符之间")
    @Pattern(regexp = "^[\\u4e00-\\u9fa5A-Za-z·\\s]{2,20}$", message = "请输入正确的骑手姓名")
    String riderName,
    @NotBlank
    @Size(min = 2, max = 20, message = "姓名长度需在2到20个字符之间")
    @Pattern(regexp = "^[\\u4e00-\\u9fa5A-Za-z·\\s]{2,20}$", message = "请输入正确的骑手姓名")
    String displayName,
    @NotBlank
    @Pattern(regexp = "^1\\d{10}$", message = "请输入正确的11位手机号")
    String phone,
    String areaCode,
    String employmentStatus,
    String updatedBy
) {
}
