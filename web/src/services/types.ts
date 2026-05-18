export type DashboardAdmin = {
  activeVendors: number;
  activeCouriers: number;
  cancelledOrders: number;
  failedDeliveries: number;
};

export type DashboardVendor = {
  ordersByStatusCount: number;
  rejectedOrdersCount: number;
  salesTotal: number;
};

export type DashboardCourier = {
  completedDeliveries: number;
  failedDeliveries: number;
  activeAssignments: number;
};

export type DashboardFinance = {
  transactionsTotal: number;
  commissionsTotal: number;
  refundsTotal: number;
  unreconciledCashTotal: number;
};

export type Vendor = {
  id: string;
  name: string;
  categoryId: string;
  status: string;
  verificationStatus: string;
  rating: number;
  estimatedDeliveryMinutes: number;
  available: boolean;
};

export type DispatchJob = {
  id: string;
  orderId: string;
  deliveryId: string;
  courierId: string | null;
  status: string;
  rejectionReason: string | null;
};

export type FinanceSummary = {
  confirmedPaymentsTotal: number;
  commissionTotal: number;
  refundsTotal: number;
  unreconciledCashTotal: number;
};

export type NotificationItem = {
  id: string;
  type: string;
  title: string;
  message: string;
  businessReference: string | null;
  readAt: string | null;
  createdAt: string;
};

export type SupportTicketItem = {
  id: string;
  orderId: string | null;
  subject: string;
  description: string;
  status: string;
  classification: string;
  internalNote: string | null;
  assigneeUserId: string | null;
  createdAt: string;
};
