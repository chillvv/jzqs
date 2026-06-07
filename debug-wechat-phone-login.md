# [OPEN] WeChat Phone Login Debug

## Session

- Session ID: `wechat-phone-login`
- Started: 2026-06-06
- Status: OPEN

## Symptom

- 用户反馈：两个小程序的微信手机号一键登录仍然无法获取手机号。
- 期望行为：点击 `open-type="getPhoneNumber"` 后，用户确认微信默认手机号，前端拿到动态令牌 `code`，后端调用微信接口换取手机号并完成登录。

## Initial Hypotheses

1. 前端事件没有稳定拿到 `e.detail.code`，或 `errMsg` 并非 `getPhoneNumber:ok`。
2. 前端虽然拿到了 `code`，但请求没有发到当前改造后的接口，或者请求体字段不对。
3. 后端调用微信 `getuserphonenumber` 失败，真实错误是 `appid/secret/access_token`、IP 白名单、主体能力或 code 失效等配置问题。
4. 小程序当前连到的不是本地这版后端，而是旧服务，导致线上行为仍走旧链路。
5. 微信开发者工具/当前运行环境本身没有返回真实手机号，需要真机或特定环境才会拿到有效动态令牌。

## Evidence Plan

- 在前端手机号授权事件处记录 `errMsg`、是否存在 `code`、请求目标接口。
- 在后端手机号换号入口记录请求来源、是否收到 `code`、微信接口返回的 `errcode/errmsg`。
- 让用户按固定步骤复现一次，再根据日志排除或确认假设。

## Runtime Evidence

- 调试服务已重启，`http://127.0.0.1:7777/health` 与 `http://192.168.1.3:7777/health` 均返回 `status=ok`。
- 顾客端多次点击后，前端埋点记录 `location=miniapp/pages/profile/index.js:getPhoneNumber:entry`，`errMsg` 为：
  - `getPhoneNumber:fail no permission`
  - `getPhoneNumber:fail operateWXData:fail jsapi has no permission`
- 骑手端多次点击后，前端埋点记录 `location=miniapp-rider/pages/profile/index.js:onWechatLogin:entry`，`errMsg` 为：
  - `getPhoneNumber:fail no permission`
- 所有前端埋点均显示 `hasCode=false`、`codeLength=0`，说明问题发生在微信手机号能力授权之前，尚未进入后端换号阶段。
- 代码仓库内两个小程序的 `app.json` 当前都没有任何隐私授权相关声明，也没有发现统一隐私引导处理逻辑。
- 修复后再次复测，微信客户端明确报错：`getPhoneNumber:fail api scope is not declared in the privacy agreement, errno:112`。
- 同次复测还暴露出前端调试埋点写法错误：`wx.request(...).catch is not a function`，已确认为 instrumentation bug，不属于微信权限根因。

## Hypothesis Status

1. 前端事件没有稳定拿到 `e.detail.code`，或 `errMsg` 并非 `getPhoneNumber:ok`。 -> Confirmed
2. 前端虽然拿到了 `code`，但请求没有发到当前改造后的接口，或者请求体字段不对。 -> Not reached
3. 后端调用微信 `getuserphonenumber` 失败，真实错误是 `appid/secret/access_token`、IP 白名单、主体能力或 code 失效等配置问题。 -> Not reached
4. 小程序当前连到的不是本地这版后端，而是旧服务，导致线上行为仍走旧链路。 -> Still possible for backend verification, but unrelated to current permission failure
5. 微信开发者工具/当前运行环境本身没有返回真实手机号，需要真机或特定环境才会拿到有效动态令牌。 -> Possible contributing factor, but current frontend permission error is primary blocker

## Current Root Cause

- 当前主阻塞点不是后端、证书或 JWT，而是微信平台侧《小程序用户隐私保护指引》没有声明手机号相关隐私范围。
- 根据微信官方公告，只有在平台《小程序用户隐私保护指引》中声明了所处理的用户个人信息，才可以调用对应隐私接口或组件；未声明时，对应接口会被直接禁用。
