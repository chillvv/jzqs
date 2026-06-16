const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const file = path.join(__dirname, "..", "admin", "src", "shared", "components", "DatePicker.tsx");
const source = fs.readFileSync(file, "utf8");

assert.match(
  source,
  /typeof value === ["']string["']/,
  "DatePicker 应在 split 前先判断 value 是否为字符串"
);

assert.match(
  source,
  /value instanceof Date/,
  "DatePicker 应兼容直接传入 Date 对象"
);

console.log("PASS: DatePicker 已对非字符串 value 做防御处理");
