function mealLabel(mealPeriod) {
  return mealPeriod === 'LUNCH' ? '午餐' : '晚餐';
}

function batchStatusLabel(status) {
  const map = {
    READY: '待开始',
    IN_PROGRESS: '配送中',
    PARTIALLY_DONE: '部分完成',
    FINISHED: '已完成',
    ABNORMAL: '异常'
  };
  return map[status] || status || '待开始';
}

function buildTodayCards(summary) {
  return [summary.lunchBatch, summary.dinnerBatch]
    .filter(Boolean)
    .map((item) => ({
      ...item,
      mealLabel: mealLabel(item.mealPeriod),
      batchStatusLabel: batchStatusLabel(item.batchStatus),
      currentCustomerName: item.currentCustomerName || '待开始',
      nextCustomerName: item.nextCustomerName || '无'
    }));
}

module.exports = {
  batchStatusLabel,
  buildTodayCards,
  mealLabel
};
