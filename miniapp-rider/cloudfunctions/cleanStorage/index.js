/**
 * 云函数：cleanStorage
 * 功能：删除前一天的回执图片
 * 触发方式：定时触发器，每天凌晨3点执行
 *
 * 工作原理：
 * 1. 骑手上传图片时，会往云数据库 receipt_files 集合写一条记录
 * 2. 本云函数查询 uploadedAt < 今天0点 的记录
 * 3. 批量删除云存储文件
 * 4. 删除云数据库中对应的记录
 */

const cloud = require('wx-server-sdk');

cloud.init({ env: cloud.DYNAMIC_CURRENT_ENV });

exports.main = async (event, context) => {
  const db = cloud.database();
  const _ = db.command;

  // 截止时间：今天0点（昨天及之前的图片全部删除）
  const today = new Date();
  today.setHours(0, 0, 0, 0);

  console.log('[cleanStorage] 开始执行，删除', today.toLocaleDateString('zh-CN'), '之前的图片');

  const result = { scanned: 0, deleted: 0, failed: 0 };

  try {
    // 1. 查询需要删除的记录（每次最多500条）
    const queryRes = await db.collection('receipt_files')
      .where({ uploadedAt: _.lt(today) })
      .limit(500)
      .get();

    const records = queryRes.data || [];
    result.scanned = records.length;

    if (records.length === 0) {
      console.log('[cleanStorage] 没有需要删除的文件，退出');
      return result;
    }

    console.log('[cleanStorage] 待删除文件数:', records.length);

    // 2. 分批删除云存储文件（每批最多50个，微信限制）
    const BATCH_SIZE = 50;
    const successDocIds = [];

    for (let i = 0; i < records.length; i += BATCH_SIZE) {
      const batch = records.slice(i, i + BATCH_SIZE);
      const fileIds = batch.map(r => r.fileID);

      try {
        const deleteRes = await cloud.deleteFile({ fileList: fileIds });

        deleteRes.fileList.forEach((file, idx) => {
          if (file.status === 0) {
            result.deleted++;
            successDocIds.push(batch[idx]._id);
          } else {
            result.failed++;
            console.warn('[cleanStorage] 文件删除失败:', file.fileID, file.errMsg);
          }
        });
      } catch (err) {
        result.failed += batch.length;
        console.error('[cleanStorage] 批次异常:', err.message);
      }
    }

    // 3. 删除云数据库中已成功删除的记录
    for (const docId of successDocIds) {
      await db.collection('receipt_files').doc(docId).remove().catch(() => {});
    }

    console.log('[cleanStorage] 完成:', JSON.stringify(result));
  } catch (err) {
    console.error('[cleanStorage] 执行失败:', err.message);
    result.error = err.message;
  }

  return result;
};
