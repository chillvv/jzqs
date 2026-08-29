/**
 * 播种 admin E2E 测试账号（本地与 CI 通用）
 *
 * 用法：node seed-e2e-user.js
 * 环境变量（可选）：E2E_DB_HOST / E2E_DB_PORT / E2E_DB_USER / E2E_DB_PASSWORD / E2E_DB_NAME
 *                 E2E_ADMIN_USER / E2E_ADMIN_PASSWORD（默认 e2e_tester / E2E12345）
 *
 * 密码哈希与后端 PasswordUtils.hash(password, phone) 完全一致：
 *   {sha256} + sha256(utf8(password + ":" + phone)) 的 hex
 * 登录接口按 phone + password 校验（AdminAuthService.login），故 username=phone。
 */
const mysql = require("mysql2/promise");
const crypto = require("crypto");

async function main() {
  const host = process.env.E2E_DB_HOST || "127.0.0.1";
  const port = Number(process.env.E2E_DB_PORT || 3307);
  const user = process.env.E2E_DB_USER || "root";
  const password = process.env.E2E_DB_PASSWORD || "root";
  const database = process.env.E2E_DB_NAME || "jzqs";

  const adminUser = process.env.E2E_ADMIN_USER || "e2e_tester";
  const adminPassword = process.env.E2E_ADMIN_PASSWORD || "E2E12345";

  const hash =
    "{sha256}" +
    crypto.createHash("sha256").update(`${adminPassword}:${adminUser}`, "utf8").digest("hex");

  const conn = await mysql.createConnection({ host, port, user, password, database });
  try {
    await conn.query("DELETE FROM users WHERE username = ?", [adminUser]);
    await conn.query(
      "INSERT INTO users (username, phone, display_name, role, status, password_hash) VALUES (?, ?, 'E2E Tester', 'ADMIN', 'ENABLED', ?)",
      [adminUser, adminUser, hash]
    );
    console.log(`✅ seeded e2e admin user: ${adminUser} (db ${host}:${port}/${database})`);
  } finally {
    await conn.end();
  }
}

main().catch((err) => {
  console.error("❌ seed failed:", err.message);
  process.exit(1);
});
