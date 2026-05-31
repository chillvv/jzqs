package com.jzqs.app.menu.service;

import com.jzqs.app.menu.api.MenuWeekAdminResponse;
import com.jzqs.app.menu.api.MenuWeekDaySaveRequest;
import java.util.Map;

public interface MenuWeekAdminService {
    MenuWeekAdminResponse currentWeek();

    MenuWeekAdminResponse weekByDate(String targetDate);

    Map<String, Object> createNextWeekTemplate(String operatorName);

    Map<String, Object> saveDay(long weekId, String serveDate, MenuWeekDaySaveRequest request);

    Map<String, Object> publish(long weekId, String operatorName);

    Map<String, Object> copyFromLastWeek(String operatorName);
}
