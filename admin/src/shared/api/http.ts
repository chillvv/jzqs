import axios from "axios";
import type {
  AdminAftersaleResolveResponse,
  AdminAftersaleItemResponse,
  AdminAftersaleOrderOptionResponse,
  AdminAuthLoginResponse,
  AdminAuthProfileResponse,
  ApiResponse,
  BatchOperationResponse,
  CustomerAssetResponse,
  CustomerAddressMutationPayload,
  CustomerAddressMutationResponse,
  CustomerDetailResponse,
  DashboardOverviewResponse,
  DispatchBatchResponse,
  DispatchAreaBindingResponse,
  DispatchAreaDeleteBlockedResponse,
  DispatchAreaAiCorrectionPreviewResponse,
  DispatchBoardItemResponse,
  DispatchCreateRiderPayload,
  DispatchCreateRiderResponse,
  DispatchExceptionItemResponse,
  DispatchManagedRiderResponse,
  DispatchOverviewResponse,
  DispatchPendingItemResponse,
  DispatchRiderProgressResponse,
  DispatchReassignmentResponse,
  DispatchRiderAuthBindingResponse,
  PendingRiderResponse,
  AdminMenuWeekResponse,
  BannerImageUploadResponse,
  DeliveryReceiptUploadResponse,
  DispatchAiRunNowResponse,
  DispatchRouteLabStartResponse,
  DispatchAiWorkbenchResponse,
  DispatchAreaCodeListResponse,
  DispatchAreaMemoryListResponse,
  DispatchAreaMemorySourceListResponse,
  MenuScheduleResponse,
  OperationSettingsResponse,
  OrderPrepItemResponse,
  OrderPrepStatsResponse,
  SubscriptionConfirmationItem,
  PageResponse,
  RemarkSuggestionResponse,
  RemarkSuggestionScene,
  WalletTransactionResponse,
  SubscriptionPreviewItem,
  SubscriptionImportItem,
  AnalysisOverviewResponse,
  CostEntryItem,
  SubscriptionRuleResponse,
  SubscriptionRuleFormData,
  LowBalanceSubscriptionItem,
  SubscriptionPreviewCheckResponse,
  ManualCreateCustomerSearchResponse
} from "./types";
import { ADMIN_AUTH_STORAGE_KEY, parseAdminAuthSession } from "../../modules/auth/adminAuth.helpers";

export function extractAdminApiErrorMessage(error: unknown, fallback = "请求失败") {
  if (typeof error === "object" && error !== null) {
    const errorLike = error as { response?: { data?: { message?: string } }; message?: string };
    return errorLike.response?.data?.message || errorLike.message || fallback;
  }
  return typeof error === "string" ? error : fallback;
}

export function handleAdminAuthFailure() {
  if (typeof window === "undefined") {
    return;
  }
  window.localStorage.removeItem(ADMIN_AUTH_STORAGE_KEY);
  if (window.location.pathname !== "/login") {
    window.history.replaceState({}, "", "/login");
    window.dispatchEvent(new PopStateEvent("popstate"));
  }
}

export const http = axios.create({
  baseURL: "/",
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json;charset=UTF-8'
  }
});

http.interceptors.request.use((config) => {
  if (typeof window === "undefined") {
    return config;
  }
  const url = config.url ?? "";
  if (!url.startsWith("/api/admin") || url === "/api/admin/auth/login") {
    return config;
  }
  const session = parseAdminAuthSession(window.localStorage.getItem(ADMIN_AUTH_STORAGE_KEY));
  if (session?.token) {
    config.headers = config.headers ?? {};
    config.headers.Authorization = `Bearer ${session.token}`;
  }
  return config;
});

http.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error?.response?.status;
    const url = String(error?.config?.url ?? "");
    if (status === 401 && url.startsWith("/api/admin")) {
      handleAdminAuthFailure();
    }
    return Promise.reject(error);
  }
);

export class DispatchAreaDeleteBlockedError extends Error {
  code = "DISPATCH_AREA_HAS_ACTIVE_ORDERS";
  details: DispatchAreaDeleteBlockedResponse;

  constructor(message: string, details: DispatchAreaDeleteBlockedResponse) {
    super(message);
    this.name = "DispatchAreaDeleteBlockedError";
    this.details = details;
  }
}

export async function fetchDashboardOverview() {
  const response = await http.get<ApiResponse<DashboardOverviewResponse>>("/api/admin/dashboard/overview");
  return response.data.data;
}

export async function fetchOrderPrepStats() {
  const response = await http.get<ApiResponse<OrderPrepStatsResponse>>("/api/admin/orders/prep-stats");
  return response.data.data;
}

export async function fetchSubscriptionConfirmations(serveDate: string) {
  const response = await http.get<ApiResponse<SubscriptionConfirmationItem[]>>(`/api/admin/orders/subscription-confirmations?serveDate=${serveDate}`);
  return response.data.data;
}

export async function confirmSubscription(confirmationId: number) {
  const response = await http.post<ApiResponse<{ confirmationId: number; status: string }>>(`/api/admin/orders/subscription-confirmations/${confirmationId}/confirm`);
  return response.data.data;
}

export async function cancelSubscriptionConfirmation(confirmationId: number, cancelReason: string) {
  const response = await http.post<ApiResponse<{ confirmationId: number; status: string }>>(`/api/admin/orders/subscription-confirmations/${confirmationId}/cancel`, {
    cancelReason
  });
  return response.data.data;
}

export async function fetchOrderPrepList(serveDate?: string) {
  const url = serveDate 
    ? `/api/admin/orders?serveDate=${encodeURIComponent(serveDate)}`
    : '/api/admin/orders';
  const response = await http.get<ApiResponse<PageResponse<OrderPrepItemResponse>>>(url);
  return response.data.data;
}

