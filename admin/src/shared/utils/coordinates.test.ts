import { describe, expect, it } from "vitest";
import { gcj02ToWgs84, outOfChina, wgs84ToGcj02 } from "./coordinates";

describe("outOfChina", () => {
  it("中国境内坐标返回 false", () => {
    expect(outOfChina(30.5928, 114.3055)).toBe(false);
  });

  it("海外坐标返回 true", () => {
    expect(outOfChina(35.6762, 139.6503)).toBe(true); // 东京
    expect(outOfChina(40.7128, -74.006)).toBe(true); // 纽约
  });
});

describe("wgs84ToGcj02 / gcj02ToWgs84", () => {
  it("武汉坐标偏移量在合理范围（约 100~700m，即 0.001~0.006 度）", () => {
    const gcj = wgs84ToGcj02(30.5928, 114.3055);
    expect(gcj.lat).not.toBe(30.5928);
    expect(gcj.lng).not.toBe(114.3055);
    expect(Math.abs(gcj.lat - 30.5928)).toBeLessThan(0.01);
    expect(Math.abs(gcj.lng - 114.3055)).toBeLessThan(0.01);
  });

  it("互转往返误差小于 0.0001 度（约 10m）", () => {
    const gcj = wgs84ToGcj02(30.484039, 114.411133);
    const wgs = gcj02ToWgs84(gcj.lat, gcj.lng);
    expect(Math.abs(wgs.lat - 30.484039)).toBeLessThan(0.0001);
    expect(Math.abs(wgs.lng - 114.411133)).toBeLessThan(0.0001);
  });

  it("海外坐标不做转换（原样返回）", () => {
    const gcj = wgs84ToGcj02(35.6762, 139.6503);
    expect(gcj).toEqual({ lat: 35.6762, lng: 139.6503 });
  });

  it("已知偏移量校验：GCJ-02 应在 WGS-84 的东南方向（中国境内）", () => {
    const gcj = wgs84ToGcj02(30.484039, 114.411133);
    expect(gcj.lat).toBeLessThan(30.484039);
    expect(gcj.lng).toBeGreaterThan(114.411133);
  });
});
