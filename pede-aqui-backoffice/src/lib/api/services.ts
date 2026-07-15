import { apiClient, getAuthToken, getTenantId } from "./client";
import type {
  AdminDashboard,
  VendorDashboard,
  FinanceDashboard,
  CourierDashboard,
  Order,
  Tenant,
  Vertical,
  Vendor,
  VendorDocument,
  VendorOpeningHour,
  VendorOpeningHourRequest,
  Category,
  Product,
  Sku,
  CreateProductPayload,
  CreateSkuPayload,
  ProductVariationGroup,
  ProductVariationOption,
  Coupon,
  Promotion,
  CreateCouponPayload,
  CreatePromotionPayload,
  Courier,
  CourierDocument,
  UserProfile,
  UploadUrlResponse,
  Transaction,
  Commission,
  Refund,
  CashReconciliation,
  FinanceSummary,
  SupportTicket,
  Notification,
  MerchantRegistrationPayload,
  MerchantRegistrationResponse,
  PlatformStats,
  ProductEditResponse,
  UpdateProductRequest,
  PriceUpdateResponse,
  PendingPriceChange,
  SalesPage,
  SaleDetail,
  SalesActionResponse,
  RefundResponse,
  SalesFilter,
  SalesNotificationType,
  PromotionResponse,
  PromotionUpsertRequest,
  PromotionStatus,
  SalesSummary,
  SalesBucket,
  DimensionRow,
  ProductDimensionRow,
  ReportInterval,
  SalesReportName,
  ReportRangeParams,
} from "./types";

/** Serialises a flat params object into a query string, omitting undefined/null/empty values. */
function toQueryString(params: Record<string, string | number | undefined | null>): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") {
      search.append(key, String(value));
    }
  });
  const qs = search.toString();
  return qs ? `?${qs}` : "";
}

// Platform super-admin (no tenant context)
export const platformService = {
  getStats: () => apiClient.get<PlatformStats>("/platform/stats"),
};

// Tenant management (platform admin only)
export const tenantService = {
  list: () => apiClient.get<Tenant[]>("/tenants"),
  create: (data: { name: string; slug: string; defaultCurrency: string }) =>
    apiClient.post<Tenant>("/tenants", data),
  updateStatus: (id: string, status: string) =>
    apiClient.patch<Tenant>(`/tenants/${id}/status`, { status }),
};

// Auth
export const authService = {
  getMe: () => apiClient.get<{ id: string; tenantId: string | null; displayName: string; email: string; roles: string[] }>("/me"),
};

// Dashboards
export const dashboardService = {
  getAdmin: () => apiClient.get<AdminDashboard>("/dashboards/admin"),
  getVendor: () => apiClient.get<VendorDashboard>("/dashboards/vendor"),
  getFinance: () => apiClient.get<FinanceDashboard>("/dashboards/finance"),
  getCourier: () => apiClient.get<CourierDashboard>("/dashboards/courier"),
};

// Orders
export const orderService = {
  list: () => apiClient.get<Order[]>("/orders"),
  getById: (id: string) => apiClient.get<Order>(`/orders/${id}`),
  getTracking: (id: string) => apiClient.get<{ orderStatus: string; deliveryStatus: string }>(`/orders/${id}/tracking`),
  accept: (id: string) => apiClient.patch<Order>(`/vendor/orders/${id}/accept`),
  reject: (id: string, reason: string) => apiClient.patch<Order>(`/vendor/orders/${id}/reject`, { reason }),
  markPreparing: (id: string) => apiClient.patch<Order>(`/vendor/orders/${id}/preparing`),
  markReadyForPickup: (id: string) => apiClient.patch<Order>(`/vendor/orders/${id}/ready-for-pickup`),
};

// Marketing
export const marketingService = {
  listCoupons: () => apiClient.get<Coupon[]>("/marketing/coupons"),
  createCoupon: (data: CreateCouponPayload) => apiClient.post<Coupon>("/marketing/coupons", data),
  deactivateCoupon: (id: string) => apiClient.patch<Coupon>(`/marketing/coupons/${id}/deactivate`),
  listPromotions: () => apiClient.get<Promotion[]>("/marketing/promotions"),
  createPromotion: (data: CreatePromotionPayload) => apiClient.post<Promotion>("/marketing/promotions", data),
  deactivatePromotion: (id: string) => apiClient.patch<Promotion>(`/marketing/promotions/${id}/deactivate`),
};