export async function fetchAftersales(params?: {
  status?: string;
  type?: string;
  startDate?: string;
  endDate?: string;
  view?: string;
  hideAutoRefund?: boolean;
}) {
  const query = new URLSearchParams();
  if (params?.status) query.set("status", params.status);
  if (params?.type) query.set("type", params.type);
  if (params?.startDate) query.set("startDate", params.startDate);
  if (params?.endDate) query.set("endDate", params.endDate);
  if (params?.view) query.set("view", params.view);
  if (params?.hideAutoRefund !== undefined) query.set("hideAutoRefund", String(params.hideAutoRefund));
  const suffix = query.toString() ? `?${query.toString()}` : "";
  const response = await http.get<ApiResponse<AdminAftersaleItemResponse[]>>(`/api/admin/aftersales${suffix}`);
  return response.data.data;
}

export async function fetchAftersaleOrderOptions(serveDate: string) {
  const response = await http.get<ApiResponse<AdminAftersaleOrderOptionResponse[]>>(
    `/api/admin/aftersales/order-options?serveDate=${encodeURIComponent(serveDate)}`
  );
  return response.data.data;
}

export async function createAftersaleCase(payload: {
  orderId: number;
  type: string;
  reasonCode: string;
  reasonText: string;
  issueParamSummary?: string;
  estimatedLossMeals?: number;
  sourceCategory?: string;
  remark?: string;
}) {
  const response = await http.post<ApiResponse<{ afterSaleId: number; status: string }>>(
    "/api/admin/aftersales",
    payload
  );
  return response.data.data;
}

export async function resolveAftersaleCase(caseId: number, payload: {
  resolutionAction: string;
  refundBlocking: boolean;
  walletDelta: number;
  settledLossMeals: number;
  giftZeroMealCount: number;
  giftVeggieJuiceCount: number;
  adminRemark: string;
}) {
  const response = await http.post<ApiResponse<AdminAftersaleResolveResponse>>(`/api/admin/aftersales/${caseId}/resolve`, payload);
  return response.data.data;
}

export async function adminLogin(phone: string, password: string) {
  const response = await http.post<ApiResponse<AdminAuthLoginResponse>>("/api/admin/auth/login", {
    phone,
    password
  });
  return response.data.data;
}

export async function fetchAdminProfile() {
  const response = await http.get<ApiResponse<AdminAuthProfileResponse>>("/api/admin/auth/me");
  return response.data.data;
}

export async function changeAdminPassword(oldPassword: string, newPassword: string) {
  const response = await http.post<ApiResponse<{ status: string }>>("/api/admin/auth/change-password", {
    oldPassword,
    newPassword
  });
  return response.data.data;
}

export async function logoutAdmin() {
  const response = await http.post<ApiResponse<{ status: string }>>("/api/admin/auth/logout");
  return response.data.data;
}

export async function updateOrderMerchantRemark(orderId: number, merchantRemark: string) {
  const response = await http.post<ApiResponse<{ orderId: number; status: string }>>(`/api/admin/orders/${orderId}/merchant-remark`, {
    merchantRemark
  });
  return response.data.data;
}

export async function updateOrderProfile(orderId: number, payload: {
  mealPeriod: "LUNCH" | "DINNER";
  quantity: number;
  deliveryAddress: string;
  merchantRemark: string;
  priorityCustomer?: boolean;
  status?: string;
}) {
  const response = await http.post<ApiResponse<{ orderId: number; status: string }>>(`/api/admin/orders/${orderId}/profile`, payload);
  return response.data.data;
}

export async function applyOrderSpecialDispatch(orderId: number, deliveryMealPeriod: "LUNCH" | "DINNER") {
  const response = await http.post<ApiResponse<{ orderId: number; status: string; deliveryMealPeriod: string }>>(
    `/api/admin/orders/${orderId}/special-dispatch`,
    { deliveryMealPeriod }
  );
  return response.data.data;
}

export async function clearOrderSpecialDispatch(orderId: number) {
  const response = await http.post<ApiResponse<{ orderId: number; status: string; deliveryMealPeriod: string }>>(
    `/api/admin/orders/${orderId}/special-dispatch/reset`
  );
  return response.data.data;
}

export async function createOrderAftersale(orderId: number, payload: {
  type: string;
  reasonCode: string;
  reasonText: string;
  remark?: string;
}) {
  const response = await http.post<ApiResponse<{ afterSaleId: number; status: string }>>(`/api/admin/orders/${orderId}/after-sales`, payload);
  return response.data.data;
}

export async function directRefund(orderId: number, payload: {
  reasonCode: string;
  reasonText: string;
}) {
  const response = await http.post<ApiResponse<{ afterSaleId: number; status: string }>>(`/api/admin/orders/${orderId}/direct-refund`, payload);
  return response.data.data;
}

export async function fetchSubscriptionPreview(serveDate: string) {
  const response = await http.get<ApiResponse<SubscriptionPreviewItem[]>>(`/api/admin/orders/subscription-preview?serveDate=${serveDate}`);
  return response.data.data;
}

export async function bulkImportSubscription(serveDate: string, items: SubscriptionImportItem[]) {
  const response = await http.post<ApiResponse<{ successCount: number }>>("/api/admin/orders/bulk-import-subscription", {
    serveDate,
    items
  });
  return response.data.data;
}

export async function fetchCustomerAssets() {
  const response = await http.get<ApiResponse<PageResponse<CustomerAssetResponse>>>("/api/admin/customers/assets");
  return response.data.data;
}

export async function fetchCustomerDetail(customerId: number) {
  const response = await http.get<ApiResponse<CustomerDetailResponse>>(`/api/admin/customers/${customerId}`);
  return response.data.data;
}

