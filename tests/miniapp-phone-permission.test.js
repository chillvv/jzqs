const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const repoRoot = path.resolve(__dirname, '..');

const files = {
  customerApp: path.join(repoRoot, 'miniapp', 'app.json'),
  riderApp: path.join(repoRoot, 'miniapp-rider', 'app.json'),
  customerProfile: path.join(repoRoot, 'miniapp', 'pages', 'profile', 'index.js'),
  customerProfileWxml: path.join(repoRoot, 'miniapp', 'pages', 'profile', 'index.wxml'),
  customerLogin: path.join(repoRoot, 'miniapp', 'pages', 'login', 'index.js'),
  customerLoginWxml: path.join(repoRoot, 'miniapp', 'pages', 'login', 'index.wxml'),
  riderProfile: path.join(repoRoot, 'miniapp-rider', 'pages', 'profile', 'index.js'),
  riderProfileWxml: path.join(repoRoot, 'miniapp-rider', 'pages', 'profile', 'index.wxml'),
  riderLogin: path.join(repoRoot, 'miniapp-rider', 'pages', 'login', 'index.js'),
  riderLoginWxml: path.join(repoRoot, 'miniapp-rider', 'pages', 'login', 'index.wxml'),
  customerPrivacy: path.join(repoRoot, 'miniapp', 'utils', 'privacy-auth.js'),
  riderImageUtil: path.join(repoRoot, 'miniapp-rider', 'utils', 'image.js'),
  riderAuthUtil: path.join(repoRoot, 'miniapp-rider', 'utils', 'auth.js'),
  riderAuthService: path.join(repoRoot, 'miniapp-rider', 'services', 'auth.service.js'),
  riderMobileAuthController: path.join(repoRoot, 'backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'mobile', 'api', 'MobileRiderAuthController.java'),
  riderMobileAuthService: path.join(repoRoot, 'backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'mobile', 'MobileAuthService.java'),
  riderEntity: path.join(repoRoot, 'backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'rider', 'model', 'entity', 'RiderEntity.java'),
  riderCreateRequest: path.join(repoRoot, 'backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'rider', 'model', 'dto', 'RiderCreateRequest.java'),
  riderUpdateRequest: path.join(repoRoot, 'backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'rider', 'model', 'dto', 'RiderUpdateRequest.java'),
  dispatchCreateRiderRequest: path.join(repoRoot, 'backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'dispatch', 'api', 'DispatchCreateRiderRequest.java'),
  dispatchRiderUpdateRequest: path.join(repoRoot, 'backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'dispatch', 'api', 'DispatchRiderUpdateRequest.java'),
  dispatchController: path.join(repoRoot, 'backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'dispatch', 'api', 'DispatchController.java'),
  dispatchService: path.join(repoRoot, 'backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'dispatch', 'service', 'DispatchService.java'),
  dispatchServiceImpl: path.join(repoRoot, 'backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'dispatch', 'service', 'impl', 'DispatchServiceImpl.java'),
  riderAdminServiceImpl: path.join(repoRoot, 'backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'rider', 'service', 'impl', 'RiderAdminServiceImpl.java'),
  riderDropPasswordMigration: path.join(repoRoot, 'backend', 'src', 'main', 'resources', 'db', 'migration', 'V40__drop_rider_password_hash.sql')
};

for (const [label, file] of Object.entries(files)) {
  assert.ok(fs.existsSync(file), `${label} missing: ${file}`);
}

const customerAppJson = fs.readFileSync(files.customerApp, 'utf8');
const riderAppJson = fs.readFileSync(files.riderApp, 'utf8');

assert.equal(
  customerAppJson.includes('pages/login/index'),
  true,
  'miniapp/app.json 缺少独立登录页路由'
);

assert.equal(
  customerAppJson.includes('__usePrivacyCheck__'),
  true,
  'miniapp/app.json 缺少 __usePrivacyCheck__ 隐私检查开关'
);

assert.equal(
  customerAppJson.includes('"requiredPrivateInfos"'),
  false,
  'miniapp/app.json 不应再保留 requiredPrivateInfos 隐私能力声明'
);

assert.equal(
  customerAppJson.includes('"getPhoneNumber"'),
  false,
  'miniapp/app.json 不应再通过 requiredPrivateInfos 声明 getPhoneNumber'
);

assert.equal(
  riderAppJson.includes('pages/login/index'),
  true,
  'miniapp-rider/app.json 缺少独立登录页路由'
);

assert.equal(
  riderAppJson.includes('__usePrivacyCheck__'),
  true,
  'miniapp-rider/app.json 缺少 __usePrivacyCheck__ 隐私检查开关'
);

assert.equal(
  riderAppJson.includes('"requiredPrivateInfos"'),
  false,
  'miniapp-rider/app.json 不应再保留 getPhoneNumber 隐私能力声明'
);

assert.equal(
  riderAppJson.includes('"getPhoneNumber"'),
  false,
  'miniapp-rider/app.json 不应再保留 getPhoneNumber 能力声明'
);

const customerProfile = fs.readFileSync(files.customerProfile, 'utf8');
const customerProfileWxml = fs.readFileSync(files.customerProfileWxml, 'utf8');
const customerLogin = fs.readFileSync(files.customerLogin, 'utf8');
const customerLoginWxml = fs.readFileSync(files.customerLoginWxml, 'utf8');
const riderProfile = fs.readFileSync(files.riderProfile, 'utf8');
const riderProfileWxml = fs.readFileSync(files.riderProfileWxml, 'utf8');
const riderLogin = fs.readFileSync(files.riderLogin, 'utf8');
const riderLoginWxml = fs.readFileSync(files.riderLoginWxml, 'utf8');
const customerPrivacy = fs.readFileSync(files.customerPrivacy, 'utf8');
const riderImageUtil = fs.readFileSync(files.riderImageUtil, 'utf8');
const riderAuthUtil = fs.readFileSync(files.riderAuthUtil, 'utf8');
const riderAuthService = fs.readFileSync(files.riderAuthService, 'utf8');
const riderQueueJs = fs.readFileSync(path.join(repoRoot, 'miniapp-rider', 'pages', 'queue', 'index.js'), 'utf8');
const riderQueueWxml = fs.readFileSync(path.join(repoRoot, 'miniapp-rider', 'pages', 'queue', 'index.wxml'), 'utf8');
const riderPasswordLoginRequest = path.join(repoRoot, 'backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'mobile', 'api', 'RiderPasswordLoginRequest.java');
const riderMobileAuthController = fs.readFileSync(files.riderMobileAuthController, 'utf8');
const riderMobileAuthService = fs.readFileSync(files.riderMobileAuthService, 'utf8');
const riderEntity = fs.readFileSync(files.riderEntity, 'utf8');
const riderCreateRequest = fs.readFileSync(files.riderCreateRequest, 'utf8');
const riderUpdateRequest = fs.readFileSync(files.riderUpdateRequest, 'utf8');
const dispatchCreateRiderRequest = fs.readFileSync(files.dispatchCreateRiderRequest, 'utf8');
const dispatchRiderUpdateRequest = fs.readFileSync(files.dispatchRiderUpdateRequest, 'utf8');
const dispatchController = fs.readFileSync(files.dispatchController, 'utf8');
const dispatchService = fs.readFileSync(files.dispatchService, 'utf8');
const dispatchServiceImpl = fs.readFileSync(files.dispatchServiceImpl, 'utf8');
const riderAdminServiceImpl = fs.readFileSync(files.riderAdminServiceImpl, 'utf8');
const riderDropPasswordMigration = fs.readFileSync(files.riderDropPasswordMigration, 'utf8');

assert.equal(
  customerLogin.includes('getPhonePrivacyErrorMessage'),
  true,
  'miniapp/pages/login/index.js 未接入手机号隐私错误兜底逻辑'
);

assert.equal(
  customerLogin.includes('agreementAccepted'),
  true,
  'miniapp/pages/login/index.js 缺少业务协议同意状态'
);

assert.equal(
  customerLogin.includes("miniapp_customer_auth_agreement_accepted_v2"),
  true,
  'miniapp/pages/login/index.js 应升级协议持久化版本，避免沿用错误历史勾选状态'
);

assert.equal(
  customerLogin.includes('openAgreementSheet('),
  true,
  'miniapp/pages/login/index.js 缺少协议抽屉打开逻辑'
);

assert.equal(
  customerLogin.includes('completeAgreementAndContinueWechat'),
  false,
  'miniapp/pages/login/index.js 不应在协议弹窗内直接拉起微信登录'
);

assert.equal(
  customerLogin.includes('ensurePhonePrivacyPermission'),
  true,
  'miniapp/pages/login/index.js 缺少微信隐私授权预处理'
);

assert.equal(
  customerLogin.includes('startWechatLoginFlow('),
  true,
  'miniapp/pages/login/index.js 缺少统一微信登录入口'
);

assert.equal(
  customerLogin.includes('isWechatBusy()'),
  true,
  'miniapp/pages/login/index.js 缺少统一微信登录忙碌态判断'
);

assert.equal(
  customerLogin.includes('198.18.0.1:7777/event'),
  false,
  'miniapp/pages/login/index.js 不应继续向本地调试代理上报事件'
);

assert.equal(
  customerLogin.includes('192.168.1.3:7777/event'),
  false,
  'miniapp/pages/login/index.js 不应继续向局域网调试地址上报事件'
);

assert.equal(
  customerLogin.includes('wx.onNeedPrivacyAuthorization'),
  false,
  'miniapp/pages/login/index.js 不应再混用自定义隐私拦截和业务协议弹层'
);

assert.equal(
  customerLogin.includes('agreementSheetChecked: this.data.agreementSheetChecked'),
  true,
  'miniapp/pages/login/index.js 关闭协议抽屉后应保留临时勾选状态'
);

assert.match(
  customerLogin,
  /toggleAgreementSheetChecked\(\)\s*\{[\s\S]*const nextChecked = !this\.data\.agreementSheetChecked;[\s\S]*agreementSheetChecked:\s*nextChecked[\s\S]*\}/,
  'miniapp/pages/login/index.js 勾选协议时只应更新弹窗内勾选态'
);

assert.equal(
  /toggleAgreementSheetChecked\(\)\s*\{[\s\S]*agreementAccepted:\s*nextChecked[\s\S]*\}/.test(customerLogin),
  false,
  'miniapp/pages/login/index.js 勾选协议时不应直接把正式同意状态写死'
);

assert.equal(
  customerProfile.includes("wx.navigateTo({ url: '/pages/login/index' })"),
  true,
  'miniapp/pages/profile/index.js 应跳转到独立登录页'
);

assert.equal(
  customerProfileWxml.includes('auth-popup-container'),
  false,
  'miniapp/pages/profile/index.wxml 不应再保留登录弹窗结构'
);

assert.equal(
  riderLogin.includes('getPhonePrivacyErrorMessage'),
  false,
  'miniapp-rider/pages/login/index.js 不应再保留微信手机号隐私兜底逻辑'
);

assert.equal(
  riderLogin.includes('agreementAccepted'),
  true,
  'miniapp-rider/pages/login/index.js 缺少业务协议同意状态'
);

assert.equal(
  riderLogin.includes("miniapp_rider_auth_agreement_accepted_v2"),
  true,
  'miniapp-rider/pages/login/index.js 应升级协议持久化版本，避免沿用错误历史勾选状态'
);

assert.equal(
  riderLogin.includes('openAgreementSheet('),
  true,
  'miniapp-rider/pages/login/index.js 缺少协议抽屉打开逻辑'
);

assert.equal(
  riderLogin.includes('completeAgreementAndContinueWechat'),
  false,
  'miniapp-rider/pages/login/index.js 不应在协议弹窗内直接拉起微信登录'
);

assert.equal(
  riderLogin.includes('ensurePhonePrivacyPermission'),
  false,
  'miniapp-rider/pages/login/index.js 不应再保留微信隐私授权预处理'
);

assert.equal(
  riderLogin.includes('startWechatLoginFlow('),
  false,
  'miniapp-rider/pages/login/index.js 不应再保留微信登录入口'
);

assert.equal(
  riderLogin.includes('isWechatBusy()'),
  false,
  'miniapp-rider/pages/login/index.js 不应再保留微信登录忙碌态判断'
);

assert.equal(
  riderLogin.includes('wx.onNeedPrivacyAuthorization'),
  false,
  'miniapp-rider/pages/login/index.js 不应再混用自定义隐私拦截和业务协议弹层'
);

assert.equal(
  riderLogin.includes('agreementSheetChecked: this.data.agreementSheetChecked'),
  true,
  'miniapp-rider/pages/login/index.js 关闭协议抽屉后应保留临时勾选状态'
);

assert.equal(
  riderQueueJs.includes('specialSummary'),
  false,
  'miniapp-rider/pages/queue/index.js 不应继续保留特殊单摘要'
);

assert.equal(
  riderQueueWxml.includes('special-tag-row'),
  false,
  'miniapp-rider/pages/queue/index.wxml 不应继续保留特殊单前置提醒区域'
);

assert.equal(
  riderQueueWxml.includes("item.itemStatus === 'DELIVERED' ? 'order-card-completed' : ''"),
  true,
  'miniapp-rider/pages/queue/index.wxml 应按已完成状态统一切换整卡样式'
);

assert.equal(
  riderQueueWxml.includes("currentStatusFilter === '' ? 'order-card-completed'"),
  false,
  'miniapp-rider/pages/queue/index.wxml 不应把完成态样式绑在全部筛选条件上'
);

assert.match(
  riderLogin,
  /toggleAgreementSheetChecked\(\)\s*\{[\s\S]*const nextChecked = !this\.data\.agreementSheetChecked;[\s\S]*agreementSheetChecked:\s*nextChecked[\s\S]*\}/,
  'miniapp-rider/pages/login/index.js 勾选协议时只应更新弹窗内勾选态'
);

assert.equal(
  /toggleAgreementSheetChecked\(\)\s*\{[\s\S]*agreementAccepted:\s*nextChecked[\s\S]*\}/.test(riderLogin),
  false,
  'miniapp-rider/pages/login/index.js 勾选协议时不应直接把正式同意状态写死'
);

assert.equal(
  riderProfile.includes("wx.navigateTo({ url: '/pages/login/index' })"),
  true,
  'miniapp-rider/pages/profile/index.js 应跳转到独立登录页'
);

assert.equal(
  riderProfile.includes('ensurePhonePrivacyPermission'),
  false,
  'miniapp-rider/pages/profile/index.js 不应再保留微信隐私授权逻辑'
);

assert.equal(
  riderProfile.includes('doLoginWithCode('),
  false,
  'miniapp-rider/pages/profile/index.js 不应再保留微信手机号登录逻辑'
);

assert.equal(
  riderProfile.includes('wechatLoading'),
  false,
  'miniapp-rider/pages/profile/index.js 不应再保留微信登录忙碌态'
);

assert.equal(
  riderProfile.includes('showAuthPopup'),
  false,
  'miniapp-rider/pages/profile/index.js 不应再保留旧登录弹窗状态'
);

assert.equal(
  riderProfileWxml.includes('auth-popup-container'),
  false,
  'miniapp-rider/pages/profile/index.wxml 不应再保留登录弹窗结构'
);

assert.equal(
  customerProfile.includes('192.168.1.3:7777/event'),
  false,
  'miniapp/pages/profile/index.js 仍保留本地调试事件上报'
);

assert.equal(
  riderProfile.includes('192.168.1.3:7777/event'),
  false,
  'miniapp-rider/pages/profile/index.js 仍保留本地调试事件上报'
);

assert.equal(
  riderImageUtil.includes('127.0.0.1:7778/event'),
  false,
  'miniapp-rider/utils/image.js 仍保留本地图片调试上报'
);

assert.equal(
  riderAuthUtil.includes('127.0.0.1:7778/event'),
  false,
  'miniapp-rider/utils/auth.js 仍保留本地认证调试上报'
);

assert.equal(
  customerLoginWxml.includes('《隐私政策》') || customerLoginWxml.includes('查看隐私政策'),
  true,
  'miniapp/pages/login/index.wxml 缺少隐私政策入口'
);

assert.equal(
  customerLoginWxml.includes('我已阅读并同意'),
  true,
  'miniapp/pages/login/index.wxml 缺少协议勾选文案'
);

assert.equal(
  customerLoginWxml.includes('auth-agreement-sheet'),
  true,
  'miniapp/pages/login/index.wxml 缺少协议展开区'
);

assert.equal(
  customerLoginWxml.includes('auth-agreement-bar'),
  true,
  'miniapp/pages/login/index.wxml 缺少登录页协议栏'
);

assert.equal(
  customerLoginWxml.includes("agreementAccepted || agreementSheetChecked"),
  false,
  'miniapp/pages/login/index.wxml 未正式同意前不应在外层协议栏显示已勾选'
);

assert.equal(
  customerLoginWxml.includes('auth-page-shell'),
  true,
  'miniapp/pages/login/index.wxml 缺少独立登录页容器'
);

assert.equal(
  customerLoginWxml.includes('auth-agreement-modal-mask'),
  true,
  'miniapp/pages/login/index.wxml 缺少协议弹窗遮罩层'
);

assert.equal(
  customerLoginWxml.includes('auth-agreement-modal-panel'),
  true,
  'miniapp/pages/login/index.wxml 缺少居中协议弹窗容器'
);

assert.equal(
  customerLoginWxml.includes('bindtap="closeAgreementSheet"'),
  true,
  'miniapp/pages/login/index.wxml 缺少协议弹窗关闭交互'
);

assert.equal(
  customerLoginWxml.includes('open-type="getPhoneNumber"') && customerLoginWxml.includes('bindgetphonenumber="completeAgreementAndContinueWechat"'),
  false,
  'miniapp/pages/login/index.wxml 不应在协议弹窗里直接拉起微信登录'
);

assert.equal(
  customerLoginWxml.includes('bindtap="openAgreementSheetForWechat"'),
  true,
  'miniapp/pages/login/index.wxml 缺少未同意时的微信登录拦截按钮'
);

assert.equal(
  customerLoginWxml.includes('bindgetphonenumber="getPhoneNumber"'),
  true,
  'miniapp/pages/login/index.wxml 缺少统一微信手机号事件绑定'
);

assert.equal(
  customerLoginWxml.includes('disabled="{{savingProfile || wechatLoading}}"'),
  true,
  'miniapp/pages/login/index.wxml 微信登录按钮缺少统一忙碌态禁用'
);

assert.equal(
  customerLoginWxml.includes('我已阅读并同意'),
  true,
  'miniapp/pages/login/index.wxml 缺少极简协议文案'
);

assert.equal(
  customerLoginWxml.includes('查看用户协议'),
  false,
  'miniapp/pages/login/index.wxml 不应再额外显示独立的查看用户协议入口'
);

assert.equal(
  customerLoginWxml.includes('查看隐私政策'),
  false,
  'miniapp/pages/login/index.wxml 不应再额外显示独立的查看隐私政策入口'
);

assert.equal(
  customerLoginWxml.includes('点击微信一键登录后，将按微信官方规则拉起手机号与隐私授权。'),
  false,
  'miniapp/pages/login/index.wxml 不应保留冗长登录提示文案'
);

assert.equal(
  riderLoginWxml.includes('《隐私政策》') || riderLoginWxml.includes('查看隐私政策'),
  true,
  'miniapp-rider/pages/login/index.wxml 缺少隐私政策入口'
);

assert.equal(
  riderLoginWxml.includes('我已阅读并同意'),
  true,
  'miniapp-rider/pages/login/index.wxml 缺少协议勾选文案'
);

assert.equal(
  riderLoginWxml.includes('auth-agreement-sheet'),
  true,
  'miniapp-rider/pages/login/index.wxml 缺少协议展开区'
);

assert.equal(
  riderLoginWxml.includes('auth-agreement-bar'),
  true,
  'miniapp-rider/pages/login/index.wxml 缺少登录页协议栏'
);

assert.equal(
  riderLoginWxml.includes("agreementAccepted || agreementSheetChecked"),
  false,
  'miniapp-rider/pages/login/index.wxml 未正式同意前不应在外层协议栏显示已勾选'
);

assert.equal(
  riderLoginWxml.includes('auth-page-shell'),
  true,
  'miniapp-rider/pages/login/index.wxml 缺少独立登录页容器'
);

assert.equal(
  riderLoginWxml.includes('auth-agreement-modal-mask'),
  true,
  'miniapp-rider/pages/login/index.wxml 缺少协议弹窗遮罩层'
);

assert.equal(
  riderLoginWxml.includes('auth-agreement-modal-panel'),
  true,
  'miniapp-rider/pages/login/index.wxml 缺少居中协议弹窗容器'
);

assert.equal(
  riderLoginWxml.includes('bindtap="closeAgreementSheet"'),
  true,
  'miniapp-rider/pages/login/index.wxml 缺少协议弹窗关闭交互'
);

assert.equal(
  riderLoginWxml.includes('open-type="getPhoneNumber"') && riderLoginWxml.includes('bindgetphonenumber="completeAgreementAndContinueWechat"'),
  false,
  'miniapp-rider/pages/login/index.wxml 不应在协议弹窗里直接拉起微信登录'
);

assert.equal(
  riderLoginWxml.includes('bindtap="openAgreementSheetForWechat"'),
  false,
  'miniapp-rider/pages/login/index.wxml 不应再保留微信登录拦截按钮'
);

assert.equal(
  riderLoginWxml.includes('bindgetphonenumber="getPhoneNumber"'),
  false,
  'miniapp-rider/pages/login/index.wxml 不应再保留微信手机号事件绑定'
);

assert.equal(
  riderLoginWxml.includes('骑手微信登录'),
  false,
  'miniapp-rider/pages/login/index.wxml 不应再展示骑手微信登录按钮'
);

assert.equal(
  riderLoginWxml.includes('我已阅读并同意'),
  true,
  'miniapp-rider/pages/login/index.wxml 缺少极简协议文案'
);

assert.equal(
  riderLoginWxml.includes('查看骑手服务协议'),
  false,
  'miniapp-rider/pages/login/index.wxml 不应再额外显示独立的查看协议入口'
);

assert.equal(
  riderLoginWxml.includes('查看隐私政策'),
  false,
  'miniapp-rider/pages/login/index.wxml 不应再额外显示独立的查看隐私政策入口'
);

assert.equal(
  riderLoginWxml.includes('点击骑手微信登录后，将按微信官方规则拉起手机号与隐私授权。'),
  false,
  'miniapp-rider/pages/login/index.wxml 不应保留冗长登录提示文案'
);

const customerProfileWxss = fs.readFileSync(path.join(repoRoot, 'miniapp', 'pages', 'login', 'index.wxss'), 'utf8');
const riderProfileWxss = fs.readFileSync(path.join(repoRoot, 'miniapp-rider', 'pages', 'login', 'index.wxss'), 'utf8');

assert.equal(
  customerProfileWxss.includes('width: 320rpx !important') || customerProfileWxss.includes('width: 300rpx !important'),
  true,
  'miniapp/pages/login/index.wxss 应将登录按钮缩短为更克制的宽度'
);

assert.equal(
  customerProfileWxss.includes('max-height: 86vh') && customerProfileWxss.includes('overflow-y: auto'),
  true,
  'miniapp/pages/login/index.wxss 缺少页面滚动兼容处理'
);

assert.equal(
  customerProfileWxss.includes('.auth-agreement-modal-mask') &&
    customerProfileWxss.includes('position: fixed') &&
    customerProfileWxss.includes('background: rgba(15, 23, 42, 0.48)'),
  true,
  'miniapp/pages/login/index.wxss 缺少真正的协议弹窗遮罩样式'
);

assert.equal(
  riderProfileWxss.includes('width: 320rpx !important') || riderProfileWxss.includes('width: 300rpx !important'),
  true,
  'miniapp-rider/pages/login/index.wxss 应将登录按钮缩短为更克制的宽度'
);

assert.equal(
  riderProfileWxss.includes('max-height: 86vh') && riderProfileWxss.includes('overflow-y: auto'),
  true,
  'miniapp-rider/pages/login/index.wxss 缺少页面滚动兼容处理'
);

assert.equal(
  riderProfileWxss.includes('.auth-agreement-modal-mask') &&
    riderProfileWxss.includes('position: fixed') &&
    riderProfileWxss.includes('background: rgba(15, 23, 42, 0.48)'),
  true,
  'miniapp-rider/pages/login/index.wxss 缺少真正的协议弹窗遮罩样式'
);

assert.equal(
  customerPrivacy.includes('errno:112') || customerPrivacy.includes('privacy agreement'),
  true,
  'miniapp/utils/privacy-auth.js 未覆盖隐私协议未声明能力错误'
);

assert.equal(
  customerPrivacy.includes('微信手机号能力未生效'),
  true,
  'miniapp/utils/privacy-auth.js 应对 no permission 给出更准确的手机号能力提示'
);

assert.equal(
  riderAuthUtil.includes('bindPhone('),
  false,
  'miniapp-rider/utils/auth.js 不应再保留骑手微信绑手机号登录'
);

assert.equal(
  riderAuthUtil.includes("const RIDER_WX_LOGIN_URL = '/api/mobile/rider-auth/wx-login';"),
  false,
  'miniapp-rider/utils/auth.js 不应再保留骑手微信登录接口常量'
);

assert.equal(
  riderAuthUtil.includes('/api/mobile/rider-auth/wx-login'),
  false,
  'miniapp-rider/utils/auth.js 不应再发起骑手微信静默登录'
);

assert.equal(
  riderAuthUtil.includes('bindPhone('),
  false,
  'miniapp-rider/utils/auth.js 不应再暴露骑手微信绑手机号方法'
);

assert.equal(
  riderAuthService.includes('riderWechatLogin'),
  false,
  'miniapp-rider/services/auth.service.js 不应再保留骑手微信登录服务'
);

assert.equal(
  riderAuthService.includes('bindPhone('),
  false,
  'miniapp-rider/services/auth.service.js 不应再保留骑手微信绑手机号服务'
);

assert.equal(
  riderAuthService.includes('passwordLogin('),
  false,
  'miniapp-rider/services/auth.service.js 不应再保留骑手密码登录服务'
);

assert.equal(
  riderMobileAuthController.includes('@PostMapping("/login")'),
  false,
  'MobileRiderAuthController 不应再暴露骑手密码登录接口'
);

assert.equal(
  fs.existsSync(riderPasswordLoginRequest),
  false,
  'RiderPasswordLoginRequest 应已删除'
);

assert.equal(
  riderMobileAuthService.includes('riderPasswordLogin('),
  false,
  'MobileAuthService 不应再保留骑手密码登录服务定义'
);

assert.equal(
  riderEntity.includes('password_hash'),
  false,
  'RiderEntity 不应再映射 password_hash 字段'
);

assert.equal(
  riderCreateRequest.includes('String password'),
  false,
  'RiderCreateRequest 不应再包含密码字段'
);

assert.equal(
  riderUpdateRequest.includes('String password'),
  false,
  'RiderUpdateRequest 不应再包含密码字段'
);

assert.equal(
  dispatchCreateRiderRequest.includes('String password'),
  false,
  'DispatchCreateRiderRequest 不应再包含密码字段'
);

assert.equal(
  dispatchRiderUpdateRequest.includes('String password'),
  false,
  'DispatchRiderUpdateRequest 不应再包含密码字段'
);

assert.equal(
  dispatchController.includes('request.password()'),
  false,
  'DispatchController 不应再传递骑手密码参数'
);

assert.equal(
  dispatchService.includes('String password'),
  false,
  'DispatchService 不应再声明骑手密码参数'
);

assert.equal(
  dispatchServiceImpl.includes('password_hash'),
  false,
  'DispatchServiceImpl 不应再读写骑手 password_hash'
);

assert.equal(
  riderAdminServiceImpl.includes('setPasswordHash'),
  false,
  'RiderAdminServiceImpl 不应再写入骑手密码哈希'
);

assert.equal(
  riderDropPasswordMigration.includes('DROP COLUMN password_hash'),
  true,
  '数据库迁移应删除 rider_profiles.password_hash 字段'
);

console.log('PASS: 两个小程序已补齐手机号隐私适配防护');
