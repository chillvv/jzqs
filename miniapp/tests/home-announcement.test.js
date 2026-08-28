const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

const homePagePath = path.join(__dirname, '..', 'pages', 'home', 'index.js');
const requestModulePath = path.join(__dirname, '..', 'utils', 'request.js');

function applySetData(target, patch) {
  Object.keys(patch).forEach((key) => {
    target.data[key] = patch[key];
  });
}

function loadHomePage({ homeOverride = {} } = {}) {
  let pageConfig = null;
  const app = {
    globalData: {
      token: 'test-token',
      apiBaseUrl: 'http://localhost:8080'
    },
    waitForAuth() {
      return Promise.resolve();
    }
  };

  global.Page = function registerPage(config) {
    pageConfig = config;
  };

  global.getApp = () => app;

  global.wx = {
    showToast() {},
    stopPullDownRefresh() {}
  };

  delete require.cache[require.resolve(requestModulePath)];
  require.cache[require.resolve(requestModulePath)] = {
    id: requestModulePath,
    filename: requestModulePath,
    loaded: true,
    exports: {
      request({ url }) {
        if (url === '/api/mobile/customer/home') {
          return Promise.resolve({
            orderingEnabled: true,
            popupAnnouncementEnabled: true,
            popupAnnouncementContent: '系统维护中',
            holidayNoticeTitle: '系统公告',
            bannerImages: [],
            ...homeOverride
          });
        }
        if (url === '/api/mobile/customer/menu/current-week') {
          return Promise.resolve({
            weekStartDate: '2026-05-25',
            weekEndDate: '2026-05-31',
            days: []
          });
        }
        return Promise.reject(new Error(`unexpected url: ${url}`));
      }
    }
  };

  delete require.cache[require.resolve(homePagePath)];
  require(homePagePath);

  assert.ok(pageConfig, 'home page should register via Page()');

  const page = {
    data: JSON.parse(JSON.stringify(pageConfig.data || {})),
    setData(patch) {
      applySetData(this, patch);
    },
    showGuide() {},
    startAnnouncementPolling() {},
    stopAnnouncementPolling() {},
    syncMealReminderPopup() {},
    applyWeekTab(tab) {
      const index = Number(tab) === 1 ? 1 : 0;
      this.setData({
        weekTab: index,
        weekTitle: index === 1 ? '下周主厨菜单' : '本周主厨菜单',
        weekCards: [],
        weekUnpublished: false
      });
    }
  };

  return { app, page, pageConfig };
}

test('全屏公告开启时 fullscreenAnnouncementLines 被填充', async () => {
  const { page, pageConfig } = loadHomePage({
    homeOverride: {
      popupAnnouncementEnabled: true,
      popupAnnouncementContent: '系统维护中\n预计明天恢复'
    }
  });

  await pageConfig.loadPageData.call(page);

  assert.ok(page.data.fullscreenAnnouncementLines.length > 0);
  assert.equal(page.data.fullscreenAnnouncementLines[0], '系统维护中');
  assert.equal(page.data.fullscreenAnnouncementLines[1], '预计明天恢复');
});

test('全屏公告关闭时 fullscreenAnnouncementLines 为空', async () => {
  const { page, pageConfig } = loadHomePage({
    homeOverride: {
      popupAnnouncementEnabled: false,
      popupAnnouncementContent: ''
    }
  });

  await pageConfig.loadPageData.call(page);

  assert.equal(page.data.fullscreenAnnouncementLines.length, 0);
});

test('全屏公告不依赖 token（游客也可见公告）', async () => {
  const { page, pageConfig } = loadHomePage({
    homeOverride: {
      popupAnnouncementEnabled: true,
      popupAnnouncementContent: '紧急通知'
    }
  });

  await pageConfig.loadPageData.call(page);

  assert.ok(page.data.fullscreenAnnouncementLines.length > 0);
});
