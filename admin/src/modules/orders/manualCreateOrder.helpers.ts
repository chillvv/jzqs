import type { AdminMenuWeekResponse, AdminMenuWeekSlot } from "../../shared/api/types";

export type ManualCreateMealPeriod = "LUNCH" | "DINNER";

export type ManualCreateAddressOption = {
  addressId: number;
  addressLine: string;
  areaCode: string;
  isDefault: boolean;
};

export type ManualCreateCustomerOption = {
  customerId: number;
  customerName: string;
  customerPhone: string;
  addresses: ManualCreateAddressOption[];
};

export type ManualCreateFormState = {
  customerKeyword: string;
  customerId: string;
  addressId: number | null;
  mealPeriod: ManualCreateMealPeriod | "";
  deliveryMealPeriod: ManualCreateMealPeriod | "";
  merchantRemark: string;
  deliveryAddress: string;
  quantity: number;
};

export type ManualCreateMenuOption = {
  mealPeriod: ManualCreateMealPeriod;
  label: string;
  available: boolean;
  mealSummary: string;
  disabledReason: string;
};

export type ManualCreateExistingOrder = {
  id: number;
  customerId: number;
  addressId: number;
  mealPeriod: ManualCreateMealPeriod;
  status: string;
  displayStatus?: string;
};

export type ManualCreateMergeDecision =
  | { mode: "MERGE"; targetOrderId: number }
  | { mode: "CREATE" };

export type ManualCustomerEmptyStateParams = {
  keyword: string;
  isLoading: boolean;
  customers: Array<unknown>;
  selectedCustomerId: number | null;
};

export function createInitialManualCreateForm(): ManualCreateFormState {
  return {
    customerKeyword: "",
    customerId: "",
    addressId: null,
    mealPeriod: "",
    deliveryMealPeriod: "",
    merchantRemark: "",
    deliveryAddress: "",
    quantity: 1
  };
}

export function formatManualCreateCustomerKeyword(customer: Pick<ManualCreateCustomerOption, "customerName" | "customerPhone">) {
  return `${customer.customerName} / ${customer.customerPhone}`;
}

function resolvePreferredAddress(addresses: ManualCreateAddressOption[]) {
  return addresses.find((address) => address.isDefault) ?? addresses[0] ?? null;
}

export function applyManualCreateCustomerSelection(
  currentForm: ManualCreateFormState,
  customer: ManualCreateCustomerOption
): ManualCreateFormState {
  const preferredAddress = resolvePreferredAddress(customer.addresses);
  return {
    ...currentForm,
    customerKeyword: formatManualCreateCustomerKeyword(customer),
    customerId: String(customer.customerId),
    addressId: preferredAddress?.addressId ?? null,
    deliveryAddress: preferredAddress?.addressLine ?? ""
  };
}

export function applyManualCreateAddressSelection(
  currentForm: ManualCreateFormState,
  addresses: ManualCreateAddressOption[],
  addressId: number
): ManualCreateFormState {
  const selectedAddress = addresses.find((address) => address.addressId === addressId);
  if (!selectedAddress) {
    return currentForm;
  }
  return {
    ...currentForm,
    addressId: selectedAddress.addressId,
    deliveryAddress: selectedAddress.addressLine
  };
}

function buildMealPeriodLabel(mealPeriod: ManualCreateMealPeriod) {
  return mealPeriod === "LUNCH" ? "午餐" : "晚餐";
}

function buildMealSummary(mealPeriod: ManualCreateMealPeriod, dishItems: string[]) {
  return `${buildMealPeriodLabel(mealPeriod)} / ${dishItems.join("、")}`;
}

function toMenuOption(mealPeriod: ManualCreateMealPeriod, slot?: AdminMenuWeekSlot): ManualCreateMenuOption {
  const label = buildMealPeriodLabel(mealPeriod);
  const dishes = (slot?.dishItems ?? []).map((item) => item.trim()).filter(Boolean);

  if (!slot || slot.slotStatus !== "ACTIVE" || dishes.length === 0) {
    return {
      mealPeriod,
      label,
      available: false,
      mealSummary: "",
      disabledReason: `当天${label}菜单未配置`
    };
  }

  return {
    mealPeriod,
    label,
    available: true,
    mealSummary: buildMealSummary(mealPeriod, dishes),
    disabledReason: ""
  };
}

export function resolveManualCreateMenuOptions(week: AdminMenuWeekResponse | null | undefined, targetDate: string): ManualCreateMenuOption[] {
  const day = week?.days.find((item) => item.serveDate === targetDate);
  return [
    toMenuOption("LUNCH", day?.lunch),
    toMenuOption("DINNER", day?.dinner)
  ];
}

export function applyManualCreateMealPeriodSelection(
  currentForm: ManualCreateFormState,
  mealPeriod: ManualCreateMealPeriod,
  menuOptions: ManualCreateMenuOption[]
): ManualCreateFormState {
  const selectedOption = menuOptions.find((option) => option.mealPeriod === mealPeriod && option.available);
  if (!selectedOption) {
    return {
      ...currentForm,
      mealPeriod: ""
    };
  }

  return {
    ...currentForm,
    mealPeriod: selectedOption.mealPeriod,
    deliveryMealPeriod: currentForm.deliveryMealPeriod || selectedOption.mealPeriod
  };
}

export function shouldShowManualCustomerEmptyState({
  keyword,
  isLoading,
  customers,
  selectedCustomerId
}: ManualCustomerEmptyStateParams) {
  return !isLoading && keyword.trim().length > 0 && customers.length === 0 && !selectedCustomerId;
}

export function buildManualCreatePayload(form: ManualCreateFormState, serveDate: string) {
  const customerId = Number(form.customerId);
  const merchantRemark = form.merchantRemark.trim();
  const deliveryAddress = form.deliveryAddress.trim();

  if (!customerId) {
    throw new Error("请选择客户");
  }
  if (!form.addressId) {
    throw new Error("请选择客户地址");
  }
  if (!form.mealPeriod) {
    throw new Error("请选择午餐或晚餐");
  }

  return {
    customerId,
    addressId: form.addressId,
    mealPeriod: form.mealPeriod,
    deliveryMealPeriod: form.deliveryMealPeriod || form.mealPeriod,
    merchantRemark,
    deliveryAddress,
    source: "BACKEND",
    quantity: form.quantity,
    serveDate
  };
}

export function resolveManualCreateMergeDecision(
  target: Pick<ManualCreateExistingOrder, "customerId" | "addressId" | "mealPeriod">,
  existingOrders: ManualCreateExistingOrder[]
): ManualCreateMergeDecision {
  const mergeTarget = existingOrders.find((item) => {
    if (item.customerId !== target.customerId) {
      return false;
    }
    if (item.addressId !== target.addressId) {
      return false;
    }
    if (item.mealPeriod !== target.mealPeriod) {
      return false;
    }
    return item.status !== "CANCELLED" && item.status !== "REFUNDED" && item.displayStatus !== "REFUND_PROCESSING";
  });

  if (!mergeTarget) {
    return { mode: "CREATE" };
  }

  return {
    mode: "MERGE",
    targetOrderId: mergeTarget.id
  };
}
