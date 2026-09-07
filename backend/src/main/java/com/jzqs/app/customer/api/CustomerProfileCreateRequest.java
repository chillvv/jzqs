package com.jzqs.app.customer.api;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomerProfileCreateRequest(
    String name,
    String phone,
    @JsonAlias("remark") String merchantRemark,
    String customerStatus,
    String addressLine,
    BigDecimal latitude,
    BigDecimal longitude,
    Integer initialMealDelta,
    String initialMealRemark,
    Integer initialValidityDays,
    Boolean priorityCustomer,
    String priorityTag,
    String priorityNote,
    String doorNumber
) {
}
