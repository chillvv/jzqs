const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

const pagePath = path.join(__dirname, '..', 'pages', 'order', 'index.js');

test('guest checkout opens inline auth sheet', () => {
  let pageConfig = null;
  global.Page = (config) => {
    pageConfig = config;
  };
  global.getApp = () => ({ globalData: { token: null } });
  global.wx = {
    showToast() {},
    showModal() {},
    switchTab() {}
  };

  delete require.cache[require.resolve(pagePath)];
  require(pagePath);

  const page = {
    data: { ...pageConfig.data },
    setData(patch) {
      Object.assign(this.data, patch);
    }
  };

  pageConfig.goToCheckout.call(page);

  assert.equal(page.data.showInlineAuth, true);
});
