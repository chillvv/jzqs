package com.jzqs.app.rider.service;

import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.rider.model.dto.RiderCreateRequest;
import com.jzqs.app.rider.model.dto.RiderQueryRequest;
import com.jzqs.app.rider.model.dto.RiderUpdateRequest;
import com.jzqs.app.rider.model.vo.RiderDetailResponse;
import com.jzqs.app.rider.model.vo.RiderItemResponse;

public interface RiderAdminService {
    PageResponse<RiderItemResponse> page(RiderQueryRequest queryRequest);

    RiderDetailResponse detail(Long riderId);

    RiderDetailResponse create(RiderCreateRequest request);

    RiderDetailResponse update(Long riderId, RiderUpdateRequest request);

    void delete(Long riderId);
}
