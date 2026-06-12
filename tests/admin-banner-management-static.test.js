const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const repoRoot = path.resolve(__dirname, "..");

function read(...segments) {
  return fs.readFileSync(path.join(repoRoot, ...segments), "utf8");
}

const settingsPage = read("admin", "src", "modules", "settings", "SystemSettingsPage.tsx");
const settingsModal = read("admin", "src", "shared", "components", "SettingsModal.tsx");

assert.equal(
  fs.existsSync(path.join(repoRoot, "uploads", "settings-banners", "manual", "green.jpg")),
  true,
  "green.jpg 应放入独立的轮播图图片目录 uploads/settings-banners/manual 中"
);

assert.equal(
  settingsPage.includes("点击图片查看大图"),
  true,
  "轮播图管理页应提示支持点击图片查看大图"
);

assert.equal(
  settingsPage.includes("previewBannerImage")
    || settingsPage.includes("bannerPreviewImage")
    || settingsPage.includes("setPreviewImage"),
  true,
  "轮播图管理页应具备点击缩略图查看大图的预览状态"
);

assert.equal(
  settingsModal.includes("submitting"),
  true,
  "设置弹窗应支持提交中的状态，避免保存时看起来没反应"
);

assert.equal(
  settingsModal.includes("保存中..."),
  true,
  "设置弹窗提交时应明确展示保存中提示"
);

console.log("PASS: 轮播图目录、点击放大与保存状态静态验收通过");
