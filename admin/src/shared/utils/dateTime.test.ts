import { describe, expect, it } from "vitest";
import { formatDateLabel, formatDateTimeLabel } from "./dateTime";

describe("formatDateTimeLabel", () => {
  it("normalizes ISO and full datetime strings to minute precision", () => {
    expect(formatDateTimeLabel("2026-05-13T11:30:00")).toBe("2026-05-13 11:30");
    expect(formatDateTimeLabel("2026-05-13 11:30:45")).toBe("2026-05-13 11:30");
  });

  it("falls back to a dash for empty values", () => {
    expect(formatDateTimeLabel("")).toBe("-");
    expect(formatDateTimeLabel(null)).toBe("-");
  });

  it("renders backend array timestamps as local labels", () => {
    expect(formatDateTimeLabel([2026, 5, 13, 11, 30])).toBe("2026-05-13 11:30");
    expect(formatDateTimeLabel([2026, 5, 13])).toBe("2026-05-13 00:00");
  });
});

describe("formatDateLabel", () => {
  it("keeps date-only strings and trims datetimes to the date part", () => {
    expect(formatDateLabel("2026-05-13")).toBe("2026-05-13");
    expect(formatDateLabel("2026-05-13T11:30:00")).toBe("2026-05-13");
  });

  it("falls back to a dash for missing values", () => {
    expect(formatDateLabel(undefined)).toBe("-");
  });

  it("renders backend array timestamps as date labels", () => {
    expect(formatDateLabel([2026, 5, 13])).toBe("2026-05-13");
  });
});
