const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const migrationDir = path.join(__dirname, '..', 'backend', 'src', 'main', 'resources', 'db', 'migration');
const files = fs.readdirSync(migrationDir).filter((file) => /^V\d+__.+\.sql$/.test(file));
const versions = new Map();

for (const file of files) {
  const match = file.match(/^V(\d+)__/);
  if (!match) {
    continue;
  }
  const version = match[1];
  const list = versions.get(version) || [];
  list.push(file);
  versions.set(version, list);
}

const duplicates = Array.from(versions.entries()).filter(([, list]) => list.length > 1);
const unifiedNotesMigration = path.join(
  migrationDir,
  'V41__unified_notes_and_dispatch_redesign.sql'
);

assert.equal(
  duplicates.length,
  0,
  `Flyway migration 版本号不能重复，当前冲突: ${duplicates
    .map(([version, list]) => `V${version} -> ${list.join(', ')}`)
    .join('; ')}`
);

assert.ok(
  fs.existsSync(unifiedNotesMigration),
  '缺少统一备注重构迁移文件: V41__unified_notes_and_dispatch_redesign.sql'
);

const migrationContent = fs.readFileSync(unifiedNotesMigration, 'utf8');

assert.ok(
  migrationContent.includes('CREATE TABLE customer_notes'),
  'V41 必须创建 customer_notes'
);

assert.ok(
  migrationContent.includes('CREATE TABLE order_notes'),
  'V41 必须创建 order_notes'
);

console.log('PASS: Flyway migration 版本号唯一');