// Catalog
export const catalogService = {
  listVendorProducts: (vendorId: string) => apiClient.get<Product[]>(`/catalog/vendors/${vendorId}/products`),
  createProduct: (data: CreateProductPayload) => apiClient.post<Product>("/catalog/products", data),
  createSku: (data: CreateSkuPayload) => apiClient.post<Sku>("/catalog/skus", data),
  approveProduct: (productId: string) => apiClient.post<Product>(`/catalog/products/${productId}/approve`),
  rejectProduct: (productId: string) => apiClient.post<Product>(`/catalog/products/${productId}/reject`),

  // Spec 002 — product attribute/price/image edit (VENDOR_ADMIN own vendor; OPS/ADMIN tenant-wide).
  updateProduct: (productId: string, data: UpdateProductRequest) =>
    apiClient.patch<ProductEditResponse>(`/catalog/products/${productId}`, data),
  updateProductPrice: (productId: string, price: number) =>
    apiClient.patch<PriceUpdateResponse>(`/catalog/products/${productId}/price`, { price }),
  setProductImage: (productId: string, storageKey: string) =>
    apiClient.put<ProductEditResponse>(`/catalog/products/${productId}/image`, { storageKey }),
  deleteProductImage: (productId: string) => apiClient.delete<void>(`/catalog/products/${productId}/image`),

  // Spec 002 — OPS/ADMIN price-change moderation queue.
  listPendingPriceChanges: () => apiClient.get<PendingPriceChange[]>("/catalog/moderation/price-changes"),
  approvePriceChange: (skuId: string) => apiClient.post<void>(`/catalog/moderation/price-changes/${skuId}/approve`),
  rejectPriceChange: (skuId: string, reason: string) =>
    apiClient.post<void>(`/catalog/moderation/price-changes/${skuId}/reject`, { reason }),
};

// Spec 002 — Sales (commercial lens over orders). VENDOR_ADMIN is forced to own vendor server-side.
export const salesService = {
  list: (filter: SalesFilter = {}) =>
    apiClient.get<SalesPage>(
      `/sales/orders${toQueryString({
        from: filter.from,
        to: filter.to,
        status: filter.status,
        vendorId: filter.vendorId,
        productId: filter.productId,
        skuId: filter.skuId,
        paymentProvider: filter.paymentProvider,
        q: filter.q,
        page: filter.page,
        size: filter.size,
      })}`
    ),
  detail: (orderId: string) => apiClient.get<SaleDetail>(`/sales/orders/${orderId}`),
  cancel: (orderId: string, reason: string) =>
    apiClient.post<SalesActionResponse>(`/sales/orders/${orderId}/cancel`, { reason }),
  // Idempotent; pass the same idempotencyKey to retry safely without double-refunding.
  refund: (orderId: string, data: { amount?: number; reason: string }, idempotencyKey?: string) =>
    apiClient.post<RefundResponse>(
      `/sales/orders/${orderId}/refund`,
      data,
      idempotencyKey ? { "Idempotency-Key": idempotencyKey } : undefined
    ),
  resendNotification: (orderId: string, type: SalesNotificationType) =>
    apiClient.post<void>(`/sales/orders/${orderId}/resend-notification`, { type }),
  // Config-gated (app.sales.status-override.enabled, default false); ADMIN/OPS only; allow-list enforced server-side.
  statusOverride: (orderId: string, targetStatus: string, reason: string, idempotencyKey?: string) =>
    apiClient.post<SalesActionResponse>(
      `/sales/orders/${orderId}/status-override`,
      { targetStatus, reason },
      idempotencyKey ? { "Idempotency-Key": idempotencyKey } : undefined
    ),
};

// Spec 002 — Promotions (distinct from the legacy `marketingService` coupon/promotion CRUD above).
export const promotionService = {
  list: (params: { status?: PromotionStatus; vendorId?: string } = {}) =>
    apiClient.get<PromotionResponse[]>(`/marketing/promotions${toQueryString({ ...params })}`),
  create: (data: PromotionUpsertRequest) => apiClient.post<PromotionResponse>("/marketing/promotions", data),
  update: (promotionId: string, data: PromotionUpsertRequest) =>
    apiClient.patch<PromotionResponse>(`/marketing/promotions/${promotionId}`, data),
  delete: (promotionId: string) => apiClient.delete<void>(`/marketing/promotions/${promotionId}`),
  activate: (promotionId: string) => apiClient.post<PromotionResponse>(`/marketing/promotions/${promotionId}/activate`),
  pause: (promotionId: string) => apiClient.post<PromotionResponse>(`/marketing/promotions/${promotionId}/pause`),
};

