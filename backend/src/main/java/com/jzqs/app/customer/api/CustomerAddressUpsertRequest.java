package com.jzqs.app.customer.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomerAddressUpsertRequest(
    String contactName,
    String contactPhone,
    String addressLine,
    String areaCode,
    Boolean isDefault,
    BigDecimal latitude,
    BigDecimal longitude
) {
}
