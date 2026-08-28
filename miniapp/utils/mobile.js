function formatMonthDay(dateText) {
  if (!dateText || dateText.length < 10) {
    return '';
  }
  const month = Number(dateText.slice(5, 7));
  const day = Number(dateText.slice(8, 10));
  return `${month}.${day}`;
}

function periodLabel(mealPeriod) {
  return mealPeriod === 'DINNER' ? '晚餐' : '午餐';
}

// 用户端只对外呈现两个履约状态：待配送 / 已送达。
// 后台与骑手端仍保留 DISPATCHING / DISPATCHED 等细分状态用于调度，
// 但对顾客而言「没送到」就是待配送，不再区分是否已分配骑手。
const CUSTOMER_UNDELIVERED_STATUSES = ['PENDING_DISPATCH', 'DISPATCHING', 'DISPATCHED'];

function normalizeCustomerStatus(status) {
  if (CUSTOMER_UNDELIVERED_STATUSES.indexOf(status) !== -1) {
    return 'PENDING_DISPATCH';
  }
  return status;
}

function isUndeliveredStatus(status) {
  return CUSTOMER_UNDELIVERED_STATUSES.indexOf(status) !== -1;
}

function statusLabel(status) {
  switch (normalizeCustomerStatus(status)) {
    case 'PENDING_DISPATCH':
      return '待配送';
    case 'DELIVERED':
      return '已送达';
    case 'CANCELLED':
      return '已取消';
    case 'REFUNDED':
      return '已退款';
    default:
      return status || '未知状态';
  }
}

function statusClass(status) {
  switch (normalizeCustomerStatus(status)) {
    case 'DELIVERED':
      return 'delivered';
    case 'CANCELLED':
      return 'cancelled';
    case 'REFUNDED':
      return 'refunded';
    default:
      return 'pending';
  }
}

function maskPhone(phone) {
  if (!phone || phone.length < 7) {
    return phone || '';
  }
  return `${phone.slice(0, 3)}****${phone.slice(-4)}`;
}

function transactionLabel(type) {
  switch (type) {
    case 'OPEN':
      return '开卡';
    case 'GRANT':
      return '后台发放';
    case 'EXTEND_VALIDITY':
      return '统一延期';
    case 'RESERVE':
      return '预定核销';
    case 'RELEASE':
      return '取消释放';
    case 'CONSUME':
      return '预定核销';
    case 'REFUND':
    case 'REFUND_RETURN':
      return '退款退回';
    case 'COMPENSATION_RETURN':
      return '售后补回';
    case 'MANUAL_DEDUCT':
      return '人工扣减';
    default:
      return type || '餐次变动';
  }
}

module.exports = {
  CUSTOMER_UNDELIVERED_STATUSES,
  normalizeCustomerStatus,
  isUndeliveredStatus,
  formatMonthDay,
  periodLabel,
  statusLabel,
  statusClass,
  maskPhone,
  transactionLabel
};
