package com.jzqs.app.common.wechat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

/**
 * 取餐提醒模板字段映射的单元测试。
 *
 * <p>风险点：微信订阅消息请求体里的 data key 必须与模板字段一一对应。午餐模板的「取餐位置」是
 * thing10，晚餐模板是 thing40——写错 key 微信会直接返回 47003（参数值不符合规则），消息发不出去，
 * 且表面上像是"发送失败"而不是配置错误，很难排查。因此这里把模板与字段 key 的对应关系钉住。
 */
class WeChatServiceTest {

    private static final String LUNCH_TEMPLATE = "tmpl-lunch";
    private static final String DINNER_TEMPLATE = "tmpl-dinner";

    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;
    private WeChatService weChatService;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        objectMapper = new ObjectMapper();
        weChatService = new WeChatService(restTemplate, objectMapper);
        ReflectionTestUtils.setField(weChatService, "devMode", false);
        ReflectionTestUtils.setField(weChatService, "appid", "appid");
        ReflectionTestUtils.setField(weChatService, "secret", "secret");
        ReflectionTestUtils.setField(weChatService, "deliveryTemplateId", LUNCH_TEMPLATE);
        ReflectionTestUtils.setField(weChatService, "deliveryLocationKey", "thing10");
        ReflectionTestUtils.setField(weChatService, "deliveryDinnerTemplateId", DINNER_TEMPLATE);
        ReflectionTestUtils.setField(weChatService, "deliveryDinnerLocationKey", "thing40");
        given(restTemplate.getForObject(anyString(), eq(String.class)))
            .willReturn("{\"access_token\":\"tok\",\"expires_in\":7200}");
        given(restTemplate.postForObject(anyString(), any(), eq(String.class)))
            .willReturn("{\"errcode\":0,\"errmsg\":\"ok\"}");
    }

    @Test
    void dinnerTemplateShouldUseItsOwnLocationFieldKey() throws Exception {
        weChatService.sendDeliverySubscribeMessage(
            "openid-x",
            DINNER_TEMPLATE,
            "pages/orders/index",
            "红烧肉、青菜",
            "13800138000",
            "高新区某路1号101",
            "记得取餐"
        );

        JsonNode body = captureSendBody();
        assertEquals(DINNER_TEMPLATE, body.get("template_id").asText());
        assertEquals("高新区某路1号101", body.get("data").get("thing40").get("value").asText());
        assertTrue(body.get("data").has("thing6"));
        assertTrue(body.get("data").has("phone_number9"));
        assertTrue(body.get("data").has("thing7"));
        assertFalse(body.get("data").has("thing10"));
    }

    @Test
    void lunchTemplateShouldKeepUsingThing10() throws Exception {
        weChatService.sendDeliverySubscribeMessage(
            "openid-x",
            LUNCH_TEMPLATE,
            "pages/orders/index",
            "红烧肉",
            "13800138000",
            "高新区某路1号101",
            "记得取餐"
        );

        JsonNode body = captureSendBody();
        assertEquals(LUNCH_TEMPLATE, body.get("template_id").asText());
        assertEquals("高新区某路1号101", body.get("data").get("thing10").get("value").asText());
        assertFalse(body.get("data").has("thing40"));
    }

    @Test
    void resolveDeliveryTemplateIdShouldRouteByMealPeriod() {
        assertEquals(DINNER_TEMPLATE, weChatService.resolveDeliveryTemplateId("DINNER"));
        assertEquals(DINNER_TEMPLATE, weChatService.resolveDeliveryTemplateId("dinner"));
        assertEquals(LUNCH_TEMPLATE, weChatService.resolveDeliveryTemplateId("LUNCH"));
    }

    @Test
    void resolveDeliveryTemplateIdShouldFallBackToLunchWhenDinnerTemplateMissing() {
        // 晚餐模板未配置时回退到午餐模板，保证新增配置上线前后行为不变、不会因缺配置直接不发
        ReflectionTestUtils.setField(weChatService, "deliveryDinnerTemplateId", "");

        assertEquals(LUNCH_TEMPLATE, weChatService.resolveDeliveryTemplateId("DINNER"));
        assertEquals(LUNCH_TEMPLATE, weChatService.resolveDeliveryTemplateId("LUNCH"));
    }

    @SuppressWarnings("unchecked")
    private JsonNode captureSendBody() throws Exception {
        ArgumentCaptor<HttpEntity<String>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(anyString(), captor.capture(), eq(String.class));
        return objectMapper.readTree(captor.getValue().getBody());
    }
}
