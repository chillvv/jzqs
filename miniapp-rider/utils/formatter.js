/**
 * 数据格式化工具
 */

const { ITEM_STATUS, ITEM_STATUS_CLASS, MEAL_PERIOD_LABEL, BATCH_STATUS_LABEL } = require('./constants');

/**
 * 格式化日期为 YYYY-MM-DD
 * @param {Date|string|number} input - 日期输入
 * @returns {string}
 */
function formatDateYMD(input = new Date()) {
  const date = input instanceof Date ? new Date(input.getTime()) : new Date(input);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

/**
 * 获取当前日期的 MM-DD 格式
 * @returns {string} 例如 "06-14"
 */
function formatDateMMDD(input = new Date()) {
  const date = input instanceof Date ? new Date(input.getTime()) : new Date(input);
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${month}-${day}`;
}

/**
 * 获取当前日期的 MM-DD 格式
 * @returns {string} 例如 "06-14"
 */
function formatCurrentDateMMDD() {
  return formatDateMMDD(new Date());
}

/**
 * 生成骑手工作台日期切换选项
 * @returns {Array<{ key: string, label: string, value: string, shortLabel: string, displayLabel: string }>}
 */
function createWorkbenchDateOptions() {
  const baseDate = new Date();
  baseDate.setHours(0, 0, 0, 0);
  return [
    { key: 'yesterday', label: '昨天', offset: -1 },
    { key: 'today', label: '今天', offset: 0 },
    { key: 'tomorrow', label: '明天', offset: 1 }
  ].map((item) => {
    const date = new Date(baseDate.getTime());
    date.setDate(baseDate.getDate() + item.offset);
    const shortLabel = formatDateMMDD(date);
    return {
      key: item.key,
      label: item.label,
      value: formatDateYMD(date),
      shortLabel,
      displayLabel: `${item.label} ${shortLabel}`
    };
  });
}

/**
 * 格式化当前日期时间（ISO格式）
 * @returns {string} 格式化后的日期时间
 */
function formatCurrentDateTime() {
  const date = new Date();
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hour = String(date.getHours()).padStart(2, '0');
  const minute = String(date.getMinutes()).padStart(2, '0');
  const second = String(date.getSeconds()).padStart(2, '0');
  return `${year}-${month}-${day}T${hour}:${minute}:${second}`;
}

/**
 * 格式化日期时间（显示格式）
 * @param {string} isoString - ISO格式日期时间
 * @returns {string} 格式化后的日期时间
 */
function formatDateTime(isoString) {
  if (!isoString) return '';
  const date = new Date(isoString);
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hour = String(date.getHours()).padStart(2, '0');
  const minute = String(date.getMinutes()).padStart(2, '0');
  return `${month}-${day} ${hour}:${minute}`;
}

/**
 * 格式化时间（仅时分）
 * @param {string} isoString - ISO格式日期时间
 * @returns {string} 格式化后的时间
 */
function formatTime(isoString) {
  if (!isoString) return '';
  const date = new Date(isoString);
  const hour = String(date.getHours()).padStart(2, '0');
  const minute = String(date.getMinutes()).padStart(2, '0');
  return `${hour}:${minute}`;
}

/**
 * 获取任务状态标签
 * @param {string} status - 状态值
 * @returns {string} 状态标签
 */
function getItemStatusLabel(status) {
  return ITEM_STATUS[status] || status;
}

/**
 * 获取任务状态样式类
 * @param {string} status - 状态值
 * @returns {string} 样式类名
 */
function getItemStatusClass(status) {
  return ITEM_STATUS_CLASS[status] || 'tag-gray';
}

/**
 * 获取餐期标签
 * @param {string} mealPeriod - 餐期值
 * @returns {string} 餐期标签
 */
function getMealPeriodLabel(mealPeriod) {
  return MEAL_PERIOD_LABEL[mealPeriod] || mealPeriod;
}

/**
 * 获取批次状态标签
 * @param {string} status - 状态值
 * @returns {string} 状态标签
 */
function getBatchStatusLabel(status) {
  return BATCH_STATUS_LABEL[status] || status;
}

/**
 * 手机号脱敏
 * @param {string} phone - 手机号
 * @returns {string} 脱敏后的手机号
 */
function maskPhone(phone) {
  const value = String(phone || '').trim();
  if (value.length < 7) {
    return value || '';
  }
  return `${value.slice(0, 3)}****${value.slice(-4)}`;
}

/**
 * 地址简化（截取前N个字符）
 * @param {string} address - 完整地址
 * @param {number} maxLength - 最大长度
 * @returns {string} 简化后的地址
 */
function simplifyAddress(address, maxLength = 30) {
  if (!address) return '';
  if (address.length <= maxLength) return address;
  return address.slice(0, maxLength) + '...';
}

/**
 * 计算配送进度百分比
 * @param {number} delivered - 已送达数量
 * @param {number} total - 总数量
 * @returns {number} 百分比（0-100）
 */
function calculateProgress(delivered, total) {
  if (!total || total === 0) return 0;
  return Math.round((delivered / total) * 100);
}

module.exports = {
  formatDateYMD,
  formatDateMMDD,
  formatCurrentDateMMDD,
  createWorkbenchDateOptions,
  formatCurrentDateTime,
  formatDateTime,
  formatTime,
  getItemStatusLabel,
  getItemStatusClass,
  getMealPeriodLabel,
  getBatchStatusLabel,
  maskPhone,
  simplifyAddress,
  calculateProgress
};
