const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const repoRoot = path.resolve(__dirname, '..');

function read(...segments) {
  return fs.readFileSync(path.join(repoRoot, ...segments), 'utf8');
}

const settingsPage = read('admin', 'src', 'modules', 'settings', 'SystemSettingsPage.tsx');
const adminHttp = read('admin', 'src', 'shared', 'api', 'http.ts');
const settingsController = read('backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'settings', 'api', 'SettingsController.java');
const settingsService = read('backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'settings', 'service', 'SettingsService.java');
const settingsServiceImpl = read('backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'settings', 'service', 'impl', 'SettingsServiceImpl.java');
const homeJs = read('miniapp', 'pages', 'home', 'index.js');
const homeWxml = read('miniapp', 'pages', 'home', 'index.wxml');
const homeWxss = read('miniapp', 'pages', 'home', 'index.wxss');

assert.equal(
  settingsPage.includes('图片 JSON 数组'),
  false,
  'SystemSettingsPage 不应继续让运营手填图片 JSON 数组'
);

assert.equal(
  settingsPage.includes('type="file"'),
  true,
  'SystemSettingsPage 应提供轮播图图片上传入口'
);

assert.equal(
  adminHttp.includes('/api/admin/settings/banner-images/upload'),
  true,
  '管理端 API 应提供轮播图上传接口'
);

assert.equal(
  settingsController.includes('/banner-images/upload'),
  true,
  'SettingsController 应暴露轮播图上传接口'
);

for (const banned of ['接单通道', '暂停接单', '恢复接单', '接单状态']) {
  assert.equal(
    settingsPage.includes(banned),
    false,
    `SystemSettingsPage 不应再保留 ${banned} 相关入口`
  );
}

assert.equal(
  settingsPage.includes('轮播秒数'),
  true,
  'SystemSettingsPage 应支持配置轮播秒数'
);

assert.equal(
  settingsPage.includes('点击动作') || settingsPage.includes('页面路径') || settingsPage.includes('标题') || settingsPage.includes('说明'),
  false,
  'SystemSettingsPage 不应继续保留轮播图标题说明跳转配置'
);

assert.equal(
  settingsPage.includes('关闭公告'),
  true,
  'SystemSettingsPage 应提供关闭公告按钮'
);

assert.equal(
  homeWxml.includes('fullscreen-announcement'),
  true,
  '小程序首页应存在自定义公告弹层结构'
);

assert.equal(
  homeJs.includes('wx.navigateTo({') || homeJs.includes('wx.switchTab({'),
  false,
  '小程序首页轮播图点击后不应再跳转页面'
);

assert.equal(
  homeWxml.includes('item.actionType') || homeWxml.includes('点击跳转') || homeWxml.includes('item.title') || homeWxml.includes('item.description'),
  false,
  '小程序首页轮播图不应继续显示标题说明和跳转提示'
);

assert.equal(
  /interval="\{\{home\.bannerIntervalMs \|\| 3000\}\}"/.test(homeWxml),
  true,
  '小程序首页轮播图应读取后台下发的轮播间隔'
);

assert.equal(
  settingsServiceImpl.includes('actionType') || settingsServiceImpl.includes('actionTarget'),
  false,
  'SettingsServiceImpl 不应继续保留轮播图动作字段'
);

assert.equal(
  settingsService.includes('pauseOrderingWithNotice('),
  true,
  'SettingsService 仍应保留公告控制能力'
);

assert.equal(
  settingsController.includes('popup-announcement'),
  true,
  'SettingsController 应继续暴露公告接口'
);

assert.equal(
  homeWxss.includes('.fullscreen-announcement'),
  true,
  '小程序首页应存在公告弹层样式'
);

console.log('PASS: 系统公告与轮播图极简链路已闭环');