export async function searchManualCreateCustomers(keyword: string) {
  const response = await http.get<ApiResponse<ManualCreateCustomerSearchResponse[]>>(
    `/api/admin/orders/manual-create/customers?keyword=${encodeURIComponent(keyword)}`
  );
  return response.data.data;
}

export async function createCustomerProfile(payload: {
  name: string;
  phone: string;
  merchantRemark: string;
  addressLine: string;
  contactName: string;
  contactPhone: string;
  initialMealDelta?: number;
  initialMealRemark?: string;
  initialValidityDays?: number;
  priorityCustomer?: boolean;
  priorityTag?: string;
  priorityNote?: string;
}) {
  const response = await http.post<ApiResponse<{ customerId: number; status: string }>>("/api/admin/customers", payload);
  return response.data.data;
}

export async function updateCustomerProfile(customerId: number, payload: {
  name?: string;
  phone?: string;
  merchantRemark?: string;
  customerStatus?: string;
  openedAt?: string | null;
  expiredAt?: string | null;
  remainingValidityDays?: number | null;
}) {
  const response = await http.post<ApiResponse<{ customerId: number; status: string }>>(`/api/admin/customers/${customerId}/profile`, payload);
  return response.data.data;
}

export async function createCustomerAddress(customerId: number, payload: CustomerAddressMutationPayload) {
  const response = await http.post<ApiResponse<CustomerAddressMutationResponse>>(
    `/api/admin/customers/${customerId}/addresses`,
    payload
  );
  return response.data.data;
}

export async function updateCustomerAddress(customerId: number, addressId: number, payload: CustomerAddressMutationPayload) {
  const response = await http.post<ApiResponse<CustomerAddressMutationResponse>>(
    `/api/admin/customers/${customerId}/addresses/${addressId}`,
    payload
  );
  return response.data.data;
}

export async function deleteCustomerAddress(customerId: number, addressId: number) {
  const response = await http.delete<ApiResponse<CustomerAddressMutationResponse>>(
    `/api/admin/customers/${customerId}/addresses/${addressId}`
  );
  return response.data.data;
}

export async function fetchMenuSchedules() {
  const response = await http.get<ApiResponse<PageResponse<MenuScheduleResponse>>>("/api/admin/menu-schedules");
  return response.data.data;
}

export async function fetchCurrentMenuWeek(targetDate?: string) {
  const url = targetDate ? `/api/admin/menu-weeks/current?targetDate=${encodeURIComponent(targetDate)}` : "/api/admin/menu-weeks/current";
  const response = await http.get<ApiResponse<AdminMenuWeekResponse>>(url);
  return response.data.data;
}

export async function createNextMenuWeek() {
  const response = await http.post<ApiResponse<{ weekId: number; status: string }>>("/api/admin/menu-weeks");
  return response.data.data;
}

export async function copyMenuWeekFromLastWeek() {
  const response = await http.post<ApiResponse<{ weekId: number; weekStartDate: string; weekEndDate: string; status: string; copiedFromWeekStart: string }>>("/api/admin/menu-weeks/copy-from-last-week");
  return response.data.data;
}

export async function saveMenuWeekDay(weekId: number, serveDate: string, payload: {
  lunch: {
    slotStatus: string;
    dishItems: string[];
    totalCalories: number | null;
    merchantNote: string;
    imageUrl: string;
  };
  dinner: {
    slotStatus: string;
    dishItems: string[];
    totalCalories: number | null;
    merchantNote: string;
    imageUrl: string;
  };
}) {
  const response = await http.put<ApiResponse<{ weekId: number; serveDate: string; status: string }>>(
    `/api/admin/menu-weeks/${weekId}/days/${serveDate}`,
    payload
  );
  return response.data.data;
}

export async function publishMenuWeek(weekId: number) {
  const response = await http.post<ApiResponse<{ weekId: number; status: string }>>(`/api/admin/menu-weeks/${weekId}/publish`);
  return response.data.data;
}

export async function fetchDispatchBoard() {
  const response = await http.get<ApiResponse<PageResponse<DispatchBoardItemResponse>>>("/api/admin/dispatch/board");
  return response.data.data;
}

export async function fetchDispatchOverview(mealPeriod: "LUNCH" | "DINNER", serveDate?: string) {
  const params = new URLSearchParams({ mealPeriod });
  if (serveDate) params.set('serveDate', serveDate);
  const response = await http.get<ApiResponse<DispatchOverviewResponse>>(
    `/api/admin/dispatch/overview?${params.toString()}`
  );
  return response.data.data;
}

export async function fetchDispatchRiderProgress(mealPeriod?: "LUNCH" | "DINNER", serveDate?: string) {
  const params = new URLSearchParams();
  if (mealPeriod) params.set("mealPeriod", mealPeriod);
  if (serveDate) params.set("serveDate", serveDate);
  const suffix = params.toString() ? `?${params.toString()}` : "";
  const response = await http.get<ApiResponse<DispatchRiderProgressResponse[]>>(
    `/api/admin/dispatch/riders/progress${suffix}`
  );
  return response.data.data;
}

export async function fetchDispatchBatches(serveDate?: string, mealPeriod?: string) {
  const query = new URLSearchParams();
  if (serveDate) query.set("serveDate", serveDate);
  if (mealPeriod) query.set("mealPeriod", mealPeriod);
  const suffix = query.toString() ? `?${query.toString()}` : "";
  const response = await http.get<ApiResponse<DispatchBatchResponse[]>>(`/api/admin/dispatch/batches${suffix}`);
  return response.data.data;
}

export async function fetchDispatchExceptions() {
  const response = await http.get<ApiResponse<DispatchExceptionItemResponse[]>>("/api/admin/dispatch/exceptions");
  return response.data.data;
}

