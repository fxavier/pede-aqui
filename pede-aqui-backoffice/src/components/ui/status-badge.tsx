import { cn } from "@/lib/utils";


interface StatusBadgeProps {
  status: string;
  className?: string;
}

const statusStyles: Record<string, string> = {
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
  ACTIVE: "bg-green-100 text-green-800",
  INACTIVE: "bg-surface-container-high text-on-surface-variant",
  SUSPENDED: "bg-red-100 text-red-800",
  VERIFIED: "bg-green-100 text-green-800",
  UNVERIFIED: "bg-yellow-100 text-yellow-800",
  OPEN: "bg-blue-100 text-blue-800",
  IN_PROGRESS: "bg-yellow-100 text-yellow-800",
  RESOLVED: "bg-green-100 text-green-800",
  CLOSED: "bg-surface-container-high text-on-surface-variant",
};

const statusLabels: Record<string, string> = {
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
  ACTIVE: "Activo",
  INACTIVE: "Inactivo",
  SUSPENDED: "Suspenso",
  VERIFIED: "Verificado",
  UNVERIFIED: "Não Verificado",
  OPEN: "Aberto",
  IN_PROGRESS: "Em Progresso",
  RESOLVED: "Resolvido",
  CLOSED: "Fechado",
};

export function StatusBadge({ status, className }: StatusBadgeProps) {
  const label = statusLabels[status] ?? status;
  const style = statusStyles[status] ?? "bg-surface-container-high text-on-surface-variant";

  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-bold",
        style,
        className,
      )}
    >
      {label}
    </span>
  );
}
