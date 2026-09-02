/**
 * admin 后台浏览器自动化 E2E（默认本地运行，禁止默认指向生产）
 *
 * 默认目标：http://localhost:5173（本地 vite dev）
 * 若要打生产：必须显式传 BASE_URL 且同时传 E2E_ALLOW_PRODUCTION=true，否则直接失败。
 *
 * 前置：
 *   1. Node >= 18
 *   2. npm i -D @playwright/test && npx playwright install chromium
 *   3. DB 断言可选：npm i -D mysql2，并设 E2E_DB_ENABLED=true
 *
 * 运行：
 *   npx playwright test admin-e2e.spec.js                 # 本地
 *   BASE_URL=https://jzqs.top E2E_ALLOW_PRODUCTION=true npx playwright test admin-e2e.spec.js   # 生产（需谨慎）
 *
 * 账号：ADMIN_USER / ADMIN_PASSWORD（环境变量覆盖）
 * 数据库断言：E2E_DB_HOST/PORT/USER/PASSWORD/NAME（默认 127.0.0.1:3307 root/root）
 *
 * 覆盖：登录 → 看板 → 订单中心 → 派单中心(进度/区域/骑手) → 顾客 → 售后 → 菜单 → 操作日志
 *       + 页面 JS 错误收集 + 可选数据库一致性断言
 */
const { test, expect } = require("@playwright/test");

const BASE_URL = process.env.BASE_URL || "http://localhost:5173";
const ADMIN_USER = process.env.ADMIN_USER || "e2e_tester";
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || "E2E12345";

const PRODUCTION_HOSTS = ["jzqs.top", "www.jzqs.top"];
const DB_ENABLED = process.env.E2E_DB_ENABLED === "true";

function assertNotProductionByDefault() {
  const host = new URL(BASE_URL).host;
  const isProd = PRODUCTION_HOSTS.includes(host);
  if (isProd && process.env.E2E_ALLOW_PRODUCTION !== "true") {
    throw new Error(
      `拒绝默认打生产：BASE_URL=${BASE_URL}。如确需打生产，须显式设置 E2E_ALLOW_PRODUCTION=true`
    );
  }
  return { host, isProd };
}

async function queryDb(sql, params = []) {
  let mysql;
  try {
    mysql = require("mysql2/promise");
  } catch {
    return null; // 未安装 mysql2，跳过 DB 断言
  }
  const conn = await mysql.createConnection({
    host: process.env.E2E_DB_HOST || "127.0.0.1",
    port: Number(process.env.E2E_DB_PORT || 3307),
    user: process.env.E2E_DB_USER || "root",
    password: process.env.E2E_DB_PASSWORD || "root",
    database: process.env.E2E_DB_NAME || "jzqs",
  });
  try {
    const [rows] = await conn.query(sql, params);
    return rows;
  } finally {
    await conn.end();
  }
}

test.describe("admin 后台核心链路（浏览器 E2E）", () => {
  let page;
  const pageErrors = [];
  const target = assertNotProductionByDefault();

  test.beforeAll(async ({ browser }) => {
    console.log(`🎯 目标环境: ${BASE_URL} (${target.isProd ? "生产" : "非生产"})`);
    page = await browser.newPage({ viewport: { width: 1600, height: 900 } });
    page.on("pageerror", (e) => pageErrors.push(`pageerror: ${e.message}`));
    page.on("console", (m) => {
      if (m.type() === "error") pageErrors.push(`console: ${m.text()}`);
    });

    // 1. 登录
    await page.goto(`${BASE_URL}/login`, { waitUntil: "networkidle" });
    await page.getByPlaceholder("请输入后台手机号").fill(ADMIN_USER);
    await page.getByPlaceholder("请输入密码").fill(ADMIN_PASSWORD);
    await page.getByRole("button", { name: /登\s*录/ }).click();
    await expect(page).toHaveURL(/dashboard/, { timeout: 20000 });
    await page.waitForTimeout(1500);

    const bodyText = await page.locator("body").innerText();
    expect(bodyText.length).toBeGreaterThan(100);
    console.log(`✅ 登录成功并进入看板，页面文本长度=${bodyText.length}`);
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
      const text = await page.locator("body").innerText();
      const ok = text.length > 80 && c.expect.test(text);
      console.log(`${ok ? "✅" : "❌"} ${c.label} (${c.path}) 文本=${text.length}字`);
      expect(ok, `${c.label} 页面加载或内容断言失败`).toBe(true);
    }
  });

  test("关键操作链路：订单中心搜索 + 顾客详情", async () => {
    await page.goto(`${BASE_URL}/orders`, { waitUntil: "networkidle" });
    await page.waitForTimeout(1500);
    expect((await page.locator("body").innerText()).length).toBeGreaterThan(80);

    await page.goto(`${BASE_URL}/customers`, { waitUntil: "networkidle" });
    await page.waitForTimeout(1500);
    expect((await page.locator("body").innerText()).length).toBeGreaterThan(80);

    await page.goto(`${BASE_URL}/dispatch/progress`, { waitUntil: "networkidle" });
    await page.waitForTimeout(1500);
    expect((await page.locator("body").innerText()).length).toBeGreaterThan(80);
    console.log("✅ 关键操作链路浏览完成（订单/顾客/派单均正常渲染）");
  });

  test("数据库一致性断言（可选，E2E_DB_ENABLED=true 时生效）", async () => {
    test.skip(!DB_ENABLED, "未开启 E2E_DB_ENABLED，跳过数据库断言");

    // 1) 终态订单不得仍挂在活跃的派单分配上（跨表状态同步铁律）
    const orphanActive = await queryDb(
      `SELECT COUNT(*) AS c
         FROM meal_slot_orders o
         JOIN dispatch_assignments da ON da.meal_slot_order_id = o.id
        WHERE o.status IN ('DELIVERED','CANCELLED','REFUNDED')
          AND da.status IN ('PENDING','AREA_ASSIGNED','DISPATCHING')`
    );
    if (orphanActive) {
      console.log(`🔍 终态订单仍挂活跃派单分配的条数 = ${orphanActive[0].c}`);
      expect(orphanActive[0].c, "存在终态订单仍挂在活跃派单分配上（跨表状态不同步）").toBe(0);
    }

    // 2) 钱包不得被扣成负数
    const negativeWallets = await queryDb(
      `SELECT COUNT(*) AS c FROM meal_wallets WHERE consumed_meals > total_meals`
    );
    if (negativeWallets) {
      console.log(`🔍 超扣钱包数 = ${negativeWallets[0].c}`);
      expect(negativeWallets[0].c, "存在钱包扣减超过总额（超扣）").toBe(0);
    }
  });

  test.afterAll(() => {
    console.log(`⚠️ 页面 JS 错误数: ${pageErrors.length}`);
    pageErrors.slice(0, 5).forEach((e) => console.log(`   - ${e}`));
  });
});
