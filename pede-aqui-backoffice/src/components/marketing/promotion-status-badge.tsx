import { cn } from "@/lib/utils";
import type { PromotionStatus } from "@/lib/api/types";

// Spec 002 (US-7) — status badge for the new PromotionResponse.status enum.
// Kept local to the marketing feature instead of extending the shared
// StatusBadge component (which several other lanes touch concurrently).
const STATUS_STYLES: Record<PromotionStatus, string> = {
  DRAFT: "bg-surface-container-high text-on-surface-variant",
  ACTIVE: "bg-green-100 text-green-800",
  PAUSED: "bg-yellow-100 text-yellow-800",
  EXPIRED: "bg-red-100 text-red-800",
};

const STATUS_LABELS: Record<PromotionStatus, string> = {
  DRAFT: "Rascunho",
  ACTIVE: "Activa",
  PAUSED: "Em Pausa",
  EXPIRED: "Expirada",
};

export function PromotionStatusBadge({ status, className }: { status: PromotionStatus; className?: string }) {
  return (
    <span
      className={cn(
        "inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-bold",
        STATUS_STYLES[status] ?? "bg-surface-container-high text-on-surface-variant",
        className,
      )}
    >
      {STATUS_LABELS[status] ?? status}
    </span>
  );
}
