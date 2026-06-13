export type ApiResponse<T> = {
  code: string;
  message: string;
  data: T;
};
export type PageResponse<T> = {
  items: T[];
  page: number;
  pageSize: number;
  total: number;
};
export type CustomerAssetResponse = {
  id: number;
  name: string;
  phone: string;
  customerStatus: string;
  totalMeals: number;
  remainingMeals: number;
  hasOpenedCard: boolean;
  fixedSubscriptionEnabled: boolean;
  priorityCustomer: boolean;
  priorityTag: string | null;
  merchantRemark: string | null;
  lastOrderAt: string | null;
  registeredAt: string | null;
  status: string;
};

export type CustomerAddressItem = {
  id: number;
  contactName: string;
  contactPhone: string;
  addressLine: string;
  areaCode?: string | null;
  isDefault: boolean;
};

export type CustomerAddressMutationPayload = {
  contactName: string;
  contactPhone: string;
  addressLine: string;
  areaCode?: string | null;
  isDefault: boolean;
};

export type CustomerAddressMutationResponse = {
  customerId: number;
  addressId: number;
  status: string;
};

export type CustomerDetailResponse = {
  name?: string;
  phone?: string;
  merchantRemark?: string | null;
  customerStatus?: string;
  remainingMeals?: number;
  addresses?: CustomerAddressItem[];
  [key: string]: unknown;
};

export type CustomerNoteItem = {
  id: number;
  noteType: string;
  scopeType: string;
  content: string;
  startAt?: string | null;
  endAt?: string | null;
  active?: boolean;
  displayOrder?: number;
};

export type RemarkSuggestionScene =
  | "CUSTOMER_REMARK"
  | "PRIORITY_NOTE"
  | "WALLET_REMARK"
  | "ORDER_REMARK"
  | "SUBSCRIPTION_NOTE"
  | "RECEIPT_NOTE"
  | "MENU_NOTE"
  | "COST_REMARK";

export type RemarkSuggestionResponse = {
  scene: RemarkSuggestionScene;
  items: string[];
};
export type MenuScheduleResponse = {
  id: number;
  serveDate: string;
  mealPeriod: string;
  mealName: string;
  mealDetail: string;
  calories: number;
  merchantNote: string;
  status: string;
};
export type AdminMenuWeekSlot = {
  mealPeriod: "LUNCH" | "DINNER";
  slotStatus: "ACTIVE" | "REST" | "UNCONFIGURED";
  dishItems: string[];
  totalCalories: number | null;
  merchantNote: string;
  imageUrl: string;
};
export type AdminMenuWeekDay = {
  serveDate: string;
  weekdayLabel: string;
  lunch: AdminMenuWeekSlot;
  dinner: AdminMenuWeekSlot;
};
export type AdminMenuWeekResponse = {
  weekId: number;
  weekStartDate: string;
  weekEndDate: string;
  status: "DRAFT" | "PUBLISHED" | "ARCHIVED";
  days: AdminMenuWeekDay[];
};
export type OrderPrepStatsResponse = {
  totalMeals: number;
  lunchCount: number;
  dinnerCount: number;
  selfOrderCount: number;
  staffOrderCount: number;
  subscriptionCount: number;
  specialOrderCount: number;
  adminRemarkCount: number;
  labelRequiredCount: number;
};

export type SubscriptionConfirmationItem = {
  id: number;
  customerName: string;
  customerPhone: string;
  mealPeriod: string;
  quantity: number;
  addressLine: string;
  userNote: string;
  merchantRemark: string;
  priority: boolean;
  status: string;
};
export type DashboardOverviewResponse = {
  deliveredToday: number;
  tomorrowMealCount: number;
  tomorrowLunchCount: number;
  tomorrowDinnerCount: number;
  newCardsToday: number;
  rechargeCustomersToday: number;
  aftersaleToday: number;
  cancellationsToday: number;
  totalOrdersToday: number;
  pendingOrdersToday: number;
  pendingDispatchToday: number;
  dispatchingOrdersToday: number;
  deliveredOrdersToday: number;
  lowBalanceCustomers: number;
  openAftersaleCount: number;
  specialOrdersToday: number;
  menuRiskDays: number;
  orderTrend: Array<{
    label: string;
    total: number;
    lunch: number;
    dinner: number;
  }>;
  growthTrend: Array<{
    label: string;
    newCards: number;
    recharges: number;
  }>;
};
export type SubscriptionPreviewItem = {
  customerId: number;
  customerName: string;
  customerPhone: string;
  mealPeriod: string;
  addressId: number;
  deliveryAddress: string;
  merchantRemark: string;
  remainingMeals: number;
  hasBalance: boolean;
  selected?: boolean; // Frontend state
};

export type SubscriptionImportItem = {
  customerId: number;
  mealPeriod: string;
  addressId: number;
  merchantRemark: string;
};

