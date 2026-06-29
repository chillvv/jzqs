package com.jzqs.app.mobile;

import com.jzqs.app.mobile.api.MobileAuthStateResponse;
import com.jzqs.app.mobile.api.MobileTokenVerifyResponse;
import com.jzqs.app.mobile.api.RiderAuthProfileResponse;
import com.jzqs.app.mobile.api.RiderAuthStateResponse;
import com.jzqs.app.mobile.api.RiderLoginResponse;
import com.jzqs.app.mobile.api.RiderTokenVerifyResponse;

public interface MobileAuthService {
    MobileAuthStateResponse wxLogin(String code);

    MobileTokenVerifyResponse verify(String token);

    void logout(String token);

    MobileAuthStateResponse phoneLogin(String openid, String phone);

    MobileAuthStateResponse bindDevPhone(String openid, String phone);

    RiderAuthStateResponse riderWxLogin(String code);

    RiderAuthStateResponse bindRiderPhone(String openid, String phone, String nickname);

    RiderAuthProfileResponse riderProfile(String riderName);

    RiderAuthProfileResponse riderProfile(long riderId);

    RiderTokenVerifyResponse verifyRiderToken(String token);

    MobileAuthStateResponse completeProfile(String openid, String nickname);

    MobileAuthStateResponse bindPhoneByCode(String openid, String code);

    MobileAuthStateResponse bindPhone(String openid, String phone, String nickname);

    /**
     * 骑手注册
     */
    RiderLoginResponse riderRegister(String phone, String name, String openid);

    /**
     * 骑手微信一键登录
     */
    RiderLoginResponse riderWechatLogin(String openid, String code);

    /**
     * 骑手手机号登录
     */
    RiderLoginResponse riderPhoneLogin(String phone, String openid);

    /**
     * 骑手混合登录
     * @deprecated 使用 riderRegister 或 riderPhoneLogin 替代
     */
    @Deprecated
    RiderLoginResponse riderMixedLogin(String phone, String name, String openid);
}