export async function fetchDispatchPendingItems(mealPeriod: "LUNCH" | "DINNER", serveDate?: string) {
  const params = new URLSearchParams({ mealPeriod });
  if (serveDate) params.set('serveDate', serveDate);
  const response = await http.get<ApiResponse<DispatchPendingItemResponse[]>>(
    `/api/admin/dispatch/pending-items?${params.toString()}`
  );
  return response.data.data;
}

export async function batchAssignDispatchPendingOrders(payload: {
  orderIds: number[];
  areaCode: string;
}) {
  const response = await http.post<ApiResponse<BatchOperationResponse>>("/api/admin/dispatch/pending-items/batch-assign", payload);
  return response.data.data;
}

export async function fetchPendingRiders() {
  const response = await http.get<ApiResponse<PendingRiderResponse[]>>("/api/admin/dispatch/pending-riders");
  return response.data.data;
}

export async function fetchDispatchManagedRiders(params?: {
  authStatus?: string;
  keyword?: string;
  areaCode?: string;
}) {
  const query = new URLSearchParams();
  if (params?.authStatus) query.set("authStatus", params.authStatus);
  if (params?.keyword) query.set("keyword", params.keyword);
  if (params?.areaCode) query.set("areaCode", params.areaCode);
  const suffix = query.toString() ? `?${query.toString()}` : "";
  const response = await http.get<ApiResponse<DispatchManagedRiderResponse[]>>(`/api/admin/dispatch/riders${suffix}`);
  return response.data.data;
}

export async function createDispatchRider(payload: DispatchCreateRiderPayload) {
  const response = await http.post<ApiResponse<DispatchCreateRiderResponse>>("/api/admin/dispatch/riders", payload);
  return response.data.data;
}

export async function updateDispatchRiderProfile(riderId: number, payload: {
  riderName: string;
  displayName: string;
  phone: string;
  areaCode: string;
}) {
  const response = await http.post<ApiResponse<{ riderId: number; riderName: string; displayName: string; phone: string; areaCode: string }>>(
    `/api/admin/dispatch/riders/${riderId}/profile`,
    payload
  );
  return response.data.data;
}

export async function fetchDispatchRiderAuthBinding(riderId: number) {
  const response = await http.get<ApiResponse<DispatchRiderAuthBindingResponse>>(`/api/admin/dispatch/riders/${riderId}/auth-binding`);
  return response.data.data;
}

export async function takeoverDispatchRiderAuth(riderId: number, payload: {
  sourceRiderId: number;
}) {
  const response = await http.post<ApiResponse<{ riderId: number; sourceRiderId: number; currentOpenid: string; riderStatus: string }>>(
    `/api/admin/dispatch/riders/${riderId}/takeover-auth`,
    payload
  );
  return response.data.data;
}

export async function unbindDispatchRiderAuth(riderId: number) {
  const response = await http.post<ApiResponse<{ riderId: number; currentOpenid: string; riderStatus: string }>>(
    `/api/admin/dispatch/riders/${riderId}/unbind-auth`
  );
  return response.data.data;
}

export async function fetchDispatchAreaBindings(mealPeriod?: "LUNCH" | "DINNER", serveDate?: string) {
  const params = new URLSearchParams();
  if (mealPeriod) params.set('mealPeriod', mealPeriod);
  if (serveDate) params.set('serveDate', serveDate);
  const suffix = params.toString() ? `?${params.toString()}` : '';
  const response = await http.get<ApiResponse<DispatchAreaBindingResponse[]>>(
    `/api/admin/dispatch/area-bindings${suffix}`
  );
  return response.data.data;
}

export async function updateDispatchAreaBinding(areaCode: string, payload: {
  keywords?: string | null;
  defaultRiderId: number | null;
  backupRiderId?: number | null;
}) {
  const response = await http.post<ApiResponse<{ areaCode: string; defaultRiderId: number; backupRiderId: number | null }>>(
    `/api/admin/dispatch/area-bindings/${encodeURIComponent(areaCode)}`,
    payload
  );
  return response.data.data;
}

export async function removeDispatchAreaBinding(areaCode: string, riderId: number) {
  const response = await http.post<ApiResponse<{ areaCode: string; riderId: number; status: string }>>(
    "/api/admin/dispatch/area-bindings/remove-rider",
    { areaCode, riderId }
  );
  return response.data.data;
}

export async function assignRiderToArea(areaCode: string, riderName: string, mealPeriod: "LUNCH" | "DINNER") {
  const response = await http.post<ApiResponse<{ areaCode: string; assignedCount: number }>>(
    `/api/admin/dispatch/areas/${encodeURIComponent(areaCode)}/assign-rider?mealPeriod=${mealPeriod}`,
    { riderName }
  );
  return response.data.data;
}

export async function reorderAreaOrders(areaCode: string, items: Array<{ orderId: number; sequenceNumber: number }>) {
  const response = await http.post<ApiResponse<{ areaCode: string; updatedCount: number }>>(
    `/api/admin/dispatch/areas/${encodeURIComponent(areaCode)}/reorder`,
    items
  );
  return response.data.data;
}

export async function previewAreaAiCorrection(areaCode: string, payload: {
  routeRunId?: number | null;
  originalOrderIds: number[];
  merchantOrderIds: number[];
  inputAddresses: string[];
  merchantInstruction?: string;
  merchantReasonSummary?: string;
}) {
  const response = await http.post<ApiResponse<DispatchAreaAiCorrectionPreviewResponse>>(
    `/api/admin/dispatch/areas/${encodeURIComponent(areaCode)}/ai-correction/preview`,
    payload
  );
  return response.data.data;
}

