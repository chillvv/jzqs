package com.jzqs.app.analysis.service;

import com.jzqs.app.analysis.api.AnalysisOverviewResponse;
import com.jzqs.app.analysis.api.CostEntryItem;
import com.jzqs.app.analysis.api.CreateCostEntryRequest;
import com.jzqs.app.analysis.api.CreateCostEntryResponse;
import java.util.List;

public interface OperationsAnalysisService {
    AnalysisOverviewResponse overview(String date);

    List<CostEntryItem> costEntries(String month);

    CreateCostEntryResponse createCostEntry(CreateCostEntryRequest request);
}
