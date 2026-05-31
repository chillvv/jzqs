import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const dashboardPage = fs.readFileSync(path.join(root, "src/modules/dashboard/DashboardPage.tsx"), "utf8");
const layoutPage = fs.readFileSync(path.join(root, "src/app/layout/AdminLayout.tsx"), "utf8");

assert.equal(dashboardPage.includes('useNavigate'), true, "DashboardPage should use navigate for entry buttons");
assert.equal(dashboardPage.includes('navigate("/customers")'), true, "DashboardPage should navigate to customers");
assert.equal(dashboardPage.includes('navigate("/orders")'), true, "DashboardPage should navigate to orders");
assert.equal(layoutPage.includes("今日重点"), false, "AdminLayout should not contain guide block title");
assert.equal(layoutPage.includes("先处理 `每日运营台`"), false, "AdminLayout should not contain guide copy");

console.log("dashboard entry test: ok");
