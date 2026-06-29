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

const https = require('https');
const cloud = require('wx-server-sdk');

cloud.init({
  env: cloud.DYNAMIC_CURRENT_ENV
});

const REQUEST_TIMEOUT_MS = 10000;

exports.main = async (event, context) => {
  console.log('[cleanupReceipts] 开始执行');
  const startedAt = new Date().toISOString();
  const { apiBaseUrl, internalApiToken } = readRequiredConfig();
  let cutoffLabel = '今天 00:00';

  const result = {
    scanned: 0,
    deleted: 0,
    failed: 0,
    errors: [],
    warnings: []
  };

  try {
    const firstBatch = await fetchExpiredFileIds(apiBaseUrl, internalApiToken);
    cutoffLabel = firstBatch.cutoff;
    if (firstBatch.fileIds.length === 0) {
      console.log('[cleanupReceipts] 没有需要删除的文件，退出');
      await reportMaintenanceJob(apiBaseUrl, internalApiToken, {
        jobType: 'CLOUD_RECEIPT_CLEANUP',
        status: 'SUCCESS',
        timeRangeLabel: `删除 ${firstBatch.cutoff} 之前的回执云图片`,
        startedAt,
        finishedAt: new Date().toISOString(),
        scannedCount: 0,
        deletedCount: 0,
        failedCount: 0,
        message: 'cleanupReceipts 无需清理',
        metadata: { cutoff: firstBatch.cutoff, requested: 0 }
      });
      return result;
    }

    await drainExpiredFileIds(
      async () => fetchExpiredFileIds(apiBaseUrl, internalApiToken),
      async (expiredResult, round) => {
        const fileIds = expiredResult.fileIds;
        cutoffLabel = expiredResult.cutoff || cutoffLabel;
        result.scanned += fileIds.length;
        console.log(`[cleanupReceipts] 第 ${round} 轮待删除文件数:`, fileIds.length);

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

            console.log(`[cleanupReceipts] 第 ${round} 轮批次 ${Math.floor(i / BATCH_SIZE) + 1} 完成，本批 ${batch.length} 个`);
          } catch (batchError) {
            console.error('[cleanupReceipts] 批次删除异常:', batchError.message);
            result.failed += batch.length;
            result.errors.push(batchError.message);
          }
        }

        // 3. 每轮完成后立即通知后端标记已删除，再拉取下一轮
        if (deletedIds.length > 0) {
          try {
            await notifyBackendDeleted(apiBaseUrl, internalApiToken, deletedIds);
          } catch (err) {
            result.warnings.push(`通知后端标记删除失败: ${err.message}`);
            console.warn('[cleanupReceipts] 通知后端失败（不影响删除结果）:', err.message);
          }
        }
      },
      firstBatch
    );

  } catch (error) {
    console.error('[cleanupReceipts] 执行失败:', error.message);
    result.errors.push(error.message);
  }

  await reportMaintenanceJob(apiBaseUrl, internalApiToken, {
    jobType: 'CLOUD_RECEIPT_CLEANUP',
    status: result.failed > 0 ? 'PARTIAL_SUCCESS' : (result.errors.length > 0 ? 'FAILED' : 'SUCCESS'),
    timeRangeLabel: `删除 ${cutoffLabel} 之前的回执云图片`,
    startedAt,
    finishedAt: new Date().toISOString(),
    scannedCount: result.scanned,
    deletedCount: result.deleted,
    failedCount: result.failed,
    message: result.errors.length > 0 ? 'cleanupReceipts 执行失败' : 'cleanupReceipts 执行完成',
    errorDetail: result.errors.join('\n'),
    metadata: { errors: result.errors, warnings: result.warnings }
  });

  console.log('[cleanupReceipts] 执行完成:', JSON.stringify(result));
  return result;
};

/**
 * 从后端获取需要删除的 cloud:// 文件ID列表
 */