export async function confirmAreaAiCorrection(areaCode: string, payload: {
  correctionId: number;
  finalOrderIds: number[];
}) {
  const response = await http.post<ApiResponse<DispatchAreaAiCorrectionPreviewResponse>>(
    `/api/admin/dispatch/areas/${encodeURIComponent(areaCode)}/ai-correction/confirm`,
    payload
  );
  return response.data.data;
}

export async function moveOrderToArea(areaCode: string, orderId: number, payload: {
  targetAreaCode: string;
}) {
  const response = await http.post<ApiResponse<{ areaCode: string; orderId: number; targetAreaCode: string }>>(
    `/api/admin/dispatch/areas/${encodeURIComponent(areaCode)}/orders/${orderId}/move`,
    payload
  );
  return response.data.data;
}

export async function assignRiderToAreaOrder(areaCode: string, orderId: number, riderName: string) {
  const response = await http.post<ApiResponse<{ areaCode: string; orderId: number; status: string }>>(
    `/api/admin/dispatch/areas/${encodeURIComponent(areaCode)}/orders/${orderId}/assign-rider`,
    { riderName }
  );
  return response.data.data;
}

export async function renameDispatchArea(areaCode: string, newAreaCode: string) {
  const response = await http.put<ApiResponse<{ oldAreaCode: string; newAreaCode: string; status: string }>>(
    `/api/admin/dispatch/area-bindings/${encodeURIComponent(areaCode)}/rename`,
    { newAreaCode }
  );
  return response.data.data;
}

export async function deleteDispatchArea(areaCode: string) {
  try {
    const response = await http.post<ApiResponse<{ areaCode: string; status: string }>>(
      "/api/admin/dispatch/area-bindings/delete",
      { areaCode }
    );
    return response.data.data;
  } catch (error) {
    if (axios.isAxiosError(error) && error.response?.data?.code === "DISPATCH_AREA_HAS_ACTIVE_ORDERS") {
      throw new DispatchAreaDeleteBlockedError(
        error.response.data.message,
        error.response.data.data as DispatchAreaDeleteBlockedResponse
      );
    }
    throw error;
  }
}

export interface DispatchRouteSuggestionRequest {
  serveDate: string;
  mealPeriod: string;
  strategyMode: string;
  anchorName: string;
  anchorAddress: string;
  enableAiRefine?: boolean;
}

export interface DispatchRouteSuggestionItemResponse {
  orderId: number;
  suggestedSequence: number;
  aiAdjusted: boolean;
  ruleSequence: number;
  addressLabel: string;
  clusterName: string;
  buildingName: string;
  roadName: string;
  distanceBand: string;
  neighborCount: number;
  adjustmentReason: string;
}

export interface DispatchRouteSuggestionResponse {
  suggestionId: number;
  strategyMode: string;
  suggestionSource: string;
  reasonSummary: string;
  runStatusCode: string;
  runStatusLabel: string;
  runStatusDescription: string;
  items: DispatchRouteSuggestionItemResponse[];
}

export interface DispatchRouteSuggestionFeedbackRequest {
  suggestionId: number;
  acceptedDirectly: boolean;
  changeCount: number;
  feedbackSummary?: string;
}

export interface DispatchRouteSuggestionFeedbackResponse {
  feedbackId: number;
}

export async function getDispatchAreaRouteSuggestion(
  areaCode: string,
  request: DispatchRouteSuggestionRequest
) {
  const response = await http.post<ApiResponse<DispatchRouteSuggestionResponse>>(
    `/api/admin/dispatch/areas/${encodeURIComponent(areaCode)}/route-suggestion`,
    request
  );
  return response.data.data;
}

export async function simulateRouteLab(
  request: {
    addresses: string[];
    strategyMode: string;
    anchorAddress: string;
  }
) {
  const response = await http.post<ApiResponse<DispatchRouteLabStartResponse>>(
    "/api/admin/dispatch/route-lab/simulate",
    request
  );
  return response.data.data;
}

export async function fetchDispatchAiJobLog(logId: number) {
  const response = await http.get<ApiResponse<import("./types").DispatchAiJobLogResponse>>(
    `/api/admin/dispatch/job-logs/${logId}`
  );
  return response.data.data;
}

export async function deleteDispatchAiJobLogs(request: { ids: number[] }) {
  const response = await http.delete<ApiResponse<void>>("/api/admin/dispatch/job-logs", {
    data: request
  });
  return response.data.data;
}

export async function saveDispatchAreaRouteSuggestionFeedback(
  areaCode: string,
  request: DispatchRouteSuggestionFeedbackRequest
) {
  const response = await http.post<ApiResponse<DispatchRouteSuggestionFeedbackResponse>>(
    `/api/admin/dispatch/areas/${encodeURIComponent(areaCode)}/route-suggestion-feedback`,
    request
  );
  return response.data.data;
}

export async function fetchDispatchReassignments(serveDate?: string) {
  const suffix = serveDate ? `?serveDate=${encodeURIComponent(serveDate)}` : "";
  const response = await http.get<ApiResponse<DispatchReassignmentResponse[]>>(`/api/admin/dispatch/reassignments${suffix}`);
  return response.data.data;
}

export async function reassignDispatchWork(payload: {
  reassignLevel: string;
  targetId: number;
  fromRiderName?: string;
  toRiderName: string;
  toAreaCode?: string;
  serveDate: string;
  mealPeriod?: string;
  syncDefaultBinding: boolean;
  reason?: string;
}) {
  const response = await http.post<ApiResponse<{
    reassignLevel: string;
    targetId: number;
    toRiderName: string;
    toAreaCode: string | null;
    syncDefaultBinding: boolean;
    affectedOrderCount: number;
  }>>("/api/admin/dispatch/reassign", payload);
  return response.data.data;
}

