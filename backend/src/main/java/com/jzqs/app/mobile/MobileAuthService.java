package com.jzqs.app.mobile;

import com.jzqs.app.mobile.api.RiderAuthProfileResponse;
import com.jzqs.app.mobile.api.RiderLoginResponse;
import java.util.Map;

public interface MobileAuthService {
    Map<String, Object> wxLogin(String code);

    Map<String, Object> bindDevPhone(String openid, String phone);

    Map<String, Object> riderWxLogin(String code);

    Map<String, Object> bindRiderPhone(String openid, String phone, String nickname);

    Map<String, Object> riderPasswordLogin(String phone, String password);

    RiderAuthProfileResponse riderProfile(String riderName);

    Map<String, Object> verifyRiderToken(String token);

    Map<String, Object> completeProfile(String openid, String nickname);

    Map<String, Object> bindPhone(String openid, String phone, String nickname);

    /**
     * 骑手注册
     */
    RiderLoginResponse riderRegister(String phone, String name, String openid);

    /**
     * 骑手微信一键登录
     */
    RiderLoginResponse riderWechatLogin(String openid, String code, String encryptedData, String iv);

    /**
     * 骑手手机号登录
     */
    RiderLoginResponse riderPhoneLogin(String phone, String openid);

    /**
     * 骑手混合登录
     * @deprecated 使用 riderRegister 或 riderPhoneLogin 替代
     */
    RiderLoginResponse riderMixedLogin(String phone, String name, String openid);
}
