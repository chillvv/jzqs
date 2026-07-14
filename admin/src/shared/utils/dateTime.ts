function normalizeDateInput(value?: string | null) {
  if (!value) return "";
  return String(value).trim();
}

export function formatLocalDateInputValue(value: Date = new Date()) {
  const year = value.getFullYear();
  const month = String(value.getMonth() + 1).padStart(2, "0");
  const day = String(value.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

export function shiftLocalDateInputValue(value: string, offsetDays: number) {
  const raw = normalizeDateInput(value);
  const [year, month, day] = raw.split("-").map(Number);
  if (![year, month, day].every((item) => Number.isFinite(item))) {
    return formatLocalDateInputValue();
  }
  const date = new Date(year, month - 1, day);
  date.setDate(date.getDate() + offsetDays);
  return formatLocalDateInputValue(date);
}

export function formatDateTimeLabel(value?: string | number[] | null) {
  if (Array.isArray(value)) {
    if (value.length >= 5) {
      const [year, month, day, hour, minute] = value;
      return `${year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")} ${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}`;
    }
    if (value.length >= 3) {
      const [year, month, day] = value;
      return `${year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")} 00:00`;
    }
  }
  const raw = normalizeDateInput(value as string);
  if (!raw) return "-";
  const normalized = raw.replace("T", " ");
  if (normalized.length >= 16) {
    return normalized.slice(0, 16);
  }
  return normalized;
}

export function formatDateLabel(value?: string | number[] | null) {
  if (Array.isArray(value)) {
    if (value.length >= 3) {
      const [year, month, day] = value;
      return `${year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
    }
  }
  const raw = normalizeDateInput(value as string);
  if (!raw) return "-";
  return raw.replace("T", " ").slice(0, 10);
}
