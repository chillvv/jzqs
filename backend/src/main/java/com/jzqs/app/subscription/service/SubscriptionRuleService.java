package com.jzqs.app.subscription.service;

import com.jzqs.app.subscription.api.SubscriptionRuleRequest;
import com.jzqs.app.subscription.api.SubscriptionRuleResponse;
import java.util.List;
import java.util.Map;

public interface SubscriptionRuleService {
    List<SubscriptionRuleResponse> listRules(String keyword, String status);
    
    SubscriptionRuleResponse createRule(SubscriptionRuleRequest request);
    
    SubscriptionRuleResponse updateRule(long id, SubscriptionRuleRequest request);
    
    Map<String, Object> deleteRule(long id);
    
    Map<String, Object> togglePause(long id);
}
