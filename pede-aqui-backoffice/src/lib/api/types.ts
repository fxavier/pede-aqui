// Dashboard types
export interface AdminDashboard {
  ordersByStatus: Record<string, number>;
  activeVendors: number;
  activeCouriers: number;
  cancellations: number;
  failedDeliveries: number;
  totalOrders: number;
  totalRevenue: number;
}

export interface VendorDashboard {
  ordersByStatusCount: number;
  rejectedOrdersCount: number;
  salesTotal: number;
}

export interface FinanceDashboard {
  transactions: number;
  commissions: number;
  refunds: number;
  cashReconciliation: number;
}

export interface CourierDashboard {
  completedDeliveries: number;
  failedDeliveries: number;
  activeAssignments: number;
  earningsTotal: number;
}

// Order types
export interface Order {
  id: string;
  reference: string;
  status: string;
  total: number;
  deliveryCode: string | null;
  customerName: string | null;
  vendorName: string | null;
  createdAt: string | null;
  items: OrderItem[] | null;
}

export interface OrderItem {
  id: string;
  productName: string;
  skuName: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
}

// Tenant types
export interface Tenant {
  id: string;
  name: string;
  slug: string;
  status: string;
  defaultCurrency: string;
  createdAt: string;
  updatedAt: string;
}

// Platform super-admin types
export interface PlatformStats {
  totalTenants: number;
  activeTenants: number;
  inactiveTenants: number;
  totalUsers: number;
  totalCouriers: number;
  totalVendors: number;
}

// Vertical types
export interface Vertical {
  id: string;
  slug: string;
  label: string;
  active: boolean;
}

// Category types
export interface Category {
  id: string;
  name: string;
  vertical: string;
  active: boolean;
  parentId?: string;
  children?: Category[];
}

// Vendor types
export interface Vendor {
  id: string;
  name: string;
  categoryId: string;
  status: string;
  verificationStatus: string;
  rating: number;
  estimatedDeliveryMinutes: number;
  available: boolean;
  latitude?: number;
  longitude?: number;
  ownerName?: string;
  nif?: string;
  phone?: string;
  address?: string;
  description?: string;
  logoStorageKey?: string;
}

export interface VendorDocument {
  id: string;
  vendorId: string;
  documentType: string;
  storageKey: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}

// Courier types
export interface Courier {
  id: string;
  userProfileId: string;
  verificationStatus: string;
  available: boolean;
  operatingZoneId?: string;
  fullName?: string;
  phone?: string;
  nif?: string;
  vehicleType?: string;
  vehiclePlate?: string;
  rating: number;
}

export interface CourierDocument {
  id: string;
  courierId: string;
  documentType: string;
  storageKey: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}

// Finance types
export interface Transaction {
  id: string;
  orderId: string;
  amount: number;
  status: string;
}

export interface Commission {
  id: string;
  orderId: string;
  vendorId: string;
  commissionAmount: number;
  status: string;
  createdAt: string;
}

export interface Refund {
  id: string;
  paymentId: string;
  orderId: string;
  amount: number;
  reason: string;
  status: string;
}

export interface CashReconciliation {
  id: string;
  orderId: string;
  deliveryId: string;
  courierId: string;
  amount: number;
  status: string;
  recordedAt: string;
}

export interface FinanceSummary {
  confirmedPaymentsTotal: number;
  commissionTotal: number;
  refundsTotal: number;
  unreconciledCashTotal: number;
}

// Support ticket types
export interface SupportTicket {
  id: string;
  orderId: string | null;
  subject: string;
  description: string;
  status: string;
  classification: string | null;
  internalNote: string | null;
  assigneeUserId: string | null;
  createdAt: string;
}

// Notification types
export interface Notification {
  id: string;
  title: string;
  message: string;
  type: string;
  readAt?: string;
  createdAt: string;
}

// Pagination
export interface PaginatedResponse<T> {
  data: T[];
  total: number;
  page: number;
  pageSize: number;
}

// User types
export interface UserProfile {
  id: string;
  keycloakUserId: string;
  email: string;
  displayName: string;
  fullName?: string;
  phone?: string;
  nif?: string;
  dateOfBirth?: string;
  address?: string;
  avatarStorageKey?: string;
  roles: string[];
  status: string;
  createdAt: string;
  updatedAt: string;
}

// Marketing types
export interface Coupon {
  id: string;
  code: string;
  discountType: string;
  discountValue: number;
  minOrderAmount?: number;
  maxUses?: number;
  usesCount: number;
  vendorId?: string;
  validFrom: string;
  validUntil?: string;
  active: boolean;
  createdAt: string;
}

