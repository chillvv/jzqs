package com.jzqs.app.subscription.service;

import com.jzqs.app.subscription.api.SubscriptionRuleRequest;
import com.jzqs.app.subscription.api.SubscriptionRuleDeleteResponse;
import com.jzqs.app.subscription.api.SubscriptionRuleResponse;
import com.jzqs.app.subscription.api.SubscriptionRuleTogglePauseResponse;
import java.util.List;

public interface SubscriptionRuleService {
    List<SubscriptionRuleResponse> listRules(String keyword, String status);
    
    SubscriptionRuleResponse createRule(SubscriptionRuleRequest request);
    
    SubscriptionRuleResponse updateRule(long id, SubscriptionRuleRequest request);
    
    SubscriptionRuleDeleteResponse deleteRule(long id);
    
    SubscriptionRuleTogglePauseResponse togglePause(long id);

    com.jzqs.app.mobile.api.MobileSubscriptionRuleResponse getRuleByCustomerId(long customerId);

    com.jzqs.app.mobile.api.MobileSubscriptionRuleResponse updateRuleByCustomer(long customerId, com.jzqs.app.mobile.api.MobileSubscriptionRuleRequest request);
}
