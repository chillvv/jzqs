package com.jzqs.app.menu.service;

import com.jzqs.app.menu.api.MenuWeekAdminResponse;
import com.jzqs.app.menu.api.MenuWeekCopyResponse;
import com.jzqs.app.menu.api.MenuWeekDaySaveRequest;
import com.jzqs.app.menu.api.MenuWeekDaySaveResponse;
import com.jzqs.app.menu.api.MenuWeekPublishResponse;
import com.jzqs.app.menu.api.MenuWeekTemplateResponse;

public interface MenuWeekAdminService {
    MenuWeekAdminResponse currentWeek();

    MenuWeekAdminResponse weekByDate(String targetDate);

    MenuWeekTemplateResponse createNextWeekTemplate(String operatorName);

    MenuWeekDaySaveResponse saveDay(long weekId, String serveDate, MenuWeekDaySaveRequest request);

    MenuWeekPublishResponse publish(long weekId, String operatorName);

    MenuWeekCopyResponse copyFromLastWeek(String operatorName);
}
