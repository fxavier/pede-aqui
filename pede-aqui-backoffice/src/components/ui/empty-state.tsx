import { Inbox } from "lucide-react";

interface EmptyStateProps {
  title?: string;
  message?: string;
  icon?: React.ReactNode;
}

export function EmptyState({
  title = "Nenhum dado encontrado",
  message = "Ainda não existem registos para mostrar.",
  icon,
}: EmptyStateProps) {
  return (
    <div className="flex flex-col items-center justify-center rounded-2xl border border-dashed border-outline-variant bg-white p-12 text-center">
      <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-full bg-surface-container-low">
        {icon ?? <Inbox className="h-8 w-8 text-on-surface-variant" />}
      </div>
      <h3 className="font-headline-sm text-headline-sm text-on-surface">{title}</h3>
      <p className="mt-2 max-w-md text-body-md text-on-surface-variant">{message}</p>
    </div>
  );
}
