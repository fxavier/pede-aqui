import { apiClient } from "./client";
import type {
  AdminDashboard,
  VendorDashboard,
  FinanceDashboard,
  CourierDashboard,
  Order,
  Vendor,
  VendorDocument,
  Category,
  Courier,
  CourierDocument,
  UserProfile,
  UploadUrlResponse,
  Transaction,
  Commission,
  Refund,
  SupportTicket,
  Notification,
} from "./types";

// Auth
export const authService = {
  getMe: () => apiClient.get<{ id: string; displayName: string; email: string; roles: string[] }>("/me"),
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
};

// Categories
export const categoryService = {
  list: async (): Promise<Category[]> => {
    try {
      // Try different possible endpoints for categories
      return await apiClient.get<Category[]>("/catalog/categories");
    } catch (error) {
      try {
        return await apiClient.get<Category[]>("/admin/categories");
      } catch (adminError) {
        // Fallback categories if endpoints don't exist
        console.warn("Categories endpoints not available, using fallback categories");
        return [
          { id: "restaurant", name: "Restaurante", vertical: "food", active: true },
          { id: "grocery", name: "Mercearia", vertical: "retail", active: true },
          { id: "pharmacy", name: "Farmácia", vertical: "health", active: true },
          { id: "other", name: "Outro", vertical: "general", active: true },
        ];
      }
    }
  },
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
};

// Finance
export const financeService = {
  getTransactions: () => apiClient.get<Transaction[]>("/finance/transactions"),
  getCommissions: () => apiClient.get<Commission[]>("/finance/commissions"),
  getRefunds: () => apiClient.get<Refund[]>("/finance/refunds"),
  getCashReconciliation: () => apiClient.get<unknown[]>("/finance/cash-reconciliation"),
  getSummary: () => apiClient.get<{ totalTransactions: number; totalCommissions: number; totalRefunds: number }>("/finance/summary"),
  getPayoutStatus: () => apiClient.get<{ pending: number; settled: number }>("/finance/payout-status"),
};

// Support Tickets
export const supportService = {
  list: () => apiClient.get<SupportTicket[]>("/support/tickets"),
  getMine: () => apiClient.get<SupportTicket[]>("/support/tickets/mine"),
  create: (data: { subject: string; description: string; orderId?: string }) =>
    apiClient.post<SupportTicket>("/support/tickets", data),
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

// Users - Note: No user management endpoints exist in backend yet
export const userService = {
  // These endpoints are not implemented in the backend
  list: () => Promise.reject(new Error("User management endpoints not implemented in backend")),
  getById: (id: string) => Promise.reject(new Error("User management endpoints not implemented in backend")),
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
  }) => Promise.reject(new Error("User management endpoints not implemented in backend")),
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
