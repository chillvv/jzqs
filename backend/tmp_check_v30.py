import pymysql
import zlib

conn = pymysql.connect(
    host="127.0.0.1",
    port=3306,
    user="root",
    password="root",
    database="jzqs",
    charset="utf8mb4",
)
cur = conn.cursor()

lines = []

cur.execute("SELECT version, description, checksum, success FROM flyway_schema_history WHERE version = '30'")
lines.append(f"history = {cur.fetchone()}")

for name in ["related_transaction_id", "refunded", "refund_reason_code", "refund_reason_text"]:
    cur.execute(f"SHOW COLUMNS FROM wallet_transactions LIKE '{name}'")
    lines.append(f"{name} = {cur.fetchone()}")

for name in ["idx_wallet_transactions_related_transaction", "idx_wallet_transactions_related_aftersale"]:
    cur.execute(f"SHOW INDEX FROM wallet_transactions WHERE Key_name = '{name}'")
    lines.append(f"{name} = {cur.fetchone()}")

cur.close()
conn.close()

for path in [
    "src/main/resources/db/migration/V30__aftersale_refund_traceability.sql",
    "target/classes/db/migration/V30__aftersale_refund_traceability.sql",
]:
    with open(path, "r", encoding="utf-8") as f:
        crc = 0
        for line in f.read().splitlines():
            crc = zlib.crc32((line + "\n").encode("utf-8"), crc)
        if crc > 0x7FFFFFFF:
            crc -= 0x100000000
        lines.append(f"flyway_line_crc[{path}] = {crc}")

with open("tmp_v30_output.txt", "w", encoding="utf-8") as f:
    f.write("\n".join(lines) + "\n")
