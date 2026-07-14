package com.jzqs.app.dashboard.service;

import com.jzqs.app.dashboard.api.DashboardOverviewResponse;
import com.jzqs.app.subscription.api.LowBalanceSubscriptionItem;

import java.util.List;

public interface DashboardService {
    DashboardOverviewResponse overview();
    
    List<LowBalanceSubscriptionItem> lowBalanceSubscriptions();
}
