package com.jzqs.app.dispatch.api;

import com.jzqs.app.order.MealPeriod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DispatchCreateRiderRequest(
    MealPeriod mealPeriod,
    @NotBlank
    @Size(min = 2, max = 20, message = "姓名长度需在2到20个字符之间")
    @Pattern(regexp = "^[\\u4e00-\\u9fa5·]{2,20}$", message = "骑手姓名仅支持汉字（2-20个字符）")
    String riderName,
    @NotBlank
    @Size(min = 2, max = 20, message = "姓名长度需在2到20个字符之间")
    @Pattern(regexp = "^[\\u4e00-\\u9fa5·]{2,20}$", message = "骑手姓名仅支持汉字（2-20个字符）")
    String displayName,
    @NotBlank
    @Pattern(regexp = "^1\\d{10}$", message = "请输入正确的11位手机号")
    String phone,
    String areaCode,
    String employmentStatus,
    String updatedBy
) {
}
