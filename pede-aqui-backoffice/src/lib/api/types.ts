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
  salesSummary: {
    totalRevenue: number;
    totalOrders: number;
    averageOrderValue: number;
  };
  ordersByStatus: Record<string, number>;
  topProducts: { name: string; quantity: number; revenue: number }[];
  rejectedOrders: number;
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
  customerName: string;
  vendorName: string;
  status: string;
  total: number;
  createdAt: string;
  items: OrderItem[];
}

export interface OrderItem {
  name: string;
  quantity: number;
  price: number;
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
  orderReference: string;
  amount: number;
  method: string;
  status: string;
  createdAt: string;
}

export interface Commission {
  id: string;
  orderReference: string;
  vendorName: string;
  basisAmount: number;
  rate: number;
  amount: number;
  status: string;
}

export interface Refund {
  id: string;
  orderReference: string;
  amount: number;
  reason: string;
  status: string;
  createdAt: string;
}

// Support ticket types
export interface SupportTicket {
  id: string;
  subject: string;
  status: string;
  classification: string;
  createdBy: string;
  orderReference?: string;
  createdAt: string;
  internalNotes?: string;
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
