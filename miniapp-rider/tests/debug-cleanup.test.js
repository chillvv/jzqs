const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const authPath = path.join(__dirname, '..', 'utils', 'auth.js');
const imagePath = path.join(__dirname, '..', 'utils', 'image.js');

function read(filePath) {
  return fs.readFileSync(filePath, 'utf8');
}

test('rider auth utility does not keep debug-point markers or no-op debug reporters', () => {
  const content = read(authPath);

  assert.equal(content.includes('#region debug-point'), false);
  assert.equal(content.includes('function reportAuthDebug'), false);
});

test('rider image utility does not keep debug-point markers or no-op debug reporters', () => {
  const content = read(imagePath);

  assert.equal(content.includes('#region debug-point'), false);
  assert.equal(content.includes('function reportImageDebug'), false);
});
