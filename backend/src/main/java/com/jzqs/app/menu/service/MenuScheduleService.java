package com.jzqs.app.menu.service;

import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.menu.api.MenuScheduleResponse;
import java.util.Map;

public interface MenuScheduleService {
    PageResponse<MenuScheduleResponse> list();

    Map<String, Object> create(String serveDate, String mealPeriod, String mealName, String mealDetail, int calories, String merchantNote);

    Map<String, Object> update(long id, String serveDate, String mealPeriod, String mealName, String mealDetail, int calories, String merchantNote);

    Map<String, Object> disable(long id);
}
