package com.jzqs.app.customer.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomerAddressUpsertRequest(
    String contactName,
    String contactPhone,
    String addressLine,
    String areaCode,
    Boolean isDefault
) {
}
