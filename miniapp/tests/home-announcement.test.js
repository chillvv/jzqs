const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

const homePagePath = path.join(__dirname, '..', 'pages', 'home', 'index.js');
const requestModulePath = path.join(__dirname, '..', 'utils', 'request.js');

function applySetData(target, patch) {
  Object.keys(patch).forEach((key) => {
    const segments = key.split('.');
    let cursor = target;
    for (let index = 0; index < segments.length - 1; index += 1) {
      const segment = segments[index];
      if (!cursor[segment] || typeof cursor[segment] !== 'object') {
        cursor[segment] = {};
      }
      cursor = cursor[segment];
    }
    cursor[segments[segments.length - 1]] = patch[key];
  });
}

function loadHomePage({ token = null, homeOverride = {} } = {}) {
  let pageConfig = null;
  const showModalCalls = [];
  const requestCalls = [];
  const app = {
    globalData: {
      token,
      announcementShown: false
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
    showModal(options) {
      showModalCalls.push(options);
      if (typeof options.success === 'function') {
        options.success({ confirm: true });
      }
    },
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
        requestCalls.push(url);
        if (url === '/api/mobile/customer/home') {
          return Promise.resolve({
            orderingEnabled: true,
            popupAnnouncementEnabled: true,
            popupAnnouncementContent: '仅登录后应看到的公告',
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
      applySetData(this.data, patch);
    }
  };

  return {
    app,
    page,
    pageConfig,
    requestCalls,
    showModalCalls
  };
}

test('guest home load does not show popup announcement', async () => {
  const { page, pageConfig, showModalCalls } = loadHomePage({ token: null });

  await pageConfig.loadPageData.call(page);

  assert.equal(showModalCalls.length, 0);
});