export type ManualCreateCustomerAddressResponse = {
  addressId: number;
  addressLine: string;
  areaCode: string;
  isDefault: boolean;
};

export type ManualCreateCustomerSearchResponse = {
  customerId: number;
  customerName: string;
  customerPhone: string;
  remainingMeals: number;
  addresses: ManualCreateCustomerAddressResponse[];
};

export type AdminAuthLoginResponse = {
  token: string;
  userId: number;
  displayName: string;
  phone: string;
  role: string;
};

export type AdminAuthProfileResponse = {
  userId: number;
  displayName: string;
  phone: string;
  role: string;
};

export type OrderPrepItemResponse = {
  id: number;
  customerName: string;
  customerPhone: string;
  mealSummary: string;
  quantity: number;
  userNote: string;
  merchantRemark: string;
  deliveryAddress: string;
  source: string;
  priorityCustomer: boolean;
  fixedSubscription: boolean;
  status: string;
  displayStatus?: string;
  displayStatusLabel?: string;
  canAssign: boolean;
  canCancel: boolean;
  canReceipt: boolean;
  walletStatusLabel: string;
};

export type AdminAftersaleItemResponse = {
  id: number;
  orderId: number;
  customerId: number;
  customerName: string;
  customerPhone: string;
  serveDate: string;
  mealPeriod: string;
  orderStatus: string;
  type: string;
  status: string;
  source: string;
  reasonCode: string;
  reasonText: string;
  refundBlocking: boolean;
  adminRemark: string;
  requestedAt: string;
  processedAt: string | null;
};

export type AdminAftersaleResolveResponse = {
  caseId: number;
  status: string;
};

export type SpecialOrderItem = {
  id: number;
  customerName: string;
  customerPhone: string;
  addressLine: string;
  mealPeriod: string;
  quantity: number;
  userNote: string;
  merchantRemark: string;
  priorityCustomer: boolean;
};

export type AnalysisOverviewResponse = {
  date: string;
  totalSales: number | string;
  totalCost: number | string;
  totalProfit: number | string;
  totalOrders: number;
  totalMeals: number;
  specialOrders: number;
  aftersaleCount: number;
};

