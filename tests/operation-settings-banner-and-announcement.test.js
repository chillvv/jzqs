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

assert.equal(
  settingsPage.includes('/api/admin/settings/ordering/pause-with-notice') ||
    adminHttp.includes('/api/admin/settings/ordering/pause-with-notice'),
  true,
  '停单流程应改为公告编辑后再执行暂停'
);

assert.equal(
  settingsService.includes('pauseOrderingWithNotice('),
  true,
  'SettingsService 应提供保存公告并暂停接单的原子方法'
);

assert.equal(
  settingsServiceImpl.includes('ordering_enabled = FALSE'),
  true,
  'SettingsServiceImpl 应在保存公告时一并关闭接单通道'
);

assert.equal(
  homeJs.includes('wx.showModal({'),
  false,
  '小程序首页不应继续使用系统原生 showModal 展示特殊情况公告'
);

assert.equal(
  homeWxml.includes('announcement-modal'),
  true,
  '小程序首页应存在自定义公告弹层结构'
);

assert.equal(
  homeWxss.includes('.announcement-modal'),
  true,
  '小程序首页应存在公告弹层样式'
);

console.log('PASS: 运营设置轮播图上传与停单公告链路已闭环');
