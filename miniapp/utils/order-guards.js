function getCheckoutMealLimitMessage({ totalQty, remainingMeals }) {
  if (remainingMeals <= 0) {
    return '当前剩余餐数为 0。请联系专属客服办理套餐后再下单。';
  }
  if (totalQty > remainingMeals) {
    return '剩余餐次不足，请调整餐食数量后再结算';
  }
  return '';
}

function canCancelMiniappOrder({ status, serveDate, now }) {
  if (status !== 'PENDING_DISPATCH') {
    return false;
  }
  if (!serveDate || !now) {
    return false;
  }
  const current = new Date(now);
  if (Number.isNaN(current.getTime())) {
    return false;
  }
  const tomorrow = new Date(current);
  tomorrow.setHours(0, 0, 0, 0);
  tomorrow.setDate(tomorrow.getDate() + 1);
  const serveDay = new Date(`${serveDate}T00:00:00`);
  if (Number.isNaN(serveDay.getTime())) {
    return false;
  }
  if (serveDay.getTime() !== tomorrow.getTime()) {
    return false;
  }
  const cutoff = new Date(current);
  cutoff.setHours(23, 0, 0, 0);
  return current.getTime() < cutoff.getTime();
}

module.exports = {
  getCheckoutMealLimitMessage,
  canCancelMiniappOrder
};