export interface Promotion {
  id: string;
  name: string;
  description?: string;
  discountType: string;
  discountValue: number;
  vendorId?: string;
  appliesTo: string;
  startsAt: string;
  endsAt?: string;
  active: boolean;
  createdAt: string;
}

export interface CreateCouponPayload {
  code: string;
  discountType: string;
  discountValue: number;
  minOrderAmount?: number;
  maxUses?: number;
  vendorId?: string;
  validFrom: string;
  validUntil?: string;
}

export interface CreatePromotionPayload {
  name: string;
  description?: string;
  discountType: string;
  discountValue: number;
  vendorId?: string;
  appliesTo: string;
  startsAt: string;
  endsAt?: string;
}

// Catalog types
export interface Sku {
  id: string;
  skuCode: string;
  name: string;
  price: number;
  active: boolean;
}

export interface Product {
  id: string;
  vendorId: string;
  categoryId: string;
  name: string;
  description?: string;
  requiresPrescriptionMetadata: boolean;
  prohibitedFuel: boolean;
  skus: Sku[];
  status?: string;
  attributes?: Record<string, any>;
  primaryImageKey?: string;
  imageGallery?: string[];
}

export interface CreateProductPayload {
  vendorId: string;
  categoryId: string;
  name: string;
  description?: string;
  requiresPrescriptionMetadata?: boolean;
  prohibitedFuel?: boolean;
  primaryImageKey?: string;
}

export interface CreateSkuPayload {
  productId: string;
  vendorId: string;
  skuCode: string;
  name: string;
  price: number;
  initialStock: number;
}

// Product Variation Groups (Families)
export interface ProductVariationGroup {
  id: string;
  productId: string;
  name: string;
  description?: string;
  required: boolean;
  minSelections: number;
  maxSelections: number;
  displayOrder: number;
  options?: ProductVariationOption[];
}

export interface ProductVariationOption {
  id: string;
  groupId: string;
  name: string;
  description?: string;
  priceModifier: number;
  available: boolean;
  displayOrder: number;
}

// Vendor opening hours
export interface VendorOpeningHour {
  id: string;
  vendorId: string;
  dayOfWeek: number; // 1=Mon … 7=Sun
  opensAt: string | null;  // "HH:mm:ss"
  closesAt: string | null;
  closed: boolean;
}

export interface VendorOpeningHourRequest {
  dayOfWeek: number;
  opensAt: string | null;
  closesAt: string | null;
  closed: boolean;
}

// ---------------------------------------------------------------------------
// Spec 002 — Catalog edit & price moderation
// (Backend DTO source of truth: backend/src/main/java/com/delivery/catalog/dto/*.
//  The OpenAPI fragment names this "ProductResponse"; the backend returns the narrower
//  single-SKU edit shape `ProductEditResponse`, distinct from the existing `Product` type
//  used by the create/list catalog flows above.)
export interface ProductEditResponse {
  id: string;
  vendorId: string;
  categoryId: string;
  name: string;
  description: string | null;
  status: string;
  requiresPrescription: boolean;
  imageUrl: string | null;
  price: number;
  pendingPrice: number | null;
  updatedAt: string;
}

export interface UpdateProductRequest {
  name?: string;
  description?: string;
  categoryId?: string;
  requiresPrescription?: boolean;
}

export interface PriceUpdateResponse {
  skuId: string;
  currentPrice: number;
  pendingPrice: number | null;
  reviewRequired: boolean;
}

export interface PendingPriceChange {
  skuId: string;
  productId: string;
  productName: string;
  vendorId: string;
  currentPrice: number;
  pendingPrice: number;
  deltaPercent: number;
  submittedBy: string;
  submittedAt: string;
}

// ---------------------------------------------------------------------------
// Spec 002 — Sales
// (Backend DTO source of truth: backend/src/main/java/com/delivery/sales/dto/*.
//  Diverges from the OpenAPI fragment: SalesRow/SaleDetail additionally carry
//  `customerName` (masked for SUPPORT); SaleDetail carries `appliedPromotionId` (UUID) instead
//  of an embedded `appliedPromotion` object, plus a `payments[]` array not in the fragment.)
export interface SalesRow {
  orderId: string;
  reference: string;
  createdAt: string;
  vendorId: string;
  vendorName: string;
  customerName: string | null;
  itemCount: number;
  subtotal: number;
  fees: number;
  taxes: number;
  discountTotal: number;
  total: number;
  orderStatus: string;
  paymentStatus: string;
  paymentProvider: string;
}

export interface SalesPage {
  content: SalesRow[];
  page: number;
  size: number;
  totalElements: number;
}

export interface SaleLineItem {
  productNameSnapshot: string;
  unitPriceSnapshot: number;
  quantity: number;
  lineTotal: number;
}