// Spec 002 — Sales reports (FINANCE/ADMIN/OPS; VENDOR_ADMIN scoped to own vendor server-side).
export const reportService = {
  summary: (params: ReportRangeParams) =>
    apiClient.get<SalesSummary>(`/reports/sales/summary${toQueryString({ ...params })}`),
  timeseries: (params: ReportRangeParams & { interval?: ReportInterval }) =>
    apiClient.get<SalesBucket[]>(`/reports/sales/timeseries${toQueryString({ ...params })}`),
  byVendor: (params: ReportRangeParams) =>
    apiClient.get<DimensionRow[]>(`/reports/sales/by-vendor${toQueryString({ ...params })}`),
  byProduct: (params: ReportRangeParams) =>
    apiClient.get<ProductDimensionRow[]>(`/reports/sales/by-product${toQueryString({ ...params })}`),
  byCategory: (params: ReportRangeParams) =>
    apiClient.get<DimensionRow[]>(`/reports/sales/by-category${toQueryString({ ...params })}`),
  // Builds the export query string (report/format/date-range/vendor/interval); callers combine
  // it with `downloadExport` rather than linking directly, since the endpoint requires the
  // Bearer/X-Tenant-Id headers a plain <a href> cannot attach.
  buildExportQuery: (report: SalesReportName, params: ReportRangeParams & { interval?: ReportInterval }) =>
    toQueryString({ report, format: "csv", ...params }),
  // Streams the CSV export as a Blob using the same auth/tenant headers as the JSON client;
  // callers turn the Blob into an object URL to trigger a browser download.
  downloadExport: async (
    report: SalesReportName,
    params: ReportRangeParams & { interval?: ReportInterval }
  ): Promise<Blob> => {
    const apiBase = process.env.NEXT_PUBLIC_API_BASE_URL;
    if (!apiBase) {
      throw new Error("API base URL not configured");
    }
    const query = toQueryString({ report, format: "csv", ...params });
    const response = await fetch(`${apiBase}/reports/sales/export${query}`, {
      method: "GET",
      headers: {
        ...(getAuthToken() ? { Authorization: `Bearer ${getAuthToken()}` } : {}),
        ...(getTenantId() ? { "X-Tenant-Id": getTenantId()! } : {}),
      },
    });
    if (!response.ok) {
      throw new Error(`Exportação falhou: ${response.status} ${response.statusText}`);
    }
    return response.blob();
  },
};

// Verticals
export const verticalService = {
  list: () => apiClient.get<Vertical[]>("/catalog/verticals"),
  create: (data: { label: string }) => apiClient.post<Vertical>("/catalog/verticals", data),
  update: (id: string, data: { label: string; active: boolean }) =>
    apiClient.put<Vertical>(`/catalog/verticals/${id}`, data),
  delete: (id: string) => apiClient.delete(`/catalog/verticals/${id}`),
};

// Categories
export const categoryService = {
  list: async (): Promise<Category[]> => {
    return await apiClient.get<Category[]>("/catalog/categories");
  },
  listHierarchical: async (): Promise<Category[]> => {
    return await apiClient.get<Category[]>("/catalog/categories/hierarchical");
  },
  getById: (id: string) => apiClient.get<Category>(`/catalog/categories/${id}`),
  create: (data: {
    name: string;
    vertical: string;
    parentId?: string;
  }) => apiClient.post<Category>("/catalog/categories", data),
  update: (id: string, data: {
    name: string;
    vertical: string;
    parentId?: string;
    active: boolean;
  }) => apiClient.put<Category>(`/catalog/categories/${id}`, data),
  delete: (id: string) => apiClient.delete(`/catalog/categories/${id}`),
};

// Product Families (Variation Groups)
export const productFamilyService = {
  listForProduct: (productId: string) => apiClient.get<ProductVariationGroup[]>(`/catalog/products/${productId}/variation-groups`),
  getById: (groupId: string) => apiClient.get<ProductVariationGroup>(`/catalog/product-variation-groups/${groupId}`),
  create: (data: {
    productId: string;
    name: string;
    description?: string;
    required: boolean;
    minSelections: number;
    maxSelections: number;
    displayOrder: number;
  }) => apiClient.post<ProductVariationGroup>("/catalog/product-variation-groups", data),
  update: (groupId: string, data: {
    name: string;
    description?: string;
    required: boolean;
    minSelections: number;
    maxSelections: number;
    displayOrder: number;
  }) => apiClient.put<ProductVariationGroup>(`/catalog/product-variation-groups/${groupId}`, data),
  delete: (groupId: string) => apiClient.delete(`/catalog/product-variation-groups/${groupId}`),
};

