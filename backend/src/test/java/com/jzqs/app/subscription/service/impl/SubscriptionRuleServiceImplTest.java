package com.jzqs.app.subscription.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.mobile.api.MobileSubscriptionRuleRequest;
import com.jzqs.app.mobile.api.MobileSubscriptionRuleResponse;
import com.jzqs.app.subscription.mapper.SubscriptionRuleMapper;
import com.jzqs.app.subscription.model.entity.SubscriptionRuleEntity;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 地址重置（V35-V37 + 运维手册）后 subscription_rules.default_address_id 被置空，
 * 顾客重新录入地址并开启固定订餐时必须自动回填，否则后台"导入订阅订单"预览
 * 永远为空、无法生成订单。
 */
class SubscriptionRuleServiceImplTest {

    private SubscriptionRuleMapper subscriptionRuleMapper;
    private JdbcTemplate jdbcTemplate;
    private SubscriptionRuleServiceImpl service;

    @BeforeEach
    void setUp() {
        subscriptionRuleMapper = Mockito.mock(SubscriptionRuleMapper.class);
        jdbcTemplate = Mockito.mock(JdbcTemplate.class);
        service = new SubscriptionRuleServiceImpl(subscriptionRuleMapper, jdbcTemplate);
    }

    private SubscriptionRuleEntity existingRuleWithNullAddress() {
        SubscriptionRuleEntity entity = new SubscriptionRuleEntity();
        entity.setId(5L);
        entity.setCustomerId(9L);
        entity.setActive(false);
        entity.setPaused(true);
        entity.setWeekDays("1,2,3,4,5");
        entity.setLunchEnabled(true);
        entity.setDinnerEnabled(false);
        entity.setDefaultAddressId(null);
        return entity;
    }

    @Test
    void shouldBackfillDefaultAddressWhenCustomerReenablesSubscriptionAfterReset() {
        SubscriptionRuleEntity entity = existingRuleWithNullAddress();
        when(subscriptionRuleMapper.selectOne(any())).thenReturn(entity);
        // 第一次查 is_default 地址（重置后无 is_default）→ 空；第二次查任一 active 地址 → 命中
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq(9L)))
            .thenReturn(Collections.emptyList(), List.of(12L));
        when(subscriptionRuleMapper.updateById(any(SubscriptionRuleEntity.class))).thenReturn(1);

        MobileSubscriptionRuleResponse response = service.updateRuleByCustomer(
            9L, new MobileSubscriptionRuleRequest(true, null, true, false));

        assertEquals(true, response.enabled());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<SubscriptionRuleEntity> captor =
            ArgumentCaptor.forClass((Class<SubscriptionRuleEntity>) (Class<?>) SubscriptionRuleEntity.class);
        verify(subscriptionRuleMapper).updateById(captor.capture());
        assertEquals(12L, captor.getValue().getDefaultAddressId());
    }

    @Test
    void shouldThrowWhenReenablingWithoutAnyActiveAddress() {
        SubscriptionRuleEntity entity = existingRuleWithNullAddress();
        when(subscriptionRuleMapper.selectOne(any())).thenReturn(entity);
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq(9L)))
            .thenReturn(Collections.emptyList(), Collections.emptyList());

        assertThrows(BusinessException.class, () -> service.updateRuleByCustomer(
            9L, new MobileSubscriptionRuleRequest(true, null, true, false)));

        verify(subscriptionRuleMapper, never()).updateById(any(SubscriptionRuleEntity.class));
    }

    @Test
    void shouldNotTouchAddressWhenDisablingSubscription() {
        SubscriptionRuleEntity entity = existingRuleWithNullAddress();
        when(subscriptionRuleMapper.selectOne(any())).thenReturn(entity);
        when(subscriptionRuleMapper.updateById(any(SubscriptionRuleEntity.class))).thenReturn(1);

        MobileSubscriptionRuleResponse response = service.updateRuleByCustomer(
            9L, new MobileSubscriptionRuleRequest(false, null, true, false));

        assertEquals(false, response.enabled());
        Mockito.verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void shouldKeepExistingAddressWhenReenabling() {
        SubscriptionRuleEntity entity = existingRuleWithNullAddress();
        entity.setDefaultAddressId(77L);
        when(subscriptionRuleMapper.selectOne(any())).thenReturn(entity);
        when(subscriptionRuleMapper.updateById(any(SubscriptionRuleEntity.class))).thenReturn(1);

        service.updateRuleByCustomer(9L, new MobileSubscriptionRuleRequest(true, null, true, false));

        Mockito.verifyNoInteractions(jdbcTemplate);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<SubscriptionRuleEntity> captor =
            ArgumentCaptor.forClass((Class<SubscriptionRuleEntity>) (Class<?>) SubscriptionRuleEntity.class);
        verify(subscriptionRuleMapper).updateById(captor.capture());
        assertEquals(77L, captor.getValue().getDefaultAddressId());
    }
}