export async function activateDispatchRider(riderId: number, payload: {
  riderName: string;
  areaCode: string;
}) {
  const response = await http.post<ApiResponse<{ riderId: number; riderName: string; riderStatus: string; areaCode: string }>>(
    `/api/admin/dispatch/riders/${riderId}/activate`,
    payload
  );
  return response.data.data;
}

export async function disableDispatchRider(riderId: number) {
  const response = await http.post<ApiResponse<{ riderId: number; riderStatus: string }>>(
    `/api/admin/dispatch/riders/${riderId}/disable`
  );
  return response.data.data;
}

export async function deleteDispatchRider(riderId: number) {
  const response = await http.delete<ApiResponse<void>>(`/api/admin/riders/${riderId}`);
  return response.data.data;
}

export async function fetchOperationSettings() {
  const response = await http.get<ApiResponse<OperationSettingsResponse>>("/api/admin/settings/operation-status");
  return response.data.data;
}

export async function fetchDispatchAiWorkbench() {
  const response = await http.get<ApiResponse<DispatchAiWorkbenchResponse>>("/api/admin/settings/dispatch-ai-workbench");
  return response.data.data;
}

export async function fetchDispatchAreaCodes() {
  const response = await http.get<ApiResponse<DispatchAreaCodeListResponse>>("/api/admin/settings/dispatch-area-codes");
  return response.data.data;
}

export async function fetchDispatchAreaMemories(areaCode: string) {
  const response = await http.get<ApiResponse<DispatchAreaMemoryListResponse>>(
    `/api/admin/settings/dispatch-area-memories?areaCode=${encodeURIComponent(areaCode)}`
  );
  return response.data.data;
}

export async function updateDispatchAreaMemory(memoryId: number, payload: {
  title: string;
  summary: string;
  applicableScene: string;
  status: string;
}) {
  const response = await http.post<ApiResponse<DispatchAreaMemoryListResponse>>(
    `/api/admin/settings/dispatch-area-memories/${memoryId}`,
    payload
  );
  return response.data.data;
}

export async function fetchDispatchAreaMemorySources(memoryId: number) {
  const response = await http.get<ApiResponse<DispatchAreaMemorySourceListResponse>>(
    `/api/admin/settings/dispatch-area-memories/${memoryId}/sources`
  );
  return response.data.data;
}

export async function deleteDispatchAreaMemory(memoryId: number) {
  const response = await http.post<ApiResponse<DispatchAreaMemoryListResponse>>(
    `/api/admin/settings/dispatch-area-memories/${memoryId}/delete`,
    {}
  );
  return response.data.data;
}

export async function updateDispatchRouteWorkbench(payload: {
  autoScheduleEnabled: boolean;
  autoScheduleTime: string;
  defaultStrategyMode: string;
  anchorAddress: string;
}) {
  const response = await http.post<ApiResponse<DispatchAiWorkbenchResponse>>("/api/admin/settings/dispatch-route-workbench", payload);
  return response.data.data;
}

export async function updateDispatchAiWorkbench(payload: {
  autoScheduleEnabled: boolean;
  autoScheduleTime: string;
  defaultStrategyMode: string;
  anchorName: string;
  anchorAddress: string;
  aiEnabled: boolean;
  apiBaseUrl: string;
  apiKey: string;
  aiModel: string;
  aiPromptTemplate: string;
  lowBalanceThreshold: string;
}) {
  const response = await http.post<ApiResponse<DispatchAiWorkbenchResponse>>("/api/admin/settings/dispatch-ai-workbench", payload);
  return response.data.data;
}

export async function refreshDispatchAiBalance() {
  const response = await http.post<ApiResponse<DispatchAiWorkbenchResponse>>("/api/admin/settings/dispatch-ai-workbench/refresh-balance");
  return response.data.data;
}

export async function runDispatchAiNow(payload: {
  serveDate?: string;
  mealPeriod?: string;
  areaCode?: string;
}) {
  const response = await http.post<ApiResponse<DispatchAiRunNowResponse>>("/api/admin/settings/dispatch-ai-workbench/run-now", payload);
  return response.data.data;
}

export async function assignDispatch(mealSlotOrderId: number, riderName: string, areaCode: string) {
  const response = await http.post<ApiResponse<{ status: string }>>("/api/admin/dispatch/assign", {
    mealSlotOrderId,
    riderName,
    areaCode
  });
  return response.data.data;
}

export async function autoAssignDispatch() {
  const response = await http.post<ApiResponse<{ assignedCount: number; exceptionCount: number }>>("/api/admin/dispatch/auto-assign");
  return response.data.data;
}

export async function resolveDispatchException(mealSlotOrderId: number, riderName: string, areaCode: string) {
  const response = await http.post<ApiResponse<{ status: string }>>(`/api/admin/dispatch/exceptions/${mealSlotOrderId}/resolve`, {
    riderName,
    areaCode
  });
  return response.data.data;
}

export async function confirmDispatchExceptionArea(mealSlotOrderId: number, payload: {
  areaCode: string;
  riderName: string;
  rememberAddress: boolean;
}) {
  const response = await http.post<ApiResponse<{
    mealSlotOrderId: number;
    areaCode: string;
    riderName: string;
    rememberAddress: boolean;
    status: string;
  }>>(`/api/admin/dispatch/exceptions/${mealSlotOrderId}/confirm-area`, payload);
  return response.data.data;
}

export async function notifyDispatch(dispatchId: number) {
  const response = await http.post<ApiResponse<{ notificationStatus: string }>>(`/api/admin/dispatch/${dispatchId}/notify`);
  return response.data.data;
}

