function canMoveQueueItem(list, fromIndex, delta) {
  const copy = Array.isArray(list) ? list.slice() : [];
  const toIndex = fromIndex + delta;
  if (fromIndex < 0 || fromIndex >= copy.length || toIndex < 0 || toIndex >= copy.length) {
    return false;
  }
  const fromItem = copy[fromIndex];
  const toItem = copy[toIndex];
  if (!fromItem || !toItem) {
    return false;
  }
  if (fromItem.itemStatus === 'DELIVERED' || toItem.itemStatus === 'DELIVERED') {
    return false;
  }
  return true;
}

function moveQueueItem(list, fromIndex, toIndex) {
  const copy = Array.isArray(list) ? list.slice() : [];
  if (!canMoveQueueItem(copy, fromIndex, toIndex - fromIndex)) {
    return copy;
  }
  const [item] = copy.splice(fromIndex, 1);
  copy.splice(toIndex, 0, item);
  return copy.map((entry, index) => ({
    ...entry,
    currentSequence: index + 1
  }));
}

function currentQueueItem(list) {
  return (Array.isArray(list) ? list : []).find((item) => item.itemStatus === 'CURRENT' || item.itemStatus === 'PENDING') || null;
}

module.exports = {
  canMoveQueueItem,
  moveQueueItem,
  currentQueueItem
};
