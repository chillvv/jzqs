import pymysql

EXPECTED_CHECKSUM = -1437274189

conn = pymysql.connect(
    host="127.0.0.1",
    port=3306,
    user="root",
    password="root",
    database="jzqs",
    charset="utf8mb4",
)
cur = conn.cursor()

cur.execute("UPDATE flyway_schema_history SET checksum = %s WHERE version = '30'", (EXPECTED_CHECKSUM,))
conn.commit()

cur.execute("SELECT version, description, checksum, success FROM flyway_schema_history WHERE version = '30'")
row = cur.fetchone()

with open("tmp_v30_repair_output.txt", "w", encoding="utf-8") as f:
    f.write(f"{row}\n")

cur.close()
conn.close()
