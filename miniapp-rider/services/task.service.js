/**
 * 任务服务
 * 处理配送任务相关业务逻辑
 */

const { request, uploadFile } = require('../utils/request');
const cloudStorage = require('../utils/cloud-storage');

/**
 * 获取今日任务概览
 * @param {string} riderName - 骑手姓名
 * @returns {Promise<Object>} 任务概览数据
 */
async function getTodaySummary(riderName) {
  return await request({
    url: `/api/mobile/rider/summary?riderName=${encodeURIComponent(riderName)}`
  });
}

/**
 * 获取配送队列
 * @param {string} riderName - 骑手姓名
 * @returns {Promise<Object>} 队列数据
 */
async function getQueue(riderName) {
  return await request({
    url: `/api/mobile/rider/queue?riderName=${encodeURIComponent(riderName)}`
  });
}

/**
 * 获取单个订单详情
 * @param {string} riderName - 骑手姓名
 * @param {number} batchItemId - 批次项ID
 * @returns {Promise<Object>} 订单详情
 */
async function getOrderDetail(riderName, batchItemId) {
  return await request({
    url: `/api/rider/orders/${batchItemId}?riderName=${encodeURIComponent(riderName)}`
  });
}

/**
 * 获取今日已完成任务
 * @param {string} riderName - 骑手姓名
 * @returns {Promise<Object>} 已完成任务列表
 */
async function getCompletedToday(riderName) {
  return await request({
    url: `/api/mobile/rider/completed-today?riderName=${encodeURIComponent(riderName)}`
  });
}

/**
 * 调整队列顺序
 * @param {string} riderName - 骑手姓名
 * @param {Array<number>} batchItemIds - 批次项ID数组
 * @returns {Promise<Object>} 操作结果
 */
async function reorderQueue(riderName, batchItemIds) {
  return await request({
    url: `/api/mobile/rider/queue/reorder?riderName=${encodeURIComponent(riderName)}`,
    method: 'POST',
    data: { batchItemIds },
    header: { 'content-type': 'application/json' }
  });
}

/**
 * 稍后送
 * @param {string} riderName - 骑手姓名
 * @param {number} batchItemId - 批次项ID
 * @returns {Promise<Object>} 操作结果
 */
async function deferQueueItem(riderName, batchItemId) {
  return await request({
    url: `/api/mobile/rider/queue/items/${batchItemId}/defer?riderName=${encodeURIComponent(riderName)}`,
    method: 'POST'
  });
}

/**
 * 恢复队列
 * @param {string} riderName - 骑手姓名
 * @param {number} batchItemId - 批次项ID
 * @returns {Promise<Object>} 操作结果
 */
async function resumeQueueItem(riderName, batchItemId) {
  return await request({
    url: `/api/mobile/rider/queue/items/${batchItemId}/resume?riderName=${encodeURIComponent(riderName)}`,
    method: 'POST'
  });
}

/**
 * 上传送达图片（使用微信云存储）
 * @param {string} riderName - 骑手姓名
 * @param {string} filePath - 图片路径
 * @returns {Promise<Object>} 上传结果 { fileKey: HTTPS链接 }
 */
async function uploadReceipt(riderName, filePath) {
  console.log('[上传回执] 开始上传', { riderName, filePath });
  
  // 检查云开发是否初始化
  if (!wx.cloud) {
    console.error('[上传回执] wx.cloud 未定义，云开发未初始化');
    throw new Error('云开发未初始化，请重启小程序');
  }
  
  try {
    // 生成云存储路径
    const cloudPath = cloudStorage.generateCloudPath(riderName, 'jpg');
    console.log('[上传回执] 云存储路径', cloudPath);
    
    // 上传到微信云存储
    const fileID = await cloudStorage.uploadToCloud(filePath, cloudPath);
    console.log('[上传回执] 云存储成功', { fileID, cloudPath });
    
    // 获取临时 HTTPS 链接（公开读权限下，此链接永久有效）
    const httpsUrl = await cloudStorage.getTempFileURL(fileID);
    console.log('[上传回执] 获取 HTTPS 链接成功', { fileID, httpsUrl });
    
    // 验证链接格式
    if (!httpsUrl.startsWith('https://')) {
      console.error('[上传回执] 链接格式错误', httpsUrl);
      throw new Error('获取的链接格式不正确，请检查云存储权限设置');
    }

    // 往云数据库写一条记录，供定时清理云函数使用
    // 失败不影响主流程
    try {
      await wx.cloud.database().collection('receipt_files').add({
        data: {
          fileID,
          riderName,
          uploadedAt: wx.cloud.database().serverDate()
        }
      });
    } catch (dbErr) {
      console.warn('[上传回执] 写云数据库失败（不影响上传）', dbErr.message);
    }
    
    return {
      fileKey: httpsUrl, // 返回 HTTPS 链接，后台可直接访问
      previewUrl: fileID // 小程序内部使用 fileID
    };
  } catch (error) {
    console.error('[上传回执] 失败', error);
    throw new Error(error.message || '上传失败，请重试');
  }
}

