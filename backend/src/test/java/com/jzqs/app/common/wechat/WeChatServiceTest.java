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
 * <p>风险点：微信订阅消息请求体里的 data key 必须与模板字段一一对应。两个取餐模板字段编号完全不同
 * （「取餐提醒」250 = thing6/phone_number9/thing10/thing7，「订餐提醒」22593 = thing7/phone_number5/
 * thing4/thing2），写错 key 微信会直接返回 47003（参数值不符合规则），消息发不出去，
 * 且表面上像是"发送失败"而不是配置错误，很难排查。因此这里把模板与字段 key 的整组对应关系钉住。
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
        // 午餐模板「取餐提醒」250：商品名 / 骑手电话 / 取餐位置 / 温馨提醒
        ReflectionTestUtils.setField(weChatService, "deliveryTemplateId", LUNCH_TEMPLATE);
        ReflectionTestUtils.setField(weChatService, "deliveryNameKey", "thing6");
        ReflectionTestUtils.setField(weChatService, "deliveryPhoneKey", "phone_number9");
        ReflectionTestUtils.setField(weChatService, "deliveryLocationKey", "thing10");
        ReflectionTestUtils.setField(weChatService, "deliveryHintKey", "thing7");
        // 晚餐模板「订餐提醒」22593：套餐名称 / 联系电话 / 门店地址 / 温馨提示
        ReflectionTestUtils.setField(weChatService, "deliveryDinnerTemplateId", DINNER_TEMPLATE);
        ReflectionTestUtils.setField(weChatService, "deliveryDinnerNameKey", "thing7");
        ReflectionTestUtils.setField(weChatService, "deliveryDinnerPhoneKey", "phone_number5");
        ReflectionTestUtils.setField(weChatService, "deliveryDinnerLocationKey", "thing4");
        ReflectionTestUtils.setField(weChatService, "deliveryDinnerHintKey", "thing2");
        given(restTemplate.getForObject(anyString(), eq(String.class)))
            .willReturn("{\"access_token\":\"tok\",\"expires_in\":7200}");
        given(restTemplate.postForObject(anyString(), any(), eq(String.class)))
            .willReturn("{\"errcode\":0,\"errmsg\":\"ok\"}");
    }

    @Test
    void dinnerTemplateShouldUseItsOwnFieldKeySet() throws Exception {
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
        JsonNode data = body.get("data");
        assertEquals(DINNER_TEMPLATE, body.get("template_id").asText());
        // 晚餐模板「订餐提醒」：套餐名称 thing7 / 联系电话 phone_number5 / 门店地址 thing4 / 温馨提示 thing2
        assertEquals("红烧肉、青菜", data.get("thing7").get("value").asText());
        assertEquals("13800138000", data.get("phone_number5").get("value").asText());
        assertEquals("高新区某路1号101", data.get("thing4").get("value").asText());
        assertEquals("记得取餐", data.get("thing2").get("value").asText());
        // 不能混入午餐模板的字段编号
        assertFalse(data.has("thing6"));
        assertFalse(data.has("phone_number9"));
        assertFalse(data.has("thing10"));
    }

    @Test
    void lunchTemplateShouldKeepUsingItsOwnFieldKeySet() throws Exception {
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
        JsonNode data = body.get("data");
        assertEquals(LUNCH_TEMPLATE, body.get("template_id").asText());
        // 午餐模板「取餐提醒」：商品名 thing6 / 骑手电话 phone_number9 / 取餐位置 thing10 / 温馨提醒 thing7
        assertEquals("红烧肉", data.get("thing6").get("value").asText());
        assertEquals("13800138000", data.get("phone_number9").get("value").asText());
        assertEquals("高新区某路1号101", data.get("thing10").get("value").asText());
        assertEquals("记得取餐", data.get("thing7").get("value").asText());
        // 不能混入晚餐模板的字段编号
        assertFalse(data.has("phone_number5"));
        assertFalse(data.has("thing4"));
        assertFalse(data.has("thing2"));
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
