package com.jzqs.app.customer.api;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomerProfileCreateRequest(
    String name,
    String phone,
    @JsonAlias("remark") String merchantRemark,
    String customerStatus,
    String addressLine,
    Integer initialMealDelta,
    String initialMealRemark,
    Integer initialValidityDays,
    Boolean priorityCustomer,
    String priorityTag,
    String priorityNote
) {
}
