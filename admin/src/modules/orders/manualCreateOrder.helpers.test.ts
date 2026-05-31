import { describe, expect, it } from "vitest";
import {
  applyManualCreateAddressSelection,
  applyManualCreateCustomerSelection,
  applyManualCreateMealPeriodSelection,
  buildManualCreatePayload,
  createInitialManualCreateForm,
  resolveManualCreateMergeDecision,
  resolveManualCreateMenuOptions,
  shouldShowManualCustomerEmptyState
} from "./manualCreateOrder.helpers";
import type { AdminMenuWeekResponse } from "../../shared/api/types";

const week: AdminMenuWeekResponse = {
  weekId: 1,
  weekStartDate: "2026-05-18",
  weekEndDate: "2026-05-24",
  status: "PUBLISHED",
  days: [
    {
      serveDate: "2026-05-18",
      weekdayLabel: "周一",
      lunch: { mealPeriod: "LUNCH", slotStatus: "ACTIVE", dishItems: ["鱼香肉丝", "蒜蓉生菜"], totalCalories: 520, merchantNote: "", imageUrl: "" },
      dinner: { mealPeriod: "DINNER", slotStatus: "ACTIVE", dishItems: ["番茄牛腩"], totalCalories: 560, merchantNote: "", imageUrl: "" }
    }
  ]
};

describe("applyManualCreateCustomerSelection", () => {
  it("prefills the default customer address after selecting a customer", () => {
    const next = applyManualCreateCustomerSelection(createInitialManualCreateForm(), {
      customerId: 7,
      customerName: "张先生",
      customerPhone: "13800000001",
      addresses: [
        { addressId: 11, addressLine: "软件园二期 6 号楼", areaCode: "高新区", isDefault: false },
        { addressId: 12, addressLine: "科技园 A 座 8 层", areaCode: "高新区", isDefault: true }
      ]
    });

    expect(next.customerKeyword).toBe("张先生 / 13800000001");
    expect(next.customerId).toBe("7");
    expect(next.addressId).toBe(12);
    expect(next.deliveryAddress).toBe("科技园 A 座 8 层");
  });

  it("falls back to the first address when the customer has no default address", () => {
    const next = applyManualCreateCustomerSelection(createInitialManualCreateForm(), {
      customerId: 8,
      customerName: "李女士",
      customerPhone: "13900000002",
      addresses: [
        { addressId: 21, addressLine: "天府三街腾讯大厦", areaCode: "高新区", isDefault: false },
        { addressId: 22, addressLine: "软件园 G 座", areaCode: "高新区", isDefault: false }
      ]
    });

    expect(next.addressId).toBe(21);
    expect(next.deliveryAddress).toBe("天府三街腾讯大厦");
  });
});

describe("shouldShowManualCustomerEmptyState", () => {
  it("hides the empty-state after a customer has already been selected", () => {
    expect(
      shouldShowManualCustomerEmptyState({
        keyword: "张先生",
        isLoading: false,
        customers: [],
        selectedCustomerId: 7
      })
    ).toBe(false);
  });

  it("shows the empty-state only when keyword exists, no result is returned, and no customer is selected", () => {
    expect(
      shouldShowManualCustomerEmptyState({
        keyword: "1380000",
        isLoading: false,
        customers: [],
        selectedCustomerId: null
      })
    ).toBe(true);
  });
});

describe("applyManualCreateAddressSelection", () => {
  it("switches the selected address by address id", () => {
    const selectedCustomer = {
      customerId: 7,
      customerName: "张先生",
      customerPhone: "13800000001",
      addresses: [
        { addressId: 11, addressLine: "软件园二期 6 号楼", areaCode: "高新区", isDefault: false },
        { addressId: 12, addressLine: "科技园 A 座 8 层", areaCode: "高新区", isDefault: true }
      ]
    };
    const base = applyManualCreateCustomerSelection(createInitialManualCreateForm(), selectedCustomer);

    const next = applyManualCreateAddressSelection(base, selectedCustomer.addresses, 11);

    expect(next.addressId).toBe(11);
    expect(next.deliveryAddress).toBe("软件园二期 6 号楼");
  });
});

