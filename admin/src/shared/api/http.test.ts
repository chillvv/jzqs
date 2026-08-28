// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from "vitest";
import {
  createCustomerAddress,
  deleteCustomerAddress,
  fetchRemarkSuggestions,
  handleAdminAuthFailure,
  http,
  updateBannerImages,
  updateCustomerAddress
} from "./http";

describe("fetchRemarkSuggestions", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("passes customerId for order remark suggestions", async () => {
    const getSpy = vi.spyOn(http, "get").mockResolvedValue({
      data: {
        data: {
          scene: "ORDER_REMARK",
          items: []
        }
      }
    } as never);

    await fetchRemarkSuggestions("ORDER_REMARK", 7);

    expect(getSpy).toHaveBeenCalledWith("/api/admin/customers/remark-suggestions?scene=ORDER_REMARK&customerId=7");
  });

  it("does not append customerId for other remark suggestion scenes", async () => {
    const getSpy = vi.spyOn(http, "get").mockResolvedValue({
      data: {
        data: {
          scene: "RECEIPT_NOTE",
          items: []
        }
      }
    } as never);

    await fetchRemarkSuggestions("RECEIPT_NOTE", 7);

    expect(getSpy).toHaveBeenCalledWith("/api/admin/customers/remark-suggestions?scene=RECEIPT_NOTE");
  });
});

describe("customer address mutations", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("posts to create a customer address", async () => {
    const postSpy = vi.spyOn(http, "post").mockResolvedValue({
      data: {
        data: {
          customerId: 7,
          addressId: 18,
          status: "CREATED"
        }
      }
    } as never);

    await createCustomerAddress(7, {
      contactName: "前台",
      contactPhone: "13800000007",
      addressLine: "软件园 A 座",
      areaCode: "高新区",
      isDefault: true
    });

    expect(postSpy).toHaveBeenCalledWith("/api/admin/customers/7/addresses", {
      contactName: "前台",
      contactPhone: "13800000007",
      addressLine: "软件园 A 座",
      areaCode: "高新区",
      isDefault: true
    });
  });

  it("posts to update a customer address", async () => {
    const postSpy = vi.spyOn(http, "post").mockResolvedValue({
      data: {
        data: {
          customerId: 7,
          addressId: 18,
          status: "UPDATED"
        }
      }
    } as never);

    await updateCustomerAddress(7, 18, {
      contactName: "后门",
      contactPhone: "13900000007",
      addressLine: "软件园 B 座",
      areaCode: "高新区",
      isDefault: false
    });

    expect(postSpy).toHaveBeenCalledWith("/api/admin/customers/7/addresses/18", {
      contactName: "后门",
      contactPhone: "13900000007",
      addressLine: "软件园 B 座",
      areaCode: "高新区",
      isDefault: false
    });
  });

  it("deletes a customer address", async () => {
    const deleteSpy = vi.spyOn(http, "delete").mockResolvedValue({
      data: {
        data: {
          customerId: 7,
          addressId: 18,
          status: "DELETED"
        }
      }
    } as never);

    await deleteCustomerAddress(7, 18);

    expect(deleteSpy).toHaveBeenCalledWith("/api/admin/customers/7/addresses/18");
  });
});

describe("updateBannerImages", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("falls back to the legacy payload when the interval field is rejected", async () => {
    const badRequest = {
      response: {
        status: 400
      }
    };
    const postSpy = vi.spyOn(http, "post")
      .mockRejectedValueOnce(badRequest as never)
      .mockResolvedValueOnce({
        data: {
          data: {
            orderingEnabled: true,
            orderingStatusLabel: "通道开启中",
            holidayNoticeTitle: "",
            holidayNoticeDesc: "",
            emergencyActionLabel: "",
            bannerImages: "[{\"imageUrl\":\"/uploads/settings-banners/test.jpg\",\"enabled\":true}]",
            bannerIntervalSeconds: 3,
            popupAnnouncementEnabled: false,
            popupAnnouncementContent: "",
            restNoticeTemplate: ""
          }
        }
      } as never);

    await updateBannerImages("[{\"imageUrl\":\"/uploads/settings-banners/test.jpg\",\"enabled\":true}]", 3);

    expect(postSpy).toHaveBeenNthCalledWith(1, "/api/admin/settings/banner-images", {
      bannerImages: "[{\"imageUrl\":\"/uploads/settings-banners/test.jpg\",\"enabled\":true}]",
      bannerIntervalSeconds: 3
    });
    expect(postSpy).toHaveBeenNthCalledWith(2, "/api/admin/settings/banner-images", {
      bannerImages: "[{\"imageUrl\":\"/uploads/settings-banners/test.jpg\",\"enabled\":true}]"
    });
  });
});

describe("handleAdminAuthFailure", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    window.localStorage.clear();
  });

  it("clears local session and redirects to login", () => {
    window.localStorage.setItem("jzqs_admin_auth", JSON.stringify({
      token: "token",
      userId: 1,
      displayName: "管理员",
      phone: "13800000000",
      role: "ADMIN"
    }));
    window.history.replaceState({}, "", "/dispatch");

    handleAdminAuthFailure();

    expect(window.localStorage.getItem("jzqs_admin_auth")).toBeNull();
    expect(window.location.pathname).toBe("/login");
  });
});