export interface SalePayment {
  id: string;
  amount: number;
  provider: string;
  status: string;
}

export interface SaleRefund {
  id: string;
  amount: number;
  status: string;
}

export interface SaleDetail extends SalesRow {
  items: SaleLineItem[];
  appliedPromotionId: string | null;
  payments: SalePayment[];
  refunds: SaleRefund[];
  commission: number;
}

// Minimal acknowledgement returned by cancel/status-override (never carries the delivery OTP).
export interface SalesActionResponse {
  orderId: string;
  reference: string;
  orderStatus: string;
}

// Refund response diverges from the fragment: the backend delegates to the existing finance
// path and returns `com.delivery.payment.dto.RefundResponse`, not a sales-specific shape.
export interface RefundResponse {
  id: string;
  paymentId: string;
  orderId: string;
  amount: number;
  reason: string;
  status: string;
}

export interface SalesFilter {
  from?: string;
  to?: string;
  status?: string;
  vendorId?: string;
  productId?: string;
  skuId?: string;
  paymentProvider?: string;
  q?: string;
  page?: number;
  size?: number;
}

export type SalesNotificationType = "CONFIRMATION" | "STATUS" | "DELIVERY_CODE";

// ---------------------------------------------------------------------------
// Spec 002 — Promotions
// (Backend DTO source of truth: backend/src/main/java/com/delivery/marketing/dto/
//  PromotionResponse.java / PromotionUpsertRequest.java. Named distinctly from the existing
//  `Promotion`/`CreatePromotionPayload` types above, which back the older, separate
//  `marketingService` coupon/promotion endpoints and do not share this shape.)
export type PromotionType = "PERCENTAGE" | "FIXED_AMOUNT";
export type PromotionScope = "ORDER" | "CATEGORY" | "PRODUCT";
export type PromotionStatus = "DRAFT" | "ACTIVE" | "PAUSED" | "EXPIRED";

export interface PromotionUpsertRequest {
  vendorId?: string | null;
  name: string;
  code?: string | null;
  type: PromotionType;
  value: number;
  scope: PromotionScope;
  targetCategoryId?: string | null;
  targetProductId?: string | null;
  minOrderTotal?: number | null;
  maxDiscountAmount?: number | null;
  startsAt: string;
  endsAt: string;
  usageLimit?: number | null;
  perCustomerLimit?: number | null;
}

export interface PromotionResponse extends PromotionUpsertRequest {
  id: string;
  usedCount: number;
  status: PromotionStatus;
  createdAt: string;
  updatedAt: string;
}

// ---------------------------------------------------------------------------
// Spec 002 — Sales reports
// (Backend DTO source of truth: backend/src/main/java/com/delivery/report/dto/*.)
export interface SalesSummary {
  from: string;
  to: string;
  orderCount: number;
  gross: number;
  discountTotal: number;
  refunds: number;
  net: number;
  commission: number;
  averageOrderValue: number;
  deliveredCount: number;
  cancelledCount: number;
}

// SalesBucketResponse does not repeat `from`/`to` — only the bucket timestamp — diverging from
// the fragment's `allOf: [SalesSummary, { bucket }]` composition.
export interface SalesBucket {
  bucket: string;
  orderCount: number;
  gross: number;
  discountTotal: number;
  refunds: number;
  net: number;
  commission: number;
  averageOrderValue: number;
  deliveredCount: number;
  cancelledCount: number;
}

export interface DimensionRow {
  key: string;
  label: string;
  gross: number;
  refunds: number;
  net: number;
  commission: number;
  sharePercent: number;
}

export interface ProductDimensionRow extends DimensionRow {
  quantitySold: number;
}

export type ReportInterval = "day" | "week" | "month";
export type SalesReportName = "summary" | "timeseries" | "by-vendor" | "by-product" | "by-category";

export interface ReportRangeParams {
  from: string;
  to: string;
  vendorId?: string;
}

// Upload types
export interface UploadUrlResponse {
  uploadUrl: string;
  storageKey: string;
  expiresIn: number;
}

// Error
export interface ApiError {
  code: string;
  message: string;
  fieldErrors?: { field: string; message: string }[];
  correlationId: string;
}

// Registration types
export interface MerchantRegistrationPayload {
  companyName: string;
  companySlug: string;
  legalName?: string;
  taxNumber?: string;
  businessType?: string;
  industry?: string;
  country: string;
  city?: string;
  address?: string;
  defaultCurrency: string;
  companyPhone?: string;
  companyEmail?: string;
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  password: string;
  referralCode?: string;
  promoCode?: string;
}

export interface MerchantRegistrationResponse {
  tenantId: string;
  tenantSlug: string;
  userProfileId: string;
  email: string;
  displayName: string;
}