describe("resolveManualCreateMenuOptions", () => {
  it("returns lunch and dinner menu summaries from the selected date", () => {
    expect(resolveManualCreateMenuOptions(week, "2026-05-18")).toEqual([
      {
        mealPeriod: "LUNCH",
        label: "午餐",
        available: true,
        mealSummary: "午餐 / 鱼香肉丝、蒜蓉生菜",
        disabledReason: ""
      },
      {
        mealPeriod: "DINNER",
        label: "晚餐",
        available: true,
        mealSummary: "晚餐 / 番茄牛腩",
        disabledReason: ""
      }
    ]);
  });

  it("marks a period unavailable when the menu is not configured", () => {
    const unconfiguredWeek: AdminMenuWeekResponse = {
      ...week,
      days: [
        {
          ...week.days[0],
          serveDate: "2026-05-19",
          dinner: { mealPeriod: "DINNER", slotStatus: "UNCONFIGURED", dishItems: [], totalCalories: null, merchantNote: "", imageUrl: "" }
        }
      ]
    };

    expect(resolveManualCreateMenuOptions(unconfiguredWeek, "2026-05-19")[1]).toEqual({
      mealPeriod: "DINNER",
      label: "晚餐",
      available: false,
      mealSummary: "",
      disabledReason: "当天晚餐菜单未配置"
    });
  });
});

describe("applyManualCreateMealPeriodSelection", () => {
  it("updates only the meal period from the selected menu option", () => {
    const options = resolveManualCreateMenuOptions(week, "2026-05-18");

    const next = applyManualCreateMealPeriodSelection(createInitialManualCreateForm(), "DINNER", options);

    expect(next.mealPeriod).toBe("DINNER");
  });
});

describe("buildManualCreatePayload", () => {
  it("builds the payload with meal period and serve date for manual create requests", () => {
    const form = {
      ...createInitialManualCreateForm(),
      customerId: "7",
      mealPeriod: "LUNCH" as const,
      addressId: 12,
      deliveryAddress: "科技园 A 座 8 层",
      note: "少饭",
      quantity: 1
    };

    expect(buildManualCreatePayload(form, "2026-05-18")).toEqual({
      customerId: 7,
      addressId: 12,
      mealPeriod: "LUNCH",
      deliveryAddress: "科技园 A 座 8 层",
      note: "少饭",
      source: "BACKEND",
      quantity: 1,
      serveDate: "2026-05-18"
    });
  });

  it("throws when no address is selected", () => {
    expect(() =>
      buildManualCreatePayload({
        ...createInitialManualCreateForm(),
        customerId: "7",
        mealPeriod: "LUNCH"
      }, "2026-05-18")
    ).toThrow("请选择客户地址");
  });

  it("throws when meal period is not selected from menu", () => {
    expect(() =>
      buildManualCreatePayload({
        ...createInitialManualCreateForm(),
        customerId: "7",
        addressId: 12,
        deliveryAddress: "科技园 A 座 8 层"
      }, "2026-05-18")
    ).toThrow("请选择午餐或晚餐");
  });
});

describe("resolveManualCreateMergeDecision", () => {
  it("returns merge mode for same customer same address same meal period active order", () => {
    expect(
      resolveManualCreateMergeDecision(
        {
          customerId: 7,
          addressId: 12,
          mealPeriod: "LUNCH"
        },
        [
          {
            id: 101,
            customerId: 7,
            addressId: 12,
            mealPeriod: "LUNCH",
            status: "PENDING_DISPATCH",
            displayStatus: "PENDING_DISPATCH"
          }
        ]
      )
    ).toEqual({
      mode: "MERGE",
      targetOrderId: 101
    });
  });

  it("ignores refunded history orders and allows create", () => {
    expect(
      resolveManualCreateMergeDecision(
        {
          customerId: 7,
          addressId: 12,
          mealPeriod: "LUNCH"
        },
        [
          {
            id: 101,
            customerId: 7,
            addressId: 12,
            mealPeriod: "LUNCH",
            status: "REFUNDED",
            displayStatus: "REFUNDED"
          }
        ]
      )
    ).toEqual({
      mode: "CREATE"
    });
  });
});
