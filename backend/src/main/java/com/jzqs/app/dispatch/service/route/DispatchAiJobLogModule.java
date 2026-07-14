package com.jzqs.app.dispatch.service.route;

import com.jzqs.app.settings.api.DispatchAiJobLogResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public interface DispatchAiJobLogModule {
    DispatchAiJobLogResponse readLog(ResultSet resultSet) throws SQLException;

    void deleteLogsWithSuggestions(List<Long> ids);
}
