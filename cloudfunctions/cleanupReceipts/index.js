/**
 * 云函数：cleanupReceipts
 * 功能：删除前一天的回执图片（cloud:// 格式的文件ID）
 * 触发方式：定时触发器，每天凌晨3点执行
 *
 * 工作流程：
 * 1. 调用后端 API 获取昨天及之前的回执文件ID列表
 * 2. 批量删除云存储中的文件
 * 3. 通知后端标记这些记录已清理（避免重复删除）
 */

const cloud = require('wx-server-sdk');

cloud.init({
  env: cloud.DYNAMIC_CURRENT_ENV
});

// 后端 API 地址 —— 替换为你的实际域名
const API_BASE_URL = 'https://your-domain.com';

exports.main = async (event, context) => {
  console.log('[cleanupReceipts] 开始执行');
  const startedAt = new Date().toISOString();

  const result = {
    scanned: 0,
    deleted: 0,
    failed: 0,
    errors: []
  };

  try {
    // 1. 从后端获取需要删除的 cloud:// 文件ID列表
    const expiredResult = await fetchExpiredFileIds();
    const fileIds = expiredResult.fileIds;
    result.scanned = fileIds.length;

    if (fileIds.length === 0) {
      console.log('[cleanupReceipts] 没有需要删除的文件，退出');
      await reportMaintenanceJob({
        jobType: 'CLOUD_RECEIPT_CLEANUP',
        status: 'SUCCESS',
        timeRangeLabel: `删除 ${expiredResult.cutoff} 之前的回执云图片`,
        startedAt,
        finishedAt: new Date().toISOString(),
        scannedCount: 0,
        deletedCount: 0,
        failedCount: 0,
        message: 'cleanupReceipts 无需清理',
        metadata: { cutoff: expiredResult.cutoff, requested: 0 }
      });
      return result;
    }

    console.log('[cleanupReceipts] 待删除文件数:', fileIds.length);

    // 2. 分批删除云存储文件（每批最多50个，微信限制）
    const BATCH_SIZE = 50;
    const deletedIds = [];

    for (let i = 0; i < fileIds.length; i += BATCH_SIZE) {
      const batch = fileIds.slice(i, i + BATCH_SIZE);

      try {
        const deleteResult = await cloud.deleteFile({ fileList: batch });

        deleteResult.fileList.forEach(file => {
          if (file.status === 0) {
            result.deleted++;
            deletedIds.push(file.fileID);
          } else {
            result.failed++;
            const msg = `${file.fileID}: ${file.errMsg}`;
            result.errors.push(msg);
            console.warn('[cleanupReceipts] 单文件删除失败:', msg);
          }
        });

        console.log(`[cleanupReceipts] 批次 ${Math.floor(i / BATCH_SIZE) + 1} 完成，本批 ${batch.length} 个`);
      } catch (batchError) {
        console.error('[cleanupReceipts] 批次删除异常:', batchError.message);
        result.failed += batch.length;
        result.errors.push(batchError.message);
      }
    }

    // 3. 通知后端标记已删除（避免下次重复删除）
    if (deletedIds.length > 0) {
      await notifyBackendDeleted(deletedIds);
    }

  } catch (error) {
    console.error('[cleanupReceipts] 执行失败:', error.message);
    result.errors.push(error.message);
  }

  await reportMaintenanceJob({
    jobType: 'CLOUD_RECEIPT_CLEANUP',
    status: result.failed > 0 ? 'PARTIAL_SUCCESS' : (result.errors.length > 0 ? 'FAILED' : 'SUCCESS'),
    timeRangeLabel: '删除今天 00:00 之前的回执云图片',
    startedAt,
    finishedAt: new Date().toISOString(),
    scannedCount: result.scanned,
    deletedCount: result.deleted,
    failedCount: result.failed,
    message: result.errors.length > 0 ? 'cleanupReceipts 执行失败' : 'cleanupReceipts 执行完成',
    errorDetail: result.errors.join('\n'),
    metadata: { errors: result.errors }
  });

  console.log('[cleanupReceipts] 执行完成:', JSON.stringify(result));
  return result;
};

/**
 * 从后端获取需要删除的 cloud:// 文件ID列表
 */
async function fetchExpiredFileIds() {
  const https = require('https');
  const url = `${API_BASE_URL}/api/internal/receipts/expired-file-ids`;

  return new Promise((resolve, reject) => {
    https.get(url, (res) => {
      let data = '';
      res.on('data', chunk => { data += chunk; });
      res.on('end', () => {
        try {
          const json = JSON.parse(data);
          const payload = {
            fileIds: json.data?.fileIds || [],
            cutoff: json.data?.cutoff || '今天 00:00'
          };
          console.log('[cleanupReceipts] 后端返回待删除文件数:', payload.fileIds.length);
          resolve(payload);
        } catch (e) {
          reject(new Error('解析后端响应失败: ' + e.message));
        }
      });
    }).on('error', (err) => {
      reject(new Error('请求后端失败: ' + err.message));
    });
  });
}

/**
 * 通知后端标记文件已从云存储删除
 */
async function notifyBackendDeleted(deletedIds) {
  const https = require('https');
  const url = new URL(`${API_BASE_URL}/api/internal/receipts/mark-cloud-deleted`);
  const body = JSON.stringify({ fileIds: deletedIds });

  return new Promise((resolve) => {
    const req = https.request({
      hostname: url.hostname,
      path: url.pathname,
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(body)
      }
    }, (res) => {
      res.on('data', () => {});
      res.on('end', () => {
        console.log('[cleanupReceipts] 已通知后端标记删除，状态码:', res.statusCode);
        resolve();
      });
    });

    req.on('error', (err) => {
      // 通知失败不影响主流程，只记录日志
      console.warn('[cleanupReceipts] 通知后端失败（不影响删除结果）:', err.message);
      resolve();
    });

    req.write(body);
    req.end();
  });
}

async function reportMaintenanceJob(payload) {
  const https = require('https');
  const url = new URL(`${API_BASE_URL}/api/internal/maintenance/cloud-job-logs`);
  const body = JSON.stringify(payload);

  return new Promise((resolve) => {
    const req = https.request({
      hostname: url.hostname,
      path: url.pathname,
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(body)
      }
    }, (res) => {
      res.on('data', () => {});
      res.on('end', () => {
        console.log('[cleanupReceipts] 维护日志上报状态码:', res.statusCode);
        resolve();
      });
    });

    req.on('error', (err) => {
      console.warn('[cleanupReceipts] 维护日志上报失败（不影响主流程）:', err.message);
      resolve();
    });

    req.write(body);
    req.end();
  });
}
