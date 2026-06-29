package com.jzqs.app.menu.service;

import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.menu.api.MenuScheduleResponse;
import com.jzqs.app.menu.api.MenuScheduleStatusResponse;
import com.jzqs.app.menu.api.MenuScheduleUpsertResponse;

public interface MenuScheduleService {
    PageResponse<MenuScheduleResponse> list();

    MenuScheduleUpsertResponse create(
        String serveDate,
        String mealPeriod,
        String mealName,
        String mealDetail,
        int calories,
        String merchantNote
    );

    MenuScheduleUpsertResponse update(
        long id,
        String serveDate,
        String mealPeriod,
        String mealName,
        String mealDetail,
        int calories,
        String merchantNote
    );

    MenuScheduleStatusResponse disable(long id);
}
