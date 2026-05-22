"use client";

import { BackofficeShell } from "../../components/backoffice-shell";
import { useEffect, useState } from "react";
import { safeApiGet, type AsyncState } from "../../services/api";
import type { FinanceSummary } from "../../services/types";
import { FinanceDashboard } from "../../features/finance/finance-dashboard";

export default function FinancePage() {
  const [state, setState] = useState<AsyncState<FinanceSummary>>({ kind: "loading" });

  useEffect(() => {
    const token = process.env.NEXT_PUBLIC_APP_TOKEN ?? "dev-token";
    safeApiGet<FinanceSummary>("/finance/summary", token).then(setState);
  }, []);

  return (
    <BackofficeShell title="Financeiro" subtitle="Transacoes, comissoes, reembolsos e reconciliacao COD">
      <FinanceDashboard state={state} />
    </BackofficeShell>
  );
}
