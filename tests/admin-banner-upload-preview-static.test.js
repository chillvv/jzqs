const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const repoRoot = path.resolve(__dirname, "..");
const viteConfig = fs.readFileSync(path.join(repoRoot, "admin", "vite.config.ts"), "utf8");

assert.equal(
  viteConfig.includes('"/uploads"'),
  true,
  "管理端本地开发必须代理 /uploads 到后端，否则后台看不到上传后的轮播图"
);

assert.equal(
  /target:\s*"http:\/\/localhost:8081"/.test(viteConfig),
  true,
  "管理端本地开发的上传图片代理应指向后端 8081"
);

console.log("PASS: 管理端本地轮播图预览代理已配置");