export async function updateOrderingToggle(enabled: boolean) {
  const response = await http.post<ApiResponse<OperationSettingsResponse>>("/api/admin/settings/ordering-toggle", {
    enabled
  });
  return response.data.data;
}

export async function updateHolidayNotice(title: string, description: string) {
  const response = await http.post<ApiResponse<OperationSettingsResponse>>("/api/admin/settings/holiday-notice", {
    title,
    description
  });
  return response.data.data;
}

export async function updateBannerImages(bannerImages: string, bannerIntervalSeconds: number) {
  try {
    const response = await http.post<ApiResponse<OperationSettingsResponse>>("/api/admin/settings/banner-images", {
      bannerImages,
      bannerIntervalSeconds
    });
    return response.data.data;
  } catch (err: any) {
    // Compatibility fallback for local backends still running the old payload contract.
    if (err?.response?.status !== 400) {
      throw err;
    }
    const legacyResponse = await http.post<ApiResponse<OperationSettingsResponse>>("/api/admin/settings/banner-images", {
      bannerImages
    });
    return legacyResponse.data.data;
  }
}

export async function uploadBannerImage(file: File) {
  const formData = new FormData();
  formData.append("file", file);
  const response = await http.post<ApiResponse<BannerImageUploadResponse>>(
    "/api/admin/settings/banner-images/upload",
    formData,
    {
      headers: {
        "Content-Type": "multipart/form-data"
      }
    }
  );
  return response.data.data;
}

export async function pauseOrderingWithNotice(payload: {
  title: string;
  description: string;
  popupEnabled: boolean;
  popupContent: string;
}) {
  const response = await http.post<ApiResponse<OperationSettingsResponse>>(
    "/api/admin/settings/ordering/pause-with-notice",
    payload
  );
  return response.data.data;
}

export async function updatePopupAnnouncement(payload: {
  title: string;
  description: string;
  enabled: boolean;
  content: string;
}) {
  const response = await http.post<ApiResponse<OperationSettingsResponse>>("/api/admin/settings/popup-announcement", {
    title: payload.title,
    description: payload.description,
    enabled: payload.enabled,
    content: payload.content
  });
  return response.data.data;
}

export async function createManualOrder(payload: {
  customerId: number;
  addressId: number;
  mealPeriod: "LUNCH" | "DINNER";
  merchantRemark: string;
  deliveryAddress: string;
  source: string;
  quantity: number;
  serveDate: string;
}) {
  const response = await http.post<ApiResponse<{ orderId: number; status: string }>>("/api/admin/orders/manual-create", payload);
  return response.data.data;
}

export async function cancelOrder(orderId: number) {
  const response = await http.post<ApiResponse<{ orderId: number; status: string }>>(`/api/admin/orders/${orderId}/cancel`);
  return response.data.data;
}

export async function deleteOrder(orderId: number) {
  const response = await http.post<ApiResponse<{ orderId: number; status: string }>>(`/api/admin/orders/${orderId}/delete`);
  return response.data.data;
}

export async function consumeOrders(orderIds: number[]) {
  const response = await http.post<ApiResponse<BatchOperationResponse>>("/api/admin/orders/consume", { orderIds });
  return response.data.data;
}

export async function recordDeliveryReceipt(payload: {
  mealSlotOrderId: number;
  receiptUrl: string;
  receiptNote: string;
  deliveredAt: string;
}) {
  const response = await http.post<ApiResponse<{ orderStatus: string; walletAction: string }>>("/api/admin/deliveries/receipt", payload);
  return response.data.data;
}

export async function deleteDeliveryReceipt(orderId: number) {
  const response = await http.post<ApiResponse<{ orderStatus: string; receiptUrl: string; deleted: boolean }>>(
    `/api/admin/orders/${orderId}/receipt/delete`
  );
  return response.data.data;
}

export async function uploadDeliveryReceiptImage(file: File) {
  const formData = new FormData();
  formData.append("file", file);
  const response = await http.post<ApiResponse<DeliveryReceiptUploadResponse>>(
    "/api/admin/deliveries/receipt/upload",
    formData,
    {
      headers: {
        "Content-Type": "multipart/form-data"
      }
    }
  );
  return response.data.data;
}

export async function grantWalletMeals(customerId: number, mealDelta: number, validityDays: number, remark: string) {
  const response = await http.post<ApiResponse<{ remainingMeals: number }>>(`/api/admin/customers/${customerId}/wallet/grant`, {
    mealDelta,
    validityDays,
    remark
  });
  return response.data.data;
}

export async function updatePackageReminderSettings(payload: {
  packageExpiryReminderDays: number;
  packageLowBalanceThreshold: number;
  mealReminderPopupEnabled: boolean;
  deliverySubscribeEnabled: boolean;
  deliverySubscribeLunchTime: string;
  deliverySubscribeDinnerTime: string;
}) {
  const response = await http.post<ApiResponse<OperationSettingsResponse>>("/api/admin/settings/package-reminders", {
    packageExpiryReminderDays: payload.packageExpiryReminderDays,
    packageLowBalanceThreshold: payload.packageLowBalanceThreshold,
    mealReminderPopupEnabled: payload.mealReminderPopupEnabled,
    deliverySubscribeEnabled: payload.deliverySubscribeEnabled,
    deliverySubscribeLunchTime: payload.deliverySubscribeLunchTime,
    deliverySubscribeDinnerTime: payload.deliverySubscribeDinnerTime
  });
  return response.data.data;
}

export async function deductWalletMeals(customerId: number, mealDelta: number, remark: string) {
  const response = await http.post<ApiResponse<{ remainingMeals: number }>>(`/api/admin/customers/${customerId}/wallet/deduct`, {
    mealDelta,
    remark
  });
  return response.data.data;
}

