/**
 * 任务服务
 * 处理配送任务相关业务逻辑
 */

const { request, uploadFile } = require('../utils/request');
const cloudStorage = require('../utils/cloud-storage');
const { appendServeDateQuery, resolveQueueItemRequestId } = require('../utils/rider-queue');

/**
 * 获取今日任务概览
 * @param {string} riderName - 骑手姓名
 * @returns {Promise<Object>} 任务概览数据
 */
async function getTodaySummary(riderName, serveDate) {
  return await request({
    url: appendServeDateQuery(
      `/api/mobile/rider/summary?riderName=${encodeURIComponent(riderName)}`,
      serveDate
    )
  });
}

/**
 * 获取配送队列
 * @param {string} riderName - 骑手姓名
 * @returns {Promise<Object>} 队列数据
 */
async function getQueue(riderName, serveDate) {
  return await request({
    url: appendServeDateQuery(
      `/api/mobile/rider/queue?riderName=${encodeURIComponent(riderName)}`,
      serveDate
    )
  });
}

/**
 * 获取单个订单详情
 * @param {string} riderName - 骑手姓名
 * @param {number} batchItemId - 批次项ID
 * @returns {Promise<Object>} 订单详情
 */
async function getOrderDetail(riderName, batchItemId, serveDate, mealSlotOrderId) {
  const requestId = resolveQueueItemRequestId(batchItemId, mealSlotOrderId);
  let url = `/api/rider/orders/${requestId}?riderName=${encodeURIComponent(riderName)}`;
  if (Number(mealSlotOrderId) > 0) {
    url += `&mealSlotOrderId=${encodeURIComponent(mealSlotOrderId)}`;
  }
  return await request({
    url: appendServeDateQuery(url, serveDate)
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
 * 上传送达图片（改为后端本地存储）
 * @param {string} riderName - 骑手姓名
 * @param {string} filePath - 图片路径
 * @returns {Promise<Object>} 上传结果
 */
async function uploadReceipt(riderName, filePath) {
  return uploadFile({
    url: `/api/mobile/rider/uploads/receipt?riderName=${encodeURIComponent(riderName)}`,
    filePath,
    name: 'file'
  });
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

async function getAddressReferenceImage(riderName, addressId) {
  return await request({
    url: `/api/rider/address-reference?riderName=${encodeURIComponent(riderName)}&addressId=${addressId}`
  });
}

async function saveBatchAddressReferenceImage(riderName, addressIds, referenceImageUrl) {
  return await request({
    url: `/api/rider/address-reference/batch?riderName=${encodeURIComponent(riderName)}`,
    method: 'POST',
    data: {
      addressIds,
      referenceImageUrl
    },
    header: { 'content-type': 'application/json' }
  });
}

async function replaceAddressReferenceImage(riderName, addressId, referenceImageUrl) {
  return await request({
    url: `/api/rider/address-reference/${addressId}?riderName=${encodeURIComponent(riderName)}`,
    method: 'POST',
    data: {
      referenceImageUrl
    },
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
  saveOrderSequence,
  getAddressReferenceImage,
  saveBatchAddressReferenceImage,
  replaceAddressReferenceImage
};
