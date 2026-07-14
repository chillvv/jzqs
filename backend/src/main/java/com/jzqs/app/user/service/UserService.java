package com.jzqs.app.user.service;

import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.user.model.dto.UserCreateRequest;
import com.jzqs.app.user.model.dto.UserQueryRequest;
import com.jzqs.app.user.model.dto.UserUpdateRequest;
import com.jzqs.app.user.model.vo.UserDetailResponse;
import com.jzqs.app.user.model.vo.UserItemResponse;

public interface UserService {
    PageResponse<UserItemResponse> page(UserQueryRequest queryRequest);

    UserDetailResponse detail(Long userId);

    UserDetailResponse create(UserCreateRequest request);

    UserDetailResponse update(Long userId, UserUpdateRequest request);
}
