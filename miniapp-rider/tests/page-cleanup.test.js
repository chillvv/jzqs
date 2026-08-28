const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

test('miniapp-rider should not keep legacy today v2 page files', () => {
  const legacyTodayFile = path.join(__dirname, '..', 'pages', 'today', 'index-v2.js');
  assert.equal(fs.existsSync(legacyTodayFile), false);
});

for (const pageDir of ['completed', 'today', 'rider', 'test-cloud', 'test-login', 'test-upload']) {
  test(`miniapp-rider should not keep orphan ${pageDir} page files`, () => {
    const orphanPageDir = path.join(__dirname, '..', 'pages', pageDir);
    assert.equal(fs.existsSync(orphanPageDir), false);
  });
}
