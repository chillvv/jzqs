const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const rootDir = path.resolve(__dirname, '..');

function walkFiles(dir, extension, result = []) {
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === 'node_modules') {
        continue;
      }
      walkFiles(fullPath, extension, result);
      continue;
    }
    if (entry.name.endsWith(extension)) {
      result.push(fullPath);
    }
  }
  return result;
}

test('miniapp-rider wxss files should not start with utf8 bom', () => {
  const files = walkFiles(rootDir, '.wxss');
  assert.ok(files.length > 0);
  for (const file of files) {
    const bytes = fs.readFileSync(file);
    const hasBom = bytes.length >= 3 && bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf;
    assert.equal(hasBom, false, `${path.relative(rootDir, file)} contains UTF-8 BOM`);
  }
});