export type CostEntryItem = {
  id: number;
  costDate: string;
  costCategory: string;
  amount: number | string;
  remark: string;
  recordedBy: string;
};
export type WalletTransactionResponse = {
  id: number;
  customerId: number;
  transactionType: string;
  mealDelta: number;
  operatorName: string;
  remark: string;
  relatedOrderId: number | null;
  relatedAftersaleId: number | null;
  relatedTransactionId: number | null;
  refunded: boolean;
  refundReasonCode: string;
  refundReasonText: string;
  createdAt: string;
};
export type MaintenanceLogItemResponse = {
  id: number;
  jobType: string;
  triggerSource: string;
  status: string;
  timeRangeLabel: string;
  startedAt: string | null;
  finishedAt: string | null;
  durationMs: number | null;
  scannedCount: number;
  deletedCount: number;
  failedCount: number;
  message: string;
  errorDetail: string | null;
};
export type MaintenanceOverviewResponse = {
  latestManual: MaintenanceLogItemResponse | null;
  latestAuto: MaintenanceLogItemResponse | null;
  latestCloudReceipt: MaintenanceLogItemResponse | null;
  latestCloudStorage: MaintenanceLogItemResponse | null;
};
export type MaintenanceCleanupTriggerResponse = {
  status: string;
  message: string;
};
export type BatchOperationResponse = {
  successCount: number;
  failureCount: number;
  failures: Array<{
    targetId: number;
    code: string;
    message: string;
  }>;
};
export type DispatchPendingItemResponse = {
  orderId: number;
  customerName: string;
  deliveryAddress: string;
};
export type DispatchAreaOrderItemResponse = {
  orderId: number;
  sequenceNumber: number;
  customerName: string;
  deliveryAddress: string;
  deliveryStatus: string;
  riderName: string | null;
  userNote: string;
  merchantRemark: string;
  referenceImageUrl: string;
  receiptUrl: string;
  receiptNote: string;
  deliveredAt: string | null;
  quantity: number;
};
export type DispatchBoardItemResponse = {
  dispatchId: number;
  orderId: number;
  customerName: string;
  deliveryAddress: string;
  riderName: string;
  areaCode: string;
  deliveryStatus: string;
  receiptStatus: string;
  receiptLabel: string;
  canNotifyCustomer: boolean;
};
export type DispatchAreaBlockingOrder = {
  orderId: number;
  customerName: string;
  deliveryAddress: string;
  deliveryStatus: string;
  serveDate: string;
};
export type DispatchAreaDeleteBlockedResponse = {
  areaCode: string;
  activeOrderCount: number;
  orders: DispatchAreaBlockingOrder[];
};
export type DispatchBatchResponse = {
  batchId: number;
  serveDate: string;
  mealPeriod: string;
  riderProfileId: number;
  riderName: string;
  areaCode: string;
  batchStatus: string;
  totalCount: number;
  deliveredCount: number;
  currentSequence: number;
  currentCustomerName: string | null;
  nextCustomerName: string | null;
};
export type DispatchExceptionItemResponse = {
  mealSlotOrderId: number;
  exceptionType: string;
  reason: string;
  customerName: string;
  customerPhone: string;
  deliveryAddress: string;
  suggestedAreaCode: string | null;
  suggestedRiderName: string | null;
  rememberedAddress: boolean;
};
export type DispatchOverviewResponse = {
  pendingCount: number;
  dispatchingCount: number;
  missingRiderAreaCount: number;
};
export type DispatchRiderProgressResponse = {
  riderName: string;
  areaCode: string;
  completedCount: number;
  totalCount: number;
  currentOrderId: number | null;
  currentSequenceNumber: number | null;
  nextOrderId: number | null;
  pendingCount: number;
  exceptionCount: number;
};
export type PendingRiderResponse = {
  riderId: number;
  displayName: string;
  phone: string;
  currentOpenid: string;
  authStatus: string;
  firstLoginAt: string | null;
  lastLoginAt: string | null;
};
export type DispatchManagedRiderResponse = {
  riderId: number;
  riderName: string;
  displayName: string;
  phone: string;
  authStatus: string;
  employmentStatus: string;
  areaCode: string | null;
  assignedBy: string | null;
  firstLoginAt: string | null;
  lastLoginAt: string | null;
  todayTaskCount: number;
  todayDeliveredCount: number;
  currentOpenid: string | null;
};
export type DispatchCreateRiderPayload = {
  riderName: string;
  displayName: string;
  phone: string;
  password?: string;
  areaCode?: string;
  employmentStatus?: string;
  updatedBy?: string;
};
export type DispatchCreateRiderResponse = {
  riderId: number;
  riderName: string;
  displayName: string;
  phone: string;
  areaCode: string | null;
  riderStatus: string;
};
export type DispatchRiderAuthBindingResponse = {
  riderId: number;
  riderName: string;
  displayName: string;
  phone: string;
  currentOpenid: string | null;
  authStatus: string;
  lastLoginAt: string | null;
};
export type DispatchAreaBindingResponse = {
  areaCode: string;
  keywords: string | null;
  defaultRiderId: number | null;
  defaultRiderName: string | null;
  currentRiderName: string | null;
  orderCount: number;
  missingRider: boolean;
  orders: DispatchAreaOrderItemResponse[];
  updatedBy: string;
  updatedAt: string;
};
export type DispatchReassignmentResponse = {
  reassignmentId: number;
  reassignLevel: string;
  targetId: number;
  fromRiderName: string | null;
  toRiderName: string;
  toAreaCode: string | null;
  serveDate: string;
  mealPeriod: string | null;
  syncDefaultBinding: boolean;
  reason: string | null;
  createdBy: string;
  createdAt: string | null;
};
export type OperationSettingsResponse = {
  orderingEnabled: boolean;
  orderingStatusLabel: string;
  holidayNoticeTitle: string;
  holidayNoticeDesc: string;
  emergencyActionLabel: string;
  bannerImages: string;
  bannerIntervalSeconds: number;
  popupAnnouncementEnabled: boolean;
  popupAnnouncementContent: string;
};

export type BannerImageUploadResponse = {
  url: string;
  fileKey: string;
  size: number;
};

export type SubscriptionRuleResponse = {
  id: number;
  customerId: number;
  customerName: string;
  customerPhone: string;
  startDate: string;
  endDate: string;
  lunchEnabled: boolean;
  lunchQuantity: number;
  dinnerEnabled: boolean;
  dinnerQuantity: number;
  defaultAddressId: number | null;
  defaultAddress: string;
  merchantRemark: string;
  isPriorityFollow: boolean;
  paused: boolean;
  active: boolean;
  status: string;
  remainingMeals: number;
  createdAt: string;
  updatedAt: string;
};

export type SubscriptionRuleFormData = {
  customerId: number;
  startDate: string;
  endDate: string;
  lunchEnabled: boolean;
  lunchQuantity: number;
  dinnerEnabled: boolean;
  dinnerQuantity: number;
  defaultAddressId: number | null;
  merchantRemark: string;
  isPriorityFollow: boolean;
};

export type LowBalanceSubscriptionItem = {
  customerId: number;
  customerName: string;
  customerPhone: string;
  remainingMeals: number;
  lunchEnabled: boolean;
  dinnerEnabled: boolean;
  nextServeDate: string;
  subscriptionRuleId: number;
};

export type SubscriptionPreviewCheckResponse = {
  totalCount: number;
  sufficientCount: number;
  insufficientCount: number;
  insufficientCustomers: Array<{
    customerId: number;
    customerName: string;
    customerPhone: string;
    remainingMeals: number;
    requiredMeals: number;
    mealPeriod: string;
  }>;
};
