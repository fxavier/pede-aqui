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

// Category types
export interface Category {
  id: string;
  name: string;
  vertical: string;
  active: boolean;
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
}

export interface CreateProductPayload {
  vendorId: string;
  categoryId: string;
  name: string;
  description?: string;
  requiresPrescriptionMetadata?: boolean;
  prohibitedFuel?: boolean;
}

export interface CreateSkuPayload {
  productId: string;
  vendorId: string;
  skuCode: string;
  name: string;
  price: number;
  initialStock: number;
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
