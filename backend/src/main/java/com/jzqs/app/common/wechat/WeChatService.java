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
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
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

    @Value("${wechat.subscribe.nightly-template-id:}")
    private String nightlyTemplateId;

    @Value("${wechat.subscribe.nightly-page:pages/orders/index}")
    private String nightlyPage;

    @jakarta.annotation.PostConstruct
    public void logResolvedConfig() {
        // 启动时打印一次实际解析到的配置，便于确认环境变量是否真正注入容器
        log.info("[WeChatService] 启动解析配置 devMode={}, appid={}, secretSet={}",
                devMode, appid, (secret != null && !secret.isEmpty()));
    }

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
        if (devMode) {
            // 开发模式：code 直接当手机号
            log.info("开发模式：getPhoneNumber，code={}", code);
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
            String normalizedCode = normalizePhoneCode(code);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            String requestBody = objectMapper.writeValueAsString(Map.of("code", normalizedCode));
            HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);

            String response = restTemplate.postForObject(url, requestEntity, String.class);
            JsonNode json = objectMapper.readTree(response);

            if (json.has("errcode") && json.get("errcode").asInt() != 0) {
                String errmsg = json.get("errmsg").asText();
                log.error("微信 getPhoneNumber 失败：{}", errmsg);
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "获取手机号失败：" + errmsg);
            }

            JsonNode phoneInfo = json.get("phone_info");
            String phoneNumber = phoneInfo.get("phoneNumber").asText();

            log.info("微信 getPhoneNumber 成功：phone={}", maskPhone(phoneNumber));
            return phoneNumber;
        } catch (HttpStatusCodeException e) {
            log.error("调用微信 getPhoneNumber 接口返回 HTTP 异常：status={}, body={}", e.getStatusCode().value(), e.getResponseBodyAsString(), e);
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "获取手机号失败，请稍后重试");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("调用微信 getPhoneNumber 接口异常", e);
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "获取手机号失败，请稍后重试");
        }
    }

    public void sendDeliverySubscribeMessage(
            String openid,
            String page,
            String dishNames,
            String riderPhone,
            String pickupLocation,
            String hint) {
        if (openid == null || openid.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "缺少订阅消息接收人");
        }
        if (deliveryTemplateId == null || deliveryTemplateId.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "未配置送达提醒模板");
        }
        if (devMode) {
            log.info("开发模式：跳过订阅消息发送 devMode={}, openid={}, page={}", devMode, openid, page);
            return;
        }
        try {
            String accessToken = getAccessToken();
            String url = SEND_SUBSCRIBE_MESSAGE_URL + "?access_token=" + accessToken;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            String requestBody = objectMapper.writeValueAsString(
                new SubscribeMessageRequest(
                    openid,
                    deliveryTemplateId,
                    page,
                    new SubscribeMessageData(
                        new SubscribeMessageValue(normalizeThingValue(dishNames)),
                        new SubscribeMessageValue(normalizePhoneValue(riderPhone)),
                        new SubscribeMessageValue(normalizeThingValue(pickupLocation)),
                        new SubscribeMessageValue(normalizeThingValue(hint))
                    )
                )
            );
            HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);
            String response = restTemplate.postForObject(url, requestEntity, String.class);
            JsonNode json = objectMapper.readTree(response);
            if (json.has("errcode") && json.get("errcode").asInt() != 0) {
                String errmsg = json.path("errmsg").asText();
                log.error("微信订阅消息发送失败：{}", errmsg);
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, translateWechatSendError("送达提醒：" + errmsg));
            }
        } catch (HttpStatusCodeException e) {
            log.error(
                "发送微信订阅消息 HTTP 异常：status={}, body={}",
                e.getStatusCode().value(),
                e.getResponseBodyAsString(),
                e
            );
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, translateWechatSendError("送达提醒：" + e.getResponseBodyAsString()));
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

    /**
     * 发送每晚提醒订阅消息（优惠券过期提醒模板）。
     * 字段：number7=剩余餐数, thing3=描述, thing6=温馨提示。
     */
    public void sendNightlySubscribeMessage(
            String openid,
            String page,
            int remainingMeals,
            String description,
            String tip) {
        if (openid == null || openid.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "缺少订阅消息接收人");
        }
        if (nightlyTemplateId == null || nightlyTemplateId.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "未配置每晚提醒模板");
        }
        if (devMode) {
            log.info("开发模式：跳过每晚提醒发送 devMode={}, openid={}, page={}", devMode, openid, page);
            return;
        }
        try {
            String accessToken = getAccessToken();
            String url = SEND_SUBSCRIBE_MESSAGE_URL + "?access_token=" + accessToken;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            String requestBody = objectMapper.writeValueAsString(
                new NightlySubscribeMessageRequest(
                    openid,
                    nightlyTemplateId,
                    page,
                    new NightlySubscribeMessageData(
                        new SubscribeMessageValue(String.valueOf(normalizeNumberValue(remainingMeals))),
                        new SubscribeMessageValue(normalizeThingValue(description)),
                        new SubscribeMessageValue(normalizeThingValue(tip))
                    )
                )
            );
            log.debug("每晚提醒订阅消息请求体：{}", requestBody);
            String response = restTemplate.postForObject(url, new HttpEntity<>(requestBody, headers), String.class);
            JsonNode json = objectMapper.readTree(response);
            if (json.has("errcode") && json.get("errcode").asInt() != 0) {
                String errmsg = json.get("errmsg").asText();
                log.error("微信每晚提醒发送失败：{}", errmsg);
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, translateWechatSendError("每晚提醒：" + errmsg));
            }
        } catch (HttpStatusCodeException e) {
            log.error(
                "发送微信每晚提醒 HTTP 异常：status={}, body={}",
                e.getStatusCode().value(),
                e.getResponseBodyAsString(),
                e
            );
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, translateWechatSendError("每晚提醒：" + e.getResponseBodyAsString()));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("发送微信每晚提醒异常", e);
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "发送每晚提醒失败，请稍后重试");
        }
    }

    public String buildNightlyPage() {
        return nightlyPage;
    }

    public String getDeliveryTemplateId() {
        return deliveryTemplateId;
    }

    public String getNightlyTemplateId() {
        return nightlyTemplateId;
    }

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

    private String normalizePhoneCode(String code) throws Exception {
        String value = code == null ? "" : code.trim();
        if (value.startsWith("{")) {
            JsonNode json = objectMapper.readTree(value);
            if (json.hasNonNull("code")) {
                return json.get("code").asText().trim();
            }
        }
        return value;
    }

    private String normalizeThingValue(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= 20 ? normalized : normalized.substring(0, 20);
    }

    private String normalizePhoneValue(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= 11 ? normalized : normalized.substring(0, 11);
    }

    private int normalizeNumberValue(int value) {
        return Math.max(0, value);
    }

    /**
     * 把微信订阅消息发送接口返回的英文错误信息转换成中文，方便普通用户理解。
     */
    private String translateWechatSendError(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "微信服务异常";
        }
        String s = raw.trim();
        if (s.contains("user refuse to accept the msg")) {
            return "用户关闭了订阅消息权限，请在微信→小程序设置→订阅消息中重新开启";
        }
        if (s.contains("invalid openid is empty")) {
            return "用户身份无效，请重新登录后再试";
        }
        if (s.contains("invalid openid") || s.contains("invalid openid list")) {
            return "用户身份无效（openid非法），请重新登录";
        }
        if (s.contains("invalid template_id")) {
            return "订阅模板配置错误，请联系运营人员";
        }
        if (s.contains("request limit")) {
            return "发送频率过高，请稍后再试";
        }
        // 其他错误保留原文，便于排查
        return s;
    }

    /**
     * 微信会话信息
     */
    public record WeChatSession(String openid, String sessionKey, String unionid) {}

    private record SubscribeMessageRequest(
        String touser,
        String template_id,
        String page,
        SubscribeMessageData data
    ) {
    }

    private record SubscribeMessageData(
        SubscribeMessageValue thing6,
        SubscribeMessageValue phone_number9,
        SubscribeMessageValue thing10,
        SubscribeMessageValue thing7
    ) {
    }

    private record NightlySubscribeMessageData(
        SubscribeMessageValue number7,
        SubscribeMessageValue thing3,
        SubscribeMessageValue thing6
    ) {
    }

    private record NightlySubscribeMessageRequest(
        String touser,
        String template_id,
        String page,
        NightlySubscribeMessageData data
    ) {
    }

    private record SubscribeMessageValue(String value) {
    }
}