export async function fetchWalletTransactions(customerId: number) {
  const response = await http.get<ApiResponse<PageResponse<WalletTransactionResponse>>>(`/api/admin/customers/${customerId}/wallet-transactions`);
  return response.data.data;
}

export async function fetchRemarkSuggestions(scene: RemarkSuggestionScene, customerId?: number | null) {
  const query = new URLSearchParams({ scene });
  if (scene === "ORDER_REMARK" && customerId) {
    query.set("customerId", String(customerId));
  }
  const response = await http.get<ApiResponse<RemarkSuggestionResponse>>(`/api/admin/customers/remark-suggestions?${query.toString()}`);
  return response.data.data;
}

export async function fetchAnalysisOverview(date?: string) {
  const response = await http.get<ApiResponse<AnalysisOverviewResponse>>(`/api/admin/analysis/overview${date ? `?date=${date}` : ""}`);
  return response.data.data;
}

export async function fetchCostEntries(month?: string) {
  const response = await http.get<ApiResponse<CostEntryItem[]>>(`/api/admin/analysis/cost-entries${month ? `?month=${month}` : ""}`);
  return response.data.data;
}

export async function createCostEntry(payload: Record<string, unknown>) {
  const response = await http.post<ApiResponse<{ id: number; status: string }>>("/api/admin/analysis/cost-entries", payload);
  return response.data.data;
}

export async function createMenuSchedule(payload: {
  serveDate: string;
  mealPeriod: string;
  mealName: string;
  mealDetail: string;
  calories: number;
  merchantNote: string;
}) {
  const response = await http.post<ApiResponse<MenuScheduleResponse>>("/api/admin/menu-schedules", payload);
  return response.data.data;
}

export async function updateMenuSchedule(id: number, payload: {
  serveDate: string;
  mealPeriod: string;
  mealName: string;
  mealDetail: string;
  calories: number;
  merchantNote: string;
}) {
  const response = await http.put<ApiResponse<MenuScheduleResponse>>(`/api/admin/menu-schedules/${id}`, payload);
  return response.data.data;
}

export async function disableMenuSchedule(id: number) {
  const response = await http.post<ApiResponse<MenuScheduleResponse>>(`/api/admin/menu-schedules/${id}/disable`);
  return response.data.data;
}

export async function triggerDataCleanup() {
  const response = await http.post<ApiResponse<import('./types').MaintenanceCleanupTriggerResponse>>("/api/admin/maintenance/cleanup");
  return response.data.data;
}

export async function triggerMaintenanceModuleCleanup(moduleKey: string) {
  const response = await http.post<ApiResponse<import('./types').MaintenanceCleanupTriggerResponse>>(
    `/api/admin/maintenance/cleanup/${encodeURIComponent(moduleKey)}`
  );
  return response.data.data;
}

export async function updateMaintenanceCleanupSettings(payload: {
  rules: Array<{
    moduleKey: string;
    retentionValue: number;
    retentionUnit: string;
    autoEnabled: boolean;
  }>;
}) {
  const response = await http.post<ApiResponse<import('./types').MaintenanceOverviewResponse>>(
    "/api/admin/maintenance/settings",
    payload
  );
  return response.data.data;
}

export async function fetchMaintenanceOverview() {
  const response = await http.get<ApiResponse<import('./types').MaintenanceOverviewResponse>>("/api/admin/maintenance/overview");
  return response.data.data;
}

export async function fetchMaintenanceLogs() {
  const response = await http.get<ApiResponse<import('./types').MaintenanceLogItemResponse[]>>("/api/admin/maintenance/logs");
  return response.data.data;
}

// Subscription Rules
export async function fetchSubscriptionRules(keyword?: string, status?: string) {
  const params = new URLSearchParams();
  if (keyword) params.set('keyword', keyword);
  if (status) params.set('status', status);
  const suffix = params.toString() ? `?${params.toString()}` : '';
  const response = await http.get<ApiResponse<import('./types').SubscriptionRuleResponse[]>>(`/api/admin/subscription-rules${suffix}`);
  return response.data.data;
}

export async function createSubscriptionRule(payload: import('./types').SubscriptionRuleFormData) {
  const response = await http.post<ApiResponse<import('./types').SubscriptionRuleResponse>>("/api/admin/subscription-rules", payload);
  return response.data.data;
}

export async function updateSubscriptionRule(id: number, payload: import('./types').SubscriptionRuleFormData) {
  const response = await http.put<ApiResponse<import('./types').SubscriptionRuleResponse>>(`/api/admin/subscription-rules/${id}`, payload);
  return response.data.data;
}

export async function deleteSubscriptionRule(id: number) {
  const response = await http.delete<ApiResponse<{ id: number; status: string }>>(`/api/admin/subscription-rules/${id}`);
  return response.data.data;
}

export async function toggleSubscriptionRule(id: number) {
  const response = await http.post<ApiResponse<{ id: number; paused: boolean }>>(`/api/admin/subscription-rules/${id}/toggle`);
  return response.data.data;
}

// Low Balance Subscriptions
export async function fetchLowBalanceSubscriptions() {
  const response = await http.get<ApiResponse<import('./types').LowBalanceSubscriptionItem[]>>("/api/admin/dashboard/low-balance-subscriptions");
  return response.data.data;
}

// Subscription Preview Check
export async function checkSubscriptionPreview(serveDate: string) {
  const response = await http.post<ApiResponse<import('./types').SubscriptionPreviewCheckResponse>>("/api/admin/orders/subscription-preview-check", {
    serveDate
  });
  return response.data.data;
}

// --- SWR Fetcher ---
// 使用现有的 http 实例，这样 SWR 请求就能自动继承已有的 token 和 401/403 拦截逻辑
export const swrFetcher = (url: string) => http.get(url).then(res => res.data);
