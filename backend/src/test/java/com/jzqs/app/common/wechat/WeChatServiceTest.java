package com.jzqs.app.common.wechat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class WeChatServiceTest {

    @Test
    void shouldSendJsonBodyAndJsonAcceptHeaderWhenGettingPhoneNumber() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        WeChatService service = new WeChatService(restTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(service, "devMode", false);
        ReflectionTestUtils.setField(service, "appid", "wx-test-appid");
        ReflectionTestUtils.setField(service, "secret", "wx-test-secret");

        server.expect(once(), requestTo("https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=wx-test-appid&secret=wx-test-secret"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"access_token\":\"token-1\",\"expires_in\":7200}", MediaType.APPLICATION_JSON));

        server.expect(once(), requestTo("https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=token-1"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().json("{\"code\":\"phone-code-1\"}"))
            .andExpect(request -> assertEquals(List.of(MediaType.APPLICATION_JSON), request.getHeaders().getAccept()))
            .andExpect(request -> assertEquals(23L, request.getHeaders().getContentLength()))
            .andRespond(withSuccess("{\"errcode\":0,\"phone_info\":{\"phoneNumber\":\"13800138000\"}}", MediaType.APPLICATION_JSON));

        String phone = service.getPhoneNumber("phone-code-1");

        assertEquals("13800138000", phone);
        server.verify();
    }

    @Test
    void shouldUnwrapNestedJsonCodeStringBeforeCallingWechatPhoneApi() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        WeChatService service = new WeChatService(restTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(service, "devMode", false);
        ReflectionTestUtils.setField(service, "appid", "wx-test-appid");
        ReflectionTestUtils.setField(service, "secret", "wx-test-secret");

        server.expect(once(), requestTo("https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=wx-test-appid&secret=wx-test-secret"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"access_token\":\"token-1\",\"expires_in\":7200}", MediaType.APPLICATION_JSON));

        server.expect(once(), requestTo("https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=token-1"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().json("{\"code\":\"phone-code-2\"}"))
            .andRespond(withSuccess("{\"errcode\":0,\"phone_info\":{\"phoneNumber\":\"13800138001\"}}", MediaType.APPLICATION_JSON));

        String phone = service.getPhoneNumber("{\"code\":\"phone-code-2\"}");

        assertEquals("13800138001", phone);
        server.verify();
    }
}
