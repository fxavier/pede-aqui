"use client";

import {
  ShoppingBag, TrendingUp, PercentCircle, RefreshCcw,
  Wallet, Landmark, Receipt, CheckCircle2, XCircle,
} from "lucide-react";
import { KpiCard } from "@/components/ui/kpi-card";
import { ErrorState } from "@/components/ui/error-state";
import { formatCurrency, formatNumber } from "@/lib/utils";
import type { SalesSummary } from "@/lib/api/types";

interface KpiGridProps {
  summary: SalesSummary | undefined;
  isLoading: boolean;
  isError: boolean;
  onRetry: () => void;
}

// Renders the nine summary KPI cards for the sales reports screen (AC-8.3).
export function KpiGrid({ summary, isLoading, isError, onRetry }: KpiGridProps) {
  if (isError) {
    return <ErrorState message="Erro ao carregar o resumo de vendas." onRetry={onRetry} />;
  }

  const cards = [
    { title: "Encomendas", value: summary ? formatNumber(summary.orderCount) : "—", icon: <ShoppingBag className="h-5 w-5" /> },
    { title: "Vendas Brutas", value: summary ? formatCurrency(summary.gross) : "—", icon: <TrendingUp className="h-5 w-5" /> },
    { title: "Descontos", value: summary ? formatCurrency(summary.discountTotal) : "—", icon: <PercentCircle className="h-5 w-5" /> },
    { title: "Reembolsos", value: summary ? formatCurrency(summary.refunds) : "—", icon: <RefreshCcw className="h-5 w-5" /> },
    { title: "Vendas Líquidas", value: summary ? formatCurrency(summary.net) : "—", icon: <Wallet className="h-5 w-5" /> },
    { title: "Comissão", value: summary ? formatCurrency(summary.commission) : "—", icon: <Landmark className="h-5 w-5" /> },
    { title: "Valor Médio (AOV)", value: summary ? formatCurrency(summary.averageOrderValue) : "—", icon: <Receipt className="h-5 w-5" /> },
    { title: "Entregues", value: summary ? formatNumber(summary.deliveredCount) : "—", icon: <CheckCircle2 className="h-5 w-5" /> },
    { title: "Cancelados", value: summary ? formatNumber(summary.cancelledCount) : "—", icon: <XCircle className="h-5 w-5" /> },
  ];

  return (
    <div className="grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-5">
      {cards.map((card) => (
        <KpiCard key={card.title} title={card.title} value={card.value} icon={card.icon} loading={isLoading} />
      ))}
    </div>
  );
}
