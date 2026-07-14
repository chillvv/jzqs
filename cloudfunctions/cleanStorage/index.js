/**
 * 云函数：cleanStorage
 * 功能：删除前一天的回执图片
 * 触发方式：定时触发器，每天凌晨3点执行
 *
 * 工作原理：
 * 1. 骑手上传图片时，会往云数据库 receipt_files 集合写一条记录
 * 2. 本云函数查询 uploadedAt < 昨天0点 的记录
 * 3. 批量删除云存储文件
 * 4. 删除云数据库中对应的记录
 */

const https = require('https');
const cloud = require('wx-server-sdk');

cloud.init({ env: cloud.DYNAMIC_CURRENT_ENV });

const REQUEST_TIMEOUT_MS = 10000;

exports.main = async (event, context) => {
  const { apiBaseUrl, internalApiToken } = readRequiredConfig();
  const db = cloud.database();
  const _ = db.command;
  const startedAt = new Date().toISOString();

  // 截止时间：今天0点（即只保留今天的图片，昨天及之前全删）
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
      await reportMaintenanceJob(apiBaseUrl, internalApiToken, {
        jobType: 'CLOUD_STORAGE_SWEEP',
        status: 'SUCCESS',
        timeRangeLabel: `删除 ${today.toISOString()} 之前上传的云存储图片`,
        startedAt,
        finishedAt: new Date().toISOString(),
        scannedCount: 0,
        deletedCount: 0,
        failedCount: 0,
        message: 'cleanStorage 无需清理',
        metadata: { cutoff: today.toISOString(), requested: 0 }
      });
      return result;
    }

    console.log('[cleanStorage] 待删除文件数:', records.length);

    // 2. 分批删除云存储文件（每批最多50个）
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

  await reportMaintenanceJob(apiBaseUrl, internalApiToken, {
    jobType: 'CLOUD_STORAGE_SWEEP',
    status: result.failed > 0 ? 'PARTIAL_SUCCESS' : (result.error ? 'FAILED' : 'SUCCESS'),
    timeRangeLabel: `删除 ${today.toISOString()} 之前上传的云存储图片`,
    startedAt,
    finishedAt: new Date().toISOString(),
    scannedCount: result.scanned,
    deletedCount: result.deleted,
    failedCount: result.failed,
    message: result.error ? 'cleanStorage 执行失败' : 'cleanStorage 执行完成',
    errorDetail: result.error || '',
    metadata: { cutoff: today.toISOString() }
  });

  return result;
};

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
    console.warn('[cleanStorage] 维护日志上报失败（不影响主流程）:', err.message);
  }
}

module.exports = {
  main: exports.main,
  __test__: {
    readRequiredConfig,
    requestJson
  }
};
