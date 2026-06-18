function normalizeOptionalString(value) {
  if (value == null) {
    return "";
  }
  return String(value).trim();
}

function appendServeDateQuery(url, serveDate) {
  const normalized = normalizeOptionalString(serveDate);
  if (!normalized || normalized === "undefined" || normalized === "null") {
    return url;
  }
  return `${url}&serveDate=${encodeURIComponent(normalized)}`;
}

function resolveQueueItemIdentity(item) {
  const batchItemId = Number(item && item.batchItemId);
  if (batchItemId > 0) {
    return `batch-${batchItemId}`;
  }
  const mealSlotOrderId = Number(item && item.mealSlotOrderId);
  if (mealSlotOrderId > 0) {
    return `order-${mealSlotOrderId}`;
  }
  return "unknown";
}

function resolveQueueItemRequestId(batchItemId, mealSlotOrderId) {
  const resolvedBatchItemId = Number(batchItemId);
  if (resolvedBatchItemId > 0) {
    return resolvedBatchItemId;
  }
  const resolvedMealSlotOrderId = Number(mealSlotOrderId);
  return resolvedMealSlotOrderId > 0 ? resolvedMealSlotOrderId : 0;
}

module.exports = {
  appendServeDateQuery,
  resolveQueueItemIdentity,
  resolveQueueItemRequestId
};
