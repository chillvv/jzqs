-- 去除 menu_weeks 中重复的周（相同 week_start_date），保留 id 最小的一条
-- 同时删除属于被移除重复周的 menu_week_items

DELETE FROM menu_week_items
WHERE week_id IN (
    SELECT id FROM menu_weeks
    WHERE week_start_date IN (
        SELECT week_start_date
        FROM menu_weeks
        GROUP BY week_start_date
        HAVING COUNT(*) > 1
    )
    AND id NOT IN (
        SELECT MIN(id)
        FROM menu_weeks
        GROUP BY week_start_date
    )
);

DELETE FROM menu_weeks
WHERE week_start_date IN (
    SELECT week_start_date FROM (
        SELECT week_start_date
        FROM menu_weeks
        GROUP BY week_start_date
        HAVING COUNT(*) > 1
    ) AS dup
)
AND id NOT IN (
    SELECT min_id FROM (
        SELECT MIN(id) AS min_id
        FROM menu_weeks
        GROUP BY week_start_date
    ) AS keep
);

-- 给 menu_weeks.week_start_date 加唯一索引，防止重复插入同一周
CREATE UNIQUE INDEX uk_menu_weeks_week_start_date ON menu_weeks (week_start_date);
