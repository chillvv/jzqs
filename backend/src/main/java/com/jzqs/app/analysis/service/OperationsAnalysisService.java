package com.jzqs.app.analysis.service;

import com.jzqs.app.analysis.api.AnalysisOverviewResponse;
import com.jzqs.app.analysis.api.CostEntryItem;
import java.util.List;
import java.util.Map;

public interface OperationsAnalysisService {
    AnalysisOverviewResponse overview(String date);

    List<CostEntryItem> costEntries(String month);

    Map<String, Object> createCostEntry(Map<String, Object> payload);
}
