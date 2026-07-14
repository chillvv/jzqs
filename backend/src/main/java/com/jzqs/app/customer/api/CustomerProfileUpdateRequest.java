package com.jzqs.app.customer.api;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomerProfileUpdateRequest(
    String name,
    String phone,
    @JsonAlias("remark") String merchantRemark,
    String customerStatus,
    String openedAt,
    String expiredAt,
    Integer remainingValidityDays,
    String defaultUserRemark,
    Boolean priorityCustomer,
    String priorityTag,
    String priorityNote
) {
}
