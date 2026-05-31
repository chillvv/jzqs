import { describe, expect, it } from "vitest";
import {
  buildDispatchAreaStats,
  buildDispatchPendingSearchText,
  buildCreateRiderPayload,
  createEmptyNewRiderDraft,
  mealPeriodLabel,
  normalizeDispatchAreaBindings,
  normalizeMealPeriodTab,
  validateCreateRiderDraft
} from "./dispatchCenterLayout.helpers";

describe("create rider draft helpers", () => {
  it("returns a validation message when name is blank", () => {
    expect(
      validateCreateRiderDraft({
        riderName: "   ",
        phone: "13800000001",
        password: "888888",
        areaCode: ""
      })
    ).toBe("请填写骑手姓名");
  });

  it("returns a validation message when phone is invalid", () => {
    expect(
      validateCreateRiderDraft({
        riderName: "张三",
        phone: "abc",
        password: "888888",
        areaCode: ""
      })
    ).toBe("请填写正确的手机号");
  });

  it("returns null when all fields are valid", () => {
    expect(
      validateCreateRiderDraft({
        riderName: "张三",
        phone: "13800000001",
        password: "888888",
        areaCode: ""
      })
    ).toBeNull();
  });

  it("builds a trimmed payload", () => {
    expect(
      buildCreateRiderPayload({
        riderName: "  骑手阿强  ",
        phone: " 13800000001 ",
        password: " 888888 ",
        areaCode: ""
      })
    ).toEqual({
      riderName: "骑手阿强",
      displayName: "骑手阿强",
      phone: "13800000001",
      password: "888888",
      employmentStatus: "ACTIVE",
      updatedBy: "管理员"
    });
  });

  it("creates a reusable empty draft with default password", () => {
    expect(createEmptyNewRiderDraft()).toEqual({
      riderName: "",
      phone: "",
      password: "888888",
      areaCode: ""
    });
  });

  it("normalizes meal period tabs", () => {
    expect(normalizeMealPeriodTab("午餐")).toBe("LUNCH");
    expect(normalizeMealPeriodTab("晚餐")).toBe("DINNER");
  });

  it("returns localized meal period labels", () => {
    expect(mealPeriodLabel("LUNCH")).toBe("午餐");
    expect(mealPeriodLabel("DINNER")).toBe("晚餐");
  });

  it("builds pending search text from customer and address only", () => {
    const searchText = buildDispatchPendingSearchText({
      orderId: 11,
      customerName: "张先生",
      deliveryAddress: "高新区软件园A座"
    });

    expect(searchText).toContain("张先生");
    expect(searchText).toContain("高新区软件园a座");
    expect(searchText).toBe(searchText.toLowerCase());
  });

  it("normalizes area bindings when backend omits orders", () => {
    const bindings = normalizeDispatchAreaBindings([
      {
        areaCode: "高新区",
        keywords: "高新区",
        defaultRiderId: 1,
        defaultRiderName: "骑手小李",
        currentRiderName: null,
        orderCount: 1,
        missingRider: true,
        updatedBy: "管理员",
        updatedAt: "2026-05-21 12:00:00"
      }
    ]);

    expect(bindings[0].orders).toEqual([]);
  });

  it("builds area stats from normalized area bindings", () => {
    const stats = buildDispatchAreaStats(
      normalizeDispatchAreaBindings([
        {
          areaCode: "高新区",
          keywords: "高新区",
          defaultRiderId: 1,
          defaultRiderName: "骑手小李",
          currentRiderName: "骑手小李",
          orderCount: 2,
          missingRider: false,
          orders: [
            {
              orderId: 1,
              sequenceNumber: 1,
              customerName: "张先生",
              deliveryAddress: "高新区软件园A座",
              deliveryStatus: "PENDING_DISPATCH",
              riderName: "骑手小李",
              userNote: "",
              adminNote: "",
              receiptUrl: "",
              receiptNote: "",
              deliveredAt: null,
              quantity: 1
            },
            {
              orderId: 2,
              sequenceNumber: 2,
              customerName: "王女士",
              deliveryAddress: "高新区软件园B座",
              deliveryStatus: "AREA_ASSIGNED",
              riderName: null,
              userNote: "",
              adminNote: "",
              receiptUrl: "",
              receiptNote: "",
              deliveredAt: null,
              quantity: 1
            }
          ],
          updatedBy: "管理员",
          updatedAt: "2026-05-21 12:00:00"
        },
        {
          areaCode: "商务区",
          keywords: "商务区",
          defaultRiderId: null,
          defaultRiderName: null,
          currentRiderName: null,
          orderCount: 1,
          missingRider: true,
          updatedBy: "管理员",
          updatedAt: "2026-05-21 12:00:00"
        }
      ])
    );

    expect(stats).toEqual({
      totalCount: 2,
      dispatchingCount: 1,
      missingRiderAreaCount: 1
    });
  });
});
