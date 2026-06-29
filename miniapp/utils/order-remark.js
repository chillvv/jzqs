const DEFAULT_PRESET_NOTES = [];
const ORDER_REMARK_HISTORY_KEY = 'orderRemarkHistory';

function normalizeRemarkValue(value) {
  return String(value || '')
    .trim()
    .replace(/\s+/g, ' ');
}

function resolveStorage(options) {
  if (options && options.storage) {
    return options.storage;
  }
  if (typeof wx !== 'undefined') {
    return wx;
  }
  return null;
}

function getHistoryStorageKey(customerId) {
  return customerId ? `${ORDER_REMARK_HISTORY_KEY}:${customerId}` : `${ORDER_REMARK_HISTORY_KEY}:guest`;
}

function composeRemark(selectedPresetNotes, customRemark) {
  const segments = [normalizeRemarkValue(customRemark)].filter(Boolean);

  return segments.join('，');
}

function normalizeHistoryRemarkSuggestions(historySource, options) {
  const storage = resolveStorage(options);
  const history = Array.isArray(historySource)
    ? historySource
    : ((storage && storage.getStorageSync && storage.getStorageSync(getHistoryStorageKey(options && options.customerId))) || []);

  return history
    .map((item) => normalizeRemarkValue(item))
    .filter(Boolean)
    .filter((item, index, list) => list.indexOf(item) === index);
}

function addHistoryRemark(remark, options) {
  const normalized = normalizeRemarkValue(remark);
  if (!normalized) return;

  const storage = resolveStorage(options);
  if (!storage || !storage.getStorageSync || !storage.setStorageSync) {
    return;
  }

  const storageKey = getHistoryStorageKey(options && options.customerId);
  let history = storage.getStorageSync(storageKey) || [];
  // Remove if exists to move to top
  history = history.filter((item) => item !== normalized);
  // Add to top
  history.unshift(normalized);
  // Keep only latest 5
  if (history.length > 5) {
    history = history.slice(0, 5);
  }
  storage.setStorageSync(storageKey, history);
}

function resolveInitialRemark(storedRemark, preferredRemark) {
  const localRemark = normalizeRemarkValue(storedRemark);
  if (localRemark) {
    return localRemark;
  }
  return normalizeRemarkValue(preferredRemark);
}

module.exports = {
  DEFAULT_PRESET_NOTES,
  normalizeHistoryRemarkSuggestions,
  addHistoryRemark,
  composeRemark,
  resolveInitialRemark
};
