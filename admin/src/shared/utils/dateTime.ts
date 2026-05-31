function normalizeDateInput(value?: string | null) {
  if (!value) return "";
  return String(value).trim();
}

export function formatDateTimeLabel(value?: string | null) {
  const raw = normalizeDateInput(value);
  if (!raw) return "-";
  const normalized = raw.replace("T", " ");
  if (normalized.length >= 16) {
    return normalized.slice(0, 16);
  }
  return normalized;
}

export function formatDateLabel(value?: string | null) {
  const raw = normalizeDateInput(value);
  if (!raw) return "-";
  return raw.replace("T", " ").slice(0, 10);
}
