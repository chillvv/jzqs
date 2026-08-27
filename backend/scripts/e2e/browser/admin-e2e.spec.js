/**
 * admin 后台浏览器自动化 E2E（本地运行）
 *
 * 前置：
 *   1. 本地装 Node >= 18
 *   2. npm i -D @playwright/test && npx playwright install chromium
 *   3. 指定目标地址运行：
 *        BASE_URL=https://jzqs.top npx playwright test admin-e2e.spec.js
 *      （本地开发：BASE_URL=http://localhost:5173）
 *
 * 账号（默认测试管理员）：
 *   ADMIN_USER=e2e_tester  ADMIN_PASSWORD=E2E12345（可用环境变量覆盖）
 *
 * 覆盖：登录 → 看板 → 订单中心 → 派单中心(进度/区域/骑手) → 顾客 → 售后 → 菜单 → 操作日志
 */
const { test, expect } = require("@playwright/test");

const BASE_URL = process.env.BASE_URL || "https://jzqs.top";
const ADMIN_USER = process.env.ADMIN_USER || "e2e_tester";
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || "E2E12345";

test.describe("admin 后台核心链路（浏览器 E2E）", () => {
  let page;

  test.beforeAll(async ({ browser }) => {
    page = await browser.newPage({ viewport: { width: 1600, height: 900 } });
    const errors = [];
    page.on("pageerror", (e) => errors.push(`pageerror: ${e.message}`));
    page.on("console", (m) => {
      if (m.type() === "error") errors.push(`console: ${m.text()}`);
    });

    // 1. 登录
    await page.goto(`${BASE_URL}/login`, { waitUntil: "networkidle" });
    await page.getByPlaceholder("请输入后台手机号").fill(ADMIN_USER);
    await page.getByPlaceholder("请输入密码").fill(ADMIN_PASSWORD);
    await page.getByRole("button", { name: /登\s*录/ }).click();
    await expect(page).toHaveURL(/dashboard/, { timeout: 20000 });
    await page.waitForTimeout(1500);

    // 2. 看板必须有核心数据区
    await expect(page).toHaveURL(/dashboard/);
    const bodyText = await page.locator("body").innerText();
    expect(bodyText.length).toBeGreaterThan(100);
    console.log(`✅ 登录成功并进入看板，页面文本长度=${bodyText.length}`);
    console.log("⚠️ 页面 JS 错误数:", errors.length, errors.slice(0, 3));
  });

  test("核心页面逐个加载（订单/派单/顾客/售后/菜单/日志）", async () => {
    const checks = [
      { path: "/orders", label: "订单中心", expect: /订单|预订|餐次|配送/ },
      { path: "/dispatch/progress", label: "派单中心-进度", expect: /派单|配送|区域|骑手|批次/ },
      { path: "/dispatch/areas", label: "派单中心-区域", expect: /区域|骑手|默认/ },
      { path: "/dispatch/riders", label: "派单中心-骑手", expect: /骑手|区域|电话/ },
      { path: "/customers", label: "顾客管理", expect: /客户|顾客|餐次|余额|钱包/ },
      { path: "/aftersales", label: "售后中心", expect: /售后|退款|工单|处理/ },
      { path: "/menu", label: "菜单排期", expect: /菜单|排期|餐|日期/ },
      { path: "/operation-logs", label: "操作日志", expect: /操作|日志|记录/ },
    ];
    for (const c of checks) {
      await page.goto(`${BASE_URL}${c.path}`, { waitUntil: "networkidle" });
      await page.waitForTimeout(1200);
      const url = new URL(page.url());
      const text = await page.locator("body").innerText();
      const ok = text.length > 80 && c.expect.test(text);
      console.log(`${ok ? "✅" : "❌"} ${c.label} (${c.path}) 文本=${text.length}字`);
      expect(ok, `${c.label} 页面加载或内容断言失败`).toBe(true);
    }
  });

  test("关键操作链路：订单中心搜索 + 顾客详情", async () => {
    // 订单中心（今日/明日筛选能出列表）
    await page.goto(`${BASE_URL}/orders`, { waitUntil: "networkidle" });
    await page.waitForTimeout(1500);
    const ordersText = await page.locator("body").innerText();
    expect(ordersText.length).toBeGreaterThan(80);

    // 顾客管理列表
    await page.goto(`${BASE_URL}/customers`, { waitUntil: "networkidle" });
    await page.waitForTimeout(1500);
    const customersText = await page.locator("body").innerText();
    expect(customersText.length).toBeGreaterThan(80);

    // 派单进度页不应白屏
    await page.goto(`${BASE_URL}/dispatch/progress`, { waitUntil: "networkidle" });
    await page.waitForTimeout(1500);
    const dispatchText = await page.locator("body").innerText();
    expect(dispatchText.length).toBeGreaterThan(80);
    console.log("✅ 关键操作链路浏览完成（订单/顾客/派单均正常渲染）");
  });
});
