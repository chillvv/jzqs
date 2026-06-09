package com.jzqs.app.common.wechat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 微信小程序服务
 * 处理微信 API 调用（code2session、获取手机号等）
 */
@Service
public class WeChatService {
    private static final Logger log = LoggerFactory.getLogger(WeChatService.class);
    private static final String CODE2SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session";
    private static final String GET_PHONE_NUMBER_URL = "https://api.weixin.qq.com/wxa/business/getuserphonenumber";
    private static final String GET_ACCESS_TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token";
    private static final String SEND_SUBSCRIBE_MESSAGE_URL = "https://api.weixin.qq.com/cgi-bin/message/subscribe/send";

    @Value("${wechat.dev-mode:true}")
    private boolean devMode;

    @Value("${wechat.appid:}")
    private String appid;

    @Value("${wechat.secret:}")
    private String secret;

    @Value("${wechat.subscribe.delivery-template-id:}")
    private String deliveryTemplateId;

    @Value("${wechat.subscribe.delivery-page:pages/orders/index}")
    private String deliveryPage;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    // 缓存 access_token（实际生产应该用 Redis）
    private String cachedAccessToken;
    private long accessTokenExpireTime;

    public WeChatService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 微信登录：code 换取 openid 和 session_key
     */
    public WeChatSession code2Session(String code) {
        if (devMode) {
            // 开发模式：直接返回模拟数据
            log.info("开发模式：code2session，code={}", code);
            return new WeChatSession("dev_" + code, "dev_session_" + code, null);
        }

        // 生产模式：调用微信接口
        try {
            String url = String.format("%s?appid=%s&secret=%s&js_code=%s&grant_type=authorization_code",
                    CODE2SESSION_URL, appid, secret, code);
            
            String response = restTemplate.getForObject(url, String.class);
            JsonNode json = objectMapper.readTree(response);

            if (json.has("errcode") && json.get("errcode").asInt() != 0) {
                String errmsg = json.get("errmsg").asText();
                log.error("微信 code2session 失败：{}", errmsg);
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "微信登录失败：" + errmsg);
            }

            String openid = json.get("openid").asText();
            String sessionKey = json.get("session_key").asText();
            String unionid = json.has("unionid") ? json.get("unionid").asText() : null;

            log.info("微信 code2session 成功：openid={}", openid);
            return new WeChatSession(openid, sessionKey, unionid);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用微信 code2session 接口异常", e);
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "微信登录失败，请稍后重试");
        }
    }

    /**
     * 获取用户手机号（新版 API，基础库 2.21.2+）
     * @param code getPhoneNumber 返回的动态令牌
     */
    public String getPhoneNumber(String code) {
        // #region debug-point C:wechat-get-phone-entry
        debugReport("C", "WeChatService.java:getPhoneNumber:entry", "[DEBUG] enter getPhoneNumber", Map.of("devMode", devMode, "codeLength", code == null ? 0 : code.length(), "appidConfigured", appid != null && !appid.trim().isEmpty(), "secretConfigured", secret != null && !secret.trim().isEmpty()));
        // #endregion
        if (devMode) {
            // 开发模式：code 直接当手机号
            log.info("开发模式：getPhoneNumber，code={}", code);
            // #region debug-point C:wechat-get-phone-dev
            debugReport("C", "WeChatService.java:getPhoneNumber:dev-mode", "[DEBUG] using dev-mode phone fallback", Map.of("codePreview", code == null ? "" : code));
            // #endregion
            // 如果 code 是 11 位数字，直接返回；否则返回测试手机号
            if (code.matches("^1\\d{10}$")) {
                return code;
            }
            return "13800138000";
        }

        // 生产模式：调用微信接口
        try {
            String accessToken = getAccessToken();
            String url = GET_PHONE_NUMBER_URL + "?access_token=" + accessToken;

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("code", code);

            String response = restTemplate.postForObject(url, requestBody, String.class);
            JsonNode json = objectMapper.readTree(response);
            // #region debug-point C:wechat-get-phone-response
            debugReport("C", "WeChatService.java:getPhoneNumber:response", "[DEBUG] wechat getPhoneNumber response", Map.of("response", response == null ? "" : response));
            // #endregion

            if (json.has("errcode") && json.get("errcode").asInt() != 0) {
                String errmsg = json.get("errmsg").asText();
                log.error("微信 getPhoneNumber 失败：{}", errmsg);
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "获取手机号失败：" + errmsg);
            }

            JsonNode phoneInfo = json.get("phone_info");
            String phoneNumber = phoneInfo.get("phoneNumber").asText();

            log.info("微信 getPhoneNumber 成功：phone={}", maskPhone(phoneNumber));
            return phoneNumber;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用微信 getPhoneNumber 接口异常", e);
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "获取手机号失败，请稍后重试");
        }
    }

    public void sendDeliverySubscribeMessage(String openid, String page, String merchantName, String address, String hint) {
        if (openid == null || openid.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "缺少订阅消息接收人");
        }
        if (deliveryTemplateId == null || deliveryTemplateId.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "未配置送达提醒模板");
        }
        if (devMode) {
            log.info("开发模式：跳过订阅消息发送 openid={}, page={}", openid, page);
            return;
        }
        try {
            String accessToken = getAccessToken();
            String url = SEND_SUBSCRIBE_MESSAGE_URL + "?access_token=" + accessToken;
            Map<String, Object> payload = new HashMap<>();
            payload.put("touser", openid);
            payload.put("template_id", deliveryTemplateId);
            payload.put("page", page);
            payload.put("data", Map.of(
                "thing18", Map.of("value", merchantName),
                "thing7", Map.of("value", address),
                "thing11", Map.of("value", hint)
            ));
            String response = restTemplate.postForObject(url, payload, String.class);
            JsonNode json = objectMapper.readTree(response);
            if (json.has("errcode") && json.get("errcode").asInt() != 0) {
                String errmsg = json.path("errmsg").asText();
                log.error("微信订阅消息发送失败：{}", errmsg);
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "发送订阅消息失败：" + errmsg);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("发送微信订阅消息异常", e);
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "发送订阅消息失败，请稍后重试");
        }
    }

    public String buildDeliveryPage(long orderId) {
        return deliveryPage + "?orderId=" + orderId;
    }

    // #region debug-point C:wechat-debug-report-helper
    private void debugReport(String hypothesisId, String location, String msg, Map<String, ?> data) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sessionId", "wechat-phone-login");
            payload.put("runId", "pre-fix");
            payload.put("hypothesisId", hypothesisId);
            payload.put("location", location);
            payload.put("msg", msg);
            payload.put("data", data);
            payload.put("ts", System.currentTimeMillis());
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForObject("http://127.0.0.1:7777/event", new HttpEntity<>(payload, headers), String.class);
        } catch (Exception ignored) {
        }
    }
    // #endregion

    /**
     * 获取 access_token（带缓存）
     */
    private String getAccessToken() {
        // 检查缓存
        if (cachedAccessToken != null && System.currentTimeMillis() < accessTokenExpireTime) {
            return cachedAccessToken;
        }

        // 重新获取
        try {
            String url = String.format("%s?grant_type=client_credential&appid=%s&secret=%s",
                    GET_ACCESS_TOKEN_URL, appid, secret);

            String response = restTemplate.getForObject(url, String.class);
            JsonNode json = objectMapper.readTree(response);

            if (json.has("errcode")) {
                String errmsg = json.get("errmsg").asText();
                log.error("获取 access_token 失败：{}", errmsg);
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "微信服务异常");
            }

            cachedAccessToken = json.get("access_token").asText();
            int expiresIn = json.get("expires_in").asInt();
            // 提前 5 分钟过期
            accessTokenExpireTime = System.currentTimeMillis() + (expiresIn - 300) * 1000L;

            log.info("获取 access_token 成功，有效期：{}秒", expiresIn);
            return cachedAccessToken;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取 access_token 异常", e);
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "微信服务异常");
        }
    }

    /**
     * 手机号脱敏
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    /**
     * 微信会话信息
     */
    public record WeChatSession(String openid, String sessionKey, String unionid) {}
}
