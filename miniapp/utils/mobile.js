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

function statusLabel(status) {
  switch (status) {
    case 'PENDING_DISPATCH':
      return '待配送';
    case 'DISPATCHING':
      return '配送中';
    case 'DELIVERED':
      return '已送达';
    case 'CANCELLED':
      return '已取消';
    default:
      return status || '未知状态';
  }
}

function statusClass(status) {
  switch (status) {
    case 'DELIVERED':
      return 'delivered';
    case 'DISPATCHING':
      return 'dispatching';
    case 'CANCELLED':
      return 'cancelled';
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
    case 'RESERVE':
      return '下单占用';
    case 'RELEASE':
      return '取消释放';
    case 'CONSUME':
      return '送达核销';
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
  formatMonthDay,
  periodLabel,
  statusLabel,
  statusClass,
  maskPhone,
  transactionLabel
};
