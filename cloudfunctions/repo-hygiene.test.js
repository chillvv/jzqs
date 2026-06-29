const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const cloudFunctionsRoot = __dirname;
const functionDirs = ['cleanStorage', 'cleanupReceipts'];

for (const dir of functionDirs) {
  test(`${dir} should keep a local .gitignore for deploy dependencies`, () => {
    const gitignorePath = path.join(cloudFunctionsRoot, dir, '.gitignore');
    assert.equal(fs.existsSync(gitignorePath), true);

    const content = fs.readFileSync(gitignorePath, 'utf8');
    assert.match(content, /node_modules\//);
  });

  test(`${dir} should not keep checked-in node_modules directory`, () => {
    const nodeModulesPath = path.join(cloudFunctionsRoot, dir, 'node_modules');
    assert.equal(fs.existsSync(nodeModulesPath), false);
  });
}
