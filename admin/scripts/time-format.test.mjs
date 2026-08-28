import assert from "node:assert/strict";
import {
  formatDateTimeLabel,
  formatDateLabel
} from "../temp-test/shared/utils/dateTime.js";

assert.equal(formatDateTimeLabel("2026-05-13T11:30:00"), "2026-05-13 11:30");
assert.equal(formatDateTimeLabel("2026-05-13 11:30:45"), "2026-05-13 11:30");
assert.equal(formatDateTimeLabel(""), "-");
assert.equal(formatDateTimeLabel(null), "-");

assert.equal(formatDateLabel("2026-05-13"), "2026-05-13");
assert.equal(formatDateLabel("2026-05-13T11:30:00"), "2026-05-13");
assert.equal(formatDateLabel(undefined), "-");

console.log("time format test: ok");
