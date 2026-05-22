import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function formatNumber(value: number) {
  return new Intl.NumberFormat("pt-PT").format(value);
}

export function formatCurrency(value: number) {
  return new Intl.NumberFormat("pt-PT", { style: "currency", currency: "MZN" })
    .format(value)
    .replace("MZN", "")
    .trim() + " MT";
}

export function formatDate(value: string) {
  return new Intl.DateTimeFormat("pt-PT", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

export type OrderStatus = 
  | "PENDING" | "PAYMENT_PENDING" | "PAYMENT_CONFIRMED" 
  | "ACCEPTED_BY_VENDOR" | "REJECTED_BY_VENDOR" | "PREPARING" 
  | "READY_FOR_PICKUP" | "DISPATCH_PENDING" | "ASSIGNED_TO_COURIER" 
  | "PICKED_UP" | "DELIVERING" | "DELIVERED" 
  | "CANCELLED" | "REFUND_PENDING" | "REFUNDED";

export const orderStatusLabels: Record<OrderStatus, string> = {
  PENDING: "Pendente",
  PAYMENT_PENDING: "Pagamento Pendente",
  PAYMENT_CONFIRMED: "Pagamento Confirmado",
  ACCEPTED_BY_VENDOR: "Aceite",
  REJECTED_BY_VENDOR: "Rejeitado",
  PREPARING: "Em Preparo",
  READY_FOR_PICKUP: "Pronto para Recolha",
  DISPATCH_PENDING: "Aguardar Estafeta",
  ASSIGNED_TO_COURIER: "Atribuído",
  PICKED_UP: "Recolhido",
  DELIVERING: "Em Entrega",
  DELIVERED: "Entregue",
  CANCELLED: "Cancelado",
  REFUND_PENDING: "Reembolso Pendente",
  REFUNDED: "Reembolsado",
};

export const orderStatusColors: Record<OrderStatus, string> = {
  PENDING: "bg-yellow-100 text-yellow-800",
  PAYMENT_PENDING: "bg-yellow-100 text-yellow-800",
  PAYMENT_CONFIRMED: "bg-green-100 text-green-800",
  ACCEPTED_BY_VENDOR: "bg-green-100 text-green-800",
  REJECTED_BY_VENDOR: "bg-red-100 text-red-800",
  PREPARING: "bg-blue-100 text-blue-800",
  READY_FOR_PICKUP: "bg-green-100 text-green-800",
  DISPATCH_PENDING: "bg-yellow-100 text-yellow-800",
  ASSIGNED_TO_COURIER: "bg-blue-100 text-blue-800",
  PICKED_UP: "bg-blue-100 text-blue-800",
  DELIVERING: "bg-blue-100 text-blue-800",
  DELIVERED: "bg-green-100 text-green-800",
  CANCELLED: "bg-red-100 text-red-800",
  REFUND_PENDING: "bg-yellow-100 text-yellow-800",
  REFUNDED: "bg-green-100 text-green-800",
};