function readRequiredConfig(env = process.env) {
  const apiBaseUrl = env.API_BASE_URL;
  const internalApiToken = env.INTERNAL_API_TOKEN;

  if (!apiBaseUrl || !/^https:\/\//.test(apiBaseUrl)) {
    throw new Error('API_BASE_URL 未配置或不是合法的 https 地址');
  }
  if (!internalApiToken || internalApiToken === 'change_this_to_an_internal_call_secret') {
    throw new Error('INTERNAL_API_TOKEN 未配置或仍为占位值');
  }

  return {
    apiBaseUrl: apiBaseUrl.replace(/\/+$/, ''),
    internalApiToken
  };
}

function requestJson(httpClient, method, targetUrl, token, body) {
  const url = new URL(targetUrl);
  const payload = body ? JSON.stringify(body) : null;

  return new Promise((resolve, reject) => {
    const req = httpClient.request({
      hostname: url.hostname,
      path: `${url.pathname}${url.search}`,
      method,
      timeout: REQUEST_TIMEOUT_MS,
      headers: {
        'X-Internal-Token': token,
        ...(payload ? {
          'Content-Type': 'application/json',
          'Content-Length': Buffer.byteLength(payload)
        } : {})
      }
    }, (res) => {
      let data = '';
      res.on('data', (chunk) => {
        data += chunk;
      });
      res.on('end', () => {
        if (res.statusCode < 200 || res.statusCode >= 300) {
          reject(new Error(`请求失败: ${res.statusCode} ${data}`.trim()));
          return;
        }
        if (!data.trim()) {
          resolve(null);
          return;
        }
        try {
          resolve(JSON.parse(data));
        } catch (err) {
          reject(new Error(`解析响应失败: ${err.message}`));
        }
      });
    });

    req.on('timeout', () => {
      req.destroy(new Error('请求超时'));
    });
    req.on('error', reject);

    if (payload) {
      req.write(payload);
    }
    req.end();
  });
}

async function fetchExpiredFileIds(apiBaseUrl, internalApiToken) {
  const json = await requestJson(
    https,
    'GET',
    `${apiBaseUrl}/api/internal/receipts/expired-file-ids`,
    internalApiToken
  );

  const payload = {
    fileIds: json?.data?.fileIds || [],
    cutoff: json?.data?.cutoff || '今天 00:00'
  };
  console.log('[cleanupReceipts] 后端返回待删除文件数:', payload.fileIds.length);
  return payload;
}

async function drainExpiredFileIds(fetcher, processor, firstBatch) {
  const pull = typeof fetcher === 'function' ? fetcher : fetchExpiredFileIds;
  const handleBatch = typeof processor === 'function' ? processor : async () => {};
  let round = 0;
  let currentBatch = firstBatch;
  while (round < 20) {
    const batch = currentBatch || await pull();
    currentBatch = null;
    const fileIds = Array.isArray(batch?.fileIds) ? batch.fileIds : [];
    if (fileIds.length === 0) {
      break;
    }
    round += 1;
    await handleBatch(batch, round);
    if (fileIds.length < 500) {
      break;
    }
  }
}

/**
 * 通知后端标记文件已从云存储删除
 */
async function notifyBackendDeleted(apiBaseUrl, internalApiToken, deletedIds) {
  await requestJson(
    https,
    'POST',
    `${apiBaseUrl}/api/internal/receipts/mark-cloud-deleted`,
    internalApiToken,
    { fileIds: deletedIds }
  );
  console.log('[cleanupReceipts] 已通知后端标记删除');
}

async function reportMaintenanceJob(apiBaseUrl, internalApiToken, payload) {
  try {
    await requestJson(
      https,
      'POST',
      `${apiBaseUrl}/api/internal/maintenance/cloud-job-logs`,
      internalApiToken,
      payload
    );
  } catch (err) {
    console.warn('[cleanupReceipts] 维护日志上报失败（不影响主流程）:', err.message);
  }
}

module.exports = {
  main: exports.main,
  __test__: {
    readRequiredConfig,
    requestJson,
    drainExpiredFileIds
  }
};
