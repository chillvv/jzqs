function hasQueueOrderChanged(previousItems, nextItems) {
  const previousIds = Array.isArray(previousItems) ? previousItems.map((item) => item.batchItemId) : [];
  const nextIds = Array.isArray(nextItems) ? nextItems.map((item) => item.batchItemId) : [];
  if (previousIds.length !== nextIds.length) {
    return true;
  }
  return previousIds.some((id, index) => id !== nextIds[index]);
}

function buildReceiptSubmitState({ currentItem, receiptTempFilePath, submittingTaskId }) {
  if (!currentItem) {
    return {
      disabled: true,
      loading: false,
      buttonText: '暂无任务',
      helperText: '当前没有可提交的配送任务。'
    };
  }

  if (submittingTaskId === currentItem.mealSlotOrderId) {
    return {
      disabled: true,
      loading: true,
      buttonText: '提交中...',
      helperText: '正在上传送达图片并提交回执，请稍候。'
    };
  }

  if (!receiptTempFilePath) {
    return {
      disabled: true,
      loading: false,
      buttonText: '先上传送达图',
      helperText: '请先拍照或选择图片，再提交送达。'
    };
  }

  return {
    disabled: false,
    loading: false,
    buttonText: '确认送达',
    helperText: '已选送达图片，可以提交本单回执。'
  };
}

function getQueueFocusItems(items) {
  const actionableItems = (Array.isArray(items) ? items : []).filter((item) => item.itemStatus !== 'DELIVERED' && item.itemStatus !== 'DEFERRED');
  return {
    currentItem: actionableItems[0] || null,
    nextItem: actionableItems[1] || null
  };
}

module.exports = {
  hasQueueOrderChanged,
  buildReceiptSubmitState,
  getQueueFocusItems
};
