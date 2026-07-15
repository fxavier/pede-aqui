// Local status label/style maps for the /sales screen. Kept local to this route (rather than
// extending the shared `status-badge.tsx`) to avoid touching a file other Lane F agents may
// also be editing concurrently; values mirror backend/src/main/java/com/delivery/order/entity/
// OrderStatus.java, PaymentStatus.java and RefundStatus.java.

export const ORDER_STATUS_LABELS: Record<string, string> = {
  PENDING: "Pendente",
  PAYMENT_PENDING: "Pag. Pendente",
  PAYMENT_CONFIRMED: "Pag. Confirmado",
  ACCEPTED_BY_VENDOR: "Aceite",
  REJECTED_BY_VENDOR: "Rejeitado",
  PREPARING: "Em Preparação",
  READY_FOR_PICKUP: "Pronto p/ Recolha",
  DISPATCH_PENDING: "Desp. Pendente",
  ASSIGNED_TO_COURIER: "Atribuído",
  PICKED_UP: "Recolhido",
  DELIVERING: "Em Entrega",
  DELIVERED: "Entregue",
  CANCELLED: "Cancelado",
  REFUND_PENDING: "Reemb. Pendente",
  REFUNDED: "Reembolsado",
};

export const ORDER_STATUS_VARIANT: Record<string, "default" | "secondary" | "outline" | "destructive" | "success"> = {
  PENDING: "outline",
  PAYMENT_PENDING: "secondary",
  PAYMENT_CONFIRMED: "outline",
  ACCEPTED_BY_VENDOR: "default",
  REJECTED_BY_VENDOR: "destructive",
  PREPARING: "default",
  READY_FOR_PICKUP: "default",
  DISPATCH_PENDING: "secondary",
  ASSIGNED_TO_COURIER: "default",
  PICKED_UP: "default",
  DELIVERING: "default",
  DELIVERED: "success",
  CANCELLED: "destructive",
  REFUND_PENDING: "secondary",
  REFUNDED: "secondary",
};

export const PAYMENT_STATUS_LABELS: Record<string, string> = {
  INITIATED: "Iniciado",
  PENDING_CONFIRMATION: "A Confirmar",
  CONFIRMED: "Confirmado",
  FAILED: "Falhou",
  CANCELLED: "Cancelado",
  REFUND_PENDING: "Reemb. Pendente",
  PARTIALLY_REFUNDED: "Parc. Reembolsado",
  REFUNDED: "Reembolsado",
};

export const PAYMENT_STATUS_VARIANT: Record<string, "default" | "secondary" | "outline" | "destructive" | "success"> = {
  INITIATED: "outline",
  PENDING_CONFIRMATION: "secondary",
  CONFIRMED: "success",
  FAILED: "destructive",
  CANCELLED: "destructive",
  REFUND_PENDING: "secondary",
  PARTIALLY_REFUNDED: "secondary",
  REFUNDED: "secondary",
};

export const REFUND_STATUS_LABELS: Record<string, string> = {
  REQUESTED: "Solicitado",
  APPROVED: "Aprovado",
  REJECTED: "Rejeitado",
  REFUNDED: "Reembolsado",
};

// Order statuses eligible for the "cancel" action per spec.md AC-6.1 (pre-dispatch only). Used
// as a client-side UX hint to hide the button on obviously-ineligible rows; the backend remains
// the authority and returns 409 for any other invalid transition.
export const CANCELLABLE_STATUSES = new Set([
  "PENDING",
  "PAYMENT_PENDING",
  "PAYMENT_CONFIRMED",
  "ACCEPTED_BY_VENDOR",
  "PREPARING",
]);

export const NOTIFICATION_TYPE_LABELS: Record<string, string> = {
  CONFIRMATION: "Confirmação de Encomenda",
  STATUS: "Actualização de Estado",
  DELIVERY_CODE: "Código de Entrega",
};

export function orderStatusLabel(status: string): string {
  return ORDER_STATUS_LABELS[status] ?? status;
}

export function orderStatusVariant(status: string): "default" | "secondary" | "outline" | "destructive" | "success" {
  return ORDER_STATUS_VARIANT[status] ?? "outline";
}

export function paymentStatusLabel(status: string): string {
  return PAYMENT_STATUS_LABELS[status] ?? status;
}

export function paymentStatusVariant(status: string): "default" | "secondary" | "outline" | "destructive" | "success" {
  return PAYMENT_STATUS_VARIANT[status] ?? "outline";
}

export function refundStatusLabel(status: string): string {
  return REFUND_STATUS_LABELS[status] ?? status;
}

/** Extracts a human-readable message from a thrown API error (see src/lib/http-client.ts —
 *  non-ok responses throw the parsed JSON error body directly, shaped as ErrorResponse). */
export function apiErrorMessage(err: unknown, fallback: string): string {
  if (err && typeof err === "object" && "message" in err && typeof (err as { message?: unknown }).message === "string") {
    return (err as { message: string }).message;
  }
  if (err instanceof Error && err.message) return err.message;
  return fallback;
}

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function isUuid(value: string): boolean {
  return UUID_RE.test(value.trim());
}
