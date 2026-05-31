-- 给回执表添加云存储删除标记字段
-- 用于记录云存储中的图片是否已被云函数删除，避免重复删除
ALTER TABLE delivery_receipts
    ADD COLUMN cloud_deleted BOOLEAN NOT NULL DEFAULT FALSE;

-- 添加索引，加速云函数查询待删除文件
CREATE INDEX idx_delivery_receipts_cloud_cleanup
    ON delivery_receipts (delivered_at, cloud_deleted);
