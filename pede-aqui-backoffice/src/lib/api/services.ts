import { apiClient } from "./client";
import type {
  AdminDashboard,
  VendorDashboard,
  FinanceDashboard,
  CourierDashboard,
  Order,
  Vendor,
  Courier,
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

// Vendors
export const vendorService = {
  list: (available?: boolean) => apiClient.get<Vendor[]>(`/vendors${available !== undefined ? `?available=${available}` : ""}`),
  getById: (id: string) => apiClient.get<Vendor>(`/vendors/${id}`),
  create: (data: { name: string; category: string; status: string }) => apiClient.post<Vendor>("/vendors", data),
  update: (id: string, data: { name: string; category: string; status: string }) => apiClient.put<Vendor>(`/vendors/${id}`, data),
};

// Couriers
export const courierService = {
  getMe: () => apiClient.get<Courier>("/couriers/me"),
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

export const managementService = {
  orders: createCrudService("/orders/backoffice"),
  couriers: createCrudService("/couriers/backoffice"),
  finance: createCrudService("/finance/records"),
  marketing: createCrudService("/marketing/campaigns"),
  support: createCrudService("/support/tickets/backoffice"),
};
