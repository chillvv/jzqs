/**
 * 常量定义
 */

/**
 * 异常类型
 */
const EXCEPTION_TYPES = {
  CUSTOMER_ABSENT: '客户不在',
  WRONG_ADDRESS: '地址错误',
  CUSTOMER_REJECTED: '客户拒收',
  PHONE_UNREACHABLE: '电话无法接通',
  GATE_LOCKED: '门禁无法进入',
  OTHER: '其他异常'
};

/**
 * 异常类型选项（用于选择器）
 */
const EXCEPTION_TYPE_OPTIONS = [
  { label: '客户不在', value: 'CUSTOMER_ABSENT' },
  { label: '地址错误', value: 'WRONG_ADDRESS' },
  { label: '客户拒收', value: 'CUSTOMER_REJECTED' },
  { label: '电话无法接通', value: 'PHONE_UNREACHABLE' },
  { label: '门禁无法进入', value: 'GATE_LOCKED' },
  { label: '其他异常', value: 'OTHER' }
];

/**
 * 任务状态
 */
const ITEM_STATUS = {
  CURRENT: '当前单',
  PENDING: '待送',
  DELIVERED: '已送达',
  DEFERRED: '稍后送'
};

/**
 * 任务状态样式类
 */
const ITEM_STATUS_CLASS = {
  CURRENT: 'tag-blue',
  PENDING: 'tag-gray',
  DELIVERED: 'tag-green',
  DEFERRED: 'tag-orange'
};

/**
 * 餐期标签
 */
const MEAL_PERIOD_LABEL = {
  LUNCH: '午餐',
  DINNER: '晚餐'
};

/**
 * 批次状态标签
 */
const BATCH_STATUS_LABEL = {
  READY: '待开始',
  IN_PROGRESS: '配送中',
  COMPLETED: '已完成'
};

/**
 * 骑手状态
 */
const RIDER_STATUS = {
  UNAUTHORIZED: '未授权',
  UNASSIGNED: '待分配',
  ACTIVE: '工作中',
  DISABLED: '已停用',
  NOT_FOUND: '未开通'
};

/**
 * 视图状态
 */
const VIEW_STATE = {
  CHECKING: 'checking',
  GUEST: 'guest',
  NOT_FOUND: 'not_found',
  PENDING: 'pending',
  BLOCKED: 'blocked',
  ACTIVE: 'active'
};

/**
 * 默认回执说明
 */
const DEFAULT_RECEIPT_NOTES = [
  '已送达',
  '已放前台',
  '已交本人',
  '已放快递柜',
  '已放门口',
  '已交保安'
];

module.exports = {
  EXCEPTION_TYPES,
  EXCEPTION_TYPE_OPTIONS,
  ITEM_STATUS,
  ITEM_STATUS_CLASS,
  MEAL_PERIOD_LABEL,
  BATCH_STATUS_LABEL,
  RIDER_STATUS,
  VIEW_STATE,
  DEFAULT_RECEIPT_NOTES
};