/**
 * 提交送达回执
 * @param {string} riderName - 骑手姓名
 * @param {number} mealSlotOrderId - 订单ID
 * @param {string} receiptFileKey - 图片文件Key
 * @param {string} receiptNote - 回执说明
 * @param {string} deliveredAt - 送达时间
 * @returns {Promise<Object>} 提交结果
 */
async function submitReceipt(riderName, mealSlotOrderId, receiptFileKey, receiptNote, deliveredAt) {
  return await request({
    url: `/api/mobile/rider/tasks/${mealSlotOrderId}/receipt`,
    method: 'POST',
    data: {
      riderName,
      receiptFileKey,
      receiptNote: receiptNote || '骑手确认送达',
      deliveredAt
    },
    header: { 'content-type': 'application/json' }
  });
}

/**
 * 更新送达回执
 * @param {string} riderName - 骑手姓名
 * @param {number} mealSlotOrderId - 订单ID
 * @param {string} receiptFileKey - 图片文件Key
 * @param {string} receiptNote - 回执说明
 * @param {string} deliveredAt - 送达时间
 * @returns {Promise<Object>} 更新结果
 */
async function updateReceipt(riderName, mealSlotOrderId, receiptFileKey, receiptNote, deliveredAt) {
  return await request({
    url: `/api/rider/orders/${mealSlotOrderId}/receipt?riderName=${encodeURIComponent(riderName)}`,
    method: 'PUT',
    data: {
      receiptFileKey,
      receiptNote: receiptNote || '骑手确认送达',
      deliveredAt
    },
    header: { 'content-type': 'application/json' }
  });
}

/**
 * 删除送达照片（支持云存储）
 * @param {string} riderName - 骑手姓名
 * @param {number} mealSlotOrderId - 订单ID
 * @param {string} receiptUrl - 回执图片URL（可能是云存储fileID）
 * @returns {Promise<Object>} 删除结果
 */
async function deleteReceiptImage(riderName, mealSlotOrderId, receiptUrl) {
  try {
    // 如果是云存储fileID（以cloud://开头），先删除云存储文件
    if (receiptUrl && receiptUrl.startsWith('cloud://')) {
      await cloudStorage.deleteFromCloud(receiptUrl);
      console.log('[删除回执] 云存储文件已删除', receiptUrl);
    }
  } catch (error) {
    console.error('[删除回执] 云存储删除失败', error);
    // 继续执行后端删除，不中断流程
  }
  
  // 调用后端API删除数据库记录
  return await request({
    url: `/api/rider/orders/${mealSlotOrderId}/receipt-image?riderName=${encodeURIComponent(riderName)}`,
    method: 'DELETE'
  });
}

/**
 * 上报配送异常
 * @param {string} riderName - 骑手姓名
 * @param {number} mealSlotOrderId - 订单ID
 * @param {string} exceptionType - 异常类型
 * @param {string} exceptionNote - 异常说明
 * @param {Array<string>} exceptionImages - 异常图片
 * @returns {Promise<Object>} 上报结果
 */
async function reportException(riderName, mealSlotOrderId, exceptionType, exceptionNote, exceptionImages) {
  return await request({
    url: `/api/mobile/rider/tasks/${mealSlotOrderId}/report-exception`,
    method: 'POST',
    data: {
      riderName,
      exceptionType,
      exceptionNote,
      exceptionImages: exceptionImages || []
    },
    header: { 'content-type': 'application/json' }
  });
}

/**
 * 撤回订单状态
 * 将已完成的订单恢复为待配送状态
 * @param {string} riderName - 骑手姓名
 * @param {number} mealSlotOrderId - 订单ID
 * @returns {Promise<Object>} 操作结果
 */
async function revertOrderStatus(riderName, mealSlotOrderId) {
  return await request({
    url: `/api/rider/orders/${mealSlotOrderId}/revert?riderName=${encodeURIComponent(riderName)}`,
    method: 'POST'
  });
}

/**
 * 撤回送达（别名）
 * 将已完成的订单恢复为待配送状态
 * @param {string} riderName - 骑手姓名
 * @param {number} mealSlotOrderId - 订单ID
 * @returns {Promise<Object>} 操作结果
 */
async function undoDelivery(riderName, mealSlotOrderId) {
  return await revertOrderStatus(riderName, mealSlotOrderId);
}

/**
 * 保存订单排序
 * @param {string} riderName - 骑手姓名
 * @param {string} mealPeriod - 餐期：LUNCH 或 DINNER
 * @param {Array<number>} batchItemIds - 排序后的批次项ID列表
 * @returns {Promise<Object>} 操作结果
 */
async function saveOrderSequence(riderName, mealPeriod, batchItemIds) {
  return await request({
    url: `/api/rider/orders/reorder?riderName=${encodeURIComponent(riderName)}`,
    method: 'POST',
    data: { batchItemIds },
    header: { 'content-type': 'application/json' }
  });
}

module.exports = {
  getTodaySummary,
  getQueue,
  getOrderDetail,
  getCompletedToday,
  reorderQueue,
  deferQueueItem,
  resumeQueueItem,
  uploadReceipt,
  submitReceipt,
  updateReceipt,
  deleteReceiptImage,
  reportException,
  revertOrderStatus,
  undoDelivery,
  saveOrderSequence
};