// Product Variation Options
export const productVariationOptionService = {
  listForGroup: (groupId: string) => apiClient.get<ProductVariationOption[]>(`/catalog/product-variation-groups/${groupId}/options`),
  getById: (optionId: string) => apiClient.get<ProductVariationOption>(`/catalog/product-variation-options/${optionId}`),
  create: (data: {
    groupId: string;
    name: string;
    description?: string;
    priceModifier?: number;
    available: boolean;
    displayOrder: number;
  }) => apiClient.post<ProductVariationOption>("/catalog/product-variation-options", data),
  update: (optionId: string, data: {
    name: string;
    description?: string;
    priceModifier?: number;
    available: boolean;
    displayOrder: number;
  }) => apiClient.put<ProductVariationOption>(`/catalog/product-variation-options/${optionId}`, data),
  delete: (optionId: string) => apiClient.delete(`/catalog/product-variation-options/${optionId}`),
};

// Vendors
export const vendorService = {
  list: (available?: boolean) => apiClient.get<Vendor[]>(`/vendors${available !== undefined ? `?available=${available}` : ""}`),
  getById: (id: string) => apiClient.get<Vendor>(`/vendors/${id}`),
  create: (data: {
    name: string;
    categoryId: string;
    latitude?: number;
    longitude?: number;
    ownerName?: string;
    nif?: string;
    phone: string;
    address?: string;
    description?: string;
    logoStorageKey?: string;
  }) => apiClient.post<Vendor>("/vendors", data),
  update: (id: string, data: {
    name: string;
    categoryId: string;
    latitude?: number;
    longitude?: number;
    ownerName?: string;
    nif?: string;
    phone?: string;
    address?: string;
    description?: string;
    logoStorageKey?: string;
  }) => apiClient.patch<Vendor>(`/vendors/${id}/profile`, data),
  getDocuments: (id: string) => apiClient.get<VendorDocument[]>(`/vendors/${id}/documents`),
  uploadDocument: (id: string, data: { documentType: string; storageKey: string }) =>
    apiClient.post<VendorDocument>(`/vendors/${id}/documents`, data),
  getOpeningHours: (id: string) => apiClient.get<VendorOpeningHour[]>(`/vendors/${id}/opening-hours`),
  updateOpeningHours: (id: string, hours: VendorOpeningHourRequest[]) =>
    apiClient.put<VendorOpeningHour[]>(`/vendors/${id}/opening-hours`, { hours }),
};

// Couriers
export const courierService = {
  list: () => apiClient.get<Courier[]>("/couriers"),
  getById: (id: string) => apiClient.get<Courier>(`/couriers/${id}`),
  getMe: () => apiClient.get<Courier>("/couriers/me"),
  create: (data: {
    userProfileId: string;
    operatingZoneId?: string;
    fullName?: string;
    phone?: string;
    nif?: string;
    vehicleType?: string;
    vehiclePlate?: string;
    dateOfBirth?: string;
  }) => apiClient.post<Courier>("/couriers", data),
  getDocuments: (id: string) => apiClient.get<CourierDocument[]>(`/couriers/${id}/documents`),
  uploadDocument: (id: string, data: { documentType: string; storageKey: string }) =>
    apiClient.post<CourierDocument>(`/couriers/${id}/documents`, data),
  approve: (id: string) => apiClient.patch<Courier>(`/couriers/${id}/approve`),
  reject: (id: string) => apiClient.patch<Courier>(`/couriers/${id}/reject`),
};

// Finance
export const financeService = {
  getTransactions: () => apiClient.get<Transaction[]>("/finance/transactions"),
  getCommissions: () => apiClient.get<Commission[]>("/finance/commissions"),
  getRefunds: () => apiClient.get<Refund[]>("/finance/refunds"),
  approveRefund: (id: string) => apiClient.patch<Refund>(`/finance/refunds/${id}/approve`),
  rejectRefund: (id: string) => apiClient.patch<Refund>(`/finance/refunds/${id}/reject`),
  getCashReconciliation: () => apiClient.get<CashReconciliation[]>("/finance/cash-reconciliation"),
  getSummary: () => apiClient.get<FinanceSummary>("/finance/summary"),
  getPayoutStatus: () => apiClient.get<{ pending: number; settled: number }>("/finance/payout-status"),
};

