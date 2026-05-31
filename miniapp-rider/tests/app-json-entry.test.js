const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const appJson = JSON.parse(
  fs.readFileSync(path.join(__dirname, '..', 'app.json'), 'utf8')
);

test('miniapp-rider should open profile page first so unauthenticated rider lands in 我的', () => {
  assert.equal(appJson.pages[0], 'pages/profile/index');
});

test('miniapp-rider should not ship debug test pages in app entry list', () => {
  assert.equal(
    appJson.pages.some((page) => page.startsWith('pages/test-')),
    false
  );
});