// Support Tickets
export const supportService = {
  list: () => apiClient.get<SupportTicket[]>("/support/tickets"),
  getMine: () => apiClient.get<SupportTicket[]>("/support/tickets/mine"),
  create: (data: { subject: string; description: string; orderId?: string }) =>
    apiClient.post<SupportTicket>("/support/tickets", data),
  classify: (id: string, classification: string) =>
    apiClient.patch<SupportTicket>(`/support/tickets/${id}/classify`, { classification }),
  updateStatus: (id: string, status: string) =>
    apiClient.patch<SupportTicket>(`/support/tickets/${id}/status`, { status }),
  addInternalNote: (id: string, note: string) =>
    apiClient.patch<SupportTicket>(`/support/tickets/${id}/internal-note`, { internalNote: note }),
  resolve: (id: string) => apiClient.patch<SupportTicket>(`/support/tickets/${id}/resolve`),
};

// Notifications
export const notificationService = {
  list: () => apiClient.get<Notification[]>("/notifications"),
  markRead: (id: string) => apiClient.patch<Notification>(`/notifications/${id}/read`),
};

type CrudRecord = {
  id: string;
  nome: string;
  estado: string;
  detalhe: string;
  actualizadoEm: string;
};

function createCrudService(basePath: string) {
  return {
    list: () => apiClient.get<CrudRecord[]>(basePath),
    create: (data: Omit<CrudRecord, "id" | "actualizadoEm">) => apiClient.post<CrudRecord>(basePath, data),
    update: (id: string, data: Omit<CrudRecord, "id" | "actualizadoEm">) => apiClient.put<CrudRecord>(`${basePath}/${id}`, data),
  };
}

// Users
export const userService = {
  list: async (): Promise<UserProfile[]> => {
    try {
      return await apiClient.get<UserProfile[]>("/admin/users");
    } catch (error) {
      // If backend returns 404, return empty array instead of rejecting
      if (error && typeof error === 'object' && 'response' in error && 
          error.response && typeof error.response === 'object' && 'status' in error.response && 
          error.response.status === 404) {
        return [];
      }
      throw error;
    }
  },
  getById: (id: string) => apiClient.get<UserProfile>(`/admin/users/${id}`),
  create: (data: {
    keycloakUserId: string;
    email: string;
    displayName: string;
    fullName?: string;
    phone?: string;
    nif?: string;
    dateOfBirth?: string;
    address?: string;
    roles: string[];
  }) => apiClient.post<UserProfile>("/admin/users", data),
};

// Upload
export const uploadService = {
  getPresignedUrl: (data: {
    purpose: string;
    fileName: string;
    contentType: string;
  }) => apiClient.post<UploadUrlResponse>("/uploads/images/presigned-url", data),
  getDocumentPresignedUrl: (data: {
    purpose: string;
    fileName: string;
    contentType: string;
  }) => apiClient.post<UploadUrlResponse>("/uploads/documents/presigned-url", data),
  uploadToS3: async (uploadUrl: string, file: File): Promise<void> => {
    const response = await fetch(uploadUrl, {
      method: "PUT",
      body: file,
      headers: {
        "Content-Type": file.type,
      },
    });
    if (!response.ok) {
      throw new Error(`Envio falhou: ${response.statusText}`);
    }
  },
};

export const managementService = {
  orders: createCrudService("/orders/backoffice"),
  couriers: createCrudService("/couriers/backoffice"),
  finance: createCrudService("/finance/records"),
  marketing: createCrudService("/marketing/campaigns"),
  support: createCrudService("/support/tickets/backoffice"),
};

export const registrationService = {
  register: async (payload: MerchantRegistrationPayload): Promise<MerchantRegistrationResponse> => {
    const apiBase = process.env.NEXT_PUBLIC_API_BASE_URL;
    if (!apiBase) {
      throw new Error("API base URL not configured");
    }

    const response = await fetch(`${apiBase}/register`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(payload),
    });

    const body = await response.json();

    if (!response.ok) {
      throw body; // Throw the parsed body object directly
    }

    return body as MerchantRegistrationResponse;
  },
};
